package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class RequestFormSourceFileArchiverTest {

    private final FileService fileService = mock(FileService.class);
    private final RequestFormSourceFileArchiver archiver =
            new RequestFormSourceFileArchiver(fileService);

    private MultipartFile file(String name) {
        return new MockMultipartFile("files", name, null, new byte[] {1, 2, 3});
    }

    private RequestFormDto.FileEntry entry(String fileKey, String deptName) {
        return new RequestFormDto.FileEntry(fileKey, deptName, null, null, null);
    }

    private RequestFormDto.FileResult result(
            String fileKey,
            String deptName,
            RequestFormDto.FileStatus status,
            List<String> apfMngNos) {
        List<RequestFormDto.CreatedRecord> created = new ArrayList<>();
        for (String apfMngNo : apfMngNos) {
            created.add(
                    new RequestFormDto.CreatedRecord("BPROJM", "ABUS-" + apfMngNo, "사업", apfMngNo));
        }
        return new RequestFormDto.FileResult(
                fileKey,
                deptName,
                status,
                List.of(),
                List.copyOf(created),
                new RequestFormDto.RecordCounts(1, 0, 0),
                null);
    }

    private RequestFormDto.ImportManifest manifest(List<RequestFormDto.FileEntry> entries) {
        return new RequestFormDto.ImportManifest("2026", List.copyOf(entries), List.of());
    }

    @Test
    @DisplayName("같은 부점 폴더의 파일은 그 폴더가 만든 모든 신청서번호에 붙는다")
    void archive_linksEveryApplicationInSameFolder() {
        List<MultipartFile> files = List.of(file("a.xlsx"), file("b.xlsx"));
        List<RequestFormDto.FileEntry> entries =
                List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"), entry("IT부(D01)/b.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")),
                        result(
                                "IT부(D01)/b.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-2")));

        given(fileService.uploadFile(any(), any())).willReturn("FL-00000001", "FL-00000002");

        archiver.archive(files, manifest(entries), results);

        // 파일 2개 × 신청서 2건 = 연결 4개. 디스크 기록은 파일당 1회이므로 upload 2회, link 2회
        then(fileService).should(times(2)).uploadFile(any(), any());
        then(fileService).should(times(2)).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("다른 부점 폴더의 파일은 서로 섞이지 않는다")
    void archive_doesNotCrossFolders() {
        List<MultipartFile> files = List.of(file("a.xlsx"), file("b.xlsx"));
        List<RequestFormDto.FileEntry> entries =
                List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"), entry("총무부(D02)/b.xlsx", "총무부(D02)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")),
                        result(
                                "총무부(D02)/b.xlsx",
                                "총무부(D02)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-2")));

        given(fileService.uploadFile(any(), any())).willReturn("FL-00000001", "FL-00000002");

        archiver.archive(files, manifest(entries), results);

        ArgumentCaptor<FileDto.UploadRequest> captor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), captor.capture());
        then(fileService).should(never()).linkExistingFile(any(), any());
        assertThat(captor.getAllValues())
                .extracting(FileDto.UploadRequest::getPkCone)
                .containsExactlyInAnyOrder("APF-1", "APF-2");
    }

    @Test
    @DisplayName("BLOCKED 파일은 보관하지 않는다")
    void archive_skipsBlockedFiles() {
        List<MultipartFile> files = List.of(file("a.xlsx"));
        List<RequestFormDto.FileEntry> entries = List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.BLOCKED,
                                List.of()));

        archiver.archive(files, manifest(entries), results);

        then(fileService).should(never()).uploadFile(any(), any());
        then(fileService).should(never()).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("FAILED 파일은 보관하지 않는다")
    void archive_skipsFailedFiles() {
        List<MultipartFile> files = List.of(file("a.xlsx"));
        List<RequestFormDto.FileEntry> entries = List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.FAILED,
                                List.of()));

        archiver.archive(files, manifest(entries), results);

        then(fileService).should(never()).uploadFile(any(), any());
        then(fileService).should(never()).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("같은 신청서번호는 한 번만 연결한다")
    void archive_deduplicatesApplicationNumbers() {
        List<MultipartFile> files = List.of(file("a.xlsx"), file("b.xlsx"));
        List<RequestFormDto.FileEntry> entries =
                List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"), entry("IT부(D01)/b.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")),
                        result(
                                "IT부(D01)/b.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")));

        given(fileService.uploadFile(any(), any())).willReturn("FL-00000001");

        archiver.archive(files, manifest(entries), results);

        then(fileService).should(times(2)).uploadFile(any(), any());
        then(fileService).should(never()).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("파일 종류는 편성요청서반입으로 고정한다")
    void archive_usesFixedFileKind() {
        List<MultipartFile> files = List.of(file("a.xlsx"));
        List<RequestFormDto.FileEntry> entries = List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")));

        given(fileService.uploadFile(any(), any())).willReturn("FL-00000001");

        archiver.archive(files, manifest(entries), results);

        ArgumentCaptor<FileDto.UploadRequest> captor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should().uploadFile(any(), captor.capture());
        assertThat(captor.getValue().getPkColNm()).isEqualTo("편성요청서반입");
        assertThat(captor.getValue().getFlTpCone()).isEqualTo("첨부파일");
    }

    @Test
    @DisplayName("파일 저장이 실패해도 예외를 밖으로 던지지 않는다")
    void archive_swallowsStorageFailure() {
        List<MultipartFile> files = List.of(file("a.xlsx"));
        List<RequestFormDto.FileEntry> entries = List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1")));

        given(fileService.uploadFile(any(), any())).willThrow(new RuntimeException("디스크 오류"));

        assertThatCode(() -> archiver.archive(files, manifest(entries), results))
                .doesNotThrowAnyException();
    }
}
