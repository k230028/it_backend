package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetRateApplicationService;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.adapter.AdapterOutput;
import com.kdb.it.domain.migration.service.adapter.PlanIntent;
import com.kdb.it.domain.migration.service.adapter.RateIntent;
import com.kdb.it.domain.migration.service.adapter.SheetAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 반영 순서·전량 롤백·applyItemRates 단일 호출을 고정합니다 (§7). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigrationImportServiceTest {

    @Mock private CostService costService;
    @Mock private CostRepository costRepository;
    @Mock private ProjectService projectService;
    @Mock private ProjectRepository projectRepository;
    @Mock private BudgetRateApplicationService budgetRateApplicationService;
    @Mock private MigrationApprovalStamper approvalStamper;
    @Mock private MigrationValidator validator;
    @Mock private MigrationYearSnapshot yearSnapshot;
    @Mock private OrgIdentityResolver orgIdentityResolver;
    @Mock private MigrationIoeCatalogReader catalogReader;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private PlanService planService;

    /** BLOCKER가 하나라도 있으면 아무 서비스도 호출되지 않는다. */
    @Test
    @DisplayName("BLOCKER가 있으면 원장을 하나도 쓰지 않고 실패한다")
    void 블로커가_있으면_아무것도_쓰지_않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new MigrationDto.CellDiagnostic(
                                        SheetKind.COST,
                                        2,
                                        "deptName",
                                        "ORG_UNRESOLVED",
                                        MigrationDto.Severity.BLOCKER,
                                        "해석 실패",
                                        List.of())));

        assertThatThrownBy(() -> service.commit(commitRequest(), "999999"))
                .isInstanceOf(com.kdb.it.exception.CustomGeneralException.class)
                .hasMessageContaining("반영할 수 없습니다");

        verify(costService, never()).createCost(any(), anyBoolean());
        verify(projectService, never()).createProject(any(), anyBoolean());
        verify(budgetRateApplicationService, never()).applyItemRates(any());
        verify(approvalStamper, never())
                .stamp(anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }

    /** WARNING만 있으면 반영이 진행된다. */
    @Test
    @DisplayName("WARNING만 있으면 반영을 진행한다")
    void 경고만_있으면_반영한다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new MigrationDto.CellDiagnostic(
                                        SheetKind.COST,
                                        2,
                                        "krwAmount",
                                        "AMOUNT_MISMATCH",
                                        MigrationDto.Severity.WARNING,
                                        "금액 불일치",
                                        List.of())));
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        MigrationDto.CommitResponse response = service.commit(commitRequest(), "999999");

        assertThat(response.costCount()).isEqualTo(1);
        verify(costService).createCost(any(), anyBoolean());
    }

    /** 원장 생성은 기간 검증 생략 경로를 쓴다. */
    @Test
    @DisplayName("원장 생성은 기간 검증을 생략하는 오버로드를 호출한다")
    void 기간검증_생략경로를_쓴다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        service.commit(commitRequest(), "999999");

        ArgumentCaptor<Boolean> skip = ArgumentCaptor.forClass(Boolean.class);
        verify(costService).createCost(any(CostDto.CreateRequest.class), skip.capture());
        assertThat(skip.getValue()).isTrue();
    }

    /** applyItemRates는 정확히 한 번만 호출한다. */
    @Test
    @DisplayName("applyItemRates를 정확히 한 번만 호출한다")
    void 편성률적용은_한번만_호출한다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        service.commit(commitRequest(), "999999");

        verify(budgetRateApplicationService, times(1)).applyItemRates(any());
    }

    /** items에는 이관분과 기존 연도 데이터가 모두 담겨야 한다. */
    @Test
    @DisplayName("applyItemRates items에 이관분과 기존 연도 데이터를 함께 담는다")
    void 편성률items에_연도전체를_담는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");
        when(yearSnapshot.load("2026"))
                .thenReturn(
                        new MigrationYearSnapshot.Data(
                                "2026",
                                java.util.Set.of(),
                                new java.util.LinkedHashMap<>(Map.of("기존사업", "PRJ-2026-0099")),
                                java.util.Set.of(),
                                new java.util.LinkedHashMap<>(Map.of("BPROJM|PRJ-2026-0099", 80)),
                                List.of("COST-2026-0099")));

        service.commit(commitRequest(), "999999");

        ArgumentCaptor<BudgetWorkDto.ItemApplyRequest> captor =
                ArgumentCaptor.forClass(BudgetWorkDto.ItemApplyRequest.class);
        verify(budgetRateApplicationService).applyItemRates(captor.capture());

        assertThat(captor.getValue().items())
                .extracting(BudgetWorkDto.ItemRate::orcPkVl)
                .contains("COST-2026-0001", "PRJ-2026-0099", "COST-2026-0099");
        assertThat(captor.getValue().items())
                .filteredOn(i -> "PRJ-2026-0099".equals(i.orcPkVl()))
                .singleElement()
                .satisfies(i -> assertThat(i.assetDupRt()).isEqualTo(80));
    }

    /**
     * 원장 생성 직후 결재 받이를 만든다. 결재 받이의 원천 일련번호는 방금 저장된 행의 실제 BG_SNO여야 한다 — BbugtmRepositoryImpl의 집계 조인이
     * Cappla.fntTbCrySno = Bcostm.bgSno로 맞춰 보므로, 하드코딩한 1을 넘기면 편성률 적용 후 예산 화면 집계가 이 행을 찾지 못해 조용히 0으로
     * 보인다.
     */
    @Test
    @DisplayName("생성한 전산업무비마다 실제 BG_SNO로 결재 받이를 만든다")
    void 원장마다_실제_버전으로_결재받이를_만든다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");
        // 실제 저장된 행의 BG_SNO는 3 — 브리프 초안의 하드코딩(1)과 다른 값으로 고정해 오검출을 막는다.
        when(costRepository.findByCostBgNoAndDelYn("COST-2026-0001", "N"))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2026-0001")
                                        .bgSno(3)
                                        .lstYn("Y")
                                        .build()));

        service.commit(commitRequest(), "999999");

        verify(approvalStamper)
                .stamp(
                        eq("BCOSTM"),
                        eq("COST-2026-0001"),
                        eq(3),
                        anyString(),
                        eq("999999"),
                        eq("2026"));
    }

    /** dry-run은 아무것도 쓰지 않는다. */
    @Test
    @DisplayName("dry-run은 원장을 쓰지 않고 진단만 돌려준다")
    void dryRun은_쓰지_않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new MigrationDto.CellDiagnostic(
                                        SheetKind.COST,
                                        2,
                                        "deptName",
                                        "ORG_UNRESOLVED",
                                        MigrationDto.Severity.BLOCKER,
                                        "해석 실패",
                                        List.of())));

        MigrationDto.DryRunResponse response =
                service.dryRun(new MigrationDto.DryRunRequest(commitRequest().sheets(), List.of()));

        assertThat(response.summary().blockerCount()).isEqualTo(1);
        assertThat(response.summary().totalRows()).isEqualTo(1);
        verify(costService, never()).createCost(any(), anyBoolean());
    }

    /**
     * dry-run이 요청의 보정값을 실제로 검증기에 전달하는지 고정한다.
     *
     * <p>이전 구현은 {@code dryRun}이 항상 빈 맵({@code Map.of()})을 검증기에 넘겨 요청에 담긴 보정값을 통째로 무시했다 — 사용자가 진단
     * 후보를 골라 {@code setOverride}로 다시 dry-run을 돌려도 같은 BLOCKER가 그대로 남아 {@code canCommit}이 영원히 false로
     * 묶이는 회귀였다. 검증기 스텁을 넘어오는 보정값 맵 자체로 분기시켜, 서비스가 {@code MigrationValidator.overrideKey}와 같은 키 형식으로
     * 접어 넘기는지까지 함께 확인한다.
     */
    @Test
    @DisplayName("dry-run 보정값을 검증기에 그대로 전달해 BLOCKER를 해소한다")
    void dryRun은_보정값을_검증기에_전달한다() {
        MigrationImportService service = service();
        MigrationDto.CellDiagnostic blocker =
                new MigrationDto.CellDiagnostic(
                        SheetKind.COST,
                        2,
                        "deptName",
                        "ORG_UNRESOLVED",
                        MigrationDto.Severity.BLOCKER,
                        "해석 실패",
                        List.of());
        when(validator.validate(any(), any(), any(), eq(Map.of()))).thenReturn(List.of(blocker));
        when(validator.validate(any(), any(), any(), eq(Map.of("COST|2|deptName", "0210"))))
                .thenReturn(List.of());

        MigrationDto.DryRunResponse withoutOverride =
                service.dryRun(new MigrationDto.DryRunRequest(commitRequest().sheets(), List.of()));
        MigrationDto.DryRunResponse withOverride =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                commitRequest().sheets(),
                                List.of(
                                        new MigrationDto.CellOverride(
                                                SheetKind.COST, 2, "deptName", "0210"))));

        assertThat(withoutOverride.summary().blockerCount()).isEqualTo(1);
        assertThat(withOverride.summary().blockerCount()).isEqualTo(0);
    }

    /**
     * §7의 5단계 원장 반영 순서(일반관리비 → 자본예산 → 위임예산 → 부문계획)를 어댑터 호출 순서로 고정한다.
     *
     * <p>이 테스트만 4개 시트 종류 전부를 목 어댑터로 등록한다 — 다른 테스트는 전산업무비 경로만 검증하므로 {@link
     * com.kdb.it.domain.migration.service.adapter.CostSheetAdapter} 하나만 쓴다.
     */
    @Test
    @DisplayName("어댑터를 일반관리비→자본예산→위임예산→부문계획 순서로 호출한다")
    void 어댑터_호출_순서를_지킨다() {
        SheetAdapter costAdapter = Mockito.mock(SheetAdapter.class);
        SheetAdapter capitalAdapter = Mockito.mock(SheetAdapter.class);
        SheetAdapter delegatedAdapter = Mockito.mock(SheetAdapter.class);
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(costAdapter.supports()).thenReturn(SheetKind.COST);
        when(capitalAdapter.supports()).thenReturn(SheetKind.CAPITAL_PROJECT);
        when(delegatedAdapter.supports()).thenReturn(SheetKind.DELEGATED_BUDGET);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        when(costAdapter.adapt(any(), any())).thenReturn(AdapterOutput.empty());
        when(capitalAdapter.adapt(any(), any())).thenReturn(AdapterOutput.empty());
        when(delegatedAdapter.adapt(any(), any())).thenReturn(AdapterOutput.empty());
        when(planAdapter.adapt(any(), any())).thenReturn(AdapterOutput.empty());

        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of());
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service =
                new MigrationImportService(
                        // 등록 순서를 실제 반영 순서와 일부러 뒤섞어, 서비스가 등록 순서가 아니라 §7 순서로
                        // 어댑터를 호출한다는 것을 검증한다.
                        List.of(planAdapter, delegatedAdapter, capitalAdapter, costAdapter),
                        validator,
                        yearSnapshot,
                        orgIdentityResolver,
                        catalogReader,
                        approvalStamper,
                        costService,
                        costRepository,
                        projectService,
                        projectRepository,
                        budgetRateApplicationService,
                        projectItemRepository,
                        planService);

        Map<String, String> emptyCells = Map.of();
        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, emptyCells))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.DELEGATED_BUDGET,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, emptyCells))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, emptyCells))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, emptyCells)))),
                        List.of());

        service.commit(request, "999999");

        InOrder order = Mockito.inOrder(costAdapter, capitalAdapter, delegatedAdapter, planAdapter);
        order.verify(costAdapter).adapt(any(), any());
        order.verify(capitalAdapter).adapt(any(), any());
        order.verify(delegatedAdapter).adapt(any(), any());
        order.verify(planAdapter).adapt(any(), any());
    }

    /** requireSupported가 시트 목록이 비면 어댑터에 닿기 전에 거부한다. */
    @Test
    @DisplayName("시트 목록이 비어 있으면 IllegalArgumentException을 던진다")
    void 시트목록이_비면_예외를_던진다() {
        MigrationImportService service = service();

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        new MigrationDto.CommitRequest(List.of(), List.of()),
                                        "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올린 시트가 없습니다");
    }

    /** requireSupported가 등록된 어댑터가 없는 시트 종류를 거부한다. */
    @Test
    @DisplayName("지원하지 않는 시트 종류는 IllegalArgumentException을 던진다")
    void 지원하지_않는_시트종류는_예외를_던진다() {
        // service()는 COST 어댑터만 등록하므로 PLAN_ADJUSTMENT는 미등록 종류다.
        MigrationImportService service = service();
        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of());

        assertThatThrownBy(() -> service.commit(request, "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 시트 종류입니다");
    }

    /** 편성률 의도의 원천이 이번 요청·기존 스냅샷 어디에서도 PK를 찾지 못하면 예외 없이 건너뛴다. */
    @Test
    @DisplayName("편성률 의도의 PK를 찾지 못하면 건너뛰고 나머지는 그대로 적용한다")
    void 편성률_PK_미매칭은_건너뛴다() {
        SheetAdapter costAdapter = Mockito.mock(SheetAdapter.class);
        when(costAdapter.supports()).thenReturn(SheetKind.COST);
        when(costAdapter.adapt(any(), any()))
                .thenReturn(
                        new AdapterOutput(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(new RateIntent("BPROJM", "존재하지않는사업", 90))));

        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of());
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service =
                new MigrationImportService(
                        List.of(costAdapter),
                        validator,
                        yearSnapshot,
                        orgIdentityResolver,
                        catalogReader,
                        approvalStamper,
                        costService,
                        costRepository,
                        projectService,
                        projectRepository,
                        budgetRateApplicationService,
                        projectItemRepository,
                        planService);

        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of());

        MigrationDto.CommitResponse response = service.commit(request, "999999");

        assertThat(response).isNotNull();
        ArgumentCaptor<BudgetWorkDto.ItemApplyRequest> captor =
                ArgumentCaptor.forClass(BudgetWorkDto.ItemApplyRequest.class);
        verify(budgetRateApplicationService).applyItemRates(captor.capture());
        assertThat(captor.getValue().items())
                .extracting(BudgetWorkDto.ItemRate::orcPkVl)
                .doesNotContain("존재하지않는사업");
    }

    /** 부문계획 시트를 올리지 않으면 planReqDocNo는 null이다. */
    @Test
    @DisplayName("부문계획 시트가 없으면 planReqDocNo는 null이다")
    void 부문계획시트가_없으면_planReqDocNo는_null이다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        MigrationDto.CommitResponse response = service.commit(commitRequest(), "999999");

        assertThat(response.planReqDocNo()).isNull();
        verify(planService, never())
                .createPlanForMigration(anyString(), anyString(), any(), any(), any());
    }

    /**
     * 부문계획 조정 대상 사업이 이미 있으면(연도 스냅샷에 존재) 기존 활성 품목을 버전 교체하고 조정 계획을 만든다.
     *
     * <p>devAmount만 채우고 hw·swAmount는 null로 두어 {@code addAdjustedItem}의 null-스킵 분기와 실제-추가 분기를 함께
     * 지나가게 한다.
     */
    @Test
    @DisplayName("부문계획 대상 사업이 있으면 품목을 버전 교체하고 조정 계획을 만든다")
    void 부문계획_대상사업이_있으면_품목을_교체하고_계획을_만든다() {
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        PlanIntent intent =
                new PlanIntent(
                        "문자메시지안심마크도입",
                        new BigDecimal("1000000"),
                        null,
                        null,
                        "202603",
                        Map.of("사업진행", "진행(품의)"));
        when(planAdapter.adapt(any(), any()))
                .thenReturn(new AdapterOutput(List.of(), List.of(), List.of(intent), List.of()));

        when(yearSnapshot.load(anyString()))
                .thenReturn(
                        TestSnapshots.snapshotWithProjectName(
                                "2026", "문자메시지안심마크도입", "PRJ-2026-0005"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of());
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0005", "N", "Y"))
                .thenReturn(List.of(Bitemm.builder().gclMngNo("GCL-2025-0001").sno(1).build()));
        when(planService.createPlanForMigration(eq("2026"), eq("조정"), any(), any(), any()))
                .thenReturn("PLN-2026-0009");
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service =
                new MigrationImportService(
                        List.of(planAdapter),
                        validator,
                        yearSnapshot,
                        orgIdentityResolver,
                        catalogReader,
                        approvalStamper,
                        costService,
                        costRepository,
                        projectService,
                        projectRepository,
                        budgetRateApplicationService,
                        projectItemRepository,
                        planService);

        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of());

        MigrationDto.CommitResponse response = service.commit(request, "999999");

        assertThat(response.planReqDocNo()).isEqualTo("PLN-2026-0009");
        assertThat(response.itemCount()).isEqualTo(1);
        verify(projectService).replaceItemsForMigration(eq("PRJ-2026-0005"), any());
        verify(planService)
                .createPlanForMigration(
                        eq("2026"), eq("조정"), eq(List.of("PRJ-2026-0005")), any(), any());
    }

    /** 부문계획 대상 사업을 찾지 못하면 품목 교체도 계획 생성도 건너뛰고 예외를 던지지 않는다. */
    @Test
    @DisplayName("부문계획 대상 사업을 찾지 못하면 건너뛰고 planReqDocNo는 null이다")
    void 부문계획_대상사업_미매칭은_건너뛴다() {
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        PlanIntent intent =
                new PlanIntent("존재하지않는사업", new BigDecimal("1000000"), null, null, null, Map.of());
        when(planAdapter.adapt(any(), any()))
                .thenReturn(new AdapterOutput(List.of(), List.of(), List.of(intent), List.of()));

        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of());
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        when(validator.validate(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service =
                new MigrationImportService(
                        List.of(planAdapter),
                        validator,
                        yearSnapshot,
                        orgIdentityResolver,
                        catalogReader,
                        approvalStamper,
                        costService,
                        costRepository,
                        projectService,
                        projectRepository,
                        budgetRateApplicationService,
                        projectItemRepository,
                        planService);

        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of());

        MigrationDto.CommitResponse response = service.commit(request, "999999");

        assertThat(response.planReqDocNo()).isNull();
        assertThat(response.itemCount()).isZero();
        verify(projectService, never()).replaceItemsForMigration(anyString(), any());
        verify(planService, never())
                .createPlanForMigration(anyString(), anyString(), any(), any(), any());
    }

    private MigrationImportService service() {
        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of("유지보수료", "011"));
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        // 전산업무비 채번 결과의 실제 BG_SNO 조회 — 값 자체를 검증하는 테스트는 별도로 이 스텁을 덮어쓴다.
        when(costRepository.findByCostBgNoAndDelYn(anyString(), eq("N")))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2026-0001")
                                        .bgSno(1)
                                        .lstYn("Y")
                                        .build()));
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));
        return new MigrationImportService(
                List.of(new com.kdb.it.domain.migration.service.adapter.CostSheetAdapter()),
                validator,
                yearSnapshot,
                orgIdentityResolver,
                catalogReader,
                approvalStamper,
                costService,
                costRepository,
                projectService,
                projectRepository,
                budgetRateApplicationService,
                projectItemRepository,
                planService);
    }

    private static MigrationDto.CommitRequest commitRequest() {
        Map<String, String> cells =
                new java.util.LinkedHashMap<>(
                        Map.of(
                                "abusCode", "571",
                                "ioeName", "유지보수료",
                                "abusTcLabel", "계속",
                                "vendorName", "커브",
                                "requestDetail", "올인원워크스페이스",
                                "deptName", "IT기획부",
                                "teamName", "IT기획팀",
                                "currency", "KRW",
                                "krwAmount", "15401"));
        return new MigrationDto.CommitRequest(
                List.of(
                        new MigrationDto.SheetPayload(
                                SheetKind.COST,
                                "2026",
                                List.of(new MigrationDto.NormalizedRow(2, cells)))),
                List.of());
    }
}
