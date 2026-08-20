package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.CapitalOverviewReader;
import com.kdb.it.domain.migration.request.service.adapter.CapitalProjectFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.FormApproverReader;
import com.kdb.it.domain.migration.request.service.adapter.FormCheckboxReader;
import com.kdb.it.domain.migration.request.service.adapter.FormLabelReader;
import com.kdb.it.domain.migration.request.service.adapter.FormSheetAdapter;
import com.kdb.it.domain.migration.request.service.adapter.GeneralExpenseFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.RecurringProjectFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.ResourceTableReader;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationApprovalStamper;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class RequestForm2026SampleSmokeTest {

    private static final String SAMPLE_DIR_ENV = "REQUEST_FORM_SAMPLE_2026_DIR";

    private static final Path SINGLE_RECURRING_SAMPLE =
            Path.of(
                    "IT기획부(180)",
                    "_IT인프라팀",
                    "붙임2. 2026년 전산예산 편성 요청서(IT인프라팀)",
                    "04. (자본예산) IT인프라 자원증설",
                    "[자료1] 2026년 전산예산 편성 요청서_IT인프라 자원증설.xls");

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    @Test
    @DisplayName("2026 샘플 Excel을 모두 열고 요청서와 증빙을 기존 개수로 분류한다")
    void opensAndClassifiesSample2026() throws IOException {
        Path sampleRoot = sampleRoot();
        Assumptions.assumeTrue(Files.isDirectory(sampleRoot), "로컬 2026 샘플이 없어 건너뜁니다");

        List<Path> excelFiles;
        try (var paths = Files.walk(sampleRoot)) {
            excelFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(RequestForm2026SampleSmokeTest::isExcel)
                            .sorted()
                            .toList();
        }

        int requestForms = 0;
        for (Path path : excelFiles) {
            String fileKey = sampleRoot.relativize(path).toString().replace('\\', '/');
            try (Workbook workbook = reader.open(Files.readAllBytes(path), fileKey)) {
                if (!reader.classify(workbook).isEmpty()) requestForms++;
            }
        }

        assertThat(excelFiles).hasSize(44);
        assertThat(requestForms).isEqualTo(32);
        assertThat(excelFiles.size() - requestForms).isEqualTo(12);
    }

    @Test
    @DisplayName("단건 경상사업 샘플을 모든 해당 어댑터로 읽어 품목 수량을 유지한다")
    void adaptsSingleRecurringSample() throws IOException {
        Path sample = sampleRoot().resolve(SINGLE_RECURRING_SAMPLE);
        Assumptions.assumeTrue(Files.isRegularFile(sample), "로컬 단건 샘플이 없어 건너뜁니다");

        SheetAnchorScanner scanner = new SheetAnchorScanner();
        FormLabelReader labelReader = new FormLabelReader(scanner);
        ResourceTableReader resourceReader = new ResourceTableReader(scanner);
        MigrationIoeCatalogReader catalogReader = mock(MigrationIoeCatalogReader.class);
        List<FormSheetAdapter> adapters =
                List.of(
                        new CapitalProjectFormAdapter(
                                new CapitalOverviewReader(
                                        scanner, labelReader, new FormCheckboxReader()),
                                resourceReader,
                                catalogReader),
                        new RecurringProjectFormAdapter(
                                labelReader, resourceReader, new FormApproverReader(scanner)),
                        new GeneralExpenseFormAdapter(
                                scanner, catalogReader, new FormApproverReader(scanner)));

        OrgIdentityResolver orgResolver = mock(OrgIdentityResolver.class);
        OrgIdentityResolver.Index orgIndex = mock(OrgIdentityResolver.Index.class);
        when(orgResolver.snapshot()).thenReturn(orgIndex);
        when(orgIndex.resolveOrgFolder(anyString()))
                .thenReturn(new OrgIdentityResolver.Resolution("180", "IT기획부", List.of(), false));
        IoeHierarchyIndex ioeHierarchy = mock(IoeHierarchyIndex.class);
        IoeHierarchyIndex.Snapshot ioeSnapshot = TestIoeIndex.snapshot();
        when(ioeHierarchy.snapshot()).thenReturn(ioeSnapshot);
        CostRepository costRepository = mock(CostRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(costRepository.findByBseYyAndLstYnAndDelYn(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(projectRepository.findByBseYyAndLstYnAndDelYn(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        RequestFormFileImporter fileImporter =
                spy(
                        new RequestFormFileImporter(
                                mock(CostService.class),
                                mock(ProjectService.class),
                                mock(MigrationApprovalStamper.class),
                                new RequestFormValidator(costRepository, projectRepository)));
        RequestFormImportService service =
                new RequestFormImportService(
                        reader,
                        orgResolver,
                        ioeHierarchy,
                        fileImporter,
                        mock(RequestFormSourceFileArchiver.class),
                        adapters,
                        1);
        RequestFormDto.FileEntry entry =
                new RequestFormDto.FileEntry("단건/요청서.xls", "IT기획부(180)", null, null, null);

        RequestFormDto.ImportResponse response =
                service.importBatch(
                        List.of(
                                new MockMultipartFile(
                                        "files",
                                        "요청서.xls",
                                        "application/vnd.ms-excel",
                                        Files.readAllBytes(sample))),
                        new RequestFormDto.ImportManifest("2026", List.of(entry), List.of()),
                        "00000000",
                        true);

        RequestFormDto.FileResult result = response.files().get(0);
        assertThat(result.status()).isNotEqualTo(RequestFormDto.FileStatus.FAILED);
        assertThat(result.counts().recurringProjects()).isEqualTo(1);
        ArgumentCaptor<FormAdapterOutput> outputCaptor =
                ArgumentCaptor.forClass(FormAdapterOutput.class);
        verify(fileImporter).preview(outputCaptor.capture(), any(), anyString());
        assertThat(outputCaptor.getValue().projects()).singleElement();
        assertThat(outputCaptor.getValue().projects().get(0).getItems())
                .hasSize(2)
                .allSatisfy(
                        item -> {
                            assertThat(item.getGclNm()).isNotBlank();
                            assertThat(item.getQty()).isPositive();
                        });
    }

    /** worktree에서는 공유 샘플의 절대경로를 환경변수로 받고, 일반 실행은 기존 형제 경로를 씁니다. */
    private static Path sampleRoot() {
        String configured = System.getenv(SAMPLE_DIR_ENV);
        Path path =
                configured == null || configured.isBlank()
                        ? Path.of("..", "sample", "2026")
                        : Path.of(configured);
        return path.toAbsolutePath().normalize();
    }

    private static boolean isExcel(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xls") || name.endsWith(".xlsx");
    }
}
