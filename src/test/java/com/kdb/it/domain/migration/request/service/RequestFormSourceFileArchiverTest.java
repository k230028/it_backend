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

    private void archive(
            List<MultipartFile> files,
            List<RequestFormDto.FileEntry> entries,
            List<RequestFormDto.FileResult> results) {
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            plan.add(
                    new RequestFormSourceFileArchiver.ArchivePlanItem(
                            files.get(i),
                            entries.get(i).fileKey(),
                            RequestFormArchiveGroup.keyOf(entries.get(i).fileKey()),
                            entries.get(i).deptName(),
                            results.get(i)));
        }
        archiver.archive(plan);
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

        archive(files, entries, results);

        // 파일 2개 × 신청서 2건 = 연결 4개. 디스크 기록은 파일당 1회이므로 upload 2회, link 2회
        then(fileService).should(times(2)).uploadFile(any(), any());
        then(fileService).should(times(2)).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("같은 부점 폴더의 PDF도 정상 반입된 신청서에 함께 붙는다")
    void archive_linksArchiveOnlyFileInAppliedGroup() {
        MultipartFile excel = file("요청서.xlsx");
        MultipartFile pdf = file("증빙.pdf");
        RequestFormDto.FileResult applied =
                result(
                        "IT부(D01)/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-1"));
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                excel, "2026/IT부(D01)/01. 사업/요청서.xlsx", "IT부(D01)", "D01", applied),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                pdf, "2026\\IT부(D01)\\01. 사업\\근거.pdf", "IT부(D01)", "D01", null));
        given(fileService.uploadFile(any(), any())).willReturn("FL-EXCEL", "FL-PDF");

        archiver.archive(plan);

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        then(fileService).should(times(2)).uploadFile(fileCaptor.capture(), any());
        assertThat(fileCaptor.getAllValues())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("요청서.xlsx", "증빙.pdf");
        ArgumentCaptor<FileDto.UploadRequest> requestCaptor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(FileDto.UploadRequest::getRelativePath)
                .containsExactly("2026/IT부(D01)/01. 사업/요청서.xlsx", "2026/IT부(D01)/01. 사업/증빙.pdf");
    }

    @Test
    @DisplayName("같은 부서의 서로 다른 번호 사업 폴더는 원본을 공유하지 않는다")
    void archive_doesNotCrossArchiveGroupsInSameDepartment() {
        RequestFormDto.FileResult first =
                result(
                        "2026/IT부(D01)/01. 사업A/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-A"));
        RequestFormDto.FileResult second =
                result(
                        "2026/IT부(D01)/02. 사업B/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-B"));
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("a.xlsx"),
                                first.fileKey(),
                                "2026/IT부(D01)/01. 사업A",
                                "D01",
                                first),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("b.xlsx"),
                                second.fileKey(),
                                "2026/IT부(D01)/02. 사업B",
                                "D01",
                                second));
        assertThat(plan)
                .extracting(RequestFormSourceFileArchiver.ArchivePlanItem::archiveGroupKey)
                .containsExactly("2026/IT부(D01)/01. 사업A", "2026/IT부(D01)/02. 사업B");
        given(fileService.uploadFile(any(), any())).willReturn("FL-A", "FL-B");

        archiver.archive(plan);

        ArgumentCaptor<FileDto.UploadRequest> requestCaptor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), requestCaptor.capture());
        then(fileService).should(never()).linkExistingFile(any(), any());
        assertThat(requestCaptor.getAllValues())
                .extracting(FileDto.UploadRequest::getPkCone)
                .containsExactlyInAnyOrder("APF-A", "APF-B");
    }

    @Test
    @DisplayName("상위 폴더의 파일은 모든 하위 사업 신청서에 붙는다")
    void archive_linksAncestorFileToEveryDescendantBusiness() {
        String parent = "2026/IT부(D01)/_인프라팀/붙임2";
        String firstGroup = parent + "/01. 사업A";
        String secondGroup = parent + "/02. 사업B";
        RequestFormDto.FileResult first =
                result(
                        firstGroup + "/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-A"));
        RequestFormDto.FileResult second =
                result(
                        secondGroup + "/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-B"));
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("공통근거.pdf"), parent + "/공통근거.pdf", parent, "D01", null),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("a.xlsx"), first.fileKey(), firstGroup, "D01", first),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("b.xlsx"), second.fileKey(), secondGroup, "D01", second));
        given(fileService.uploadFile(any(), any())).willReturn("FL-PARENT", "FL-A", "FL-B");

        archiver.archive(plan);

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        then(fileService).should(times(3)).uploadFile(fileCaptor.capture(), any());
        assertThat(fileCaptor.getAllValues())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("공통근거.pdf", "a.xlsx", "b.xlsx");
        then(fileService).should(times(1)).linkExistingFile(any(), any());
    }

    @Test
    @DisplayName("같은 사업 그룹의 SKIPPED 엑셀과 PDF도 정상 반입 APF에 붙인다")
    void archive_linksSkippedExcelAndPdfInAppliedArchiveGroup() {
        String group = "2026/IT부(D01)/01. 사업A";
        RequestFormDto.FileResult applied =
                result(
                        group + "/요청서.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-A"));
        RequestFormDto.FileResult skipped =
                result(
                        group + "/산출근거.xlsx",
                        "IT부(D01)",
                        RequestFormDto.FileStatus.SKIPPED,
                        List.of());
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("요청서.xlsx"), group + "/요청서.xlsx", group, "D01", applied),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("산출근거.xlsx"), group + "/산출근거.xlsx", group, "D01", skipped),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file("견적.pdf"), group + "/견적.pdf", group, "D01", null));
        given(fileService.uploadFile(any(), any())).willReturn("FL-1", "FL-2", "FL-3");

        archiver.archive(plan);

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        then(fileService).should(times(3)).uploadFile(fileCaptor.capture(), any());
        assertThat(fileCaptor.getAllValues())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("요청서.xlsx", "산출근거.xlsx", "견적.pdf");
    }

    @Test
    @DisplayName("원장이 없는 보관 전용 파일은 저장하지 않는다")
    void archive_skipsArchiveOnlyPlanWithoutAppliedRecord() {
        RequestFormSourceFileArchiver.ArchivePlanItem item =
                new RequestFormSourceFileArchiver.ArchivePlanItem(
                        file("근거.pdf"), "IT부(D01)/근거.pdf", "IT부(D01)", "D01", null);

        archiver.archive(List.of(item));

        then(fileService).should(never()).uploadFile(any(), any());
        then(fileService).should(never()).linkExistingFile(any(), any());
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

        archive(files, entries, results);

        ArgumentCaptor<FileDto.UploadRequest> captor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), captor.capture());
        then(fileService).should(never()).linkExistingFile(any(), any());
        assertThat(captor.getAllValues())
                .extracting(FileDto.UploadRequest::getPkCone)
                .containsExactlyInAnyOrder("APF-1", "APF-2");
    }

    @Test
    @DisplayName("다른 폴더가 같은 검증 부서코드로 확정돼도 파일과 신청서번호를 섞지 않는다")
    void archive_doesNotCrossFoldersSharingEffectiveDepartmentCode() {
        MultipartFile firstFile = file("a.xlsx");
        MultipartFile secondFile = file("b.xlsx");
        RequestFormDto.FileResult firstResult =
                result(
                        "첫폴더/a.xlsx",
                        "첫폴더",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-FIRST"));
        RequestFormDto.FileResult secondResult =
                result(
                        "둘째폴더/b.xlsx",
                        "둘째폴더",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-SECOND"));
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                firstFile, firstResult.fileKey(), "첫폴더", "D01", firstResult),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                secondFile, secondResult.fileKey(), "둘째폴더", "D01", secondResult));
        given(fileService.uploadFile(any(), any())).willReturn("FL-FIRST", "FL-SECOND");

        archiver.archive(plan);

        ArgumentCaptor<FileDto.UploadRequest> requestCaptor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), requestCaptor.capture());
        then(fileService).should(never()).linkExistingFile(any(), any());
        assertThat(requestCaptor.getAllValues())
                .extracting(FileDto.UploadRequest::getPkCone)
                .containsExactlyInAnyOrder("APF-FIRST", "APF-SECOND");
    }

    @Test
    @DisplayName("같은 폴더명이 서로 다른 검증 부서코드로 확정되면 파일과 신청서번호를 섞지 않는다")
    void archive_doesNotCrossEffectiveDepartmentCodes() {
        MultipartFile firstFile = file("a.xlsx");
        MultipartFile secondFile = file("b.xlsx");
        RequestFormDto.FileResult firstResult =
                result(
                        "동일폴더/a.xlsx",
                        "동일폴더",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-D01"));
        RequestFormDto.FileResult secondResult =
                result(
                        "동일폴더/b.xlsx",
                        "동일폴더",
                        RequestFormDto.FileStatus.APPLIED,
                        List.of("APF-D02"));
        List<RequestFormSourceFileArchiver.ArchivePlanItem> plan =
                List.of(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                firstFile, firstResult.fileKey(), "동일폴더", "D01", firstResult),
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                secondFile, secondResult.fileKey(), "동일폴더", "D02", secondResult));
        given(fileService.uploadFile(any(), any())).willReturn("FL-D01", "FL-D02");

        archiver.archive(plan);

        ArgumentCaptor<FileDto.UploadRequest> requestCaptor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        then(fileService).should(times(2)).uploadFile(any(), requestCaptor.capture());
        then(fileService).should(never()).linkExistingFile(any(), any());
        assertThat(requestCaptor.getAllValues())
                .extracting(FileDto.UploadRequest::getPkCone)
                .containsExactlyInAnyOrder("APF-D01", "APF-D02");
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

        archive(files, entries, results);

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

        archive(files, entries, results);

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

        archive(files, entries, results);

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

        archive(files, entries, results);

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

        assertThatCode(() -> archive(files, entries, results)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("원본 저장이 실패하면 다음 신청서번호에 다시 저장하지 않는다")
    void archive_doesNotRetryUploadAfterStorageFailure() {
        List<MultipartFile> files = List.of(file("a.xlsx"));
        List<RequestFormDto.FileEntry> entries = List.of(entry("IT부(D01)/a.xlsx", "IT부(D01)"));
        List<RequestFormDto.FileResult> results =
                List.of(
                        result(
                                "IT부(D01)/a.xlsx",
                                "IT부(D01)",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of("APF-1", "APF-2")));

        given(fileService.uploadFile(any(), any())).willThrow(new RuntimeException("디스크 오류"));

        assertThatCode(() -> archive(files, entries, results)).doesNotThrowAnyException();

        then(fileService).should(times(1)).uploadFile(any(), any());
        then(fileService).should(never()).linkExistingFile(any(), any());
    }
}
