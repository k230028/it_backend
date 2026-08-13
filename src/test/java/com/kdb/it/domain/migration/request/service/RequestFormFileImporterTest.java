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
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.service.MigrationApprovalStamper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, 1L, "571");

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
                        1L);
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
        assertThat(result.suggestedGeneralExpenseMultiplier()).isEqualTo(1L);
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
}
