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
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class FileUploadUnitService {

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

        Path storageDir = buildStorageDir(request.getPkColNm());
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
                        .flNm(originalFilename)
                        .flPysNm(flPysNm)
                        .flKpnPth(flKpnPth)
                        .flTpCone(request.getFlTpCone())
                        .apgFlSz(file.getSize())
                        .pkCone(request.getPkCone())
                        .pkColNm(request.getPkColNm())
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
                        .pkCone(request.getPkCone())
                        .pkColNm(request.getPkColNm())
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

    private Path buildStorageDir(String pkColNm) {
        LocalDate today = LocalDate.now();
        return Paths.get(
                basePath,
                pkColNm,
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()));
    }
}
