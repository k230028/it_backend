package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 반입한 편성요청서 원본을 공통첨부파일에 보관합니다.
 *
 * <p>보관 단위는 <b>부점 폴더</b>입니다. 한 폴더의 파일은 그 폴더가 만든 모든 원장에서 함께 보여야 하므로, 폴더가 만든 신청서번호마다 연결을 만듭니다. 다만 디스크
 * 기록은 <b>파일당 1회</b>이고 두 번째 연결부터는 물리 경로를 공유하는 메타행만 추가합니다({@link FileService#linkExistingFile(String,
 * FileDto.UploadRequest)}).
 *
 * <p>APPLIED 파일만 보관합니다. BLOCKED·FAILED 파일은 원장을 만들지 않아 붙일 신청서번호가 없고, 같은 폴더의 정상 건에 얹으면 그 사업과 무관한 실패
 * 파일이 목록에 섞입니다. 반입 실패는 반입 화면의 진단이 다룹니다.
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

    /**
     * 반입 배치의 원본 파일을 부점 폴더 단위로 보관합니다.
     *
     * <p>호출자는 commit 경로에서만 부릅니다. dry-run은 원장을 만들지 않으므로 보관할 대상도 없습니다. 보관 중 파일 저장이나 재연결이 실패하면 ERROR
     * 로그만 남기고 예외를 전파하지 않습니다.
     *
     * @param files 업로드 파일. {@code manifest.entries()}와 순서로 짝지어집니다
     * @param manifest 파일별 부가 정보
     * @param results 파일별 반영 결과. {@code fileKey}로 manifest 항목과 이어집니다
     */
    public void archive(
            List<MultipartFile> files,
            RequestFormDto.ImportManifest manifest,
            List<RequestFormDto.FileResult> results) {
        Map<String, RequestFormDto.FileResult> resultByFileKey = new LinkedHashMap<>();
        for (RequestFormDto.FileResult result : results) {
            resultByFileKey.put(result.fileKey(), result);
        }

        // 부점 폴더별로 (보관할 파일 목록, 그 폴더가 만든 신청서번호 집합)을 모은다
        Map<String, List<MultipartFile>> filesByDept = new LinkedHashMap<>();
        Map<String, Set<String>> apfMngNosByDept = new LinkedHashMap<>();

        for (int i = 0; i < files.size() && i < manifest.entries().size(); i++) {
            RequestFormDto.FileEntry entry = manifest.entries().get(i);
            RequestFormDto.FileResult result = resultByFileKey.get(entry.fileKey());
            if (result == null || result.status() != RequestFormDto.FileStatus.APPLIED) {
                continue;
            }
            String dept = entry.deptName();
            filesByDept.computeIfAbsent(dept, key -> new ArrayList<>()).add(files.get(i));
            Set<String> apfMngNos =
                    apfMngNosByDept.computeIfAbsent(dept, key -> new LinkedHashSet<>());
            for (RequestFormDto.CreatedRecord created : result.created()) {
                if (created.apfMngNo() != null) {
                    apfMngNos.add(created.apfMngNo());
                }
            }
        }

        for (Map.Entry<String, List<MultipartFile>> group : filesByDept.entrySet()) {
            Set<String> apfMngNos = apfMngNosByDept.getOrDefault(group.getKey(), Set.of());
            if (apfMngNos.isEmpty()) {
                continue;
            }
            for (MultipartFile file : group.getValue()) {
                archiveOne(file, apfMngNos, group.getKey());
            }
        }
    }

    /**
     * 파일 1건을 폴더가 만든 모든 신청서번호에 연결합니다.
     *
     * <p>첫 신청서번호에만 디스크에 쓰고, 나머지는 그 물리 파일을 공유하는 메타행만 만듭니다.
     */
    private void archiveOne(MultipartFile file, Set<String> apfMngNos, String deptName) {
        String sourceFlMpnId = null;
        for (String apfMngNo : apfMngNos) {
            try {
                if (sourceFlMpnId == null) {
                    sourceFlMpnId = fileService.uploadFile(file, request(apfMngNo));
                } else {
                    fileService.linkExistingFile(sourceFlMpnId, request(apfMngNo));
                }
            } catch (RuntimeException e) {
                // 보관 실패가 이미 커밋된 원장을 되돌리게 두지 않는다. 해당 건은 파일 0건 상태로 남는다
                log.error(
                        "편성요청서 반입 원본 보관 실패: deptName={}, apfMngNo={}, fileName={}",
                        deptName,
                        apfMngNo,
                        file.getOriginalFilename(),
                        e);
            }
        }
    }

    private FileDto.UploadRequest request(String apfMngNo) {
        return FileDto.UploadRequest.builder()
                .flTpCone(FL_TP_CONE)
                .pkColNm(PK_COL_NM)
                .pkCone(apfMngNo)
                .build();
    }
}
