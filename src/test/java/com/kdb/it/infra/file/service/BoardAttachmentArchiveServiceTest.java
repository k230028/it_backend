package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class BoardAttachmentArchiveServiceTest {

    private static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of("ITPZZ001"), "D01");

    @Mock private FileService fileService;

    private BoardAttachmentArchiveService service;

    @BeforeEach
    void setUp() {
        service = new BoardAttachmentArchiveService(fileService);
    }

    @Test
    @DisplayName("전체 다운로드는 게시물 읽기 권한을 통과한 첨부를 조회 순서대로 ZIP에 담는다")
    void writeArchive_nullSelection_writesAllAuthorizedBoardAttachments() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "계약서.pdf"), file("FL-2", "견적서.xlsx")));
        given(fileService.downloadFile("FL-1")).willReturn(download("PDF", "계약서.pdf"));
        given(fileService.downloadFile("FL-2")).willReturn(download("XLSX", "견적서.xlsx"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("계약서.pdf", "PDF"), new ZipContent("견적서.xlsx", "XLSX"));
        ArgumentCaptor<FileDto.SearchCondition> condition =
                ArgumentCaptor.forClass(FileDto.SearchCondition.class);
        verify(fileService).getFiles(condition.capture(), org.mockito.ArgumentMatchers.same(USER));
        assertThat(condition.getValue().getPkColNm()).isEqualTo("공통게시판");
        assertThat(condition.getValue().getPkCone()).isEqualTo("NAC-2026-0003");
    }

    @Test
    @DisplayName("선택 다운로드는 선택한 파일만 담고 같은 파일명은 모두 보존한다")
    void writeArchive_selection_writesOnlySelectedFilesAndKeepsDuplicateNames() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(
                        List.of(
                                file("FL-1", "첨부.txt"),
                                file("FL-2", "제외.txt"),
                                file("FL-3", "첨부.txt")));
        given(fileService.downloadFile("FL-1")).willReturn(download("ONE", "첨부.txt"));
        given(fileService.downloadFile("FL-3")).willReturn(download("THREE", "첨부.txt"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", List.of("FL-3", "FL-1"), USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("첨부.txt", "ONE"), new ZipContent("첨부(2).txt", "THREE"));
        verify(fileService, never()).downloadFile("FL-2");
    }

    @Test
    @DisplayName("권한 조회 결과에 없는 선택 ID가 있으면 어떤 파일도 내려받지 않는다")
    void writeArchive_foreignSelection_rejectedBeforeDownload() {
        given(fileService.getFiles(any(), any())).willReturn(List.of(file("FL-1", "허용.txt")));

        assertThatThrownBy(
                        () ->
                                service.writeArchive(
                                        "NAC-2026-0003",
                                        List.of("FL-1", "FOREIGN"),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("공백 게시물 번호와 비어 있거나 중복된 선택 목록은 조회 전에 거부한다")
    void writeArchive_invalidRequest_rejectedBeforeLookup() {
        assertThatThrownBy(() -> service.writeArchive(" ", null, USER, new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.writeArchive(
                                        "NAC-2026-0003",
                                        List.of(),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.writeArchive(
                                        "NAC-2026-0003",
                                        List.of("FL-1", "FL-1"),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("파일명의 경로 구분자는 제거해 ZIP 경로 탈출을 막는다")
    void writeArchive_pathSeparatorsInFileName_areSanitized() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "../폴더\\비밀.txt")));
        given(fileService.downloadFile("FL-1")).willReturn(download("SAFE", "../폴더\\비밀.txt"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(new ZipContent(".._폴더_비밀.txt", "SAFE"));
    }

    @Test
    @DisplayName("접근 가능한 첨부가 하나도 없으면 내려받기 전에 거부한다")
    void writeArchive_noAuthorizedFiles_rejectedBeforeDownload() {
        given(fileService.getFiles(any(), any())).willReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.writeArchive(
                                        "NAC-2026-0003", null, USER, new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("없습니다");

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("공백 파일매핑ID가 섞이면 조회 전에 거부한다")
    void writeArchive_blankFileId_rejectedBeforeLookup() {
        List<String> withBlank = new ArrayList<>();
        withBlank.add("FL-1");
        withBlank.add("  ");

        assertThatThrownBy(
                        () ->
                                service.writeArchive(
                                        "NAC-2026-0003",
                                        withBlank,
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("공백");

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("파일명이 비어 있으면 파일매핑ID를 항목 이름으로 쓴다")
    void writeArchive_blankFileName_fallsBackToFileId() throws Exception {
        given(fileService.getFiles(any(), any())).willReturn(List.of(file("FL-1", "  ")));
        given(fileService.downloadFile("FL-1")).willReturn(download("BLANK", "  "));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray())).containsExactly(new ZipContent("FL-1", "BLANK"));
    }

    @Test
    @DisplayName("제어문자와 DEL은 밑줄로 바꿔 ZIP 항목 이름을 위조하지 못하게 한다")
    void writeArchive_controlCharactersInFileName_areSanitized() throws Exception {
        String hostile = "보고\u0000서\u001F메모\u007F.txt";
        given(fileService.getFiles(any(), any())).willReturn(List.of(file("FL-1", hostile)));
        given(fileService.downloadFile("FL-1")).willReturn(download("SAFE", hostile));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(new ZipContent("보고_서_메모_.txt", "SAFE"));
    }

    @Test
    @DisplayName("파일명이 점뿐이면 상위 경로로 해석되지 않도록 밑줄로 바꾼다")
    void writeArchive_dotOnlyFileName_isNeutralized() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "."), file("FL-2", "..")));
        given(fileService.downloadFile("FL-1")).willReturn(download("ONE", "."));
        given(fileService.downloadFile("FL-2")).willReturn(download("TWO", ".."));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(new ZipContent("_", "ONE"), new ZipContent("__", "TWO"));
    }

    @Test
    @DisplayName("확장자가 없는 동명 파일도 순번을 붙여 세 건 모두 보존한다")
    void writeArchive_duplicateNamesWithoutExtension_areNumbered() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "첨부"), file("FL-2", "첨부"), file("FL-3", "첨부")));
        given(fileService.downloadFile("FL-1")).willReturn(download("ONE", "첨부"));
        given(fileService.downloadFile("FL-2")).willReturn(download("TWO", "첨부"));
        given(fileService.downloadFile("FL-3")).willReturn(download("THREE", "첨부"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("첨부", "ONE"),
                        new ZipContent("첨부(2)", "TWO"),
                        new ZipContent("첨부(3)", "THREE"));
    }

    @Test
    @DisplayName("점으로 시작하는 파일명은 확장자로 보지 않고 이름 전체에 순번을 붙인다")
    void writeArchive_leadingDotNames_treatWholeNameAsStem() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", ".env"), file("FL-2", ".env")));
        given(fileService.downloadFile("FL-1")).willReturn(download("ONE", ".env"));
        given(fileService.downloadFile("FL-2")).willReturn(download("TWO", ".env"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeArchive("NAC-2026-0003", null, USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(new ZipContent(".env", "ONE"), new ZipContent(".env(2)", "TWO"));
    }

    private static FileDto.Response file(String id, String fileName) {
        return FileDto.Response.builder()
                .flMpnId(id)
                .flNm(fileName)
                .pkColNm("공통게시판")
                .pkCone("NAC-2026-0003")
                .build();
    }

    private static FileService.FileDownloadResult download(String content, String fileName) {
        return new FileService.FileDownloadResult(
                new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)),
                fileName,
                "application/octet-stream");
    }

    private static List<ZipContent> unzip(byte[] archive) throws Exception {
        List<ZipContent> contents = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                contents.add(
                        new ZipContent(
                                entry.getName(),
                                new String(zip.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return contents;
    }

    private record ZipContent(String name, String content) {}
}
