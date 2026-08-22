package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.CapitalProjectFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterContext;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.FormSheetAdapter;
import com.kdb.it.domain.migration.request.service.adapter.GeneralExpenseFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.RecurringProjectFormAdapter;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestFormImportServiceTest {

    @Mock private OrgIdentityResolver orgIdentityResolver;
    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private IoeHierarchyIndex ioeHierarchyIndex;
    @Mock private RequestFormFileImporter fileImporter;
    @Mock private RequestFormSourceFileArchiver sourceFileArchiver;
    @Mock private CapitalProjectFormAdapter capitalAdapter;
    @Mock private RecurringProjectFormAdapter recurringAdapter;
    @Mock private GeneralExpenseFormAdapter generalAdapter;

    @BeforeEach
    void setUp() {
        // TestIoeIndex.snapshot()이 내부에서 Mockito를 쓰므로 when(...) 인자 안에서 부르면
        // 바깥 스터빙이 미완료 상태로 깨진다(UnfinishedStubbingException). 먼저 만들어 둔다.
        IoeHierarchyIndex.Snapshot ioeSnapshot = TestIoeIndex.snapshot();

        when(orgIdentityResolver.snapshot()).thenReturn(orgIndex);
        when(ioeHierarchyIndex.snapshot()).thenReturn(ioeSnapshot);
        when(orgIndex.resolveOrgFolder(anyString()))
                .thenReturn(new OrgIdentityResolver.Resolution("0210", "자금운용실", List.of(), false));

        when(capitalAdapter.trigger()).thenReturn(FormSheetKind.CAPITAL_OVERVIEW);
        when(recurringAdapter.trigger()).thenReturn(FormSheetKind.RECURRING);
        when(generalAdapter.trigger()).thenReturn(FormSheetKind.GENERAL_EXPENSE);
        when(capitalAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
        when(recurringAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
        when(generalAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
    }

    private RequestFormImportService service(int maxFilesPerBatch) {
        return service(new WorkbookReader(10_485_760L, 20, 5000), maxFilesPerBatch);
    }

    private RequestFormImportService service(WorkbookReader workbookReader, int maxFilesPerBatch) {
        return new RequestFormImportService(
                workbookReader,
                orgIdentityResolver,
                ioeHierarchyIndex,
                fileImporter,
                sourceFileArchiver,
                List.of(capitalAdapter, recurringAdapter, generalAdapter),
                maxFilesPerBatch);
    }

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "application/vnd.ms-excel", bytes);
    }

    private static RequestFormDto.ImportManifest manifest(String... fileKeys) {
        List<RequestFormDto.FileEntry> entries =
                Arrays.stream(fileKeys)
                        .map(
                                key ->
                                        new RequestFormDto.FileEntry(
                                                key,
                                                key.split("/")[0],
                                                null,
                                                AmountUnit.WON,
                                                "571"))
                        .toList();
        return new RequestFormDto.ImportManifest("2026", entries, List.of());
    }

    private static RequestFormDto.FileResult applied(String fileKey) {
        return new RequestFormDto.FileResult(
                fileKey,
                "자금운용실",
                RequestFormDto.FileStatus.APPLIED,
                List.of(),
                List.of(
                        new RequestFormDto.CreatedRecord(
                                "BCOSTM", "COST-2026-0001", "계약", "APF-2026-00000001")),
                new RequestFormDto.RecordCounts(0, 0, 1),
                AmountUnit.WON);
    }

    @Test
    @DisplayName("파일마다 어댑터를 태우고 결과를 모아 요약한다")
    void aggregatesPerFileResults() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/요청서.xls"),
                                "12345678",
                                false);

        assertThat(response.dryRun()).isFalse();
        assertThat(response.summary().totalFiles()).isEqualTo(1);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
        assertThat(response.summary().created().costs()).isEqualTo(1);
    }

    @Test
    @DisplayName("사전검증은 preview 경로로 보낸다")
    void dryRunUsesPreview() {
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/요청서.xls"),
                                "12345678",
                                true);

        assertThat(response.dryRun()).isTrue();
        org.mockito.Mockito.verify(fileImporter, org.mockito.Mockito.never())
                .apply(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("어댑터 결과를 합친 뒤 경상사업의 빈 담당자를 전산업무비 담당자로 보정한다")
    void fillsMissingResponsibleAfterMergingAdapters() {
        ProjectDto.CreateRequest recurring = new ProjectDto.CreateRequest();
        recurring.setOdnYn("Y");
        CostDto.CreateRequest expense = new CostDto.CreateRequest();
        expense.setCgprId("김담당");
        when(recurringAdapter.adapt(any()))
                .thenReturn(new FormAdapterOutput(List.of(recurring), List.of(), List.of(), null));
        when(generalAdapter.adapt(any()))
                .thenReturn(new FormAdapterOutput(List.of(), List.of(expense), List.of(), null));
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실/요청서.xls"),
                        "12345678",
                        true);

        ArgumentCaptor<FormAdapterOutput> outputCaptor =
                ArgumentCaptor.forClass(FormAdapterOutput.class);
        org.mockito.Mockito.verify(fileImporter)
                .preview(outputCaptor.capture(), any(), anyString());
        assertThat(outputCaptor.getValue().projects())
                .filteredOn(project -> "Y".equals(project.getOdnYn()))
                .extracting(ProjectDto.CreateRequest::getUsid)
                .containsExactly("김담당");
    }

    @Test
    @DisplayName("dry-run은 원본을 보관하지 않는다")
    void dryRun_doesNotArchive() {
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실/요청서.xls"),
                        "12345678",
                        true);

        org.mockito.Mockito.verify(sourceFileArchiver, org.mockito.Mockito.never()).archive(any());
    }

    @Test
    @DisplayName("commit은 원본을 보관한다")
    void commit_archives() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실/요청서.xls"),
                        "12345678",
                        false);

        org.mockito.Mockito.verify(sourceFileArchiver).archive(any());
    }

    @Test
    @DisplayName("보관 전용 PDF는 파싱·결과 집계에서 빼고 반입 원본 계획에 포함한다")
    void commit_keepsArchiveOnlyFileOutOfImportResults() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571", false),
                        new RequestFormDto.FileEntry(
                                "자금운용실/증빙.pdf", "자금운용실", null, null, null, true));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        file("요청서.xls", RequestFormFixtures.fullFormXls()),
                                        new MockMultipartFile(
                                                "files",
                                                "증빙.pdf",
                                                "application/pdf",
                                                "%PDF".getBytes(StandardCharsets.UTF_8))),
                                new RequestFormDto.ImportManifest("2026", entries, List.of()),
                                "12345678",
                                false);

        assertThat(response.files()).singleElement();
        assertThat(response.summary().totalFiles()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestFormSourceFileArchiver.ArchivePlanItem>> planCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sourceFileArchiver).archive(planCaptor.capture());
        assertThat(planCaptor.getValue()).hasSize(2);
        assertThat(planCaptor.getValue())
                .extracting(RequestFormSourceFileArchiver.ArchivePlanItem::archiveGroupKey)
                .containsExactly("자금운용실", "자금운용실");
        assertThat(planCaptor.getValue().get(1))
                .satisfies(
                        item -> {
                            assertThat(item.fileKey()).isEqualTo("자금운용실/증빙.pdf");
                            assertThat(item.result()).isNull();
                        });
    }

    @Test
    @DisplayName("사업 폴더에 요청서 엑셀이 없으면 차단하지 않고 폴더당 한 번 경고한다")
    void warnsArchiveGroupWithoutRequestWorkbook() {
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "IT기획부(180)/01. VDI 고도화/견적서.pdf",
                                "IT기획부(180)",
                                null,
                                null,
                                null,
                                true),
                        new RequestFormDto.FileEntry(
                                "IT기획부(180)/01. VDI 고도화/산출근거.xlsx",
                                "IT기획부(180)",
                                null,
                                null,
                                null,
                                true));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        new MockMultipartFile(
                                                "files",
                                                "견적서.pdf",
                                                "application/pdf",
                                                "%PDF".getBytes(StandardCharsets.UTF_8)),
                                        file("산출근거.xlsx", RequestFormFixtures.capitalOnlyXlsx())),
                                new RequestFormDto.ImportManifest("2026", entries, List.of()),
                                "12345678",
                                true);

        assertThat(response.files())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.fileKey())
                                    .isEqualTo("IT기획부(180)/01. VDI 고도화/견적서.pdf");
                            assertThat(result.status())
                                    .isEqualTo(RequestFormDto.FileStatus.SKIPPED);
                            assertThat(result.diagnostics())
                                    .singleElement()
                                    .satisfies(
                                            diagnostic -> {
                                                assertThat(diagnostic.field())
                                                        .isEqualTo("requestFormFile");
                                                assertThat(diagnostic.severity())
                                                        .isEqualTo(MigrationDto.Severity.WARNING);
                                                assertThat(diagnostic.message())
                                                        .contains("'요청서'", "Excel");
                                            });
                        });
        assertThat(response.summary().totalFiles()).isEqualTo(2);
        assertThat(response.summary().blockedFiles()).isZero();
        org.mockito.Mockito.verifyNoInteractions(fileImporter);
    }

    @Test
    @DisplayName("dry-run은 조작된 manifest의 비요청서 엑셀을 파싱 결과에서 제외한다")
    void dryRun_ignoresNonTargetWorkbookWithManipulatedManifest() {
        WorkbookReader workbookReader =
                org.mockito.Mockito.spy(new WorkbookReader(10_485_760L, 20, 5000));
        byte[] targetBytes = RequestFormFixtures.fullFormXls();
        byte[] nonTargetBytes = RequestFormFixtures.capitalOnlyXlsx();
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571", false),
                        new RequestFormDto.FileEntry(
                                "자금운용실/견적서.xlsx", "자금운용실", null, null, null, false));

        RequestFormDto.ImportResponse response =
                service(workbookReader, 50)
                        .importBatch(
                                List.of(
                                        file("요청서.xls", targetBytes),
                                        file("견적서.xlsx", nonTargetBytes)),
                                new RequestFormDto.ImportManifest("2026", entries, List.of()),
                                "12345678",
                                true);

        OpenedWorkbook openedWorkbook = openedWorkbook(workbookReader);
        assertThat(openedWorkbook.bytes()).isEqualTo(targetBytes);
        assertThat(openedWorkbook.filename()).isEqualTo("자금운용실/요청서.xls");
        assertThat(previewedEntry())
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(capitalAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(recurringAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(generalAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(response.files())
                .extracting(RequestFormDto.FileResult::fileKey)
                .containsExactly("자금운용실/요청서.xls");
        org.mockito.Mockito.verify(sourceFileArchiver, org.mockito.Mockito.never()).archive(any());
    }

    @Test
    @DisplayName("commit은 조작된 manifest의 비요청서 엑셀을 열지 않고 원본 보관 계획에 넣는다")
    void commit_archivesNonTargetWorkbookWithManipulatedManifestWithoutParsingIt() {
        WorkbookReader workbookReader =
                org.mockito.Mockito.spy(new WorkbookReader(10_485_760L, 20, 5000));
        byte[] targetBytes = RequestFormFixtures.fullFormXls();
        byte[] nonTargetBytes = RequestFormFixtures.capitalOnlyXlsx();
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571", false),
                        new RequestFormDto.FileEntry(
                                "자금운용실/견적서.xlsx", "자금운용실", null, null, null, false));

        RequestFormDto.ImportResponse response =
                service(workbookReader, 50)
                        .importBatch(
                                List.of(
                                        file("요청서.xls", targetBytes),
                                        file("견적서.xlsx", nonTargetBytes)),
                                new RequestFormDto.ImportManifest("2026", entries, List.of()),
                                "12345678",
                                false);

        OpenedWorkbook openedWorkbook = openedWorkbook(workbookReader);
        assertThat(openedWorkbook.bytes()).isEqualTo(targetBytes);
        assertThat(openedWorkbook.filename()).isEqualTo("자금운용실/요청서.xls");
        assertThat(appliedEntry())
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(capitalAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(recurringAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(adaptedEntry(generalAdapter))
                .extracting(RequestFormDto.FileEntry::fileKey)
                .isEqualTo("자금운용실/요청서.xls");
        assertThat(response.files())
                .extracting(RequestFormDto.FileResult::fileKey)
                .containsExactly("자금운용실/요청서.xls");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestFormSourceFileArchiver.ArchivePlanItem>> planCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sourceFileArchiver).archive(planCaptor.capture());
        assertThat(planCaptor.getValue())
                .extracting(RequestFormSourceFileArchiver.ArchivePlanItem::fileKey)
                .containsExactly("자금운용실/요청서.xls", "자금운용실/견적서.xlsx");
        assertThat(planCaptor.getValue().get(1).result()).isNull();
    }

    private OpenedWorkbook openedWorkbook(WorkbookReader workbookReader) {
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(workbookReader, org.mockito.Mockito.times(1))
                .open(bytesCaptor.capture(), filenameCaptor.capture());
        return new OpenedWorkbook(bytesCaptor.getValue(), filenameCaptor.getValue());
    }

    private RequestFormDto.FileEntry previewedEntry() {
        ArgumentCaptor<RequestFormDto.FileEntry> entryCaptor =
                ArgumentCaptor.forClass(RequestFormDto.FileEntry.class);
        org.mockito.Mockito.verify(fileImporter, org.mockito.Mockito.times(1))
                .preview(any(), entryCaptor.capture(), anyString());
        return entryCaptor.getValue();
    }

    private RequestFormDto.FileEntry appliedEntry() {
        ArgumentCaptor<RequestFormDto.FileEntry> entryCaptor =
                ArgumentCaptor.forClass(RequestFormDto.FileEntry.class);
        org.mockito.Mockito.verify(fileImporter, org.mockito.Mockito.times(1))
                .apply(any(), entryCaptor.capture(), anyString(), anyString());
        return entryCaptor.getValue();
    }

    private RequestFormDto.FileEntry adaptedEntry(FormSheetAdapter adapter) {
        ArgumentCaptor<FormAdapterContext> contextCaptor =
                ArgumentCaptor.forClass(FormAdapterContext.class);
        org.mockito.Mockito.verify(adapter, org.mockito.Mockito.times(1))
                .adapt(contextCaptor.capture());
        return contextCaptor.getValue().entry();
    }

    private record OpenedWorkbook(byte[] bytes, String filename) {}

    @Test
    @DisplayName("SKIPPED 엑셀도 그룹 키와 해석된 부서코드로 보관 계획에 남긴다")
    void commit_keepsSkippedExcelInArchivePlan() {
        RequestFormDto.FileEntry entry =
                new RequestFormDto.FileEntry(
                        "2026/자금운용실(420)/팀1/사업1/요청서_참고자료.xls",
                        "자금운용실(420)",
                        null,
                        AmountUnit.WON,
                        "571");

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        file(
                                                "요청서_참고자료.xls",
                                                RequestFormFixtures.unrelatedSheetXls())),
                                new RequestFormDto.ImportManifest(
                                        "2026", List.of(entry), List.of()),
                                "12345678",
                                false);

        assertThat(response.files())
                .singleElement()
                .extracting(RequestFormDto.FileResult::status)
                .isEqualTo(RequestFormDto.FileStatus.SKIPPED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestFormSourceFileArchiver.ArchivePlanItem>> planCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sourceFileArchiver).archive(planCaptor.capture());
        assertThat(planCaptor.getValue())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.archiveGroupKey()).isEqualTo("2026/자금운용실(420)/팀1");
                            assertThat(item.effectiveDeptCode()).isEqualTo("0210");
                        });
    }

    @Test
    @DisplayName("같은 폴더의 상충한 부서 보정값을 검증된 부서코드별 archive plan으로 전달한다")
    void commit_carriesEffectiveDepartmentCodesIntoArchivePlan() {
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "동일폴더/요청서-a.xls", "동일폴더", "D01", AmountUnit.WON, "571"),
                        new RequestFormDto.FileEntry(
                                "동일폴더/요청서-b.xls", "동일폴더", "D02", AmountUnit.WON, "571"));
        RequestFormDto.ImportManifest manifest =
                new RequestFormDto.ImportManifest("2026", entries, List.of());
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenAnswer(
                        invocation -> {
                            RequestFormDto.FileEntry entry = invocation.getArgument(1);
                            return applied(entry.fileKey());
                        });

        service(50)
                .importBatch(
                        List.of(
                                file("요청서-a.xls", RequestFormFixtures.fullFormXls()),
                                file("요청서-b.xls", RequestFormFixtures.fullFormXls())),
                        manifest,
                        "12345678",
                        false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestFormSourceFileArchiver.ArchivePlanItem>> planCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sourceFileArchiver).archive(planCaptor.capture());
        assertThat(planCaptor.getValue())
                .extracting(RequestFormSourceFileArchiver.ArchivePlanItem::effectiveDeptCode)
                .containsExactly("D01", "D02");
    }

    @Test
    @DisplayName("열지 못한 파일은 FAILED로 남기고 배치를 계속한다")
    void keepsGoingWhenOneFileIsUnreadable() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("런던지점/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        file(
                                                "요청서_깨진.xlsx",
                                                "엑셀 아님".getBytes(StandardCharsets.UTF_8)),
                                        file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/요청서_깨진.xlsx", "런던지점/요청서.xls"),
                                "12345678",
                                false);

        assertThat(response.files()).hasSize(2);
        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.FAILED);
        assertThat(response.files().get(0).diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.FILE_UNREADABLE);
        assertThat(response.files().get(1).status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("인식할 시트가 없는 파일은 실패가 아니라 SKIPPED로 남긴다")
    void skipsFileWithoutRecognizableSheet() {
        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        file(
                                                "요청서_참고자료.xls",
                                                RequestFormFixtures.unrelatedSheetXls())),
                                manifest("자금운용실(420)/팀1/사업1/요청서_참고자료.xls"),
                                "12345678",
                                true);

        RequestFormDto.FileResult result = response.files().get(0);
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.SKIPPED);
        assertThat(result.diagnostics())
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.SHEET_NOT_FOUND);
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.WARNING);
                        });
        // 건너뛴 파일은 차단도 반영도 아니다
        assertThat(response.summary().blockedFiles()).isZero();
        assertThat(response.summary().appliedFiles()).isZero();
    }

    @Test
    @DisplayName("폴더명 원문을 폴더 해석기에 그대로 넘겨 부서코드 병기를 살린다")
    void passesRawFolderNameToFolderResolver() {
        when(fileImporter.preview(any(), any(), anyString()))
                .thenReturn(applied("자금운용실(420)/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실(420)/요청서.xls"),
                        "12345678",
                        true);

        // 이름만 남기는 가공 없이 원문을 넘겨야 Index가 괄호 안 코드를 볼 수 있다
        org.mockito.Mockito.verify(orgIndex).resolveOrgFolder("자금운용실(420)");
        org.mockito.Mockito.verify(orgIndex, org.mockito.Mockito.never()).resolveOrg(anyString());
    }

    @Test
    @DisplayName("부서를 확정하지 못하면 차단하고 후보를 담아 돌려준다")
    void blocksWhenDepartmentUnresolved() {
        when(orgIndex.resolveOrgFolder(anyString()))
                .thenReturn(new OrgIdentityResolver.Resolution(null, "미등록부서", List.of(), false));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("미등록부서/요청서.xls"),
                                "12345678",
                                true);

        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.BLOCKED);
        assertThat(response.files().get(0).diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.ORG_UNRESOLVED);
    }

    @Test
    @DisplayName("배치 파일 수 상한을 넘으면 거부한다")
    void rejectsOversizeBatch() {
        assertThatThrownBy(
                        () ->
                                service(1)
                                        .importBatch(
                                                List.of(
                                                        file(
                                                                "a.xls",
                                                                RequestFormFixtures.fullFormXls()),
                                                        file(
                                                                "b.xls",
                                                                RequestFormFixtures.fullFormXls())),
                                                manifest("d/a.xls", "d/b.xls"),
                                                "12345678",
                                                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일은");
    }

    @Test
    @DisplayName("파일 수와 manifest 항목 수가 다르면 거부한다")
    void rejectsWhenManifestCountDiffers() {
        assertThatThrownBy(
                        () ->
                                service(50)
                                        .importBatch(
                                                List.of(
                                                        file(
                                                                "요청서.xls",
                                                                RequestFormFixtures.fullFormXls())),
                                                manifest("자금운용실/요청서.xls", "자금운용실/빠진파일.xls"),
                                                "12345678",
                                                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifest");
    }
}
