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
     * @throws CustomGeneralException 빈 파일, 저장소 쓰기 실패, DB 저장 실패 시 발생
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cfilem uploadFileInNewTransaction(MultipartFile file, FileDto.UploadRequest request) {
        if (file == null || file.isEmpty()) {
            throw new CustomGeneralException("업로드할 파일이 비어있습니다.");
        }

        fileValidator.validateExtension(file.getOriginalFilename());

        Path storageDir = buildStorageDir(request.getPkColNm());
        String flPysNm = generateFlPysNm(file.getOriginalFilename());
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
            throw new CustomGeneralException(
                    "파일 저장에 실패했습니다. 파일명: " + file.getOriginalFilename(), e);
        }

        Cfilem cfilem =
                Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm(file.getOriginalFilename())
                        .flPysNm(flPysNm)
                        .flKpnPth(flKpnPth)
                        .flTpCone(request.getFlTpCone())
                        .pkCone(request.getPkCone())
                        .pkColNm(request.getPkColNm())
                        .build();

        entityManager.persist(cfilem);
        entityManager.flush();
        return cfilem;
    }

    private String generateFlMpnId() {
        Long seq = fileRepository.getNextSequenceValue();
        return String.format("FL_%08d", seq);
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
