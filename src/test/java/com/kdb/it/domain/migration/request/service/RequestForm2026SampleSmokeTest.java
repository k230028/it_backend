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
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class RequestForm2026SampleSmokeTest {

    private static final String SAMPLE_DIR_ENV = "REQUEST_FORM_SAMPLE_2026_DIR";

    private static final String SINGLE_RECURRING_SAMPLE_SUFFIX = "자원증설.xls";

    private static final Pattern DEPARTMENT_FOLDER =
            Pattern.compile(".*[(（]\\s*([0-9A-Za-z]{1,100})\\s*[)）]$");

    private static final Pattern NUMBERED_FOLDER = Pattern.compile("^\\s*\\d{1,3}\\..*");

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
        Path root = sampleRoot();
        Path sample = findSingleRecurringSample(root);
        String fileKey = browserFileKey(root, sample);
        String deptName = browserDepartmentFolder(fileKey);
        String archiveGroupKey = browserArchiveGroupKey(fileKey);
        assertThat(RequestFormArchiveGroup.keyOf(fileKey)).isEqualTo(archiveGroupKey).isNotBlank();

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
                .thenReturn(
                        new OrgIdentityResolver.Resolution(
                                departmentCode(deptName),
                                departmentLabel(deptName),
                                List.of(),
                                false));
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
                new RequestFormDto.FileEntry(fileKey, deptName, null, null, null, false);

        RequestFormDto.ImportResponse response =
                service.importBatch(
                        List.of(
                                new MockMultipartFile(
                                        "files",
                                        sample.getFileName().toString(),
                                        "application/vnd.ms-excel",
                                        Files.readAllBytes(sample))),
                        new RequestFormDto.ImportManifest("2026", List.of(entry), List.of()),
                        "00000000",
                        true);

        RequestFormDto.FileResult result = response.files().get(0);
        assertThat(result.fileKey()).isEqualTo(fileKey);
        assertThat(result.deptName()).isEqualTo(deptName);
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.diagnostics())
                .noneMatch(
                        diagnostic ->
                                diagnostic.code() == RequestFormDiagnosticCode.FILE_UNREADABLE);
        assertThat(result.counts().recurringProjects()).isEqualTo(1);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
        assertThat(response.summary().blockedFiles()).isZero();
        verify(orgIndex).resolveOrgFolder(deptName);
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

    /** 실제 경로는 소스에 남기지 않고 파일명의 최소 suffix로 대상 한 건을 찾습니다. */
    private static Path findSingleRecurringSample(Path root) throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(root), "로컬 2026 샘플이 없어 건너뜁니다");
        List<Path> matches;
        try (var paths = Files.walk(root)) {
            matches =
                    paths.filter(Files::isRegularFile)
                            .filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .toLowerCase(Locale.ROOT)
                                                    .endsWith(
                                                            SINGLE_RECURRING_SAMPLE_SUFFIX
                                                                    .toLowerCase(Locale.ROOT)))
                            .sorted()
                            .toList();
        }
        Assumptions.assumeFalse(matches.isEmpty(), "로컬 단건 샘플이 없어 건너뜁니다");
        assertThat(matches.size()).as("단건 샘플 suffix는 유일해야 합니다").isEqualTo(1);
        return matches.get(0);
    }

    /** `webkitdirectory`가 선택한 최상위 폴더명을 포함하는 브라우저 상대경로를 만듭니다. */
    private static String browserFileKey(Path root, Path file) {
        return root.getFileName() + "/" + root.relativize(file).toString().replace('\\', '/');
    }

    /** 브라우저와 같이 가장 안쪽의 코드 병기 폴더를 부서 폴더로 고릅니다. */
    private static String browserDepartmentFolder(String fileKey) {
        String[] segments = fileKey.replace('\\', '/').split("/");
        for (int index = segments.length - 2; index >= 0; index--) {
            if (DEPARTMENT_FOLDER.matcher(segments[index]).matches()) return segments[index];
        }
        return segments.length < 2 ? "" : segments[0];
    }

    /** 브라우저와 같이 부서 뒤 첫 번호 사업 폴더까지 원본 보관 그룹으로 접습니다. */
    private static String browserArchiveGroupKey(String fileKey) {
        List<String> segments =
                java.util.Arrays.stream(fileKey.replace('\\', '/').split("/"))
                        .filter(segment -> !segment.isBlank())
                        .toList();
        List<String> folders = segments.subList(0, segments.size() - 1);
        int departmentIndex = 0;
        for (int index = folders.size() - 1; index >= 0; index--) {
            if (DEPARTMENT_FOLDER.matcher(folders.get(index)).matches()) {
                departmentIndex = index;
                break;
            }
        }
        int groupEnd = departmentIndex;
        for (int index = departmentIndex + 1; index < folders.size(); index++) {
            if (!NUMBERED_FOLDER.matcher(folders.get(index)).matches()) continue;
            groupEnd = index;
            return String.join("/", folders.subList(0, groupEnd + 1));
        }
        if (departmentIndex + 1 < folders.size()) groupEnd = departmentIndex + 1;
        return String.join("/", folders.subList(0, groupEnd + 1));
    }

    private static String departmentCode(String deptName) {
        Matcher matcher = DEPARTMENT_FOLDER.matcher(deptName);
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    private static String departmentLabel(String deptName) {
        int open = Math.max(deptName.lastIndexOf('('), deptName.lastIndexOf('（'));
        return open < 0 ? deptName : deptName.substring(0, open).trim();
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
