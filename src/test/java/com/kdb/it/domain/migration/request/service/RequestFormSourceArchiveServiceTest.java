package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.request.dto.RequestFormSourceArchiveRequest;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class RequestFormSourceArchiveServiceTest {

    private static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of("ITPZZ001"), "D01");

    @Mock private FileService fileService;

    private RequestFormSourceArchiveService service;

    @BeforeEach
    void setUp() {
        service = new RequestFormSourceArchiveService(fileService);
    }

    /**
     * 확정(prepareArchive) → 전송(writeArchive) 두 단계를 이어 부르는 테스트 헬퍼입니다. 컨트롤러는 확정을 응답 헤더 확정 전에 따로
     * 부르므로(BE-67), 여기서는 기존 시나리오의 동작만 그대로 재현합니다.
     */
    private void writeArchive(
            RequestFormSourceArchiveRequest request,
            CustomUserDetails userDetails,
            java.io.OutputStream output) {
        service.writeArchive(service.prepareArchive(request, userDetails), output);
    }

    @Test
    @DisplayName("선택 ID가 null이면 권한이 있는 모든 원본을 조회 순서와 폴더 구조대로 압축한다")
    void writeArchive_nullSelection_writesAllAuthorizedFilesInNestedFolders() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(
                        List.of(
                                file("FL-1", "근거.pdf", "2026/IT부(D01)/근거.pdf"),
                                file("FL-2", "설명.txt", "2026/IT부(D01)/02. 설명/설명.txt")));
        given(fileService.downloadFile("FL-1")).willReturn(download("PDF", "근거.pdf"));
        given(fileService.downloadFile("FL-2")).willReturn(download("TEXT", "설명.txt"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("2026/IT부(D01)/근거.pdf", "PDF"),
                        new ZipContent("2026/IT부(D01)/02. 설명/설명.txt", "TEXT"));
        ArgumentCaptor<FileDto.SearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(FileDto.SearchCondition.class);
        verify(fileService)
                .getFiles(conditionCaptor.capture(), org.mockito.ArgumentMatchers.same(USER));
        assertThat(conditionCaptor.getValue().getPkColNm()).isEqualTo("편성요청서반입");
        assertThat(conditionCaptor.getValue().getPkCone()).isEqualTo("APF-1");
    }

    @Test
    @DisplayName("선택 ID가 있으면 권한 결과의 조회 순서를 유지하며 선택한 원본만 압축한다")
    void writeArchive_selection_writesOnlySelectedAuthorizedFiles() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(
                        List.of(
                                file("FL-1", "첫째.txt", "첫째.txt"),
                                file("FL-2", "둘째.txt", "둘째.txt"),
                                file("FL-3", "셋째.txt", "셋째.txt")));
        given(fileService.downloadFile("FL-1")).willReturn(download("ONE", "첫째.txt"));
        given(fileService.downloadFile("FL-3")).willReturn(download("THREE", "셋째.txt"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeArchive(
                new RequestFormSourceArchiveRequest("APF-1", List.of("FL-3", "FL-1")),
                USER,
                output);

        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("첫째.txt", "ONE"), new ZipContent("셋째.txt", "THREE"));
        verify(fileService, never()).downloadFile("FL-2");
    }

    @Test
    @DisplayName("권한 결과에 없는 선택 ID가 있으면 어떤 파일도 다운로드하지 않는다")
    void writeArchive_missingSelectedId_rejectedBeforeDownload() {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "허용.txt", "허용.txt")));

        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest(
                                                "APF-1", List.of("FL-1", "FOREIGN")),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("비어 있는 선택 목록을 거부한다")
    void writeArchive_emptySelection_rejected() {
        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest("APF-1", List.of()),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("권한이 있는 원본이 하나도 없으면 빈 ZIP 대신 오류를 반환한다")
    void writeArchive_emptyAuthorizedResult_rejected() {
        given(fileService.getFiles(any(), any())).willReturn(List.of());

        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest("APF-1", null),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("중복된 선택 파일 ID를 거부한다")
    void writeArchive_duplicateSelection_rejected() {
        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest(
                                                "APF-1", List.of("FL-1", "FL-1")),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("공백 신청번호를 거부한다")
    void writeArchive_blankApplicationNumber_rejected() {
        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest(" ", null),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("공백 선택 파일 ID를 거부한다")
    void writeArchive_blankSelectedId_rejected() {
        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest(
                                                "APF-1", List.of("FL-1", " ")),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class);

        verify(fileService, never()).getFiles(any(), any());
    }

    @Test
    @DisplayName("같은 ZIP 경로는 확장자 앞에 순번을 붙여 모두 보존한다")
    void writeArchive_duplicateEntryPaths_receiveNumberSuffixes() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(
                        List.of(
                                file("FL-1", "근거.pdf", "폴더/근거.pdf"),
                                file("FL-2", "근거.pdf", "폴더/근거.pdf"),
                                file("FL-3", "근거.pdf", "폴더/근거.pdf"),
                                file("FL-4", "README", "README"),
                                file("FL-5", "README", "README")));
        given(fileService.downloadFile("FL-1")).willReturn(download("1", "근거.pdf"));
        given(fileService.downloadFile("FL-2")).willReturn(download("2", "근거.pdf"));
        given(fileService.downloadFile("FL-3")).willReturn(download("3", "근거.pdf"));
        given(fileService.downloadFile("FL-4")).willReturn(download("4", "README"));
        given(fileService.downloadFile("FL-5")).willReturn(download("5", "README"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(unzip(output.toByteArray()))
                .extracting(ZipContent::name)
                .containsExactly(
                        "폴더/근거.pdf", "폴더/근거(2).pdf", "폴더/근거(3).pdf", "README", "README(2)");
    }

    @Test
    @DisplayName("레거시 null 상대경로는 원본 파일명을 ZIP 루트에 사용한다")
    void writeArchive_nullRelativePath_placesFileAtRoot() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "레거시.pdf", null)));
        given(fileService.downloadFile("FL-1")).willReturn(download("OLD", "레거시.pdf"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(unzip(output.toByteArray())).containsExactly(new ZipContent("레거시.pdf", "OLD"));
    }

    @Test
    @DisplayName("레거시 공백 상대경로는 원본 파일명을 ZIP 루트에 사용한다")
    void writeArchive_blankRelativePath_placesFileAtRoot() throws Exception {
        given(fileService.getFiles(any(), any())).willReturn(List.of(file("FL-1", "레거시.pdf", " ")));
        given(fileService.downloadFile("FL-1")).willReturn(download("OLD", "레거시.pdf"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(unzip(output.toByteArray())).containsExactly(new ZipContent("레거시.pdf", "OLD"));
    }

    @Test
    @DisplayName("저장된 상대경로가 안전하지 않으면 공통 정규화 규칙으로 다운로드 전에 거부한다")
    void writeArchive_unsafeStoredRelativePath_rejectedBeforeDownload() {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "secret.pdf", "../secret.pdf")));

        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest("APF-1", null),
                                        USER,
                                        new ByteArrayOutputStream()))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageNotContaining("C:\\")
                .hasMessageNotContaining("/data/");

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("각 리소스 입력 스트림은 복사 후 닫고 호출자가 소유한 출력 스트림은 닫지 않는다")
    void writeArchive_closesEveryResourceStream_butLeavesOutputOpen() throws Exception {
        TrackingResource first = new TrackingResource("FIRST");
        TrackingResource second = new TrackingResource("SECOND");
        given(fileService.getFiles(any(), any()))
                .willReturn(
                        List.of(
                                file("FL-1", "첫째.txt", "첫째.txt"),
                                file("FL-2", "둘째.txt", "둘째.txt")));
        given(fileService.downloadFile("FL-1"))
                .willReturn(new FileService.FileDownloadResult(first, "첫째.txt", "text/plain"));
        given(fileService.downloadFile("FL-2"))
                .willReturn(new FileService.FileDownloadResult(second, "둘째.txt", "text/plain"));
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(first.closed).isTrue();
        assertThat(second.closed).isTrue();
        assertThat(output.closed).isFalse();
        assertThat(unzip(output.toByteArray()))
                .containsExactly(
                        new ZipContent("첫째.txt", "FIRST"), new ZipContent("둘째.txt", "SECOND"));
    }

    @Test
    @DisplayName("정상 완료 시 ZIP 래퍼를 닫아 압축 자원을 해제하고 호출자 출력은 열어 둔다")
    void writeArchive_success_closesZipWrapperButLeavesCallerOutputOpen() throws Exception {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "원본.txt", "원본.txt")));
        given(fileService.downloadFile("FL-1")).willReturn(download("SOURCE", "원본.txt"));
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        AtomicReference<TrackingZipOutputStream> zipReference = new AtomicReference<>();
        service =
                new RequestFormSourceArchiveService(
                        fileService,
                        target -> {
                            TrackingZipOutputStream zip = new TrackingZipOutputStream(target);
                            zipReference.set(zip);
                            return zip;
                        });

        writeArchive(new RequestFormSourceArchiveRequest("APF-1", null), USER, output);

        assertThat(zipReference.get().closed).isTrue();
        assertThat(output.closed).isFalse();
        assertThat(unzip(output.toByteArray())).containsExactly(new ZipContent("원본.txt", "SOURCE"));
    }

    @Test
    @DisplayName("파일 복사 실패 시에도 ZIP 래퍼를 닫고 호출자 출력은 열어 둔다")
    void writeArchive_copyFailure_closesZipWrapperButLeavesCallerOutputOpen() {
        given(fileService.getFiles(any(), any()))
                .willReturn(List.of(file("FL-1", "원본.txt", "원본.txt")));
        given(fileService.downloadFile("FL-1"))
                .willReturn(
                        new FileService.FileDownloadResult(
                                new FailingResource(), "원본.txt", "text/plain"));
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        AtomicReference<TrackingZipOutputStream> zipReference = new AtomicReference<>();
        service =
                new RequestFormSourceArchiveService(
                        fileService,
                        target -> {
                            TrackingZipOutputStream zip = new TrackingZipOutputStream(target);
                            zipReference.set(zip);
                            return zip;
                        });

        assertThatThrownBy(
                        () ->
                                writeArchive(
                                        new RequestFormSourceArchiveRequest("APF-1", null),
                                        USER,
                                        output))
                .isInstanceOf(CustomGeneralException.class);

        assertThat(zipReference.get().closed).isTrue();
        assertThat(output.closed).isFalse();
    }

    private static FileDto.Response file(String id, String fileName, String relativePath) {
        return FileDto.Response.builder()
                .flMpnId(id)
                .flNm(fileName)
                .relativePath(relativePath)
                .pkColNm("편성요청서반입")
                .pkCone("APF-1")
                .build();
    }

    private static FileService.FileDownloadResult download(String content, String fileName) {
        return new FileService.FileDownloadResult(
                new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)),
                fileName,
                "application/octet-stream");
    }

    private static List<ZipContent> unzip(byte[] archive) throws IOException {
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

    private static final class TrackingResource extends ByteArrayResource {
        private boolean closed;

        private TrackingResource(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() {
            return new FilterInputStream(new ByteArrayInputStream(getByteArray())) {
                @Override
                public void close() throws IOException {
                    closed = true;
                    super.close();
                }
            };
        }
    }

    private static final class CloseTrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingZipOutputStream extends ZipOutputStream {
        private boolean closed;

        private TrackingZipOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingResource extends ByteArrayResource {
        private FailingResource() {
            super(new byte[] {1});
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("테스트 복사 실패");
                }
            };
        }
    }
}
