package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
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
    public static final String APG_FL_KD_NM = "편성요청서반입";

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
    List<String> archive(List<ArchivePlanItem> plan) {
        Set<String> failedFileKeys = new LinkedHashSet<>();
        Map<ArchiveGroupKey, Set<String>> apfMngNosByGroup = new LinkedHashMap<>();

        for (ArchivePlanItem item : plan) {
            String deptCode = item.effectiveDeptCode();
            if (!StringUtils.hasText(item.archiveGroupKey()) || !StringUtils.hasText(deptCode)) {
                continue;
            }
            ArchiveGroupKey groupKey = new ArchiveGroupKey(item.archiveGroupKey(), deptCode);
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

        for (ArchivePlanItem item : plan) {
            if (!StringUtils.hasText(item.archiveGroupKey())
                    || !StringUtils.hasText(item.effectiveDeptCode())) continue;
            Set<String> targets = new LinkedHashSet<>();
            for (Map.Entry<ArchiveGroupKey, Set<String>> group : apfMngNosByGroup.entrySet()) {
                ArchiveGroupKey key = group.getKey();
                if (!key.effectiveDeptCode().equals(item.effectiveDeptCode())) continue;
                if (key.archiveGroupKey().equals(item.archiveGroupKey())
                        || key.archiveGroupKey().startsWith(item.archiveGroupKey() + "/")) {
                    targets.addAll(group.getValue());
                }
            }
            if (!targets.isEmpty())
                archiveOne(
                        item,
                        targets,
                        new ArchiveGroupKey(item.archiveGroupKey(), item.effectiveDeptCode()),
                         failedFileKeys);
        }
        return List.copyOf(failedFileKeys);
    }

    /**
     * 파일 1건을 폴더가 만든 모든 신청서번호에 연결합니다.
     *
     * <p>첫 신청서번호에만 디스크에 쓰고, 나머지는 그 물리 파일을 공유하는 메타행만 만듭니다.
     */
    private void archiveOne(
            ArchivePlanItem item,
            Set<String> apfMngNos,
            ArchiveGroupKey groupKey,
            Set<String> failedFileKeys) {
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
            failedFileKeys.add(failureKey(item));
            return;
        }

        while (applicationNumbers.hasNext()) {
            String apfMngNo = applicationNumbers.next();
            try {
                fileService.linkExistingFile(sourceFlMpnId, uploadRequest(apfMngNo, null, null));
            } catch (RuntimeException e) {
                // 보관 실패가 이미 커밋된 원장을 되돌리게 두지 않는다. 해당 건은 파일 0건 상태로 남는다
                log.error(
                        "편성요청서 반입 원본 보관 실패: archiveGroup={}, deptCode={}, apfMngNo={}, fileName={}",
                        groupKey.archiveGroupKey(),
                        groupKey.effectiveDeptCode(),
                        apfMngNo,
                        file.getOriginalFilename(),
                        e);
                failedFileKeys.add(failureKey(item));
            }
        }
    }

    private FileDto.UploadRequest request(String apfMngNo, ArchivePlanItem item) {
        String displayFileName =
                RequestFormArchiveMetadata.fitFileName(item.file().getOriginalFilename());
        String relativePath = RequestFormRelativePath.normalize(item.fileKey(), displayFileName);
        return uploadRequest(
                apfMngNo,
                RequestFormArchiveMetadata.fitRelativePath(relativePath),
                displayFileName);
    }

    /** 실패 보고에 사용할 안전한 상대 식별자를 만듭니다. 잘못된 키는 업로드 파일명으로 대체합니다. */
    private String failureKey(ArchivePlanItem item) {
        String displayFileName =
                RequestFormArchiveMetadata.fitFileName(item.file().getOriginalFilename());
        try {
            RequestFormRelativePath.normalize(item.fileKey(), displayFileName);
            if (item.fileKey() == null || item.fileKey().isBlank()) return displayFileName;
            return RequestFormArchiveMetadata.fitRelativePath(item.fileKey().replace('\\', '/'));
        } catch (RuntimeException ignored) {
            return displayFileName;
        }
    }

    /**
     * 업로드 요청을 만듭니다.
     *
     * <p>Lombok이 만드는 빌더 타입을 반환하지 않습니다 — {@code javadoc} 태스크는 애너테이션 처리 결과를 보지 못해 생성 타입을 시그니처에서 만나면
     * {@code cannot find symbol}로 실패합니다(BE-74).
     *
     * @param apfMngNo 신청서관리번호
     * @param relativePath 원본 폴더 상대경로. 상대경로가 없는 재연결에는 {@code null}
     * @return 업로드 요청 DTO
     */
    private FileDto.UploadRequest uploadRequest(
            String apfMngNo, String relativePath, String displayFileName) {
        return FileDto.UploadRequest.builder()
                .flTpCone(FL_TP_CONE)
                .apgFlKdNm(APG_FL_KD_NM)
                .apgFlLnkCtzNm(apfMngNo)
                .relativePath(relativePath)
                .displayFileName(displayFileName)
                .build();
    }
}
