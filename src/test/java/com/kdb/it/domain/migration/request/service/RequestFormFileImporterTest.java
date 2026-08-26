package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.ProjectAmounts;
import com.kdb.it.domain.migration.service.MigrationApprovalStamper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestFormFileImporterTest {

    @Mock private CostService costService;
    @Mock private ProjectService projectService;
    @Mock private MigrationApprovalStamper stamper;
    @Mock private RequestFormValidator validator;

    private static final RequestFormDto.FileEntry ENTRY =
            new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571");

    @BeforeEach
    void keepAllProjectsUnlessTheTestMarksDuplicates() {
        when(validator.withoutDuplicateProjects(any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RequestFormFileImporter importer() {
        return new RequestFormFileImporter(costService, projectService, stamper, validator);
    }

    private static FormAdapterOutput outputWithOneOfEach() {
        CostDto.CreateRequest cost = new CostDto.CreateRequest();
        cost.setCttNm("블룸버그 회선사용료");
        cost.setCostTotXpAmt(new BigDecimal("1000"));
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm("국채전문유통시장 접속인프라 도입");
        project.setItems(List.of());
        return new FormAdapterOutput(List.of(project), List.of(cost), List.of(), null);
    }

    private static FormAdapterOutput outputWithOneProject() {
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm("국채전문유통시장 접속인프라 도입");
        project.setItems(List.of());
        return new FormAdapterOutput(List.of(project), List.of(), List.of(), null);
    }

    @Test
    @DisplayName("기간 검증을 생략하는 오버로드로 원장을 만든다")
    void createsLedgerWithPeriodValidationSkipped() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        RequestFormDto.FileResult result =
                importer().apply(outputWithOneOfEach(), ENTRY, "2026", "12345678");

        verify(projectService).createProject(any(), eq(true));
        verify(costService).createCost(any(), eq(true));
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.created())
                .extracting(RequestFormDto.CreatedRecord::key)
                .containsExactlyInAnyOrder("PRJ-2026-0001", "COST-2026-0001");
    }

    @Test
    @DisplayName("생성한 원장마다 이관용 결재완료 받이를 만든다")
    void stampsApprovalForEachLedger() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        importer().apply(outputWithOneOfEach(), ENTRY, "2026", "12345678");

        verify(stamper)
                .stamp(
                        eq("BPROJM"),
                        eq("PRJ-2026-0001"),
                        anyInt(),
                        anyString(),
                        eq("12345678"),
                        eq("2026"));
        verify(stamper)
                .stamp(
                        eq("BCOSTM"),
                        eq("COST-2026-0001"),
                        anyInt(),
                        anyString(),
                        eq("12345678"),
                        eq("2026"));
    }

    @Test
    @DisplayName("생성된 원장에 반입 받이 신청서번호가 실린다")
    void apply_carriesApprovalNumber() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        when(stamper.stamp(any(), any(), any(), any(), any(), any()))
                .thenReturn("APF-2026-00000007");

        RequestFormDto.FileResult result =
                importer().apply(outputWithOneProject(), ENTRY, "2026", "12345678");

        assertThat(result.created())
                .singleElement()
                .extracting(RequestFormDto.CreatedRecord::apfMngNo)
                .isEqualTo("APF-2026-00000007");
    }

    @Test
    @DisplayName("BLOCKER가 남으면 아무것도 쓰지 않고 차단 상태로 돌려준다")
    void writesNothingWhenBlockerRemains() {
        when(validator.validate(any(), anyString()))
                .thenReturn(
                        List.of(
                                RequestFormDto.FormDiagnostic.of(
                                        null,
                                        null,
                                        "ioeC",
                                        RequestFormDiagnosticCode.CODE_UNRESOLVED,
                                        "비목 미해석",
                                        List.of())));

        RequestFormDto.FileResult result =
                importer().apply(outputWithOneOfEach(), ENTRY, "2026", "12345678");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.BLOCKED);
        assertThat(result.created()).isEmpty();
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(costService, never()).createCost(any(), anyBoolean());
        verify(stamper, never()).stamp(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("일괄 반입은 담당자 ID를 비우고 이름 스냅샷만 저장한다")
    void importsPersonNamesWithoutIds() {
        FormAdapterOutput output = outputWithOneOfEach();
        ProjectDto.CreateRequest project = output.projects().getFirst();
        project.setTlrUsid("Luke Buckingham-Brown");
        project.setUsid("홍길동");
        project.setDvmTlrUsid("IT팀장 이름");
        project.setDvmUsid("IT담당자 이름");
        output.costs().getFirst().setCgprId("김담당");
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-1");
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-1");

        RequestFormDto.FileResult result = importer().apply(output, ENTRY, "2026", "12345678");

        ArgumentCaptor<ProjectDto.CreateRequest> projectCaptor =
                ArgumentCaptor.forClass(ProjectDto.CreateRequest.class);
        verify(projectService).createProject(projectCaptor.capture(), eq(true));
        assertThat(projectCaptor.getValue().getTlrUsid()).isNull();
        assertThat(projectCaptor.getValue().getUsid()).isNull();
        assertThat(projectCaptor.getValue().getDvmTlrUsid()).isNull();
        assertThat(projectCaptor.getValue().getDvmUsid()).isNull();
        verify(projectService)
                .assignImportedPersonNames("PRJ-1", "Luke Buckingham-Brown", "홍길동");
        verify(costService).assignImportedPersonName("COST-1", "김담당");
        assertThat(result.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.SUBSTITUTE_DROPPED)
                .extracting(RequestFormDto.FormDiagnostic::field)
                .containsExactlyInAnyOrder("dvmTlrUsid", "dvmUsid");
    }

    @Test
    @DisplayName("정보화사업 BLOCKER가 있어도 진단을 유지하고 일반관리비는 반입한다")
    void importsCostsWhenOnlyCapitalSectionIsBlocked() {
        RequestFormDto.FormDiagnostic blocker =
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.CAPITAL_RESOURCE,
                        15,
                        "ioeC",
                        RequestFormDiagnosticCode.CODE_AMBIGUOUS,
                        "정보화사업 품목 비목을 확정하지 못했습니다.",
                        List.of());
        when(validator.validate(any(), anyString())).thenReturn(List.of(blocker));
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        RequestFormDto.FileResult result =
                importer().apply(outputWithOneOfEach(), ENTRY, "2026", "12345678");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.diagnostics()).contains(blocker);
        assertThat(result.created())
                .extracting(RequestFormDto.CreatedRecord::key)
                .containsExactly("COST-2026-0001");
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(costService).createCost(any(), eq(true));
    }

    @Test
    @DisplayName("경상사업 BLOCKER가 있어도 진단을 유지하고 일반관리비는 반입한다")
    void importsCostsWhenOnlyRecurringSectionIsBlocked() {
        FormAdapterOutput output = outputWithOneOfEach();
        output.projects().getFirst().setOdnYn("Y");
        RequestFormDto.FormDiagnostic blocker =
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.RECURRING,
                        null,
                        "abusNm",
                        RequestFormDiagnosticCode.REQUIRED_MISSING,
                        "경상사업명이 비어 있습니다.",
                        List.of());
        when(validator.validate(any(), anyString())).thenReturn(List.of(blocker));
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        RequestFormDto.FileResult result =
                importer().apply(output, ENTRY, "2026", "12345678");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.diagnostics()).contains(blocker);
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(costService).createCost(any(), eq(true));
    }

    @Test
    @DisplayName("부분 반입 가능한 파일은 사전검증에서도 APPLIED이고 BLOCKER 진단은 남긴다")
    void previewMarksPartiallyApplicableFileReadyAndKeepsBlocker() {
        RequestFormDto.FormDiagnostic blocker =
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.RECURRING,
                        null,
                        "abusNm",
                        RequestFormDiagnosticCode.REQUIRED_MISSING,
                        "경상사업명이 비어 있습니다.",
                        List.of());
        when(validator.validate(any(), anyString())).thenReturn(List.of(blocker));

        RequestFormDto.FileResult result = importer().preview(outputWithOneOfEach(), ENTRY, "2026");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.diagnostics()).contains(blocker);
    }

    @Test
    @DisplayName("중복 사업 경고가 있어도 사업만 건너뛰고 같은 파일의 일반관리비는 반입한다")
    void skipsDuplicateProjectButImportsCost() {
        FormAdapterOutput original = outputWithOneOfEach();
        FormAdapterOutput withoutDuplicateProject =
                new FormAdapterOutput(
                        List.of(), original.costs(), original.diagnostics(), original.suggestedGeneralExpenseUnit());
        when(validator.validate(any(), anyString()))
                .thenReturn(
                        List.of(
                                RequestFormDto.FormDiagnostic.of(
                                        null,
                                        null,
                                        "abusNm",
                                        RequestFormDiagnosticCode.DUPLICATE_EXISTS,
                                        "이미 반입된 사업입니다. 덮어쓰지 않고 건너뜁니다.",
                                        List.of())));
        when(validator.withoutDuplicateProjects(eq(original), eq("2026")))
                .thenReturn(withoutDuplicateProject);
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        RequestFormDto.FileResult result = importer().apply(original, ENTRY, "2026", "12345678");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.counts().capitalProjects()).isEqualTo(1);
        assertThat(result.counts().costs()).isEqualTo(1);
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(costService).createCost(any(), eq(true));
    }

    @Test
    @DisplayName("어댑터 진단과 검증기 진단을 합쳐 돌려준다")
    void mergesAdapterAndValidatorDiagnostics() {
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(),
                        List.of(),
                        List.of(
                                RequestFormDto.FormDiagnostic.of(
                                        null,
                                        null,
                                        "bzDttNm",
                                        RequestFormDiagnosticCode.OPTIONAL_MISSING,
                                        "공란",
                                        List.of())),
                        AmountUnit.WON);
        when(validator.validate(any(), anyString()))
                .thenReturn(
                        List.of(
                                RequestFormDto.FormDiagnostic.of(
                                        null,
                                        null,
                                        "abusNm",
                                        RequestFormDiagnosticCode.REQUIRED_MISSING,
                                        "필수",
                                        List.of())));

        RequestFormDto.FileResult result = importer().preview(output, ENTRY, "2026");

        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .containsExactlyInAnyOrder(
                        RequestFormDiagnosticCode.OPTIONAL_MISSING,
                        RequestFormDiagnosticCode.REQUIRED_MISSING);
        assertThat(result.suggestedGeneralExpenseUnit()).isEqualTo(AmountUnit.WON);
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.BLOCKED);
    }

    @Test
    @DisplayName("사전검증은 원장을 만들지 않는다")
    void previewWritesNothing() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());

        RequestFormDto.FileResult result = importer().preview(outputWithOneOfEach(), ENTRY, "2026");

        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(result.created()).isEmpty();
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(costService, never()).createCost(any(), anyBoolean());
    }

    @Test
    @DisplayName("예산연도는 화면 지정값으로 덮어써 반영한다")
    void overwritesBudgetYearFromManifest() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2099-0001");
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2099-0001");
        FormAdapterOutput output = outputWithOneOfEach();

        importer().apply(output, ENTRY, "2099", "12345678");

        assertThat(output.projects().get(0).getBseYy()).isEqualTo("2099");
        assertThat(output.costs().get(0).getBseYy()).isEqualTo("2099");
    }

    @Test
    @DisplayName("선언 금액이 있으면 생성 후 전체기간·예정·지급 금액을 master에 기록한다")
    void assignsDeclaredAmountsAfterCreatingProject() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm("국채전문유통시장 접속인프라 도입");
        project.setItems(List.of());
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(project),
                        List.of(),
                        List.of(),
                        null,
                        List.of(
                                new ProjectAmounts(
                                        new BigDecimal("2000000000"),
                                        BigDecimal.ZERO,
                                        new BigDecimal("734375300"))));

        importer().apply(output, ENTRY, "2026", "12345678");

        ArgumentCaptor<ProjectDto.CreateRequest> projectCaptor =
                ArgumentCaptor.forClass(ProjectDto.CreateRequest.class);
        verify(projectService).createProject(projectCaptor.capture(), eq(true));
        assertThat(projectCaptor.getValue().getDfrAmt()).isEqualByComparingTo("734375300");
        verify(projectService)
                .assignDeclaredAmounts(
                        "PRJ-2026-0001",
                        new BigDecimal("2000000000"),
                        BigDecimal.ZERO,
                        new BigDecimal("734375300"));
    }

    @Test
    @DisplayName("선언 master는 품목 생성 뒤에도 전체기간·예정·지급 금액을 유지한다")
    void keepsDeclaredMasterAmountsAfterItemCreation() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setAmt(new BigDecimal("100"));
        item.setMplAmt(new BigDecimal("300"));
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm("합성 품목 사업");
        project.setItems(List.of(item));
        ProjectAmounts amounts =
                new ProjectAmounts(
                        new BigDecimal("999"), new BigDecimal("888"), new BigDecimal("20"));
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(project), List.of(), List.of(), null, List.of(amounts));

        importer().apply(output, ENTRY, "2026", "12345678");

        ArgumentCaptor<ProjectDto.CreateRequest> projectCaptor =
                ArgumentCaptor.forClass(ProjectDto.CreateRequest.class);
        verify(projectService).createProject(projectCaptor.capture(), eq(true));
        ProjectDto.BitemmDto importedItem = projectCaptor.getValue().getItems().getFirst();
        assertThat(importedItem.getAmt()).isEqualByComparingTo("100");
        assertThat(importedItem.getMplAmt()).isEqualByComparingTo("300");
        assertThat(projectCaptor.getValue().getDfrAmt()).isEqualByComparingTo("20");
        assertThat(
                        importedItem
                                .getAmt()
                                .add(importedItem.getMplAmt())
                                .add(projectCaptor.getValue().getDfrAmt()))
                .isEqualByComparingTo("420")
                .isNotEqualByComparingTo(amounts.totRqmAmt());
        verify(projectService)
                .assignDeclaredAmounts(
                        "PRJ-2026-0001",
                        new BigDecimal("999"),
                        new BigDecimal("888"),
                        new BigDecimal("20"));
    }

    @Test
    @DisplayName("선언 금액이 없으면 덮어쓰지 않고 품목 합계 스냅샷을 남긴다")
    void keepsItemSnapshotWhenNoDeclaredAmounts() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0001");
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        importer().apply(outputWithOneOfEach(), ENTRY, "2026", "12345678");

        verify(projectService, never()).assignDeclaredAmounts(any(), any(), any(), any());
    }

    @Test
    @DisplayName("사전검증은 선언 금액을 기록하지 않는다")
    void previewNeverAssignsDeclaredAmounts() {
        when(validator.validate(any(), anyString())).thenReturn(List.of());

        importer().preview(outputWithOneOfEach(), ENTRY, "2026");

        verify(projectService, never()).assignDeclaredAmounts(any(), any(), any(), any());
    }
}
