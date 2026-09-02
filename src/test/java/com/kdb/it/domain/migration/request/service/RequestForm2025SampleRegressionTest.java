package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
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
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestForm2025SampleRegressionTest {

    private static final Path SAMPLE_ROOT = Path.of("..", "sample", "2025");

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    @Test
    @DisplayName("IT인프라팀의 번호 매긴 계약 금액을 같은 번호 항목에 배정한다")
    void keepsEnumeratedGeneralExpenseAmount() throws IOException {
        FormAdapterOutput output = adapt("2025년 전산예산 편성 요청서_IT인프라팀.xls", "180");

        CostDto.CreateRequest contract =
                output.costs().stream()
                        .filter(cost -> cost.getCttNm().contains("MS사 SW사용권 신규 계약"))
                        .findFirst()
                        .orElseThrow();

        assertThat(contract.getCostTotXpAmt())
                .as("cost=%s diagnostics=%s", contract, validatedDiagnostics(output))
                .isNotNull()
                .isPositive();
        assertThat(validatedDiagnostics(output))
                .filteredOn(diagnostic -> contract.getCttNm().equals(diagnostic.subject()))
                .extracting(RequestFormDto.FormDiagnostic::message)
                .doesNotContain("금액이 비어 있습니다.");
    }

    @Test
    @DisplayName("비목 해석 실패는 선택 가능한 진단 하나만 남긴다")
    void reportsOneIoeDiagnosticForLabradorContract() throws IOException {
        FormAdapterOutput output = adapt("2025년 전산예산 편성 요청서(품질관리팀).xls", "180");

        assertThat(validatedDiagnostics(output))
                .filteredOn(
                        diagnostic ->
                                "ioeC".equals(diagnostic.field())
                                        && diagnostic.subject() != null
                                        && diagnostic.subject().contains("Labrador SCM"))
                .hasSize(1)
                .allSatisfy(
                        diagnostic -> {
                            assertThat(diagnostic.excelRow()).isEqualTo(14);
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.CODE_AMBIGUOUS);
                            assertThat(diagnostic.candidates()).isNotEmpty();
                        });
    }

    @Test
    @DisplayName("계약명이 비면 비목명을 계약명으로 사용한다")
    void defaultsMissingContractNameFromExpenseName() throws IOException {
        FormAdapterOutput output = adapt("2025년 전산예산 편성 요청서(하노이지점).xls", "952");

        assertThat(output.costs())
                .extracting(CostDto.CreateRequest::getCttNm)
                .doesNotContainNull()
                .doesNotContain("")
                .contains("국외전산용역비", "전산소모품비");
        assertThat(validatedDiagnostics(output))
                .filteredOn(diagnostic -> "cttNm".equals(diagnostic.field()))
                .isEmpty();
    }

    @Test
    @DisplayName("상세 품목이 없는 2025 정보화사업은 1-1 예산 소요 상세로 품목을 만든다")
    void synthesizesCapitalItemsFromOverviewSummary() throws IOException {
        assertSingleItem(
                adapt("2025년 전산예산 편성 요청서(채널관리팀)_일반관리비.xls", "180"),
                "전자팩스 팩스보드 구매",
                "101",
                "85800000",
                "0");
        assertSingleItem(
                adapt("2025년 전산예산 편성 요청서_자금부.xls", "300"),
                "한은금융망 ISO 20022 도입",
                "103",
                "891922869",
                "760384304");
        assertSingleItem(
                adapt("2025년 전산예산 편성 요청서_종기부.xls", "120"),
                "Paper-Less 업무환경 구축사업(컨설팅)",
                "103",
                "1650000000",
                "0");

        ProjectDto.CreateRequest implementation =
                project(
                        adapt("2025년 전산예산 편성 요청서_종기부.xls", "120"),
                        "Paper-Less 업무환경 구축사업(도입 및 개발사업)");
        assertThat(implementation.getItems())
                .extracting(
                        ProjectDto.BitemmDto::getIoeC,
                        ProjectDto.BitemmDto::getAmt,
                        ProjectDto.BitemmDto::getMplAmt)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "103", new BigDecimal("440000000"), new BigDecimal("3955000000")),
                        org.assertj.core.groups.Tuple.tuple(
                                "101", BigDecimal.ZERO, new BigDecimal("1155000000")),
                        org.assertj.core.groups.Tuple.tuple(
                                "106", BigDecimal.ZERO, new BigDecimal("584000000")));
    }

    private void assertSingleItem(
            FormAdapterOutput output,
            String projectName,
            String ioeCode,
            String currentAmount,
            String plannedAmount) {
        ProjectDto.CreateRequest project = project(output, projectName);
        assertThat(project.getItems())
                .as(
                        "items=%s diagnostics=%s",
                        project.getItems().stream()
                                .map(
                                        item ->
                                                "%s|%s|%s|%s"
                                                        .formatted(
                                                                item.getGclNm(),
                                                                item.getIoeC(),
                                                                item.getAmt(),
                                                                item.getMplAmt()))
                                .toList(),
                        validatedDiagnostics(output))
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.getGclNm()).isEqualTo(project.getAbusNm());
                            assertThat(item.getIoeC()).isEqualTo(ioeCode);
                            assertThat(item.getAmt()).isEqualByComparingTo(currentAmount);
                            assertThat(item.getMplAmt()).isEqualByComparingTo(plannedAmount);
                        });
    }

    private static ProjectDto.CreateRequest project(FormAdapterOutput output, String namePart) {
        return output.projects().stream()
                .filter(project -> project.getAbusNm().contains(namePart))
                .findFirst()
                .orElseThrow();
    }

    private List<RequestFormDto.FormDiagnostic> validatedDiagnostics(FormAdapterOutput output) {
        CostRepository costRepository = mock(CostRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(costRepository.findByBseYyAndLstYnAndDelYn(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(projectRepository.findByBseYyAndLstYnAndDelYn(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        List<RequestFormDto.FormDiagnostic> diagnostics =
                new java.util.ArrayList<>(output.diagnostics());
        diagnostics.addAll(
                new RequestFormValidator(costRepository, projectRepository)
                        .validate(output, "2025"));
        return List.copyOf(diagnostics);
    }

    private FormAdapterOutput adapt(String filename, String departmentCode) throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SAMPLE_ROOT), "로컬 2025 샘플이 없어 건너뜁니다");
        Path sample;
        try (var paths = Files.walk(SAMPLE_ROOT)) {
            sample =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().equals(filename))
                            .findFirst()
                            .orElseThrow();
        }

        SheetAnchorScanner scanner = new SheetAnchorScanner();
        FormLabelReader labelReader = new FormLabelReader(scanner);
        ResourceTableReader resourceReader = new ResourceTableReader(scanner);
        MigrationIoeCatalogReader catalogReader = catalogReader();
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
                                scanner,
                                catalogReader,
                                new FormApproverReader(scanner),
                                resourceReader));
        OrgIdentityResolver.Index orgIndex = mock(OrgIdentityResolver.Index.class);
        when(orgIndex.parentOrgNameOf(anyString())).thenReturn("IT기획부");

        try (Workbook workbook = reader.open(Files.readAllBytes(sample), filename)) {
            FormAdapterOutput output = FormAdapterOutput.empty();
            for (Map<FormSheetKind, org.apache.poi.ss.usermodel.Sheet> sheets :
                    reader.classifyGroups(workbook)) {
                FormAdapterContext context =
                        new FormAdapterContext(
                                sheets,
                                "2025",
                                new RequestFormDto.FileEntry(filename, "sample", null, null, "571"),
                                departmentCode,
                                "sample",
                                orgIndex,
                                TestIoeIndex.snapshot(),
                                Map.of(),
                                "00000000");
                for (FormSheetAdapter adapter : adapters) {
                    if (sheets.containsKey(adapter.trigger()))
                        output = output.merge(adapter.adapt(context));
                }
            }
            return output;
        }
    }

    private static MigrationIoeCatalogReader catalogReader() {
        MigrationIoeCatalogReader reader = mock(MigrationIoeCatalogReader.class);
        List<MigrationDto.Candidate> currencies =
                List.of(
                        new MigrationDto.Candidate("KRW", "원화"),
                        new MigrationDto.Candidate("USD", "미국 달러"),
                        new MigrationDto.Candidate("JPY", "일본 엔"),
                        new MigrationDto.Candidate("VND", "베트남 동"));
        when(reader.currencyCandidates()).thenReturn(currencies);
        when(reader.candidates(anyString(), any(Boolean.class))).thenReturn(List.of());
        when(reader.edrtCapitalCandidates()).thenReturn(List.of());
        when(reader.exePttCodeByName()).thenReturn(Map.of());
        when(reader.edrtCapitalCodeByName()).thenReturn(Map.of());
        when(reader.reportStatusCodeByName()).thenReturn(Map.of());
        return reader;
    }
}
