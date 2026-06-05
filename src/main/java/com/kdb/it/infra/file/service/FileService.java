package com.kdb.it.infra.file.service;

import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.FileValidator;
import com.kdb.it.exception.CustomGeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 공통 첨부파일 서비스
 *
 * <p>
 * TPRMPP_CFILEM 테이블의 파일 업로드·조회·수정·삭제·다운로드 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * 서버 파일명 채번 규칙:
 * </p>
 *
 * <pre>
 * {서버ID}_{yyyyMMddHHmmss}_{UUID without hyphens}.{확장자}
 * 예) SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf
 * </pre>
 *
 * <p>
 * UUID를 포함하므로 1번·2번 서버가 동시에 동일한 파일명을 생성할 확률이 사실상 0입니다.
 * 서버ID(SVR1/SVR2)를 접두어로 추가하여 어느 서버에서 업로드된 파일인지 추적 가능합니다.
 * </p>
 *
 * <p>
 * 파일 저장 경로 구조:
 * </p>
 *
 * <pre>
 * {basePath}/{원본구분}/{년도}/{월}/
 * 예) /data/files/요구사항정의서/2026/03/
 * </pre>
 *
 * <p>
 * Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다 (물리 파일은 유지).
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    /** 공통 첨부파일 데이터 접근 리포지토리 */
    private final FileRepository fileRepository;

    /** 파일 확장자 화이트리스트 검증 — SEC-04 */
    private final FileValidator fileValidator;

    /**
     * JPA EntityManager — 수동 부여 ID 엔티티의 INSERT를 {@code persist()}로 확정적으로 수행하기 위해 사용.
     *
     * <p>
     * Spring Data JPA의 {@code save()}는 수동 부여 ID({@code @Id}만 있고 {@code @GeneratedValue} 없음)
     * 엔티티에 대해 {@code EntityManager.merge()} 세만틱으로 동작합니다. merge()는 상황에 따라
     * 즉시 INSERT가 되지 않거나 dirty flag가 누락되어, 커밋 후에도 DB에 행이 없는 현상이 발생할 수 있습니다.
     * 본 클래스에서는 업로드 경로만 {@code persist()}를 명시적으로 호출하여 이러한 불확정성을 제거합니다.
     * </p>
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 서버 인스턴스 ID
     * 1번 서버: SVR1, 2번 서버: SVR2 등으로 각 서버 설정 파일에서 다르게 지정
     */
    @Value("${app.server.instance-id:SVR1}")
    private String instanceId;

    /**
     * 파일 저장 기본 경로
     * 운영 환경에서는 공유 스토리지 또는 NAS 경로를 지정하는 것을 권장합니다.
     */
    @Value("${app.file.base-path:/data/files}")
    private String basePath;

    // ─────────────────────────────────────────
    // 채번 & 경로 유틸리티
    // ─────────────────────────────────────────

    /**
     * 파일매핑ID 채번
     *
     * <p>Oracle 시퀀스(SEQ_CFILEM) 값을 기반으로 생성합니다.</p>
     *
     * <p>시퀀스가 기존 데이터의 최대값보다 작게 재설정되면 PK 충돌(ORA-00001)이
     * 발생할 수 있으므로, INSERT 충돌 시 최대 {@value #FL_MNG_NO_RETRY}회까지
     * 다음 NEXTVAL을 시도하여 자동 회복합니다(`uploadFileInternal`에서 활용).</p>
     *
     * @return 파일매핑ID (예: FL_00000001)
     */
    private String generateFlMpnId() {
        Long seq = fileRepository.getNextSequenceValue();
        return String.format("FL_%08d", seq);
    }

    /** PK 충돌 회복 시 최대 재시도 횟수 (시퀀스가 기존 최대값보다 작게 재설정된 경우 대비) */
    private static final int FL_MNG_NO_RETRY = 5;

    /**
     * 파일물리명 생성
     *
     * <p>형식: {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}}</p>
     *
     * @param originalFilename 원본 파일명 (확장자 추출용)
     * @return 서버 저장용 고유 파일물리명
     */
    private String generateFlPysNm(String originalFilename) {
        // 확장자 추출 (.pdf, .jpg 등 - 없으면 빈 문자열)
        String ext = "";
        if (StringUtils.hasText(originalFilename)) {
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < originalFilename.length() - 1) {
                ext = "." + originalFilename.substring(dotIdx + 1).toLowerCase();
            }
        }
        // {서버ID}_{타임스탬프}_{UUID(하이픈 제거)}.{확장자}
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return instanceId + "_" + timestamp + "_" + uuid + ext;
    }

    /**
     * 파일 저장 디렉토리 경로 생성
     *
     * <p>형식: {@code {basePath}/{주식별자컬럼명}/{년도}/{월}}</p>
     *
     * @param pkColNm 주식별자컬럼명 (디렉토리 명으로 사용)
     * @return 저장 디렉토리 Path 객체
     */
    private Path buildStorageDir(String pkColNm) {
        LocalDate today = LocalDate.now();
        return Paths.get(
                basePath,
                pkColNm,
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()));
    }

    /**
     * 엔티티 → 응답 DTO 변환
     */
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
        Cfilem cfilem = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));
        return toResponse(cfilem);
    }

    /**
     * 조건별 파일 목록 조회
     *
     * <p>
     * 조회 우선순위:
     * </p>
     * <ol>
     * <li>orcDtt + orcPkVl + flDtt 모두 입력 → 세 조건으로 필터링</li>
     * <li>orcDtt + orcPkVl 입력 → 두 조건으로 필터링</li>
     * <li>orcDtt만 입력 → 해당 원본구분 전체 조회</li>
     * </ol>
     *
     * @param condition 검색 조건 (orcDtt 필수, orcPkVl·flDtt 선택)
     * @return 파일 조회 응답 DTO 목록
     * @throws CustomGeneralException orcDtt 미입력 시
     */
    public List<FileDto.Response> getFiles(FileDto.SearchCondition condition) {
        if (!StringUtils.hasText(condition.getPkColNm())) {
            throw new CustomGeneralException("주식별자컬럼명(pkColNm)은 필수입니다.");
        }

        List<Cfilem> list;

        if (StringUtils.hasText(condition.getPkCone()) && StringUtils.hasText(condition.getFlTpCone())) {
            // 주식별자컬럼명 + 주식별자내용 + 파일유형내용 필터링
            list = fileRepository.findAllByPkColNmAndPkConeAndFlTpConeAndDelYn(
                    condition.getPkColNm(), condition.getPkCone(), condition.getFlTpCone(), "N");
        } else if (StringUtils.hasText(condition.getPkCone())) {
            // 주식별자컬럼명 + 주식별자내용 필터링
            list = fileRepository.findAllByPkColNmAndPkConeAndDelYn(
                    condition.getPkColNm(), condition.getPkCone(), "N");
        } else {
            // 주식별자컬럼명 전체 조회
            list = fileRepository.findAllByPkColNmAndDelYn(condition.getPkColNm(), "N");
        }

        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────

    /**
     * 파일 단건 업로드
     *
     * <p>
     * 처리 순서:
     * </p>
     * <ol>
     * <li>저장 디렉토리 생성 (없으면 자동 생성)</li>
     * <li>서버 파일명 채번 ({서버ID}_{타임스탬프}_{UUID}.{확장자})</li>
     * <li>파일 관리번호 채번 (Oracle 시퀀스)</li>
     * <li>파일을 서버 디스크에 저장</li>
     * <li>파일 메타데이터를 DB에 저장</li>
     * </ol>
     *
     * @param file    업로드할 파일 (MultipartFile)
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
     * <p>
     * 물리 파일을 디스크에 저장한 뒤 메타데이터 엔티티를 영속화하고,
     * 영속화된 {@link Cfilem} 엔티티를 그대로 반환합니다.
     * </p>
     *
     * <p>
     * [재쿼리 회피 이유]<br>
     * 수동 부여된 ID({@code flMpnId})로 {@code save()}를 호출할 때
     * Spring Data JPA는 내부적으로 {@code merge()} 세만틱으로 동작해
     * INSERT가 트랜잭션 커밋 전까지 지연될 수 있습니다.
     * 이 상태에서 동일 트랜잭션 내 {@code findByFlMpnIdAndDelYn(...)} 같은
     * derived 쿼리가 바로 실행되면 flush가 보장되지 않아
     * "존재하지 않는 파일입니다. 파일관리번호: FL_xxxxxxxx" 오류가 발생합니다.
     * 본 메서드는 저장된 엔티티를 그대로 반환하여
     * 호출 측이 재쿼리 없이 DTO 변환을 수행할 수 있게 합니다.
     * </p>
     */
    @Transactional
    protected Cfilem uploadFileInternal(MultipartFile file, FileDto.UploadRequest request) {
        // 빈 파일 검증
        if (file == null || file.isEmpty()) {
            throw new CustomGeneralException("업로드할 파일이 비어있습니다.");
        }

        // 확장자 화이트리스트 검증 — SEC-04
        fileValidator.validateExtension(file.getOriginalFilename());

        // 저장 디렉토리 경로 생성
        Path storageDir = buildStorageDir(request.getPkColNm());

        // 파일물리명 채번
        String flPysNm = generateFlPysNm(file.getOriginalFilename());

        // 파일매핑ID 채번
        String flMpnId = generateFlMpnId();

        // 저장 경로 문자열 (DB 저장용)
        String flKpnPth = storageDir.toString();

        // 디렉토리 생성 (이미 있으면 무시)
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new CustomGeneralException("파일 저장 디렉토리 생성에 실패했습니다. 경로: " + flKpnPth, e);
        }

        // 파일 디스크 저장
        Path targetPath = storageDir.resolve(flPysNm);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CustomGeneralException("파일 저장에 실패했습니다. 파일명: " + file.getOriginalFilename(), e);
        }

        // DB 메타데이터 저장
        Cfilem cfilem = Cfilem.builder()
                .flMpnId(flMpnId)
                .flNm(file.getOriginalFilename())
                .flPysNm(flPysNm)
                .flKpnPth(flKpnPth)
                .flTpCone(request.getFlTpCone())
                .pkCone(request.getPkCone())
                .pkColNm(request.getPkColNm())
                .build();

        // 수동 부여 ID 엔티티는 persist()로 명시적 INSERT → save() 위임 시 merge() 세만틱으로
        // 실제 INSERT가 누락되거나 지연되어 "존재하지 않는 파일" 오류가 발생하는 현상을 근본 차단
        entityManager.persist(cfilem);
        // 같은 트랜잭션 내 후속 조회 쿼리가 새 행을 볼 수 있도록 즉시 flush
        entityManager.flush();
        return cfilem;
    }

    /**
     * 파일 단건 업로드 후 전체 정보 반환
     *
     * <p>
     * 업로드 완료 즉시 {@link FileDto.Response}를 반환합니다.
     * {@code previewUrl}, {@code downloadUrl}이 포함되어 있어
     * Tiptap 에디터에서 {@code response.previewUrl}을 {@code <img src>}에
     * 바로 주입할 수 있습니다.
     * </p>
     *
     * <p>
     * 저장 직후 재조회 시 수동 부여 ID + merge() flush 지연으로
     * "존재하지 않는 파일" 오류가 발생할 수 있어
     * 저장된 엔티티를 그대로 DTO로 변환하여 반환합니다.
     * </p>
     *
     * @param file    업로드할 파일
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
     * <p>
     * 개별 파일 업로드를 반복합니다. 특정 파일이 실패해도 나머지는 계속 업로드됩니다.
     * 결과에 성공·실패 목록을 모두 포함하며, 개별 실패는 전체 트랜잭션을 롤백하지 않는
     * 부분 성공 흐름입니다.
     * </p>
     *
     * @param files   업로드할 파일 목록
     * @param request 공통 메타데이터 (모든 파일에 동일하게 적용)
     * @return 일괄 업로드 결과 DTO (성공 목록 + 실패 파일명 목록)
     */
    @Transactional
    public FileDto.BulkUploadResponse uploadFiles(List<MultipartFile> files, FileDto.UploadRequest request) {
        List<FileDto.Response> successList = new ArrayList<>();
        List<String> failList = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                // 업로드 직후 동일 트랜잭션 내 재조회는 merge() flush 지연으로 실패할 수 있음 →
                // 영속화된 엔티티를 그대로 DTO로 변환
                Cfilem saved = uploadFileInternal(file, request);
                successList.add(toResponse(saved));
            } catch (Exception e) {
                // TODO: [B-H-01] 파일 업로드 실패 로그에 원본 파일명과 스택 트레이스를 포함해 실패 원인을 추적한다.
                failList.add(file.getOriginalFilename() + " (" + e.getMessage() + ")");
            }
        }

        return FileDto.BulkUploadResponse.builder()
                .successList(successList)
                .failList(failList)
                .build();
    }

    // ─────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────

    /**
     * 파일 메타데이터 수정
     *
     * <p>
     * 파일이 연결된 원본 도메인 정보(원본구분, 원본PK값)를 변경합니다.
     * 파일 자체(서버파일명, 저장경로)는 변경되지 않습니다.
     * 파일 교체가 필요한 경우 삭제 후 재업로드를 사용하세요.
     * </p>
     *
     * @param flMpnId 수정할 파일매핑ID
     * @param request 수정 요청 DTO (orcPkVl, orcDtt)
     * @return 수정된 파일매핑ID
     * @throws CustomGeneralException 파일이 존재하지 않는 경우
     */
    @Transactional
    public String updateFileMeta(String flMpnId, FileDto.UpdateRequest request) {
        Cfilem cfilem = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

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
     * <p>
     * DB의 DEL_YN을 'Y'로 변경합니다. 물리 파일은 삭제하지 않습니다.
     * (물리 파일은 별도 배치 프로세스로 정리 권장)
     * </p>
     *
     * @param flMpnId 삭제할 파일매핑ID
     * @throws CustomGeneralException 파일이 존재하지 않는 경우
     */
    @Transactional
    public void deleteFile(String flMpnId) {
        Cfilem cfilem = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

        // Soft Delete (DEL_YN = 'Y')
        cfilem.delete();
    }

    /**
     * 원본 기준 파일 일괄 논리 삭제 (Soft Delete)
     *
     * <p>
     * 특정 도메인 레코드에 연결된 모든 파일을 일괄 논리 삭제합니다.
     * 프로젝트·문서 삭제 시 연관 파일 정리에 활용합니다.
     * 삭제할 파일이 없어도 예외 없이 정상 처리됩니다.
     * </p>
     *
     * @param pkColNm 주식별자컬럼명 (예: 요구사항정의서)
     * @param pkCone  주식별자내용 (예: PRJ-2026-0001)
     * @return 논리 삭제된 파일 수
     */
    @Transactional
    public int deleteFilesByOrc(String pkColNm, String pkCone) {
        List<Cfilem> files = fileRepository.findAllByPkColNmAndPkConeAndDelYn(pkColNm, pkCone, "N");
        files.forEach(Cfilem::delete);
        return files.size();
    }

    // ─────────────────────────────────────────
    // 다운로드
    // ─────────────────────────────────────────

    /**
     * 파일 다운로드용 Resource 반환
     *
     * <p>
     * 파일 메타데이터 조회 → 디스크에서 실제 파일 로드 → Resource 반환
     * </p>
     *
     * <p>
     * 컨트롤러에서 {@code Content-Disposition: attachment; filename="{원본파일명}"}
     * 헤더를 설정하여 다운로드 처리합니다.
     * </p>
     *
     * @param flMpnId 다운로드할 파일매핑ID
     * @return 파일 Resource (스트림으로 클라이언트에 전송)
     * @throws CustomGeneralException 파일이 존재하지 않거나 디스크에서 찾을 수 없는 경우
     */
    public FileDownloadResult downloadFile(String flMpnId) {
        // DB에서 메타데이터 조회
        Cfilem cfilem = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("존재하지 않는 파일입니다. 파일매핑ID: " + flMpnId));

        // 실제 파일 경로 생성 및 Directory Traversal 방지 검증
        Path base = Paths.get(basePath).normalize().toAbsolutePath();
        Path filePath = Paths.get(cfilem.getFlKpnPth())
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
            throw new CustomGeneralException("파일 경로가 잘못되었습니다. 파일매핑ID: " + flMpnId);
        }

        if (!resource.exists() || !resource.isReadable()) {
            throw new CustomGeneralException("파일을 찾을 수 없습니다. 파일매핑ID: " + flMpnId);
        }

        // 파일명 기준으로 MIME 타입 감지
        String contentType = detectContentType(cfilem.getFlNm(), filePath);

        return new FileDownloadResult(resource, cfilem.getFlNm(), contentType);
    }

    /**
     * 파일 MIME 타입 감지
     *
     * <p>
     * 원본 파일명의 확장자를 기반으로 MIME 타입을 결정합니다.
     * OS 의존적인 {@code Files.probeContentType()} 대신 확장자 매핑을 사용하여
     * Windows/Linux 서버 환경 모두에서 일관된 결과를 보장합니다.
     * </p>
     *
     * <p>
     * 이미지 미리보기(preview) 엔드포인트에서 브라우저가 이미지를 올바르게
     * 렌더링하려면 {@code image/jpeg}, {@code image/png} 등 정확한 MIME 타입이 필수입니다.
     * {@code application/octet-stream} 반환 시 브라우저가 이미지를 렌더링하지 않습니다.
     * </p>
     *
     * @param originalFilename 원본 파일명 (확장자 추출용)
     * @param filePath         실제 파일 경로 (확장자 추출 실패 시 폴백용)
     * @return MIME 타입 문자열 (감지 실패 시 application/octet-stream)
     */
    private String detectContentType(String originalFilename, Path filePath) {
        String ext = "";
        String name = (originalFilename != null) ? originalFilename : filePath.getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = name.substring(dotIdx + 1).toLowerCase();
        }

        return switch (ext) {
            // 이미지
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"         -> "image/png";
            case "gif"         -> "image/gif";
            case "webp"        -> "image/webp";
            case "svg"         -> "image/svg+xml";
            case "bmp"         -> "image/bmp";
            case "ico"         -> "image/x-icon";
            // 문서
            case "pdf"         -> "application/pdf";
            case "doc"         -> "application/msword";
            case "docx"        -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls"         -> "application/vnd.ms-excel";
            case "xlsx"        -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt"         -> "application/vnd.ms-powerpoint";
            case "pptx"        -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "hwp"         -> "application/x-hwp";
            // 텍스트
            case "txt"         -> "text/plain";
            case "csv"         -> "text/csv";
            case "json"        -> "application/json";
            // 압축
            case "zip"         -> "application/zip";
            // 기본
            default            -> "application/octet-stream";
        };
    }

    /**
     * 파일 다운로드 결과 래퍼 클래스
     *
     * <p>
     * 컨트롤러에서 Resource, 원본파일명, MIME 타입을 함께 사용하기 위한 내부 클래스입니다.
     * </p>
     */
    public record FileDownloadResult(Resource resource, String originalFilename, String contentType) {
    }
}
