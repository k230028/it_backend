package com.kdb.it.infra.file.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공통 첨부파일 서비스
 *
 * <p>TPRMPP_CFILEM 테이블의 파일 업로드·조회·수정·삭제·다운로드 비즈니스 로직을 처리합니다.
 *
 * <p>서버 파일명 채번 규칙:
 *
 * <pre>
 * {서버ID}_{yyyyMMddHHmmss}_{UUID without hyphens}.{확장자}
 * 예) SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf
 * </pre>
 *
 * <p>UUID를 포함하므로 1번·2번 서버가 동시에 동일한 파일명을 생성할 확률이 사실상 0입니다. 서버ID(SVR1/SVR2)를 접두어로 추가하여 어느 서버에서 업로드된
 * 파일인지 추적 가능합니다.
 *
 * <p>파일 저장 경로 구조:
 *
 * <pre>
 * {basePath}/{원본구분}/{년도}/{월}/
 * 예) /data/files/요구사항정의서/2026/03/
 * </pre>
 *
 * <p>Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다 (물리 파일은 유지).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    /** 공통 첨부파일 데이터 접근 리포지토리 */
    private final FileRepository fileRepository;

    /** 파일 읽기 권한 검증 — 목록 결과를 사용자별 읽기 가능 파일로 필터링 */
    private final FileOwnershipChecker fileOwnershipChecker;

    /** 파일별 업로드를 독립 트랜잭션으로 처리하는 단위 서비스 */
    private final FileUploadUnitService fileUploadUnitService;

    /** 파일 저장 기본 경로 운영 환경에서는 공유 스토리지 또는 NAS 경로를 지정하는 것을 권장합니다. */
    @Value("${app.file.base-path:/data/files}")
    private String basePath;

    /**
     * 목록 인가 판정 요청 범위 캐시 키.
     *
     * <p>파일 읽기 권한은 {@code (PK_COL_NM, PK_CONE, user)}의 순수 함수이므로 같은 (종류, 부모)를 가리키는 파일은 동일한 판정을 공유한다.
     * {@link #getFiles} 안에서만 쓰이는 메서드 지역 캐시의 키로 사용하며, 사용자·요청 사이에 공유되지 않는다.
     */
    private record FileReadKey(String pkColNm, String pkCone) {}

    /** 엔티티 → 응답 DTO 변환 */
    private FileDto.Response toResponse(Cfilem cfilem) {
        String flMpnId = cfilem.getFlMpnId();
        return FileDto.Response.builder()
                .flMpnId(flMpnId)
                .flNm(cfilem.getFlNm())
                .flPysNm(cfilem.getFlPysNm())
                .flKpnPth(cfilem.getFlKpnPth())
                .flTpCone(cfilem.getFlTpCone())
                .pkCone(cfilem.getPkCone())
                .pkColNm(cfilem.getPkColNm())
                .fstEnrDtm(cfilem.getFstEnrDtm())
                .fstEnrUsid(cfilem.getFstEnrUsid())
                // 프론트엔드에서 URL 조합 불필요하도록 직접 제공
                .previewUrl("/api/files/" + flMpnId + "/preview")
                .downloadUrl("/api/files/" + flMpnId + "/download")
                .build();
    }

    // ─────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────

    /**
     * 파일 단건 조회
     *
     * @param flMpnId 파일매핑ID (예: FL_00000001)
     * @return 파일 조회 응답 DTO
     * @throws CustomGeneralException 파일이 존재하지 않는 경우
     */
    public FileDto.Response getFile(String flMpnId) {
        Cfilem cfilem =
                fileRepository
                        .findByFlMpnIdAndDelYn(flMpnId, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));
        return toResponse(cfilem);
    }

    /**
     * 조건별 파일 목록 조회
     *
     * <p>조회 우선순위:
     *
     * <ol>
     *   <li>orcDtt + orcPkVl + flDtt 모두 입력 → 세 조건으로 필터링
     *   <li>orcDtt + orcPkVl 입력 → 두 조건으로 필터링
     *   <li>orcDtt만 입력 → 해당 원본구분 전체 조회
     * </ol>
     *
     * @param condition 검색 조건 (orcDtt 필수, orcPkVl·flDtt 선택)
     * @param user 현재 사용자 — 읽기 권한 필터링에 사용 (게시판 비공개 파일 제외)
     * @return 파일 조회 응답 DTO 목록 (읽기 가능한 파일만)
     * @throws CustomGeneralException orcDtt 미입력 시
     */
    public List<FileDto.Response> getFiles(
            FileDto.SearchCondition condition, CustomUserDetails user) {
        if (!StringUtils.hasText(condition.getPkColNm())) {
            throw new CustomGeneralException("주식별자컬럼명(pkColNm)은 필수입니다.");
        }

        List<Cfilem> list;

        if (StringUtils.hasText(condition.getPkCone())
                && StringUtils.hasText(condition.getFlTpCone())) {
            // 주식별자컬럼명 + 주식별자내용 + 파일유형내용 필터링
            list =
                    fileRepository.findAllByPkColNmAndPkConeAndFlTpConeAndDelYn(
                            condition.getPkColNm(),
                            condition.getPkCone(),
                            condition.getFlTpCone(),
                            "N");
        } else if (StringUtils.hasText(condition.getPkCone())) {
            // 주식별자컬럼명 + 주식별자내용 필터링
            list =
                    fileRepository.findAllByPkColNmAndPkConeAndDelYn(
                            condition.getPkColNm(), condition.getPkCone(), "N");
        } else {
            // 주식별자컬럼명 전체 조회
            list = fileRepository.findAllByPkColNmAndDelYn(condition.getPkColNm(), "N");
        }

        // 같은 (종류, 부모) 파일은 판정을 한 번만 계산해 재사용한다(요청 범위 캐시 → 부모 조회 N+1 제거).
        // 캐시는 이 메서드 호출 동안에만 사는 지역 변수이므로 사용자·요청 사이에 공유되지 않는다.
        Map<FileReadKey, Boolean> decisions = new HashMap<>();
        return list.stream()
                .filter(
                        file ->
                                decisions.computeIfAbsent(
                                        new FileReadKey(file.getPkColNm(), file.getPkCone()),
                                        ignored -> fileOwnershipChecker.canRead(file, user)))
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────

    /**
     * 파일 단건 업로드
     *
     * <p>처리 순서:
     *
     * <ol>
     *   <li>저장 디렉토리 생성 (없으면 자동 생성)
     *   <li>서버 파일명 채번 ({서버ID}_{타임스탬프}_{UUID}.{확장자})
     *   <li>파일 관리번호 채번 (Oracle 시퀀스)
     *   <li>파일을 서버 디스크에 저장
     *   <li>파일 메타데이터를 DB에 저장
     * </ol>
     *
     * @param file 업로드할 파일 (MultipartFile)
     * @param request 파일 메타데이터 요청 DTO
     * @return 생성된 파일관리번호
     * @throws CustomGeneralException 빈 파일 또는 파일 저장 실패 시
     */
    @Transactional
    public String uploadFile(MultipartFile file, FileDto.UploadRequest request) {
        return uploadFileInternal(file, request).getFlMpnId();
    }

    /**
     * 파일 단건 업로드 내부 구현
     *
     * <p>물리 파일을 디스크에 저장한 뒤 메타데이터 엔티티를 영속화하고, 영속화된 {@link Cfilem} 엔티티를 그대로 반환합니다.
     *
     * <p>[재쿼리 회피 이유]<br>
     * 수동 부여된 ID({@code flMpnId})로 {@code save()}를 호출할 때 Spring Data JPA는 내부적으로 {@code merge()}
     * 세만틱으로 동작해 INSERT가 트랜잭션 커밋 전까지 지연될 수 있습니다. 이 상태에서 동일 트랜잭션 내 {@code findByFlMpnIdAndDelYn(...)}
     * 같은 derived 쿼리가 바로 실행되면 flush가 보장되지 않아 "존재하지 않는 파일입니다. 파일관리번호: FL_xxxxxxxx" 오류가 발생합니다. 본 메서드는
     * 저장된 엔티티를 그대로 반환하여 호출 측이 재쿼리 없이 DTO 변환을 수행할 수 있게 합니다.
     */
    @Transactional
    protected Cfilem uploadFileInternal(MultipartFile file, FileDto.UploadRequest request) {
        return fileUploadUnitService.uploadFileInNewTransaction(file, request);
    }

    /**
     * 파일 단건 업로드 후 전체 정보 반환
     *
     * <p>업로드 완료 즉시 {@link FileDto.Response}를 반환합니다. {@code previewUrl}, {@code downloadUrl}이 포함되어
     * 있어 Tiptap 에디터에서 {@code response.previewUrl}을 {@code <img src>}에 바로 주입할 수 있습니다.
     *
     * <p>저장 직후 재조회 시 수동 부여 ID + merge() flush 지연으로 "존재하지 않는 파일" 오류가 발생할 수 있어 저장된 엔티티를 그대로 DTO로 변환하여
     * 반환합니다.
     *
     * @param file 업로드할 파일
     * @param request 파일 메타데이터 요청 DTO
     * @return 업로드된 파일의 전체 정보 (previewUrl, downloadUrl 포함)
     */
    @Transactional
    public FileDto.Response uploadFileAndGet(MultipartFile file, FileDto.UploadRequest request) {
        Cfilem saved = uploadFileInternal(file, request);
        return toResponse(saved);
    }

    /**
     * 파일 다건 일괄 업로드
     *
     * <p>개별 파일 업로드를 반복하며 특정 파일이 실패해도 후속 파일 처리를 계속하고 결과에 성공·실패 목록을 모두 포함합니다. 다만 영속성 예외가 현재 트랜잭션을
     * rollback-only로 표시하면 최종 커밋에서 전체 DB 변경이 롤백될 수 있습니다.
     *
     * @param files 업로드할 파일 목록
     * @param request 공통 메타데이터 (모든 파일에 동일하게 적용)
     * @return 일괄 업로드 결과 DTO (성공 목록 + 실패 파일명 목록)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileDto.BulkUploadResponse uploadFiles(
            List<MultipartFile> files, FileDto.UploadRequest request) {
        List<FileDto.Response> successList = new ArrayList<>();
        List<String> failList = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                // 업로드 직후 동일 트랜잭션 내 재조회는 merge() flush 지연으로 실패할 수 있음 →
                // 영속화된 엔티티를 그대로 DTO로 변환
                Cfilem saved = fileUploadUnitService.uploadFileInNewTransaction(file, request);
                successList.add(toResponse(saved));
            } catch (Exception e) {
                // 다건 업로드 중 일부 실패는 전체를 중단하지 않고 실패 목록으로 수집한다.
                // 단, 원본 파일명과 스택트레이스를 warn으로 남겨 실패 원인을 추적한다.
                String fileName = failureFileName(file);
                log.warn("[파일] 업로드 실패 - fileName={}", fileName, e);
                failList.add(fileName + " (" + e.getMessage() + ")");
            }
        }

        return FileDto.BulkUploadResponse.builder()
                .successList(successList)
                .failList(failList)
                .build();
    }

    private String failureFileName(MultipartFile file) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            return "(unknown)";
        }
        return file.getOriginalFilename();
    }

    // ─────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────

    /**
     * 파일 메타데이터 수정
     *
     * <p>파일이 연결된 원본 도메인 정보(원본구분, 원본PK값)를 변경합니다. 파일 자체(서버파일명, 저장경로)는 변경되지 않습니다. 파일 교체가 필요한 경우 삭제 후
     * 재업로드를 사용하세요.
     *
     * @param flMpnId 수정할 파일매핑ID
     * @param request 수정 요청 DTO (orcPkVl, orcDtt)
     * @return 수정된 파일매핑ID
     * @throws CustomGeneralException 파일이 존재하지 않는 경우
     */
    @Transactional
    public String updateFileMeta(String flMpnId, FileDto.UpdateRequest request) {
        Cfilem cfilem =
                fileRepository
                        .findByFlMpnIdAndDelYn(flMpnId, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

        // JPA Dirty Checking으로 자동 UPDATE
        cfilem.updateMeta(request.getPkCone(), request.getPkColNm());
        return flMpnId;
    }

    // ─────────────────────────────────────────
    // 삭제
    // ─────────────────────────────────────────

    /**
     * 파일 단건 논리 삭제 (Soft Delete)
     *
     * <p>DB의 DEL_YN을 'Y'로 변경합니다. 물리 파일은 삭제하지 않습니다. (물리 파일은 별도 배치 프로세스로 정리 권장)
     *
     * @param flMpnId 삭제할 파일매핑ID
     * @throws CustomGeneralException 파일이 존재하지 않는 경우
     */
    @Transactional
    public void deleteFile(String flMpnId) {
        Cfilem cfilem =
                fileRepository
                        .findByFlMpnIdAndDelYn(flMpnId, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

        // Soft Delete (DEL_YN = 'Y')
        cfilem.delete();
    }

    /**
     * 원본 기준 파일 일괄 논리 삭제 (Soft Delete)
     *
     * <p>특정 도메인 레코드에 연결된 모든 파일을 일괄 논리 삭제합니다. 프로젝트·문서 삭제 시 연관 파일 정리에 활용합니다. 삭제할 파일이 없어도 예외 없이 정상
     * 처리됩니다.
     *
     * <p>소유권 검증: 관리자가 아닌 경우 대상 파일이 모두 본인이 업로드한 파일일 때만 삭제할 수 있습니다. 하나라도 타인이 업로드한 파일이 섞여 있으면 {@link
     * AccessDeniedException}을 던집니다. 관리자는 검증을 우회합니다.
     *
     * @param pkColNm 주식별자컬럼명 (예: 요구사항정의서)
     * @param pkCone 주식별자내용 (예: PRJ-2026-0001)
     * @param user 현재 사용자 — 비관리자는 본인 소유 파일만 일괄 삭제 가능
     * @return 논리 삭제된 파일 수
     * @throws AccessDeniedException 비관리자가 타인 소유 파일을 포함해 삭제를 시도한 경우
     */
    @Transactional
    public int deleteFilesByOrc(String pkColNm, String pkCone, CustomUserDetails user) {
        List<Cfilem> files = fileRepository.findAllByPkColNmAndPkConeAndDelYn(pkColNm, pkCone, "N");

        // 인증 정보가 없으면 대상 목록이 비어 있어도 즉시 거부 — 빈 목록에 기대지 않는 서비스 계약
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        // 관리자가 아니면 본인 소유 파일만 일괄 삭제 허용 — 하나라도 타인 파일이면 차단
        if (!user.isAdmin()) {
            boolean hasOthers =
                    files.stream().anyMatch(f -> !user.getUsername().equals(f.getFstEnrUsid()));
            if (hasOthers) {
                throw new AccessDeniedException("본인이 업로드한 파일만 일괄 삭제할 수 있습니다.");
            }
        }

        files.forEach(value -> value.delete());
        return files.size();
    }

    // ─────────────────────────────────────────
    // 다운로드
    // ─────────────────────────────────────────

    /**
     * 파일 다운로드용 Resource 반환
     *
     * <p>파일 메타데이터 조회 → 디스크에서 실제 파일 로드 → Resource 반환
     *
     * <p>컨트롤러에서 {@code Content-Disposition: attachment; filename="{원본파일명}"} 헤더를 설정하여 다운로드 처리합니다.
     *
     * @param flMpnId 다운로드할 파일매핑ID
     * @return 파일 Resource (스트림으로 클라이언트에 전송)
     * @throws CustomGeneralException 파일이 존재하지 않거나 필수 메타데이터가 비어 있거나 디스크에서 찾을 수 없는 경우
     */
    public FileDownloadResult downloadFile(String flMpnId) {
        // DB에서 메타데이터 조회
        Cfilem cfilem =
                fileRepository
                        .findByFlMpnIdAndDelYn(flMpnId, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

        if (!StringUtils.hasText(cfilem.getFlKpnPth())
                || !StringUtils.hasText(cfilem.getFlPysNm())) {
            throw new CustomGeneralException("파일 메타데이터가 불완전합니다. 파일매핑ID: " + flMpnId);
        }

        // 실제 파일 경로 생성 및 Directory Traversal 방지 검증
        Path base = Paths.get(basePath).normalize().toAbsolutePath();
        Path filePath =
                Paths.get(cfilem.getFlKpnPth())
                        .resolve(cfilem.getFlPysNm())
                        .normalize()
                        .toAbsolutePath();
        if (!filePath.startsWith(base)) {
            throw new CustomGeneralException("허용되지 않는 파일 경로입니다. 파일매핑ID: " + flMpnId);
        }

        // 파일 Resource 로드
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new CustomGeneralException("파일 경로가 잘못되었습니다. 파일매핑ID: " + flMpnId, e);
        }

        if (!resource.exists() || !resource.isReadable()) {
            throw new CustomGeneralException("파일을 찾을 수 없습니다. 파일매핑ID: " + flMpnId);
        }

        String originalFilename =
                StringUtils.hasText(cfilem.getFlNm()) ? cfilem.getFlNm() : cfilem.getFlPysNm();
        String contentType = detectContentType(originalFilename, filePath);

        return new FileDownloadResult(resource, originalFilename, contentType);
    }

    /**
     * 파일 MIME 타입 감지
     *
     * <p>원본 파일명의 확장자를 기반으로 MIME 타입을 결정합니다. OS 의존적인 {@code Files.probeContentType()} 대신 확장자 매핑을 사용하여
     * Windows/Linux 서버 환경 모두에서 일관된 결과를 보장합니다.
     *
     * <p>이미지 미리보기(preview) 엔드포인트에서 브라우저가 이미지를 올바르게 렌더링하려면 {@code image/jpeg}, {@code image/png} 등
     * 정확한 MIME 타입이 필수입니다. {@code application/octet-stream} 반환 시 브라우저가 이미지를 렌더링하지 않습니다.
     *
     * @param originalFilename 원본 파일명 (확장자 추출용)
     * @param filePath 실제 파일 경로 (확장자 추출 실패 시 폴백용)
     * @return MIME 타입 문자열 (감지 실패 시 application/octet-stream)
     */
    private String detectContentType(String originalFilename, Path filePath) {
        String ext = "";
        String name =
                (originalFilename != null) ? originalFilename : filePath.getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = name.substring(dotIdx + 1).toLowerCase();
        }

        return switch (ext) {
            // 이미지
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            // 문서
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "hwp" -> "application/x-hwp";
            // 텍스트
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "json" -> "application/json";
            // 압축
            case "zip" -> "application/zip";
            // 기본
            default -> "application/octet-stream";
        };
    }

    /**
     * 파일 다운로드 결과 래퍼 클래스
     *
     * <p>컨트롤러에서 Resource, 원본파일명, MIME 타입을 함께 사용하기 위한 내부 클래스입니다.
     */
    public record FileDownloadResult(
            Resource resource, String originalFilename, String contentType) {}
}
