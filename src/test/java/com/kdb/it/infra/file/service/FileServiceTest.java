package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileValidator;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;

/**
 * FileService 단위 테스트
 *
 * <p>
 * 공통 첨부파일 서비스의 단건 조회·목록 조회·논리 삭제 메서드를 검증합니다.
 * Cfilem 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * 파일 업로드(uploadFile)는 디스크 I/O·EntityManager를 사용하므로 단위 테스트 범위에서 제외합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private FileValidator fileValidator;

    @InjectMocks
    private FileService fileService;

    private static final String FL_MNG_NO = "FL_00000001";

    private Cfilem mockCfilem(String flMngNo) {
        Cfilem f = mock(Cfilem.class);
        given(f.getFlMngNo()).willReturn(flMngNo);
        given(f.getOrcFlNm()).willReturn("테스트파일.pdf");
        given(f.getSvrFlNm()).willReturn("SVR1_20260101120000_abc.pdf");
        given(f.getFlKpnPth()).willReturn("/data/files/요구사항정의서/2026/01");
        given(f.getFlDtt()).willReturn("첨부파일");
        given(f.getOrcPkVl()).willReturn("PRJ-2026-0001");
        given(f.getOrcDtt()).willReturn("요구사항정의서");
        return f;
    }

    // ───────────────────────────────────────────────────────
    // getFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFile: 존재하는 파일관리번호이면 응답 DTO를 반환한다")
    void getFile_존재하는파일_DTO반환() {
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N"))
                .willReturn(Optional.of(file));

        FileDto.Response result = fileService.getFile(FL_MNG_NO);

        assertThat(result.getFlMngNo()).isEqualTo(FL_MNG_NO);
        assertThat(result.getOrcFlNm()).isEqualTo("테스트파일.pdf");
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/" + FL_MNG_NO + "/download");
    }

    @Test
    @DisplayName("getFile: 존재하지 않는 파일관리번호이면 CustomGeneralException을 던진다")
    void getFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    // ───────────────────────────────────────────────────────
    // getFiles
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFiles: orcDtt 미입력이면 CustomGeneralException을 던진다")
    void getFiles_orcDtt없음_CustomGeneralException발생() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder().build();

        assertThatThrownBy(() -> fileService.getFiles(condition))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("orcDtt");
    }

    @Test
    @DisplayName("getFiles: orcDtt만 입력하면 해당 원본구분의 전체 파일 목록을 반환한다")
    void getFiles_orcDtt만있을때_전체목록반환() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder()
                .orcDtt("요구사항정의서")
                .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByOrcDttAndDelYn("요구사항정의서", "N"))
                .willReturn(List.of(file));

        List<FileDto.Response> result = fileService.getFiles(condition);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlMngNo()).isEqualTo(FL_MNG_NO);
    }

    @Test
    @DisplayName("getFiles: orcDtt + orcPkVl 입력이면 해당 원본구분·원본PK 파일 목록을 반환한다")
    void getFiles_orcDttAndPkVl_조건필터링반환() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder()
                .orcDtt("요구사항정의서")
                .orcPkVl("PRJ-2026-0001")
                .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(file));

        List<FileDto.Response> result = fileService.getFiles(condition);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getFiles: orcDtt + orcPkVl + flDtt 입력이면 세 조건으로 필터링한다")
    void getFiles_파일구분포함_조건필터링반환() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder()
                .orcDtt("요구사항정의서")
                .orcPkVl("PRJ-2026-0001")
                .flDtt("이미지")
                .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndFlDttAndDelYn(
                "요구사항정의서", "PRJ-2026-0001", "이미지", "N"))
                .willReturn(List.of(file));

        List<FileDto.Response> result = fileService.getFiles(condition);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPreviewUrl()).isEqualTo("/api/files/" + FL_MNG_NO + "/preview");
    }

    // ───────────────────────────────────────────────────────
    // deleteFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: 존재하지 않는 파일이면 CustomGeneralException을 던진다")
    void deleteFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    @Test
    @DisplayName("deleteFile: 존재하는 파일이면 delete()를 호출하여 Soft Delete한다")
    void deleteFile_존재하는파일_SoftDelete호출() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        fileService.deleteFile(FL_MNG_NO);

        verify(cfilem).delete();
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일 2건을 일괄 Soft Delete하고 삭제 건수를 반환한다")
    void deleteFilesByOrc_파일2건_2반환() {
        Cfilem f1 = mockCfilem("FL_00000001");
        Cfilem f2 = mockCfilem("FL_00000002");
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(f1, f2));

        int count = fileService.deleteFilesByOrc("요구사항정의서", "PRJ-2026-0001");

        assertThat(count).isEqualTo(2);
        verify(f1).delete();
        verify(f2).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일이 없으면 0을 반환한다")
    void deleteFilesByOrc_파일없음_0반환() {
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("없는구분", "PRJ-9999-9999", "N"))
                .willReturn(List.of());

        int count = fileService.deleteFilesByOrc("없는구분", "PRJ-9999-9999");

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("updateFileMeta: 존재하는 파일이면 원본 정보를 변경하고 파일관리번호를 반환한다")
    void updateFileMeta_존재하는파일_메타수정() {
        Cfilem cfilem = mock(Cfilem.class);
        FileDto.UpdateRequest request = FileDto.UpdateRequest.builder()
                .orcDtt("정보화사업")
                .orcPkVl("PRJ-2026-0002")
                .build();
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        String result = fileService.updateFileMeta(FL_MNG_NO, request);

        assertThat(result).isEqualTo(FL_MNG_NO);
        verify(cfilem).updateMeta("PRJ-2026-0002", "정보화사업");
    }

    @Test
    @DisplayName("updateFileMeta: 존재하지 않는 파일이면 CustomGeneralException을 던진다")
    void updateFileMeta_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());
        FileDto.UpdateRequest request = FileDto.UpdateRequest.builder().orcDtt("정보화사업").build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    @Test
    @DisplayName("downloadFile: 저장 경로가 기준 경로 밖이면 다운로드를 차단한다")
    void downloadFile_경로이탈_CustomGeneralException발생(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(tempDir.resolveSibling("outside").toString());
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.downloadFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("허용되지 않는 파일 경로");
    }

    @Test
    @DisplayName("downloadFile: 존재하는 파일이면 Resource와 MIME 타입을 반환한다")
    void downloadFile_존재하는파일_리소스반환(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("요구사항정의서").resolve("2026").resolve("05");
        Files.createDirectories(storageDir);
        java.nio.file.Path filePath = storageDir.resolve("SVR1_test.pdf");
        Files.writeString(filePath, "PDF", StandardCharsets.UTF_8);
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
        given(cfilem.getSvrFlNm()).willReturn("SVR1_test.pdf");
        given(cfilem.getOrcFlNm()).willReturn("요구사항정의서.pdf");
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        FileService.FileDownloadResult result = fileService.downloadFile(FL_MNG_NO);

        assertThat(result.resource().exists()).isTrue();
        assertThat(result.originalFilename()).isEqualTo("요구사항정의서.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("downloadFile: 원본 파일 확장자별 MIME 타입을 반환한다")
    void downloadFile_확장자별Mime타입반환(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("첨부");
        Files.createDirectories(storageDir);

        Object[][] cases = {
                {"jpg", "image/jpeg"},
                {"jpeg", "image/jpeg"},
                {"png", "image/png"},
                {"gif", "image/gif"},
                {"webp", "image/webp"},
                {"svg", "image/svg+xml"},
                {"bmp", "image/bmp"},
                {"ico", "image/x-icon"},
                {"doc", "application/msword"},
                {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"xls", "application/vnd.ms-excel"},
                {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                {"ppt", "application/vnd.ms-powerpoint"},
                {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
                {"hwp", "application/x-hwp"},
                {"txt", "text/plain"},
                {"csv", "text/csv"},
                {"json", "application/json"},
                {"zip", "application/zip"},
                {"bin", "application/octet-stream"}
        };

        for (Object[] testCase : cases) {
            String ext = (String) testCase[0];
            String expected = (String) testCase[1];
            String flMngNo = "FL_" + ext;
            String svrFlNm = "server." + ext;
            Files.writeString(storageDir.resolve(svrFlNm), "data", StandardCharsets.UTF_8);
            Cfilem cfilem = mockCfilem(flMngNo);
            given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
            given(cfilem.getSvrFlNm()).willReturn(svrFlNm);
            given(cfilem.getOrcFlNm()).willReturn("origin." + ext);
            given(fileRepository.findByFlMngNoAndDelYn(flMngNo, "N")).willReturn(Optional.of(cfilem));

            FileService.FileDownloadResult result = fileService.downloadFile(flMngNo);

            assertThat(result.contentType()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("downloadFile: 원본 파일명이 없으면 서버 파일명 확장자로 MIME 타입을 판정한다")
    void downloadFile_원본파일명없음_서버파일명확장자사용(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("첨부");
        Files.createDirectories(storageDir);
        Files.writeString(storageDir.resolve("server.png"), "data", StandardCharsets.UTF_8);
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
        given(cfilem.getSvrFlNm()).willReturn("server.png");
        given(cfilem.getOrcFlNm()).willReturn(null);
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        FileService.FileDownloadResult result = fileService.downloadFile(FL_MNG_NO);

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("downloadFile: 메타데이터가 없거나 실제 파일을 읽을 수 없으면 예외가 발생한다")
    void downloadFile_파일없음_CustomGeneralException발생(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        given(fileRepository.findByFlMngNoAndDelYn("MISSING", "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.downloadFile("MISSING"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("존재하지 않는 파일");

        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(tempDir.toString());
        given(cfilem.getSvrFlNm()).willReturn("missing.pdf");
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.downloadFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("uploadFile: 빈 파일이면 저장소 접근 없이 예외를 던진다")
    void uploadFile_빈파일_CustomGeneralException발생() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request = FileDto.UploadRequest.builder().orcDtt("요구사항정의서").build();

        assertThatThrownBy(() -> fileService.uploadFile(emptyFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("uploadFileAndGet: 파일을 저장하고 업로드 응답 DTO를 반환한다")
    void uploadFileAndGet_정상파일_응답반환(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileService, "entityManager", entityManager);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "요구사항.pdf", "application/pdf", "PDF".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("요구사항정의서")
                .orcPkVl("PRJ-2026-0001")
                .flDtt("첨부파일")
                .build();

        FileDto.Response result = fileService.uploadFileAndGet(file, request);

        assertThat(result.getFlMngNo()).isEqualTo("FL_00000001");
        assertThat(result.getOrcFlNm()).isEqualTo("요구사항.pdf");
        assertThat(result.getSvrFlNm()).startsWith("SVR1_").endsWith(".pdf");
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/FL_00000001/download");
        org.mockito.Mockito.verify(entityManager).persist(org.mockito.ArgumentMatchers.any(Cfilem.class));
        org.mockito.Mockito.verify(entityManager).flush();
    }

    @Test
    @DisplayName("uploadFileInternal: 디렉토리 생성 IOException 발생 시 cause 포함 예외 반환 — ERR-02")
    void uploadFileInternal_디렉토리생성IOException_cause포함(@TempDir java.nio.file.Path tempDir) throws Exception {
        // orcDtt 이름으로 파일을 미리 생성 → 같은 이름의 하위 디렉토리 생성 불가 (NotADirectoryException)
        java.nio.file.Path blockingFile = tempDir.resolve("요구사항정의서");
        java.nio.file.Files.createFile(blockingFile);

        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileService, "entityManager", entityManager);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("요구사항정의서")
                .flDtt("첨부파일")
                .build();

        // 현재 구현: CustomGeneralException(메시지, e)로 IOException을 cause로 포함하여 래핑
        assertThatThrownBy(() -> fileService.uploadFileInternal(file, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("uploadFiles: 일부 파일 실패 시 성공 목록과 실패 파일명을 함께 반환한다")
    void uploadFiles_부분실패_결과분리(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileService, "entityManager", entityManager);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile okFile = new MockMultipartFile(
                "files", "ok.txt", "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("첨부")
                .flDtt("첨부파일")
                .build();

        FileDto.BulkUploadResponse result = fileService.uploadFiles(List.of(okFile, emptyFile), request);

        assertThat(result.getSuccessList()).hasSize(1);
        assertThat(result.getFailList()).hasSize(1);
        assertThat(result.getFailList().get(0)).contains("empty.txt");
    }

    // ───────────────────────────────────────────────────────
    // deleteFile — 이미 삭제된 파일(DEL_YN=Y) 재삭제 시도
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: DEL_YN=Y 상태 파일(조회 결과 없음)은 CustomGeneralException을 던진다")
    void deleteFile_이미삭제된파일_CustomGeneralException발생() {
        // Arrange: DEL_YN=Y인 파일은 findByFlMngNoAndDelYn("N") 결과에서 제외됨
        given(fileRepository.findByFlMngNoAndDelYn("FL_DELETED", "N"))
                .willReturn(java.util.Optional.empty());

        // Act & Assert: 이미 논리 삭제된 파일 재삭제 시도 → 예외 발생
        assertThatThrownBy(() -> fileService.deleteFile("FL_DELETED"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("존재하지 않는 파일");
    }

    // ───────────────────────────────────────────────────────
    // uploadFile — 0바이트(빈 파일) 업로드
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFile: getSize()=0인 파일이면 EntityManager 접근 없이 예외를 던진다")
    void uploadFile_0바이트파일_CustomGeneralException발생() {
        // Arrange: 내용 없는 MockMultipartFile (isEmpty() == true)
        MockMultipartFile zeroByteFile = new MockMultipartFile(
                "file", "zero.pdf", "application/pdf", new byte[0]);
        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("요구사항정의서")
                .flDtt("첨부파일")
                .build();

        // Act & Assert: 빈 파일 → "업로드할 파일이 비어있습니다" 예외, EntityManager 미호출
        assertThatThrownBy(() -> fileService.uploadFile(zeroByteFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(entityManager);
    }

    // ───────────────────────────────────────────────────────
    // uploadFiles — 다중 파일 일괄 업로드, 일부 실패 (null 파일 포함)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFiles: null 파일이 포함된 경우 해당 파일만 실패 목록에 포함된다")
    void uploadFiles_null파일포함_해당파일실패목록포함(@TempDir java.nio.file.Path tempDir) {
        // Arrange: 정상 파일 1개 + null 파일 1개
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileService, "entityManager", entityManager);
        given(fileRepository.getNextSequenceValue()).willReturn(2L);

        MockMultipartFile validFile = new MockMultipartFile(
                "files", "valid.pdf", "application/pdf",
                "content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // null MultipartFile은 NullPointerException → failList에 포함
        MockMultipartFile nullContentFile = new MockMultipartFile(
                "files", "empty.txt", "text/plain", new byte[0]);

        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("요구사항정의서")
                .flDtt("첨부파일")
                .build();

        // Act
        FileDto.BulkUploadResponse result = fileService.uploadFiles(
                java.util.Arrays.asList(validFile, nullContentFile), request);

        // Assert: 정상 1개 성공, 빈 파일 1개 실패
        assertThat(result.getSuccessList()).hasSize(1);
        assertThat(result.getFailList()).hasSize(1);
        assertThat(result.getFailList().get(0)).contains("empty.txt");
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc — 존재하지 않는 원본구분·원본PK로 일괄 삭제 시도
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFilesByOrc: 해당 원본구분·원본PK에 파일이 없으면 예외 없이 0을 반환한다")
    void deleteFilesByOrc_파일없는원본PK_0반환() {
        // Arrange: DB에 매칭되는 파일 없음
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("없는구분", "PRJ-0000-0000", "N"))
                .willReturn(java.util.Collections.emptyList());

        // Act
        int count = fileService.deleteFilesByOrc("없는구분", "PRJ-0000-0000");

        // Assert: 예외 없이 0 반환
        assertThat(count).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc — 일부 파일 삭제 (3건 중 Soft Delete 3건)
    // ───────────────────────────────────────────────────────

    // ───────────────────────────────────────────────────────
    // uploadFileInternal — Files.copy IOException (파일 디스크 저장 실패)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFileInternal: getInputStream()이 IOException을 던지면 파일 저장 실패 예외가 cause 포함으로 반환된다")
    void uploadFileInternal_파일copy실패_cause포함IOException(@TempDir java.nio.file.Path tempDir) throws Exception {
        // Arrange: MultipartFile.getInputStream()이 IOException을 던지도록 mock 구성
        // MockMultipartFile은 생성 시 바이트를 미리 읽으므로 mock(MultipartFile)을 사용한다
        org.springframework.web.multipart.MultipartFile mockFile =
                mock(org.springframework.web.multipart.MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false);
        given(mockFile.getOriginalFilename()).willReturn("report.pdf");
        given(mockFile.getInputStream()).willThrow(new IOException("디스크 쓰기 시뮬레이션 오류"));

        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileService, "entityManager", entityManager);
        given(fileRepository.getNextSequenceValue()).willReturn(99L);

        FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                .orcDtt("파일copy실패")
                .flDtt("첨부파일")
                .build();

        // Act & Assert: Files.copy(inputStream, ...) → IOException → CustomGeneralException(메시지, e)
        assertThatThrownBy(() -> fileService.uploadFileInternal(mockFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("파일 저장에 실패했습니다")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("deleteFilesByOrc: 3건 파일을 일괄 Soft Delete하고 3을 반환한다")
    void deleteFilesByOrc_3건일괄삭제_3반환() {
        // Arrange: 파일 3건
        Cfilem f1 = mockCfilem("FL_00000011");
        Cfilem f2 = mockCfilem("FL_00000012");
        Cfilem f3 = mockCfilem("FL_00000013");
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("정보화사업", "BIZ-2026-0001", "N"))
                .willReturn(java.util.Arrays.asList(f1, f2, f3));

        // Act
        int count = fileService.deleteFilesByOrc("정보화사업", "BIZ-2026-0001");

        // Assert: 3건 모두 delete() 호출, 반환값 3
        assertThat(count).isEqualTo(3);
        verify(f1).delete();
        verify(f2).delete();
        verify(f3).delete();
    }
}
