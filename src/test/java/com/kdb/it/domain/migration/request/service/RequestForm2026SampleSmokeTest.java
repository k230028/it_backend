package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.CapitalOverviewReader;
import com.kdb.it.domain.migration.request.service.adapter.CapitalProjectFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterContext;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class RequestForm2026SampleSmokeTest {

    private static final String SAMPLE_DIR_ENV = "REQUEST_FORM_SAMPLE_2026_DIR";

    private static final String SINGLE_RECURRING_SAMPLE_SUFFIX = "자원증설.xls";
    private static final String THOUSAND_UNIT_SAMPLE_SUFFIX = "전산설비 유지보수.xls";
    private static final String OUTSOURCING_SAMPLE_SUFFIX = "편성 요청서_IT계약팀.xls";
    private static final String AGED_INFRA_SAMPLE_SUFFIX =
            "[자료1] 2026년 전산예산 편성 요청서(노후인프라(HW,SW) 중장기 실행방안 수립).xls";

    private static final String VDI_SAMPLE_SUFFIX = "요청서_스마트워크 인프라(VDI) 고도화 사업.xlsx";

    private static final String NAC_SAMPLE_SUFFIX = "편성 요청서(NAC 고도화).xls";

    private static final String QUALITY_AUTOMATION_SAMPLE_SUFFIX =
            "정보화사업 요청서(신규사업 양식)_테스트 자동화 솔루션 도입_v1.1.xlsx";

    private static final String FUNDING_DESK_SAMPLE_SUFFIX = "편성 요청서(자금운용실).xls";

    private static final String RISK_MANAGEMENT_SAMPLE_SUFFIX = "편성 요청서_리스크관리부.xls";

    private static final String PROCESS_AUTOMATION_SAMPLE_SUFFIX =
            "2026년 전산예산 편성 요청서_프로세스자동화팀.xls";

    private static final String AI_PLATFORM_SAMPLE_SUFFIX =
            "2026년 전산예산 편성 요청서_AI플랫폼팀.xls";

    private static final String SAMPLE_LOOKUP_FAILURE = "로컬 샘플 탐색에 실패했습니다";

    private static final String SAMPLE_READ_FAILURE = "로컬 샘플을 읽지 못했습니다";

    private static final String SAMPLE_OPEN_FAILURE = "로컬 샘플 워크북을 열지 못했습니다";

    private static final String SAMPLE_METADATA_FAILURE = "브라우저 샘플 메타데이터가 일치하지 않습니다";

    private static final Pattern DEPARTMENT_FOLDER =
            Pattern.compile(".*[(（]\\s*([0-9A-Za-z]{1,100})\\s*[)）]$");

    private static final Pattern NUMBERED_FOLDER = Pattern.compile("^\\s*\\d{1,3}\\..*");

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    @Test
    @DisplayName("샘플 탐색 실패는 실제 후보 경로를 출력하지 않는다")
    void sampleLookupFailureDoesNotExposeCandidatePaths(@TempDir Path tempDir) throws IOException {
        Path first = Files.createDirectories(tempDir.resolve("private-a"));
        Path second = Files.createDirectories(tempDir.resolve("private-b"));
        Files.createFile(first.resolve("first-" + SINGLE_RECURRING_SAMPLE_SUFFIX));
        Files.createFile(second.resolve("second-" + SINGLE_RECURRING_SAMPLE_SUFFIX));

        Throwable failure = catchThrowable(() -> findSingleRecurringSample(tempDir));

        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(failure.getMessage()).isEqualTo(SAMPLE_LOOKUP_FAILURE);
        assertThat(failure.getCause()).isNull();
    }

    @Test
    @DisplayName("샘플 읽기 실패는 실제 파일 경로를 출력하지 않는다")
    void sampleReadFailureDoesNotExposeFilePath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("private-folder").resolve("private-file.xls");

        Throwable failure = catchThrowable(() -> readSampleBytes(missing));

        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(failure.getMessage()).isEqualTo(SAMPLE_READ_FAILURE);
        assertThat(failure.getCause()).isNull();
    }

    @Test
    @DisplayName("워크북 열기 실패는 실제 파일 경로를 출력하지 않는다")
    void sampleOpenFailureDoesNotExposeFilePath(@TempDir Path tempDir) throws IOException {
        Path malformed = Files.write(tempDir.resolve("private-file.xls"), new byte[] {1, 2, 3});

        Throwable failure = catchThrowable(() -> classifySample(tempDir, malformed));

        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(failure.getMessage()).isEqualTo(SAMPLE_OPEN_FAILURE);
        assertThat(failure.getCause()).isNull();
    }

    @Test
    @DisplayName("메타데이터 비교 실패는 실제 값과 기대 값을 출력하지 않는다")
    void metadataMismatchDoesNotExposeComparedValues() {
        String actual = "private-root/private-dept/private-file.xls";
        String expected = "other-root/other-dept/other-file.xls";

        Throwable failure =
                catchThrowable(
                        () -> assertSanitizedMatch(actual, expected, SAMPLE_METADATA_FAILURE));

        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(failure.getMessage()).isEqualTo(SAMPLE_METADATA_FAILURE);
        assertThat(failure.getCause()).isNull();
    }

    @Test
    @DisplayName("2026 샘플 Excel을 모두 열고 요청서와 증빙을 기존 개수로 분류한다")
    void opensAndClassifiesSample2026() throws IOException {
        Path sampleRoot = sampleRoot();
        Assumptions.assumeTrue(Files.isDirectory(sampleRoot), "로컬 2026 샘플이 없어 건너뜁니다");

        List<Path> excelFiles = findExcelSamples(sampleRoot);

        int requestForms = 0;
        for (Path path : excelFiles) {
            if (classifySample(sampleRoot, path)) requestForms++;
        }

        assertThat(excelFiles.size()).isEqualTo(67);
        assertThat(requestForms).isEqualTo(55);
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
        assertSanitizedMatch(
                RequestFormArchiveGroup.keyOf(fileKey), archiveGroupKey, SAMPLE_METADATA_FAILURE);
        assertThat(archiveGroupKey.isBlank()).as(SAMPLE_METADATA_FAILURE).isFalse();

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
                                        "sample-요청서.xls",
                                        "application/vnd.ms-excel",
                                        readSampleBytes(sample))),
                        new RequestFormDto.ImportManifest("2026", List.of(entry), List.of()),
                        "00000000",
                        true);

        RequestFormDto.FileResult result = response.files().get(0);
        assertSanitizedMatch(result.fileKey(), fileKey, SAMPLE_METADATA_FAILURE);
        assertSanitizedMatch(result.deptName(), deptName, SAMPLE_METADATA_FAILURE);
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(
                        result.diagnostics().stream()
                                .noneMatch(
                                        diagnostic ->
                                                diagnostic.code()
                                                        == RequestFormDiagnosticCode
                                                                .FILE_UNREADABLE))
                .as("FILE_UNREADABLE 진단이 없어야 합니다")
                .isTrue();
        assertThat(result.counts().recurringProjects()).isEqualTo(1);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
        assertThat(response.summary().blockedFiles()).isZero();
        ArgumentCaptor<String> deptCaptor = ArgumentCaptor.forClass(String.class);
        verify(orgIndex).resolveOrgFolder(deptCaptor.capture());
        assertSanitizedMatch(deptCaptor.getValue(), deptName, SAMPLE_METADATA_FAILURE);
        ArgumentCaptor<FormAdapterOutput> outputCaptor =
                ArgumentCaptor.forClass(FormAdapterOutput.class);
        verify(fileImporter).preview(outputCaptor.capture(), any(), anyString());
        assertThat(outputCaptor.getValue().projects().size()).isEqualTo(1);
        List<ProjectDto.BitemmDto> items = outputCaptor.getValue().projects().get(0).getItems();
        assertThat(items.size()).isEqualTo(2);
        assertThat(
                        items.stream()
                                .allMatch(
                                        item ->
                                                item.getGclNm() != null
                                                        && !item.getGclNm().isBlank()
                                                        && item.getQty() != null
                                                        && item.getQty().signum() > 0))
                .as("품목 이름과 수량이 모두 유효해야 합니다")
                .isTrue();
        assertThat(items)
                .as("국내 경상사업의 빈 통화는 KRW 천원으로 해석해야 합니다")
                .allSatisfy(
                        item -> {
                            assertThat(item.getCurC()).isEqualTo("KRW");
                            assertThat(item.getAmt()).isPositive();
                            assertThat(item.getAmt().remainder(BigDecimal.valueOf(1_000L)))
                                    .isZero();
                            assertThat(item.getFcAmt()).isNull();
                        });
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.REQUIRED_MISSING);
    }

    @Test
    @DisplayName("전산설비 유지보수 샘플의 명시된 천원 단위를 그대로 적용한다")
    void appliesDeclaredThousandUnitFromMaintenanceSample() throws IOException {
        FormAdapterOutput output = adaptGeneralExpenseSample(THOUSAND_UNIT_SAMPLE_SUFFIX);

        assertThat(output.suggestedGeneralExpenseUnit()).isEqualTo(AmountUnit.THOUSAND);
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.UNIT_UNCERTAIN);
    }

    @Test
    @DisplayName("프로세스자동화팀 요청서에서 정보화사업 2건과 일반관리비 2건을 읽는다")
    void adaptsProcessAutomationSample() throws IOException {
        Path sample = findUniqueSample(sampleRoot(), PROCESS_AUTOMATION_SAMPLE_SUFFIX);
        SheetAnchorScanner scanner = new SheetAnchorScanner();
        FormLabelReader labelReader = new FormLabelReader(scanner);
        ResourceTableReader resourceReader = new ResourceTableReader(scanner);
        MigrationIoeCatalogReader catalogReader = mock(MigrationIoeCatalogReader.class);
        when(catalogReader.candidates(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(
                        List.of(
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "KRW", "원화"),
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "USD", "달러")));
        when(catalogReader.currencyCandidates())
                .thenReturn(
                        List.of(
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "KRW", "원화"),
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "USD", "달러")));
        when(catalogReader.edrtCapitalCandidates()).thenReturn(List.of());
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName())
                .thenReturn(Map.of("지역본부장", "23", "부점장", "24"));
        when(catalogReader.reportStatusCodeByName()).thenReturn(Map.of());
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
        OrgIdentityResolver.Index orgIndex = mock(OrgIdentityResolver.Index.class);
        when(orgIndex.parentOrgNameOf(anyString())).thenReturn("디지털전략부");

        try (Workbook workbook = reader.open(readSampleBytes(sample), "sample.xls")) {
            FormAdapterOutput output = FormAdapterOutput.empty();
            for (Map<FormSheetKind, Sheet> sheets : reader.classifyGroups(workbook)) {
                FormAdapterContext context =
                        new FormAdapterContext(
                                sheets,
                                "2026",
                                new RequestFormDto.FileEntry(
                                        "sample.xls",
                                        "디지털전략부(185)",
                                        null,
                                        AmountUnit.WON,
                                        "571"),
                                "185",
                                "디지털전략부",
                                orgIndex,
                                TestIoeIndex.snapshot(),
                                Map.of(),
                                "00000000");
                for (FormSheetAdapter adapter : adapters) {
                    if (sheets.containsKey(adapter.trigger())) {
                        output = output.merge(adapter.adapt(context));
                    }
                }
            }

            assertThat(output.projects()).hasSize(2);
            assertThat(output.costs()).hasSize(2);
            assertThat(output.diagnostics())
                    .filteredOn(
                            diagnostic ->
                                    diagnostic.code().severity()
                                            == com.kdb.it.domain.migration.dto.MigrationDto.Severity.BLOCKER)
                    .extracting(
                            RequestFormDto.FormDiagnostic::code,
                            RequestFormDto.FormDiagnostic::field,
                            RequestFormDto.FormDiagnostic::sheet,
                            RequestFormDto.FormDiagnostic::excelRow)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("IT계약팀 샘플의 외주용역을 외주운영·관제 코드로 확정한다")
    void resolvesOutsourcingFromContractTeamSample() throws IOException {
        FormAdapterOutput output = adaptGeneralExpenseSample(OUTSOURCING_SAMPLE_SUFFIX);

        assertThat(output.costs())
                .filteredOn(cost -> "정보화사업(구매) 원가용역".equals(cost.getCttNm()))
                .singleElement()
                .satisfies(cost -> assertThat(cost.getIoeC()).isEqualTo("008"));
        assertThat(output.diagnostics())
                .filteredOn(diagnostic -> "정보화사업(구매) 원가용역".equals(diagnostic.subject()))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_AMBIGUOUS);
    }

    @Test
    @DisplayName("스마트워크 VDI 요청서를 정보화사업과 소요자원으로 변환한다")
    void adaptsVdiCapitalSample() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(VDI_SAMPLE_SUFFIX);

        assertThat(output.projects())
                .singleElement()
                .satisfies(
                        project ->
                                assertThat(project.getItems())
                                        .as("diagnostics=%s", output.diagnostics())
                                        .isNotEmpty());
    }

    @Test
    @DisplayName("NAC 고도화 요청서를 정보화사업과 소요자원으로 변환한다")
    void adaptsNacCapitalSample() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(NAC_SAMPLE_SUFFIX);

        assertThat(output.projects())
                .singleElement()
                .satisfies(
                        project ->
                                assertThat(project.getItems())
                                        .as("diagnostics=%s", output.diagnostics())
                                        .isNotEmpty());
    }

    @Test
    @DisplayName("테스트 자동화 솔루션 요청서를 정보화사업과 소요자원으로 변환한다")
    void adaptsQualityAutomationCapitalSample() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(QUALITY_AUTOMATION_SAMPLE_SUFFIX);

        assertThat(output.projects())
                .singleElement()
                .satisfies(
                        project ->
                                assertThat(project.getItems())
                                        .as("diagnostics=%s", output.diagnostics())
                                        .hasSize(4));
    }

    @Test
    @DisplayName("자금운용실 자본 블록은 당해 1,014,981,660원만 품목으로 읽는다")
    void adaptsFundingDeskDeclaredAmounts() throws IOException {
        // 1-2 일반관리비가 `'27년 유지보수료`까지 담은 연간 금액이라 품목 합계(1,217,727,960)가
        // `'26년도 합계`(1,211,418,360)보다 0.518% 크다. 품목 합계로는 요약표 배수를 확정하지 못하고
        // `'26년도 필요예산 편성요청`(1,211백만원)과 대사해야 원 단위로 확정된다
        FormAdapterOutput output = adaptCapitalSample(FUNDING_DESK_SAMPLE_SUFFIX);

        assertThat(sumCurrentItems(output))
                .as("items=%s", itemSummary(output))
                .isEqualByComparingTo("1014981660");
        assertThat(sumPlannedItems(output)).isZero();

        assertThat(output.projectAmounts())
                .singleElement()
                .satisfies(
                        amounts -> {
                            assertThat(amounts.isPresent())
                                    .as("diagnostics=%s", output.diagnostics())
                                    .isTrue();
                            // 총 사업금액(전체기간) 2,000백만원
                            assertThat(amounts.totRqmAmt()).isEqualByComparingTo("2000000000");
                            // '26년도 이후 총 계
                            assertThat(amounts.mplAmt()).isEqualByComparingTo("202746300");
                            assertThat(amounts.dfrAmt()).isEqualByComparingTo("782272040");
                        });
        assertThat(output.diagnostics())
                .noneMatch(diagnostic -> "declaredYearTotal".equals(diagnostic.field()));
    }

    @Test
    @DisplayName("리스크관리부 개발비는 당해 2,122백만원이고 예정 804백만원으로 분리한다")
    void adaptsRiskManagementDeclaredAmounts() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(RISK_MANAGEMENT_SAMPLE_SUFFIX);

        assertThat(sumCurrentItems(output))
                .as("items=%s", itemSummary(output))
                .isEqualByComparingTo("2122000000");
        assertThat(sumPlannedItems(output)).isEqualByComparingTo("804000000");
        assertThat(output.projectAmounts())
                .singleElement()
                .satisfies(
                        amounts -> {
                            assertThat(amounts.totRqmAmt()).isEqualByComparingTo("2926000000");
                            assertThat(amounts.mplAmt()).isEqualByComparingTo("804000000");
                            assertThat(amounts.dfrAmt()).isZero();
                        });
    }

    @Test
    @DisplayName("AI플랫폼팀 라이선스 연장 사업의 당해 금액을 0원으로 바꾸지 않는다")
    void keepsAiPlatformCurrentAmount() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(AI_PLATFORM_SAMPLE_SUFFIX, null);

        assertThat(sumCurrentItems(output))
                .as(
                        "items=%s planned=%s diagnostics=%s",
                        itemSummary(output), sumPlannedItems(output), output.diagnostics())
                .isPositive();
    }

    private static BigDecimal sumCurrentItems(FormAdapterOutput output) {
        return output.projects().stream()
                .flatMap(project -> project.getItems().stream())
                .map(ProjectDto.BitemmDto::getAmt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumPlannedItems(FormAdapterOutput output) {
        return output.projects().stream()
                .flatMap(project -> project.getItems().stream())
                .map(ProjectDto.BitemmDto::getMplAmt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<String> itemSummary(FormAdapterOutput output) {
        return output.projects().stream()
                .flatMap(project -> project.getItems().stream())
                .map(
                        item ->
                                "%s|amt=%s|mpl=%s|ym=%s"
                                        .formatted(
                                                item.getGclNm(),
                                                item.getAmt(),
                                                item.getMplAmt(),
                                                item.getBseYm()))
                .toList();
    }

    @Test
    @DisplayName("1-2가 없는 노후인프라 샘플은 1-1 비목별 합계로 BITEMM을 만든다")
    void adaptsAgedInfrastructureSummaryItem() throws IOException {
        FormAdapterOutput output = adaptCapitalSample(AGED_INFRA_SAMPLE_SUFFIX, false);

        assertThat(output.projects())
                .singleElement()
                .satisfies(
                        project -> {
                            assertThat(project.getItems())
                                    .singleElement()
                                    .satisfies(
                                            item -> {
                                                assertThat(item.getIoeC()).isEqualTo("008");
                                                assertThat(item.getGclNm())
                                                        .isEqualTo(project.getAbusNm());
                                                assertThat(item.getQty()).isEqualByComparingTo("1");
                                                assertThat(item.getAmt())
                                                        .isEqualByComparingTo("1155000000");
                                                assertThat(item.getMplAmt())
                                                        .isEqualByComparingTo("0");
                                            });
                        });
        assertThat(output.projectAmounts())
                .singleElement()
                .satisfies(
                        amounts -> {
                            assertThat(amounts.isPresent()).isTrue();
                            assertThat(amounts.totRqmAmt()).isEqualByComparingTo("1155000000");
                            assertThat(amounts.dfrAmt()).isEqualByComparingTo("0");
                        });
    }

    private FormAdapterOutput adaptCapitalSample(String suffix) throws IOException {
        return adaptCapitalSample(suffix, true);
    }

    private FormAdapterOutput adaptCapitalSample(String suffix, Boolean requireResource)
            throws IOException {
        Path root = sampleRoot();
        Path sample = findUniqueSample(root, suffix);
        SheetAnchorScanner scanner = new SheetAnchorScanner();
        MigrationIoeCatalogReader catalogReader = mock(MigrationIoeCatalogReader.class);
        when(catalogReader.candidates(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(List.of());
        when(catalogReader.edrtCapitalCandidates()).thenReturn(List.of());
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of());
        when(catalogReader.reportStatusCodeByName()).thenReturn(Map.of());
        FormLabelReader labelReader = new FormLabelReader(scanner);
        ResourceTableReader resourceReader = new ResourceTableReader(scanner);
        CapitalProjectFormAdapter adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(scanner, labelReader, new FormCheckboxReader()),
                        resourceReader,
                        catalogReader);
        OrgIdentityResolver.Index orgIndex = mock(OrgIdentityResolver.Index.class);
        when(orgIndex.parentOrgNameOf(anyString())).thenReturn("IT기획부");

        try (Workbook workbook = reader.open(readSampleBytes(sample), "sample.xls")) {
            Map<FormSheetKind, Sheet> sheets = reader.classify(workbook);
            Sheet resourceSheet = sheets.get(FormSheetKind.CAPITAL_RESOURCE);
            if (Boolean.TRUE.equals(requireResource)) {
                ResourceTableReader.Result resources =
                        resourceReader.readCapitalResource(resourceSheet, 0, false).orElseThrow();
                assertThat(resources.rows())
                        .as("resource header row=%s", resources.headerRow())
                        .isNotEmpty();
            } else if (Boolean.FALSE.equals(requireResource)) {
                assertThat(resourceSheet).isNull();
            }
            return adapter.adapt(
                    new FormAdapterContext(
                            sheets,
                            "2026",
                            new RequestFormDto.FileEntry(
                                    "sample.xls", "IT기획부(180)", null, null, null),
                            "180",
                            "IT기획부",
                            orgIndex,
                            TestIoeIndex.snapshot(),
                            Map.of(),
                            "00000000"));
        }
    }

    private FormAdapterOutput adaptGeneralExpenseSample(String suffix) throws IOException {
        Path root = sampleRoot();
        Path sample = findUniqueSample(root, suffix);
        SheetAnchorScanner scanner = new SheetAnchorScanner();
        MigrationIoeCatalogReader catalogReader = mock(MigrationIoeCatalogReader.class);
        when(catalogReader.candidates(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(
                        List.of(
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "KRW", "원화"),
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "USD", "달러")));
        when(catalogReader.currencyCandidates())
                .thenReturn(
                        List.of(
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "KRW", "원화"),
                                new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                        "USD", "달러")));
        GeneralExpenseFormAdapter adapter =
                new GeneralExpenseFormAdapter(
                        scanner, catalogReader, new FormApproverReader(scanner));

        try (Workbook workbook = reader.open(readSampleBytes(sample), "sample.xls")) {
            return adapter.adapt(
                    new com.kdb.it.domain.migration.request.service.adapter.FormAdapterContext(
                            reader.classify(workbook),
                            "2026",
                            new RequestFormDto.FileEntry(
                                    "sample.xls", "IT기획부(180)", null, null, "571"),
                            "180",
                            "IT기획부",
                            null,
                            TestIoeIndex.snapshot(),
                            java.util.Map.of(),
                            "00000000"));
        }
    }

    /** 실제 경로는 소스에 남기지 않고 파일명의 최소 suffix로 대상 한 건을 찾습니다. */
    private static Path findSingleRecurringSample(Path root) throws IOException {
        return findUniqueSample(root, SINGLE_RECURRING_SAMPLE_SUFFIX);
    }

    private static Path findUniqueSample(Path root, String suffix) throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(root), "로컬 2026 샘플이 없어 건너뜁니다");
        List<Path> matches;
        try {
            matches =
                    findRegularFiles(root).stream()
                            .filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .toLowerCase(Locale.ROOT)
                                                    .endsWith(suffix.toLowerCase(Locale.ROOT)))
                            .sorted()
                            .toList();
        } catch (RuntimeException failure) {
            throw sanitizedFailure(SAMPLE_LOOKUP_FAILURE);
        }
        Assumptions.assumeFalse(matches.isEmpty(), "로컬 단건 샘플이 없어 건너뜁니다");
        if (matches.size() != 1) throw sanitizedFailure(SAMPLE_LOOKUP_FAILURE);
        return matches.get(0);
    }

    private static List<Path> findExcelSamples(Path root) {
        try {
            return findRegularFiles(root).stream()
                    .filter(RequestForm2026SampleSmokeTest::isExcel)
                    .sorted()
                    .toList();
        } catch (RuntimeException failure) {
            throw sanitizedFailure(SAMPLE_LOOKUP_FAILURE);
        }
    }

    private static List<Path> findRegularFiles(Path root) {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException | RuntimeException failure) {
            throw sanitizedFailure(SAMPLE_LOOKUP_FAILURE);
        }
    }

    private static byte[] readSampleBytes(Path sample) {
        try {
            return Files.readAllBytes(sample);
        } catch (IOException | RuntimeException failure) {
            throw sanitizedFailure(SAMPLE_READ_FAILURE);
        }
    }

    private boolean classifySample(Path root, Path sample) {
        try {
            String fileKey = root.relativize(sample).toString().replace('\\', '/');
            try (Workbook workbook = reader.open(readSampleBytes(sample), fileKey)) {
                return !reader.classify(workbook).isEmpty();
            }
        } catch (AssertionError failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw sanitizedFailure(SAMPLE_OPEN_FAILURE);
        }
    }

    private static void assertSanitizedMatch(String actual, String expected, String message) {
        if (!Objects.equals(actual, expected)) throw sanitizedFailure(message);
    }

    private static AssertionError sanitizedFailure(String message) {
        return new AssertionError(message);
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
