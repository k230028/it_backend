package com.kdb.it.infra.file.service;

import static java.nio.charset.StandardCharsets.UTF_8;
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
 * <p>FileServiceTest가 FileService 관점에서 업로드 성공/부분실패 흐름을 이미 검증하므로, 본 테스트는 {@code
 * uploadFileInNewTransaction}이 직접 노출하는 분기 경로 — null 파일 파라미터, 원본 파일명의 확장자 유무(점 없음/점으로 끝남/공백) — 에
 * 집중합니다. EntityManager는 필드 주입 대상이라 Mockito {@code mock()}으로 생성 후 {@code ReflectionTestUtils}로
 * 주입합니다.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadUnitServiceTest {

    @Mock private FileRepository fileRepository;

    @Mock private FileValidator fileValidator;

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
        return FileDto.UploadRequest.builder().pkColNm("첨부").flTpCone("첨부파일").build();
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
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request = request();

        // Act & Assert
        assertThatThrownBy(
                        () -> fileUploadUnitService.uploadFileInNewTransaction(emptyFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(fileValidator, entityManager, fileRepository);
    }

    // ───────────────────────────────────────────────────────
    // linkExistingFileInNewTransaction — 물리 파일 메타데이터 재연결
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInNewTransaction: 요청 상대경로를 파일 메타데이터에 저장한다")
    void uploadFileInNewTransaction_요청상대경로저장() {
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .flTpCone("첨부파일")
                        .pkColNm("편성요청서반입")
                        .pkCone("APF-1")
                        .relativePath("2026/IT부(D01)/01. 사업/근거.pdf")
                        .build();
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "근거.pdf",
                        "application/pdf",
                        "PDF".getBytes(StandardCharsets.UTF_8));
        given(fileRepository.getNextSequenceValue()).willReturn(1L);

        Cfilem saved = fileUploadUnitService.uploadFileInNewTransaction(file, request);

        assertThat(saved.getApgFlPth()).isEqualTo(request.getRelativePath());
    }

    @Test
    @DisplayName("linkExistingFileInNewTransaction: 물리 파일 메타데이터를 복사하고 새 부모 메타행을 영속화한다")
    void linkExistingFileInNewTransaction_물리파일메타데이터복사후영속화() {
        Cfilem source =
                Cfilem.builder()
                        .flMpnId("FL-00000001")
                        .flNm("편성요청서.xlsx")
                        .flPysNm("SVR1_20260818120000_abc.xlsx")
                        .flKpnPth("/data/files/편성요청서반입/2026/08")
                        .apgFlSz(2048L)
                        .apgFlPth("2026/IT부(D01)/01. 사업/근거.pdf")
                        .pkColNm("편성요청서반입")
                        .pkCone("APF-2026-00000001")
                        .build();
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .flTpCone("첨부파일")
                        .pkColNm("편성요청서반입")
                        .pkCone("APF-2026-00000002")
                        .build();
        given(fileRepository.getNextSequenceValue()).willReturn(2L);

        Cfilem linked = fileUploadUnitService.linkExistingFileInNewTransaction(source, request);

        assertThat(linked.getFlMpnId()).isEqualTo("FL-00000002");
        assertThat(linked.getFlNm()).isEqualTo("편성요청서.xlsx");
        assertThat(linked.getFlPysNm()).isEqualTo("SVR1_20260818120000_abc.xlsx");
        assertThat(linked.getFlKpnPth()).isEqualTo("/data/files/편성요청서반입/2026/08");
        assertThat(linked.getApgFlSz()).isEqualTo(2048L);
        assertThat(linked.getApgFlPth()).isEqualTo(source.getApgFlPth());
        assertThat(linked.getFlTpCone()).isEqualTo("첨부파일");
        assertThat(linked.getPkColNm()).isEqualTo("편성요청서반입");
        assertThat(linked.getPkCone()).isEqualTo("APF-2026-00000002");
        verify(entityManager).persist(linked);
        verify(entityManager).flush();
    }

    // ───────────────────────────────────────────────────────
    // generateFlPysNm 분기 — 원본 파일명의 확장자 파싱
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInNewTransaction: 원본 파일명에 점(.)이 없으면 확장자 없이 물리 파일명을 생성한다")
    void uploadFileInNewTransaction_확장자없는파일명_점없이저장() {
        // Arrange: 확장자를 구분하는 점(.)이 전혀 없는 파일명
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file =
                new MockMultipartFile(
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
        MockMultipartFile file =
                new MockMultipartFile(
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
        MockMultipartFile file =
                new MockMultipartFile(
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
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "report.PDF",
                        "application/pdf",
                        "content".getBytes(StandardCharsets.UTF_8));

        // Act
        Cfilem result = fileUploadUnitService.uploadFileInNewTransaction(file, request());

        // Assert
        assertThat(result.getFlPysNm()).endsWith(".pdf");
        assertThat(result.getFlMpnId()).isEqualTo("FL-00000004");
        assertThat(result.getPkColNm()).isEqualTo("첨부");
        assertThat(result.getFlTpCone()).isEqualTo("첨부파일");
        assertThat(result.getApgFlSz()).isEqualTo(7L);
    }

    // ───────────────────────────────────────────────────────
    // buildStorageDir — pkColNm 경로 정화 (SEC-14)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInNewTransaction: pkColNm에 상위 이동이 섞이면 저장 없이 거부한다")
    void uploadFileInNewTransaction_상위이동_pkColNm이면_거부() {
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("../../etc").flTpCone("첨부파일").build();
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes(UTF_8));

        assertThatThrownBy(() -> fileUploadUnitService.uploadFileInNewTransaction(file, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("허용되지 않는 파일 종류");
        // 값 자체를 응답에 되돌려주지 않는다.
        verifyNoInteractions(entityManager, fileRepository);
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: pkColNm에 경로 구분자가 섞이면 거부한다")
    void uploadFileInNewTransaction_경로구분자_pkColNm이면_거부() {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes(UTF_8));

        for (String kind : new String[] {"a/b", "a\b", "C:", "..", "  ", ""}) {
            FileDto.UploadRequest request =
                    FileDto.UploadRequest.builder().pkColNm(kind).flTpCone("첨부파일").build();
            assertThatThrownBy(
                            () -> fileUploadUnitService.uploadFileInNewTransaction(file, request))
                    .as("종류=%s", kind)
                    .isInstanceOf(CustomGeneralException.class);
        }
        verifyNoInteractions(entityManager, fileRepository);
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: pkColNm이 null이면 NPE가 아니라 업무 예외로 거부한다")
    void uploadFileInNewTransaction_pkColNm이null이면_업무예외() {
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm(null).flTpCone("첨부파일").build();
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes(UTF_8));

        assertThatThrownBy(() -> fileUploadUnitService.uploadFileInNewTransaction(file, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("허용되지 않는 파일 종류");
    }

    @Test
    @DisplayName("uploadFileInNewTransaction: 한글 종류는 기존과 같은 basePath/종류/년/월에 저장한다")
    void uploadFileInNewTransaction_한글종류는_그대로저장된다(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(fileUploadUnitService, "basePath", tempDir.toString());
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("편성요청서반입").flTpCone("첨부파일").build();
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes(UTF_8));

        Cfilem saved = fileUploadUnitService.uploadFileInNewTransaction(file, request);

        java.time.LocalDate today = java.time.LocalDate.now();
        Path expected =
                tempDir.resolve("편성요청서반입")
                        .resolve(String.valueOf(today.getYear()))
                        .resolve(String.format("%02d", today.getMonthValue()));
        assertThat(saved.getFlKpnPth()).isEqualTo(expected.toString());
    }
}
