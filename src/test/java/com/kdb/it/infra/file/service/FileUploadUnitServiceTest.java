package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileValidator;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FileUploadUnitService 단위 테스트
 *
 * <p>
 * FileServiceTest가 FileService 관점에서 업로드 성공/부분실패 흐름을 이미 검증하므로,
 * 본 테스트는 {@code uploadFileInNewTransaction}이 직접 노출하는 분기 경로 —
 * null 파일 파라미터, 원본 파일명의 확장자 유무(점 없음/점으로 끝남/공백) — 에 집중합니다.
 * EntityManager는 필드 주입 대상이라 Mockito {@code mock()}으로 생성 후
 * {@code ReflectionTestUtils}로 주입합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileUploadUnitServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileValidator fileValidator;

    private EntityManager entityManager;

    private FileUploadUnitService fileUploadUnitService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        entityManager = mock(EntityManager.class);
        fileUploadUnitService = new FileUploadUnitService(fileRepository, fileValidator);
        ReflectionTestUtils.setField(fileUploadUnitService, "entityManager", entityManager);
        ReflectionTestUtils.setField(fileUploadUnitService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileUploadUnitService, "basePath", tempDir.toString());
    }

    private FileDto.UploadRequest request() {
        return FileDto.UploadRequest.builder()
                .pkColNm("첨부")
                .flTpCone("첨부파일")
                .build();
    }

    // ───────────────────────────────────────────────────────
    // uploadFileInNewTransaction — file == null / file.isEmpty()
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInNewTransaction: file 파라미터가 null이면 저장소·DB 접근 없이 예외를 던진다")
    void uploadFileInNewTransaction_file이null이면_예외발생() {
        // Arrange
        FileDto.UploadRequest request = request();

        // Act & Assert
        assertThatThrownBy(() -> fileUploadUnitService.uploadFileInNewTransaction(null, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(fileValidator, entityManager, fileRepository);
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: 빈 파일(isEmpty=true)이면 저장소·DB 접근 없이 예외를 던진다")
    void uploadFileInNewTransaction_빈파일이면_예외발생() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request = request();

        // Act & Assert
        assertThatThrownBy(() -> fileUploadUnitService.uploadFileInNewTransaction(emptyFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(fileValidator, entityManager, fileRepository);
    }

    // ───────────────────────────────────────────────────────
    // generateFlPysNm 분기 — 원본 파일명의 확장자 파싱
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInNewTransaction: 원본 파일명에 점(.)이 없으면 확장자 없이 물리 파일명을 생성한다")
    void uploadFileInNewTransaction_확장자없는파일명_점없이저장() {
        // Arrange: 확장자를 구분하는 점(.)이 전혀 없는 파일명
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "README", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        // Act
        Cfilem result = fileUploadUnitService.uploadFileInNewTransaction(file, request());

        // Assert: 점(.)이 없으므로 물리 파일명에 확장자가 붙지 않는다
        assertThat(result.getFlPysNm()).startsWith("SVR1_").doesNotContain(".");
        verify(entityManager).persist(result);
        verify(entityManager).flush();
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: 원본 파일명이 점(.)으로 끝나면 확장자 없이 물리 파일명을 생성한다")
    void uploadFileInNewTransaction_점으로끝나는파일명_확장자없이저장() {
        // Arrange: 마지막 문자가 점(.)이라 점 뒤에 확장자 문자가 없는 경우 (dotIdx == length-1)
        given(fileRepository.getNextSequenceValue()).willReturn(2L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        // Act
        Cfilem result = fileUploadUnitService.uploadFileInNewTransaction(file, request());

        // Assert
        assertThat(result.getFlPysNm()).startsWith("SVR1_").doesNotContain(".");
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: 원본 파일명이 공백이면(hasText=false) 확장자 없이 물리 파일명을 생성한다")
    void uploadFileInNewTransaction_원본파일명공백_확장자없이저장() {
        // Arrange: StringUtils.hasText()가 false를 반환하는 공백 파일명
        given(fileRepository.getNextSequenceValue()).willReturn(3L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "   ", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        // Act
        Cfilem result = fileUploadUnitService.uploadFileInNewTransaction(file, request());

        // Assert
        assertThat(result.getFlPysNm()).startsWith("SVR1_").doesNotContain(".");
        assertThat(result.getFlNm()).isEqualTo("   ");
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: 정상 확장자 파일명이면 소문자 확장자를 포함한 물리 파일명을 생성한다")
    void uploadFileInNewTransaction_정상확장자파일명_확장자포함저장() {
        // Arrange: 대문자 확장자 → 소문자로 정규화되어 저장되는지 함께 확인
        given(fileRepository.getNextSequenceValue()).willReturn(4L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.PDF", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));

        // Act
        Cfilem result = fileUploadUnitService.uploadFileInNewTransaction(file, request());

        // Assert
        assertThat(result.getFlPysNm()).endsWith(".pdf");
        assertThat(result.getFlMpnId()).isEqualTo("FL_00000004");
        assertThat(result.getPkColNm()).isEqualTo("첨부");
        assertThat(result.getFlTpCone()).isEqualTo("첨부파일");
    }
}
