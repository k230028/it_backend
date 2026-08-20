package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 반입한 편성요청서 원본을 공통첨부파일에 보관합니다.
 *
 * <p>보관 단위는 <b>사업 보관 그룹과 파일 처리에서 확정한 부서코드의 조합</b>입니다. 사업 폴더가 같아도 검증 코드가 다르거나, 검증 코드가 같아도 사업 폴더가 다르면
 * 파일과 신청서번호를 섞지 않습니다. 두 값이 모두 같은 그룹의 파일만 그 그룹이 만든 모든 원장에서 함께 보이도록 연결합니다. 다만 디스크 기록은 <b>파일당 1회</b>이고
 * 두 번째 연결부터는 물리 경로를 공유하는 메타행만 추가합니다({@link FileService#linkExistingFile(String,
 * FileDto.UploadRequest)}).
 *
 * <p>APPLIED 파일이 만든 신청서번호를 기준으로, 같은 폴더·부서코드의 보관 전용 첨부파일까지 함께 연결합니다.
 *
 * <p>이 클래스는 <b>예외를 밖으로 던지지 않습니다</b>. 원장 반영이 이미 커밋된 뒤에 실행되므로, 보관 실패로 반입 전체를 실패로 돌리면 되돌릴 수 없는 원장이 남은
 * 채 사용자에게 실패로 보입니다. 보관에 실패한 건은 파일이 0건인 상태가 되어 화면에서 안내 문구로 흐르며, 원인은 ERROR 로그로 남습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestFormSourceFileArchiver {

    /** 공통첨부파일 주식별자컬럼명. 이 값이 반입 원본 파일 종류의 단일 출처입니다. */
    public static final String PK_COL_NM = "편성요청서반입";

    /** 공통첨부파일 파일유형내용. 반입 원본은 이미지가 아니라 첨부파일입니다. */
    private static final String FL_TP_CONE = "첨부파일";

    private final FileService fileService;

    /** 파일별 처리 결과와 원본 보관 그룹·실제 검증 부서코드를 묶습니다. */
    record ArchivePlanItem(
            MultipartFile file,
            String fileKey,
            String archiveGroupKey,
            String effectiveDeptCode,
            RequestFormDto.FileResult result) {}

    /** 사업 폴더와 검증된 부서코드를 모두 보존하는 보관 그룹 키입니다. */
    private record ArchiveGroupKey(String archiveGroupKey, String effectiveDeptCode) {}

    /**
     * 반입 배치의 원본 파일을 사업 보관 그룹·검증된 부서코드 조합 단위로 보관합니다.
     *
     * <p>호출자는 commit 경로에서만 부릅니다. dry-run은 원장을 만들지 않으므로 보관할 대상도 없습니다. 보관 중 파일 저장이나 재연결이 실패하면 ERROR
     * 로그만 남기고 예외를 전파하지 않습니다.
     *
     * @param plan 업로드 파일·실제 적용 부서코드·파일별 반영 결과를 묶은 내부 계획
     */
    void archive(List<ArchivePlanItem> plan) {
        // 사업 보관 그룹·실제 적용 부서코드별로 (파일 목록, 신청서번호 집합)을 모은다
        Map<ArchiveGroupKey, List<ArchivePlanItem>> filesByGroup = new LinkedHashMap<>();
        Map<ArchiveGroupKey, Set<String>> apfMngNosByGroup = new LinkedHashMap<>();

        for (ArchivePlanItem item : plan) {
            String deptCode = item.effectiveDeptCode();
            if (!StringUtils.hasText(item.archiveGroupKey()) || !StringUtils.hasText(deptCode)) {
                continue;
            }
            ArchiveGroupKey groupKey = new ArchiveGroupKey(item.archiveGroupKey(), deptCode);
            filesByGroup.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(item);
            RequestFormDto.FileResult result = item.result();
            if (result == null || result.status() != RequestFormDto.FileStatus.APPLIED) {
                continue;
            }
            Set<String> apfMngNos =
                    apfMngNosByGroup.computeIfAbsent(groupKey, key -> new LinkedHashSet<>());
            for (RequestFormDto.CreatedRecord created : result.created()) {
                if (created.apfMngNo() != null) {
                    apfMngNos.add(created.apfMngNo());
                }
            }
        }

        for (Map.Entry<ArchiveGroupKey, List<ArchivePlanItem>> group : filesByGroup.entrySet()) {
            Set<String> apfMngNos = apfMngNosByGroup.getOrDefault(group.getKey(), Set.of());
            if (apfMngNos.isEmpty()) {
                continue;
            }
            for (ArchivePlanItem item : group.getValue()) {
                archiveOne(item, apfMngNos, group.getKey());
            }
        }
    }

    /**
     * 파일 1건을 폴더가 만든 모든 신청서번호에 연결합니다.
     *
     * <p>첫 신청서번호에만 디스크에 쓰고, 나머지는 그 물리 파일을 공유하는 메타행만 만듭니다.
     */
    private void archiveOne(ArchivePlanItem item, Set<String> apfMngNos, ArchiveGroupKey groupKey) {
        MultipartFile file = item.file();
        Iterator<String> applicationNumbers = apfMngNos.iterator();
        String firstApfMngNo = applicationNumbers.next();
        String sourceFlMpnId;
        try {
            sourceFlMpnId = fileService.uploadFile(file, request(firstApfMngNo, item));
        } catch (RuntimeException e) {
            // 원본 파일을 확보하지 못하면 나머지 신청서번호에는 재연결할 물리 파일도 없다
            log.error(
                    "편성요청서 반입 원본 보관 실패: archiveGroup={}, deptCode={}, apfMngNo={}, fileName={}",
                    groupKey.archiveGroupKey(),
                    groupKey.effectiveDeptCode(),
                    firstApfMngNo,
                    file.getOriginalFilename(),
                    e);
            return;
        }

        while (applicationNumbers.hasNext()) {
            String apfMngNo = applicationNumbers.next();
            try {
                fileService.linkExistingFile(sourceFlMpnId, request(apfMngNo).build());
            } catch (RuntimeException e) {
                // 보관 실패가 이미 커밋된 원장을 되돌리게 두지 않는다. 해당 건은 파일 0건 상태로 남는다
                log.error(
                        "편성요청서 반입 원본 보관 실패: archiveGroup={}, deptCode={}, apfMngNo={}, fileName={}",
                        groupKey.archiveGroupKey(),
                        groupKey.effectiveDeptCode(),
                        apfMngNo,
                        file.getOriginalFilename(),
                        e);
            }
        }
    }

    private FileDto.UploadRequest request(String apfMngNo, ArchivePlanItem item) {
        return request(apfMngNo)
                .relativePath(
                        RequestFormRelativePath.normalize(
                                item.fileKey(), item.file().getOriginalFilename()))
                .build();
    }

    private FileDto.UploadRequest.UploadRequestBuilder request(String apfMngNo) {
        return FileDto.UploadRequest.builder()
                .flTpCone(FL_TP_CONE)
                .pkColNm(PK_COL_NM)
                .pkCone(apfMngNo);
    }
}
