package com.kdb.it.infra.file.service;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileValidator;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 1건 업로드의 저장소 쓰기와 DB 메타데이터 저장을 담당합니다.
 *
 * <p>일괄 업로드에서 한 파일 실패가 이전 성공 파일의 커밋 상태를 오염시키지 않도록 호출 단위를 항상 새 트랜잭션으로 분리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadUnitService {

    /**
     * 저장 디렉터리 이름으로 허용하는 파일 종류 문자 집합.
     *
     * <p>{@code apgFlKdNm}은 클라이언트가 보낸 값이 그대로 경로 세그먼트가 되므로, 경로 구분자({@code /}·{@code \}), 상위 이동({@code
     * ..}), 드라이브 지정({@code :})이 섞일 수 없는 문자만 받습니다. 실제 사용 중인 종류는 모두 한글이고(배너·공통게시판· 편성요청서반입 등) 영문 종류가
     * 생길 수 있어 영숫자와 밑줄·하이픈까지 허용합니다(SEC-14).
     */
    private static final Pattern SAFE_APG_FL_KD_NM = Pattern.compile("^[0-9A-Za-z가-힣_-]{1,100}$");

    private final FileRepository fileRepository;
    private final FileValidator fileValidator;

    @PersistenceContext private EntityManager entityManager;

    @Value("${app.server.instance-id:SVR1}")
    private String instanceId;

    @Value("${app.file.base-path:/data/files}")
    private String basePath;

    /**
     * 파일 1건을 물리 저장소와 DB에 저장합니다.
     *
     * @param file 업로드할 파일
     * @param request 파일 메타데이터 요청
     * @return 저장 완료된 파일 메타데이터 엔티티
     * @throws CustomGeneralException 빈 파일, 원본 파일명 없음, 저장소 쓰기 실패, DB 저장 실패 시 발생
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cfilem uploadFileInNewTransaction(MultipartFile file, FileDto.UploadRequest request) {
        if (file == null || file.isEmpty()) {
            throw new CustomGeneralException("업로드할 파일이 비어있습니다.");
        }

        // MultipartFile.getOriginalFilename()은 계약상 null을 반환할 수 있다. 아래 검증·채번이
        // 모두 파일명을 쓰므로 진입 시점에 한 번만 확정해 두고 업무 예외로 거부한다.
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new CustomGeneralException("업로드할 파일의 원본 파일명이 없습니다.");
        }

        fileValidator.validateExtension(originalFilename);

        Path storageDir = buildStorageDir(request.getApgFlKdNm());
        String flPysNm = generateFlPysNm(originalFilename);
        String flMpnId = generateFlMpnId();
        String flKpnPth = storageDir.toString();

        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new CustomGeneralException("파일 저장 디렉토리 생성에 실패했습니다. 경로: " + flKpnPth, e);
        }

        Path targetPath = storageDir.resolve(flPysNm);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CustomGeneralException("파일 저장에 실패했습니다. 파일명: " + originalFilename, e);
        }

        Cfilem cfilem =
                Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm(
                                request.getDisplayFileName() == null
                                                || request.getDisplayFileName().isBlank()
                                        ? originalFilename
                                        : request.getDisplayFileName())
                        .flPysNm(flPysNm)
                        .flKpnPth(flKpnPth)
                        .flTpCone(request.getFlTpCone())
                        .apgFlSz(file.getSize())
                        .apgFlPth(request.getRelativePath())
                        .apgFlLnkCtzNm(request.getApgFlLnkCtzNm())
                        .apgFlKdNm(request.getApgFlKdNm())
                        .build();

        entityManager.persist(cfilem);
        entityManager.flush();
        return cfilem;
    }

    /**
     * 이미 저장된 물리 파일을 다른 부모에 추가로 연결합니다.
     *
     * <p>디스크에 다시 쓰지 않고 메타데이터 행만 만듭니다. 같은 파일을 여러 원장에 붙여야 하는 편성요청서 반입이 이 경로를 씁니다. 삭제가 논리 삭제({@code
     * DEL_YN='Y'})라 물리 파일을 공유해도 형제 행의 다운로드가 깨지지 않는다는 전제 위에 있습니다. 물리 삭제를 도입하면 이 메서드도 함께 고쳐야 합니다.
     *
     * @param source 원본 파일 메타데이터. 파일물리명·저장경로·파일명·크기를 그대로 물려받습니다
     * @param request 새 연결의 종류와 부모 식별자
     * @return 새로 만들어진 파일 메타데이터 엔티티
     * @throws NullPointerException source 또는 request가 null인 경우
     * @throws RuntimeException 파일매핑ID 채번, 메타데이터 영속화 또는 flush 과정에서 발생한 예외를 그대로 전파하는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cfilem linkExistingFileInNewTransaction(Cfilem source, FileDto.UploadRequest request) {
        Cfilem linked =
                Cfilem.builder()
                        .flMpnId(generateFlMpnId())
                        .flNm(source.getFlNm())
                        .flPysNm(source.getFlPysNm())
                        .flKpnPth(source.getFlKpnPth())
                        .flTpCone(request.getFlTpCone())
                        .apgFlSz(source.getApgFlSz())
                        .apgFlPth(source.getApgFlPth())
                        .apgFlLnkCtzNm(request.getApgFlLnkCtzNm())
                        .apgFlKdNm(request.getApgFlKdNm())
                        .build();

        entityManager.persist(linked);
        entityManager.flush();
        return linked;
    }

    private String generateFlMpnId() {
        Long seq = fileRepository.getNextSequenceValue();
        return String.format("FL-%08d", seq);
    }

    private String generateFlPysNm(String originalFilename) {
        String ext = "";
        if (StringUtils.hasText(originalFilename)) {
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < originalFilename.length() - 1) {
                ext = "." + originalFilename.substring(dotIdx + 1).toLowerCase();
            }
        }
        String timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return instanceId + "_" + timestamp + "_" + uuid + ext;
    }

    /**
     * 파일 종류별 저장 디렉터리를 만듭니다.
     *
     * <p>{@code apgFlKdNm}은 클라이언트 입력이므로 허용 문자 집합으로 먼저 거르고, 통과한 뒤에도 정규화한 절대경로가 {@code basePath} 안에 있는지
     * 다운로드({@code FileService.downloadFile})와 같은 기준으로 다시 확인합니다. 확장자 화이트리스트와 서버 채번 파일명이 있어 임의 코드 배치는
     * 어렵지만, 쓰기 측에도 경로 정규화 원칙을 세웁니다(SEC-14, {@code docs/guides/security/file-security.md}).
     *
     * <p>반환하는 경로 문자열의 형태는 바꾸지 않습니다 — {@code FL_KPN_PTH}에 그대로 저장되므로 기존 행과 같은 형태를 유지해야 합니다. 검증은 별도의
     * 정규화 사본으로만 합니다.
     *
     * @param apgFlKdNm 파일 종류
     * @return {@code basePath/종류/년/월} 디렉터리 경로
     * @throws CustomGeneralException 종류가 비었거나 허용 문자 집합 밖이거나, 결과 경로가 {@code basePath} 밖인 경우
     */
    private Path buildStorageDir(String apgFlKdNm) {
        if (apgFlKdNm == null || !SAFE_APG_FL_KD_NM.matcher(apgFlKdNm).matches()) {
            // 값 자체는 응답에 싣지 않는다 — 클라이언트가 통제하는 문자열이다.
            log.warn("허용되지 않는 파일 종류로 업로드가 시도되었습니다: apgFlKdNm={}", apgFlKdNm);
            throw new CustomGeneralException("허용되지 않는 파일 종류입니다.");
        }

        LocalDate today = LocalDate.now();
        Path storageDir =
                Paths.get(
                        basePath,
                        apgFlKdNm,
                        String.valueOf(today.getYear()),
                        String.format("%02d", today.getMonthValue()));

        Path base = Paths.get(basePath).normalize().toAbsolutePath();
        if (!storageDir.normalize().toAbsolutePath().startsWith(base)) {
            log.warn("파일 저장 경로가 기준 경로를 벗어났습니다: apgFlKdNm={}", apgFlKdNm);
            throw new CustomGeneralException("허용되지 않는 파일 저장 경로입니다.");
        }
        return storageDir;
    }
}
