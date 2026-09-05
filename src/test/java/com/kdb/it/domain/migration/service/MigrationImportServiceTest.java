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

import com.kdb.it.common.approval.service.ApprovalStamper;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetRateApplicationService;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.RowDecision;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.adapter.AdapterOutput;
import com.kdb.it.domain.migration.service.adapter.AllocationIntent;
import com.kdb.it.domain.migration.service.adapter.CapitalProjectSheetAdapter;
import com.kdb.it.domain.migration.service.adapter.CostSheetAdapter;
import com.kdb.it.domain.migration.service.adapter.PlanAdjustmentSheetAdapter;
import com.kdb.it.domain.migration.service.adapter.PlanIntent;
import com.kdb.it.domain.migration.service.adapter.SheetAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 매칭 → 배분 → 편성 흐름을 고정합니다 (§6.2·§7).
 *
 * <p>매처({@link MigrationLedgerMatcher})·배분기({@link MigrationAllocationPlanner})·매칭 진단({@link
 * MigrationMatchDiagnostics})은 목이 아니라 실제 인스턴스를 씁니다 — 이 테스트가 증명하려는 것이 "종합본 금액이 실효 편성률로 환산돼 편성된다"는 계산
 * 자체라 목으로 대체하면 검증이 비어 버립니다. 원장 쓰기(서비스·리포지토리)와 규칙 검증기만 목입니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigrationImportServiceTest {
    @Mock private com.kdb.it.domain.budget.common.security.ApprovalWriteGuard approvalWriteGuard;
    @Mock private jakarta.persistence.EntityManager entityManager;

    @Mock private CostService costService;
    @Mock private CostRepository costRepository;
    @Mock private ProjectService projectService;
    @Mock private ProjectRepository projectRepository;
    @Mock private BudgetRateApplicationService budgetRateApplicationService;
    @Mock private ApprovalStamper approvalStamper;
    @Mock private MigrationValidator validator;
    @Mock private MigrationYearSnapshot yearSnapshot;
    @Mock private OrgIdentityResolver orgIdentityResolver;
    @Mock private MigrationIoeCatalogReader catalogReader;
    @Mock private PlanService planService;

    @Mock private BprojaSyncService bprojaSyncService;

    /** BLOCKER가 하나라도 있으면 아무 서비스도 호출되지 않는다. */
    @Test
    @DisplayName("BLOCKER가 있으면 원장을 하나도 쓰지 않고 실패한다")
    void 블로커가_있으면_아무것도_쓰지_않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any()))
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

    /**
     * 매칭에 실패한 행은 결정을 요구하는 BLOCKER가 되어 반영이 막힌다.
     *
     * <p>결정 보정값이 없고 스냅샷에 대응 원장도 없는 상태다. 이 화면은 원장을 새로 만들지 않는 것이 기본이므로(설계 §2.1) 조용히 만들지 않고 관리자에게
     * 되돌린다.
     */
    @Test
    @DisplayName("결정하지 않은 미매칭 행은 BLOCKER가 되어 반영을 막는다")
    void 미매칭_미결정행은_반영을_막는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.commit(commitRequestWithoutDecision(), "999999"))
                .isInstanceOf(com.kdb.it.exception.CustomGeneralException.class)
                .hasMessageContaining("반영할 수 없습니다");

        verify(costService, never()).createCost(any(), anyBoolean());
    }

    /** WARNING만 있으면 반영이 진행된다. */
    @Test
    @DisplayName("WARNING만 있으면 반영을 진행한다")
    void 경고만_있으면_반영한다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any()))
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
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
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
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        service.commit(commitRequest(), "999999");

        verify(budgetRateApplicationService, times(1)).applyItemRates(any());
    }

    /**
     * items에는 이관분과 기존 연도 데이터가 모두 담겨야 한다 (Task 7이 {@code @Disabled}로 남긴 검증의 복구).
     *
     * <p>편성률 단일 적용은 연도 전체를 재작성하므로 목록에서 빠진 원장은 편성행을 잃는다. 그래서 이번 반영이 건드리지 않은 기존 사업·전산업무비도 스냅샷에서 역산한
     * 기존 편성률을 실어 그대로 담아야 하고, 새로 만든 원장도 같은 목록에 들어와야 한다.
     */
    @Test
    @DisplayName("applyItemRates items에 이관분과 기존 연도 데이터를 함께 담는다")
    void 편성률items에_연도전체를_담는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");
        MigrationYearSnapshot.Data before =
                snapshot(
                        Map.of(
                                "PRJ-2026-0099",
                                List.of(
                                        new MigrationYearSnapshot.RequestItem(
                                                "GCL-2026-0001",
                                                1,
                                                "011",
                                                new BigDecimal("1000")))),
                        Map.of(
                                "COST-2026-0099",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-2026-0099",
                                        1,
                                        "011",
                                        new BigDecimal("2000"),
                                        "계약명",
                                        "라벨")),
                        Map.of("COST-2026-0099", new BigDecimal("90")),
                        Map.of("GCL-2026-0001", new BigDecimal("80")));
        // 원장 생성 뒤 다시 읽는 스냅샷에는 방금 만든 전산업무비가 들어온다.
        MigrationYearSnapshot.Data after =
                snapshot(
                        before.itemsByProjectNo(),
                        Map.of(
                                "COST-2026-0099",
                                before.costOf("COST-2026-0099"),
                                "COST-2026-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-2026-0001",
                                        1,
                                        "011",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "라벨")),
                        before.existingCostRateByCostNo(),
                        before.existingItemRateByItemNo());
        when(yearSnapshot.load("2026")).thenReturn(before, after);

        service.commit(commitRequest(), "999999");

        ArgumentCaptor<BudgetWorkDto.ItemApplyRequest> captor =
                ArgumentCaptor.forClass(BudgetWorkDto.ItemApplyRequest.class);
        verify(budgetRateApplicationService).applyItemRates(captor.capture());

        assertThat(captor.getValue().items())
                .extracting(BudgetWorkDto.ItemRate::orcPkVl)
                .contains("COST-2026-0001", "PRJ-2026-0099", "COST-2026-0099");
        // 기존 사업·전산업무비의 편성률은 스냅샷이 품목·전산업무비 원본에서 역산한 값을 비목코드별로
        // 실어야 한다. 'BPROJM|사업관리번호' 키를 찾던 구 구현은 항상 null을 받아 기본값 100으로
        // 리셋했고, applyItemRates가 연도 전체를 재작성하므로 그 연도 모든 기존 사업의 편성률이 조용히
        // 100이 됐다.
        assertThat(captor.getValue().items())
                .filteredOn(i -> "PRJ-2026-0099".equals(i.orcPkVl()))
                .singleElement()
                .satisfies(
                        i -> {
                            assertThat(i.assetDupRt()).isNull();
                            assertThat(i.costDupRt()).isNull();
                            assertThat(i.ioeRates().get("011")).isEqualByComparingTo("80");
                        });
        assertThat(captor.getValue().items())
                .filteredOn(i -> "COST-2026-0099".equals(i.orcPkVl()))
                .singleElement()
                .satisfies(i -> assertThat(i.ioeRates().get("011")).isEqualByComparingTo("90"));
        // 새로 만든 전산업무비는 생성 후 다시 읽은 스냅샷의 요청금액을 분모로 실효 편성률을 받는다
        // (일반관리비 조정률 100% → 100.00000).
        assertThat(captor.getValue().items())
                .filteredOn(i -> "COST-2026-0001".equals(i.orcPkVl()))
                .singleElement()
                .satisfies(i -> assertThat(i.ioeRates().get("011")).isEqualByComparingTo("100"));
    }

    /**
     * 이 서비스는 첫 시트의 예산연도 하나를 연도 스냅샷·중복 판정·편성률 적용의 기준으로 쓴다. 시트마다 연도가 다르면 두 번째 시트 이후는 다른 연도의 스냅샷으로
     * 검증되고 첫 시트의 연도로 저장된다 (IMPORTANT-8).
     */
    @Test
    @DisplayName("시트마다 예산연도가 다르면 거부한다")
    void 예산연도가_섞이면_거부한다() {
        MigrationImportService service = service();

        MigrationDto.CommitRequest mixed =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of()))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2027",
                                        List.of(new MigrationDto.NormalizedRow(3, Map.of())))),
                        List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.commit(mixed, "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예산연도가 다릅니다");
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
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
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
        when(validator.validate(any(), any(), any(), any(), any()))
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
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                commitRequest().sheets(), commitRequest().overrides()));

        assertThat(response.summary().blockerCount()).isEqualTo(1);
        assertThat(response.summary().totalRows()).isEqualTo(1);
        verify(costService, never()).createCost(any(), anyBoolean());
    }

    /**
     * dry-run이 매칭 단계까지 돌아 결정 요구 BLOCKER를 낸다.
     *
     * <p>Task 7까지의 dry-run은 규칙 검증기만 돌려 미매칭 행을 알리지 못했다. 미리보기에서 결정하지 못한 행이 commit에서야 막히면 사용자는 이유를 알 수
     * 없다.
     */
    @Test
    @DisplayName("dry-run이 미매칭 행에 결정 요구 BLOCKER를 낸다")
    void dryRun은_결정요구_블로커를_낸다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.DryRunResponse response =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                commitRequestWithoutDecision().sheets(), List.of()));

        assertThat(response.summary().blockerCount()).isEqualTo(1);
        assertThat(response.diagnostics())
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("LEDGER_NOT_MATCHED");
                            assertThat(d.column()).isEqualTo(RowDecision.COLUMN);
                            // 후보가 비면 화면에 드롭다운이 그려지지 않아 손댈 방법이 없다
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .contains("CREATE_NEW", "SKIP");
                        });
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
        Map<String, String> onlyDecision = Map.of("COST|2|" + RowDecision.COLUMN, "SKIP");
        Map<String, String> withDept = new LinkedHashMap<>(onlyDecision);
        withDept.put("COST|2|deptName", "0210");
        when(validator.validate(any(), any(), any(), eq(onlyDecision), any()))
                .thenReturn(List.of(blocker));
        when(validator.validate(any(), any(), any(), eq(withDept), any())).thenReturn(List.of());

        MigrationDto.DryRunResponse withoutOverride =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                skipDecisionRequest().sheets(), skipDecisionRequest().overrides()));
        List<MigrationDto.CellOverride> both = new ArrayList<>(skipDecisionRequest().overrides());
        both.add(new MigrationDto.CellOverride(SheetKind.COST, 2, "deptName", "0210"));
        MigrationDto.DryRunResponse withOverride =
                service.dryRun(
                        new MigrationDto.DryRunRequest(skipDecisionRequest().sheets(), both));

        assertThat(withoutOverride.summary().blockerCount()).isEqualTo(1);
        assertThat(withOverride.summary().blockerCount()).isEqualTo(0);
    }

    /**
     * dry-run이 자본예산 품목 비목의 보정 선택지를 응답에 싣는다 (MIG-10).
     *
     * <p>보정 드롭다운은 그 셀에 걸린 진단의 후보만 보여 주므로, 기본 비목이 정상이라 진단이 붙지 않는 셀은 화면에서 바꿀 수단이 없었다. 검증기는 이미 세 컬럼의
     * 보정값을 받고 있었으니 빠진 것은 <b>선택지를 내려보내는 일</b>뿐이다.
     */
    @Test
    @DisplayName("dry-run이 자본예산 품목 비목 보정 선택지를 자본 계열만 담아 세 컬럼에 싣는다")
    void dryRun은_자본비목_보정_선택지를_싣는다() {
        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        // 자본 계열(1xx)과 일반관리비 계열(0xx)을 섞어 둔다 — 품목 비목 보정은 자본 계열만 고를 수 있다
        when(catalogReader.ioeCodeByName())
                .thenReturn(
                        new LinkedHashMap<>(
                                Map.of(
                                        "국내기타무형자산(일반)", "106",
                                        "개발비(일반)", "103",
                                        "개발비(감리/컨설팅)", "104",
                                        "유지보수료", "011")));
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        when(catalogReader.abusUnitNameByCode()).thenReturn(Map.of());
        when(catalogReader.generalExpenseRate()).thenReturn(BigDecimal.valueOf(100));
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        MigrationImportService service = serviceWith(List.of(new CapitalProjectSheetAdapter()));

        MigrationDto.DryRunResponse response =
                service.dryRun(new MigrationDto.DryRunRequest(List.of(capitalSheet()), List.of()));

        assertThat(response.catalogs())
                .extracting(MigrationDto.ColumnCatalog::column)
                .containsExactly("devAmountIoeC", "hwAmountIoeC", "swAmountIoeC");
        assertThat(response.catalogs())
                .allSatisfy(
                        catalog -> {
                            assertThat(catalog.sheet()).isEqualTo(SheetKind.CAPITAL_PROJECT);
                            // 코드 오름차순 — 상시 노출되는 드롭다운이라 재조회마다 순서가 흔들리면 눈에 띈다
                            assertThat(catalog.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("103", "104", "106");
                        });
    }

    @Test
    @DisplayName("자본예산 시트가 없으면 보정 선택지 카탈로그를 내려보내지 않는다")
    void dryRun은_자본시트가_없으면_카탈로그를_비운다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.DryRunResponse response =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                commitRequest().sheets(), commitRequest().overrides()));

        // 쓸 수 없는 보정 컬럼을 미리보기에 그리지 않도록 존재 여부를 응답으로 알린다
        assertThat(response.catalogs()).isEmpty();
    }

    /**
     * §7의 원장 반영 순서(일반관리비 → 자본예산 → 위임예산 → 부문계획)를 어댑터 호출 순서로 고정한다.
     *
     * <p>이 순서는 한 사업이 자본예산 시트와 부문계획 시트 양쪽에 나올 때 **하반기 조정이 종합본 편성률을 덮게** 하는 근거이기도 하다.
     *
     * <p>이 테스트만 4개 시트 종류 전부를 목 어댑터로 등록한다 — 다른 테스트는 전산업무비 경로만 검증하므로 {@link CostSheetAdapter} 하나만 쓴다.
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
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service =
                // 등록 순서를 실제 반영 순서와 일부러 뒤섞어, 서비스가 등록 순서가 아니라 §7 순서로
                // 어댑터를 호출한다는 것을 검증한다.
                serviceWith(List.of(planAdapter, delegatedAdapter, capitalAdapter, costAdapter));

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

    /**
     * requireSupported가 같은 시트 종류의 중복 업로드를 거부한다 (MIG-20).
     *
     * <p>시트별 처리 상태({@code createNewRows}·{@code matchedPkByRow} 등)가 {@link SheetKind}를 키로 쓰므로, 같은
     * 종류를 두 번 올리면 두 페이로드의 엑셀 행 번호가 같은 키 아래 섞인다. 현재 UI는 슬롯당 1개만 허용하지만 API 계약상으로는 막혀 있지 않았다.
     */
    @Test
    @DisplayName("같은 시트 종류를 두 번 올리면 IllegalArgumentException을 던진다")
    void 같은_시트종류_중복은_예외를_던진다() {
        MigrationImportService service = service();
        MigrationDto.CommitRequest duplicated =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of()))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of());

        assertThatThrownBy(() -> service.commit(duplicated, "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 시트 종류를 두 번 올릴 수 없습니다");
    }

    /** dry-run도 같은 계약을 쓴다 — 미리보기에서 먼저 걸러야 반영 단계에서 처음 실패하지 않는다. */
    @Test
    @DisplayName("dry-run도 같은 시트 종류 중복을 거부한다")
    void dry_run도_같은_시트종류_중복을_거부한다() {
        MigrationImportService service = service();
        MigrationDto.DryRunRequest duplicated =
                new MigrationDto.DryRunRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of()))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(3, Map.of())))),
                        List.of());

        assertThatThrownBy(() -> service.dryRun(duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 시트 종류를 두 번 올릴 수 없습니다");
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

    /**
     * 결정이 가리키는 PK가 그 연도에 없으면 그 행만 배분에서 빠지고 나머지는 그대로 적용한다 (Task 7이 {@code @Disabled}로 남긴 검증의 복구).
     *
     * <p>편성률 적용 목록은 스냅샷의 원장만 담으므로, 존재하지 않는 PK는 목록에 실릴 자리 자체가 없다. 이 보호가 없으면 오타가 섞인 결정 하나가 편성률 적용 전체를
     * 예외로 끝내거나 없는 원장에 편성행을 만들려 시도한다.
     *
     * <p>목표 편성액을 0으로 둔 이유가 있다 — 배분할 금액이 남아 있는데 대상 원장의 요청 품목이 없으면 조용히 건너뛰지 않고 {@code ITEM_BASE_ZERO}
     * BLOCKER가 난다({@code MigrationMatchDiagnosticsTest}가 고정). 이 테스트가 다루는 것은 배분할 것이 없는 행이 편성 목록에서
     * 빠지는가이다.
     */
    @Test
    @DisplayName("결정 PK를 그 연도에서 찾지 못하면 건너뛰고 나머지는 그대로 적용한다")
    void 편성률_PK_미매칭은_건너뛴다() {
        SheetAdapter costAdapter = Mockito.mock(SheetAdapter.class);
        when(costAdapter.supports()).thenReturn(SheetKind.COST);
        when(costAdapter.adapt(any(), any()))
                .thenReturn(
                        new AdapterOutput(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(
                                        new AllocationIntent(
                                                SheetKind.COST,
                                                2,
                                                "BPROJM",
                                                AllocationIntent.MatchKey.ofProjectName("존재하지않는사업"),
                                                Map.of("costAmount", BigDecimal.ZERO),
                                                null))));

        when(yearSnapshot.load(anyString()))
                .thenReturn(
                        TestSnapshots.snapshotWithProjectName("2026", "실재하는사업", "PRJ-2026-0007"));
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service = serviceWith(List.of(costAdapter));

        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.COST,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        // 관리자가 없는 PK를 가리키는 결정을 보냈다
                        List.of(
                                new MigrationDto.CellOverride(
                                        SheetKind.COST,
                                        2,
                                        RowDecision.COLUMN,
                                        RowDecision.matchValue("존재하지않는사업"))));

        MigrationDto.CommitResponse response = service.commit(request, "999999");

        assertThat(response).isNotNull();
        ArgumentCaptor<BudgetWorkDto.ItemApplyRequest> captor =
                ArgumentCaptor.forClass(BudgetWorkDto.ItemApplyRequest.class);
        verify(budgetRateApplicationService).applyItemRates(captor.capture());
        assertThat(captor.getValue().items())
                .extracting(BudgetWorkDto.ItemRate::orcPkVl)
                .doesNotContain("존재하지않는사업")
                .contains("PRJ-2026-0007");
        // MIG-06 건너뛴 사실을 로그가 아니라 응답으로 알린다
        assertThat(response.skippedRateCount()).isEqualTo(1);
    }

    /** 건너뛴 것이 없으면 두 건수는 0이다 — 항상 0이 아닌 값을 내는 오검출을 막는다. */
    @Test
    @DisplayName("건너뛴 대상이 없으면 skipped 건수는 0이다")
    void 건너뛴_대상이_없으면_건수는_0이다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.CommitResponse response = service.commit(capitalRequest(List.of()), "12345");

        assertThat(response.skippedRateCount()).isZero();
        assertThat(response.skippedPlanCount()).isZero();
    }

    /** 부문계획 시트를 올리지 않으면 planReqDocNo는 null이다. */
    @Test
    @DisplayName("부문계획 시트가 없으면 planReqDocNo는 null이다")
    void 부문계획시트가_없으면_planReqDocNo는_null이다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(costService.createCost(any(), anyBoolean())).thenReturn("COST-2026-0001");

        MigrationDto.CommitResponse response = service.commit(commitRequest(), "999999");

        assertThat(response.planReqDocNo()).isNull();
        verify(planService, never())
                .createPlanForMigration(anyString(), anyString(), any(), any(), any(), any());
    }

    /** 부문계획 대상 사업이 스냅샷에 있으면 조정 계획을 만든다. 품목은 건드리지 않는다. */
    @Test
    @DisplayName("부문계획 대상 사업이 있으면 품목을 건드리지 않고 조정 계획을 만든다")
    void 부문계획_대상사업이_있으면_계획만_만든다() {
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        PlanIntent intent =
                new PlanIntent(
                        "문자메시지안심마크도입",
                        new BigDecimal("1000000"),
                        null,
                        null,
                        new BigDecimal("500000"),
                        "202603",
                        Map.of("사업진행", "진행(품의)"));
        when(planAdapter.adapt(any(), any()))
                .thenReturn(new AdapterOutput(List.of(), List.of(), List.of(intent), List.of()));

        when(yearSnapshot.load(anyString()))
                .thenReturn(
                        TestSnapshots.snapshotWithProjectName(
                                "2026", "문자메시지안심마크도입", "PRJ-2026-0005"));
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(planService.createPlanForMigration(
                        eq("2026"),
                        eq(com.kdb.it.domain.budget.plan.PlanType.ADJUSTMENT.code()),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn("PLN-2026-0009");
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service = serviceWith(List.of(planAdapter));

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
        // 하반기 조정은 요청 품목을 만들지도 지우지도 않는다 (설계 §5.4)
        assertThat(response.itemCount()).isZero();
        // 품목 교체 경로(구 ProjectService.replaceItemsForMigration)는 재설계로 삭제했으므로,
        // 이제 "조정이 품목을 건드리지 않는다"는 호출 검증이 아니라 구조로 보장된다.
        verify(planService)
                .createPlanForMigration(
                        eq("2026"),
                        eq(com.kdb.it.domain.budget.plan.PlanType.ADJUSTMENT.code()),
                        eq(List.of("PRJ-2026-0005")),
                        any(),
                        any(),
                        any());
    }

    /** 부문계획 대상 사업을 찾지 못하면 계획 생성을 건너뛰고 예외를 던지지 않는다. */
    @Test
    @DisplayName("부문계획 대상 사업을 찾지 못하면 건너뛰고 planReqDocNo는 null이다")
    void 부문계획_대상사업_미매칭은_건너뛴다() {
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        PlanIntent intent =
                new PlanIntent(
                        "존재하지않는사업", new BigDecimal("1000000"), null, null, null, null, Map.of());
        when(planAdapter.adapt(any(), any()))
                .thenReturn(new AdapterOutput(List.of(), List.of(), List.of(intent), List.of()));

        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service = serviceWith(List.of(planAdapter));

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
        // MIG-06 건너뛴 사실을 로그가 아니라 응답으로 알린다
        assertThat(response.skippedPlanCount()).isEqualTo(1);
        // 품목 교체 경로(구 ProjectService.replaceItemsForMigration)는 재설계로 삭제했으므로,
        // 이제 "조정이 품목을 건드리지 않는다"는 호출 검증이 아니라 구조로 보장된다.
        verify(planService, never())
                .createPlanForMigration(anyString(), anyString(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // 매칭 → 배분 → 편성 (Task 9의 핵심 흐름)
    // ------------------------------------------------------------------

    /**
     * 종합본의 자본예산 행이 기존 사업에 매칭되면 원장을 만들지 않고 편성률만 갱신한다.
     *
     * <p>요청 품목 1,406백만원 × 조정비율 0.7 = 984.2백만원이 목표 편성액이고, 요청 합계로 나눈 실효 편성률 70.00000이 그 비목(106)에 실린다.
     */
    @Test
    @DisplayName("매칭된 사업은 원장을 만들지 않고 편성만 한다")
    void 매칭된_사업은_원장을_만들지_않고_편성만_한다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.CommitResponse response = service.commit(capitalRequest(List.of()), "12345");

        verify(projectService, never()).createProject(any(), anyBoolean());
        assertThat(response.projectCount()).isZero();
        assertThat(response.itemCount()).isZero();

        assertThat(appliedRateOf("PRJ-2026-0001").ioeRates())
                .hasEntrySatisfying(
                        "106", rate -> assertThat(rate).isEqualByComparingTo("70.00000"));
    }

    /** CREATE_NEW로 결정한 행만 원장을 만든다. */
    @Test
    @DisplayName("CREATE_NEW로 결정한 행만 원장을 만든다")
    void CREATE_NEW로_결정한_행만_원장을_만든다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(projectService.createProject(any(), anyBoolean())).thenReturn("PRJ-2026-0100");
        when(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0100", "N"))
                .thenReturn(
                        java.util.Optional.of(
                                Bprojm.builder().abusMngNo("PRJ-2026-0100").sno(1).build()));

        MigrationDto.CommitResponse response =
                service.commit(
                        capitalRequest(
                                List.of(
                                        new MigrationDto.CellOverride(
                                                SheetKind.CAPITAL_PROJECT,
                                                2,
                                                RowDecision.COLUMN,
                                                "CREATE_NEW"))),
                        "12345");

        verify(projectService).createProject(any(), eq(true));
        assertThat(response.projectCount()).isEqualTo(1);
        // 자본예산 시트는 금액이 있는 열마다 품목을 만든다 — 여기서는 기타무형자산 한 건
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.createdIds()).containsExactly("PRJ-2026-0100");
    }

    /**
     * MIG-23① — {@code CREATE_NEW} 행에 일반관리비 열이 채워져 있으면 그 목표액이 반영되지 않는다는 경고를 낸다.
     *
     * <p>{@code CapitalProjectSheetAdapter.items()}는 자본 3열만 품목으로 만들므로, 새로 만든 원장에는 일반관리비 목표액을 담을 품목이
     * 없다. 종전에는 이 손실이 진단 없이 조용히 일어났다 — 같은 웨이브에서 부문계획 시트의 {@code CREATE_NEW} 무동작에 {@code
     * CREATE_NOT_SUPPORTED}를 붙인 원칙과 대칭을 맞춘다.
     */
    @Test
    @DisplayName("CREATE_NEW 행의 일반관리비 목표액은 WARNING으로 알린다")
    void 신규_생성행의_일반관리비_목표액은_경고를_낸다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.DryRunResponse response =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                List.of(capitalSheetWithGeneralAmount()),
                                List.of(
                                        new MigrationDto.CellOverride(
                                                SheetKind.CAPITAL_PROJECT,
                                                2,
                                                RowDecision.COLUMN,
                                                "CREATE_NEW"))));

        assertThat(response.diagnostics())
                .filteredOn(d -> "GENERAL_AMOUNT_NOT_CREATABLE".equals(d.code()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.WARNING);
                            assertThat(d.column()).isEqualTo("generalAmount");
                            assertThat(d.excelRow()).isEqualTo(2);
                        });
    }

    /** 일반관리비 열이 비어 있으면 담을 목표액 자체가 없으므로 ①의 경고를 내지 않는다. */
    @Test
    @DisplayName("일반관리비 열이 빈 CREATE_NEW 행에는 경고를 내지 않는다")
    void 일반관리비_열이_빈_신규_생성행에는_경고를_내지_않는다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        MigrationDto.DryRunResponse response =
                service.dryRun(
                        new MigrationDto.DryRunRequest(
                                List.of(capitalSheet()),
                                List.of(
                                        new MigrationDto.CellOverride(
                                                SheetKind.CAPITAL_PROJECT,
                                                2,
                                                RowDecision.COLUMN,
                                                "CREATE_NEW"))));

        assertThat(response.diagnostics())
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("GENERAL_AMOUNT_NOT_CREATABLE");
    }

    /** SKIP으로 결정한 행은 편성 대상에서 빠지고 기존 편성률만 남는다. */
    @Test
    @DisplayName("SKIP으로 결정한 행은 편성 대상에서 빠진다")
    void SKIP으로_결정한_행은_편성_대상에서_빠진다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.commit(
                capitalRequest(
                        List.of(
                                new MigrationDto.CellOverride(
                                        SheetKind.CAPITAL_PROJECT, 2, RowDecision.COLUMN, "SKIP"))),
                "12345");

        verify(projectService, never()).createProject(any(), anyBoolean());
        // 기존 편성행이 없는 품목이라 유지할 편성률도 없다 — 종합본의 조정비율이 반영되지 않았다는 뜻이다
        assertThat(appliedRateOf("PRJ-2026-0001").ioeRates()).isEmpty();
    }

    /** 종합본에 없는 기존 사업의 편성률은 그대로 유지된다. */
    @Test
    @DisplayName("종합본에 없는 기존 사업의 편성률이 유지된다")
    void 종합본에_없는_기존_사업의_편성률이_유지된다() {
        MigrationImportService service = capitalService();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.commit(capitalRequest(List.of()), "12345");

        assertThat(appliedRateOf("PRJ-2026-0009").ioeRates())
                .hasEntrySatisfying("103", rate -> assertThat(rate).isEqualByComparingTo("55"));
    }

    /**
     * 하반기 조정의 확정금액이 실효 편성률로 환산돼 기존 사업에 실린다.
     *
     * <p>Task 7이 5단계를 제거하면서 하반기 조정이 완전히 무동작이 됐던 시나리오다. 요청 품목 1,406백만원에 확정금액 703백만원을 편성하면 실효 편성률은
     * 50.00000이다.
     */
    @Test
    @DisplayName("하반기 조정의 확정금액이 실효 편성률로 실린다")
    void 하반기_조정의_확정금액이_편성률로_실린다() {
        when(yearSnapshot.load(anyString())).thenReturn(capitalSnapshot());
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));
        when(planService.createPlanForMigration(
                        anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("PLN-2026-0001");
        MigrationImportService service = serviceWith(List.of(new PlanAdjustmentSheetAdapter()));

        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("projectName", "웹한글기안기도입");
        cells.put("swAmount", "703");

        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, cells)))),
                        List.of()),
                "12345");

        assertThat(appliedRateOf("PRJ-2026-0001").ioeRates())
                .hasEntrySatisfying(
                        "106", rate -> assertThat(rate).isEqualByComparingTo("50.00000"));
    }

    /**
     * 같은 사업이 자본예산 시트와 부문계획 시트 양쪽에 나오면 하반기 조정이 이긴다.
     *
     * <p>어댑터 순서(§7)가 부문계획을 마지막에 두는 이유가 여기 있다 — 6월 조정이 더 최신 판단이다.
     */
    @Test
    @DisplayName("같은 사업이 두 시트에 나오면 하반기 조정이 종합본을 덮는다")
    void 두_시트에_나온_사업은_하반기_조정이_이긴다() {
        when(yearSnapshot.load(anyString())).thenReturn(capitalSnapshot());
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));
        when(planService.createPlanForMigration(
                        anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("PLN-2026-0001");
        MigrationImportService service =
                serviceWith(
                        List.of(
                                new CapitalProjectSheetAdapter(),
                                new PlanAdjustmentSheetAdapter()));

        Map<String, String> planCells = new LinkedHashMap<>();
        planCells.put("projectName", "웹한글기안기도입");
        planCells.put("swAmount", "703");

        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(
                                capitalSheet(),
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, planCells)))),
                        List.of()),
                "12345");

        // 종합본은 70%, 하반기 조정은 50%를 낸다. 마지막에 처리한 부문계획이 이긴다.
        assertThat(appliedRateOf("PRJ-2026-0001").ioeRates())
                .hasEntrySatisfying(
                        "106", rate -> assertThat(rate).isEqualByComparingTo("50.00000"));
    }

    /**
     * 매칭된 전산업무비 원장의 빈 사업코드를 종합본 값으로 채운다 (설계 §4.1).
     *
     * <p>편성요청서 양식에 사업코드 열이 없어 1단계가 만든 행은 대부분 비어 있는데, 예산 집계가 사업코드로 묶이므로 비워 두면 집계에서 빠진다.
     */
    @Test
    @DisplayName("매칭된 전산업무비의 빈 사업코드를 종합본 값으로 채운다")
    void 매칭된_전산업무비의_빈_사업코드를_채운다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(yearSnapshot.load(anyString())).thenReturn(costSnapshotMatching());
        Bcostm matched = Bcostm.builder().costBgNo("COST-2026-0055").bgSno(1).lstYn("Y").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0055"))
                .thenReturn(List.of(matched));

        service.commit(commitRequestWithoutDecision(), "999999");

        verify(costService, never()).createCost(any(), anyBoolean());
        assertThat(matched.getBgUntAbusC()).isEqualTo("571");
        var ordered =
                org.mockito.Mockito.inOrder(costRepository, entityManager, approvalWriteGuard);
        ordered.verify(costRepository).findCurrentVersionsForUpdate("COST-2026-0055");
        ordered.verify(entityManager).refresh(matched);
        ordered.verify(approvalWriteGuard).verifyWritable("BCOSTM", "COST-2026-0055", 1, "수정");
    }

    @Test
    void 종합이관은_잠금후_진행중결재가_있으면_사업코드를_바꾸지않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(yearSnapshot.load(anyString())).thenReturn(costSnapshotMatching());
        Bcostm matched = Bcostm.builder().costBgNo("COST-2026-0055").bgSno(1).lstYn("Y").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0055"))
                .thenReturn(List.of(matched));
        org.mockito.Mockito.doThrow(new IllegalStateException("결재중"))
                .when(approvalWriteGuard)
                .verifyWritable("BCOSTM", "COST-2026-0055", 1, "수정");
        assertThatThrownBy(() -> service.commit(commitRequestWithoutDecision(), "999999"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(matched.getBgUntAbusC()).isNull();
    }

    /**
     * 코드표에 없는 사업코드는 원장에 채우지 않는다.
     *
     * <p>정상 경로에서는 {@code MigrationValidator}가 이 값을 모든 전산업무비 행에서 검사해 미리보기에서 막지만, 코드 카탈로그가 비면(코드그룹
     * 미적재) 판정 근거가 없어 그대로 통과한다. 이 테스트는 검증기를 목으로 비워 그 구멍을 재현하고, 쓰기 직전 방어가 원장을 오염시키지 않는지 확인한다 — {@code
     * BG_UNT_ABUS_C}는 3자라 원문이 그대로 흘러가면 flush에서 {@code ORA-12899}가 나거나 길이가 맞는 오타가 조용히 저장된다.
     *
     * <p><b>이 테스트가 덮지 않는 것</b>: 검증기 자체를 목으로 비웠으므로 "카탈로그가 비면 검증기가 실제로 통과시킨다"는 조건은 증명하지 않는다 — 검증기가 어떤
     * 이유로든 통과했다고 가정할 뿐이다. 그 실제 조건(카탈로그 자체가 빈 상태에서 REAL {@code MigrationValidator}가 통과시키는 것)은 {@link
     * #사업코드_카탈로그가_비면_검증을_통과하되_채우지도_않는다}가 덮는다. 실 Oracle에서 "카탈로그가 채워진 환경에서는 검증기가 더 앞에서 막는다"는 반대 사실은
     * {@code MigrationImportIt.미등록_사업코드는_검증에서_차단되어_채워지지_않는다}(통합 테스트)가 고정한다.
     */
    @Test
    @DisplayName("코드표에 없는 사업코드는 전산업무비에 채우지 않는다")
    void 미등록_사업코드는_채우지_않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(yearSnapshot.load(anyString())).thenReturn(costSnapshotMatching());
        Bcostm matched = Bcostm.builder().costBgNo("COST-2026-0055").bgSno(1).lstYn("Y").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0055"))
                .thenReturn(List.of(matched));

        MigrationDto.CommitRequest request = commitRequestWithoutDecision();
        // 3자를 넘고 코드표에도 없는 값 — 그대로 쓰면 ORA-12899다
        List<MigrationDto.CellOverride> overrides =
                List.of(new MigrationDto.CellOverride(SheetKind.COST, 2, "abusCode", "9999"));

        service.commit(new MigrationDto.CommitRequest(request.sheets(), overrides), "999999");

        assertThat(matched.getBgUntAbusC()).isNull();
    }

    /** 공백이 섞인 사업코드는 다듬어 코드표와 대조한 뒤 채운다. */
    @Test
    @DisplayName("사업코드의 앞뒤 공백을 다듬어 채운다")
    void 사업코드의_공백을_다듬는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(yearSnapshot.load(anyString())).thenReturn(costSnapshotMatching());
        Bcostm matched = Bcostm.builder().costBgNo("COST-2026-0055").bgSno(1).lstYn("Y").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0055"))
                .thenReturn(List.of(matched));

        MigrationDto.CommitRequest request = commitRequestWithoutDecision();
        List<MigrationDto.CellOverride> overrides =
                List.of(new MigrationDto.CellOverride(SheetKind.COST, 2, "abusCode", " 571 "));

        service.commit(new MigrationDto.CommitRequest(request.sheets(), overrides), "999999");

        assertThat(matched.getBgUntAbusC()).isEqualTo("571");
    }

    /** 원장에 이미 사업코드가 있으면 종합본이 덮지 않는다. */
    @Test
    @DisplayName("전산업무비에 사업코드가 이미 있으면 덮지 않는다")
    void 사업코드가_이미_있으면_덮지_않는다() {
        MigrationImportService service = service();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        MigrationYearSnapshot.Data base = costSnapshotMatching();
        Map<String, String> withCode = new LinkedHashMap<>();
        withCode.put("COST-2026-0055", "999");
        when(yearSnapshot.load(anyString()))
                .thenReturn(
                        new MigrationYearSnapshot.Data(
                                base.bseYy(),
                                base.projectNoByNormalizedName(),
                                base.projectNameByNo(),
                                base.ordinaryProjectNosByDept(),
                                base.itemsByProjectNo(),
                                base.costByNo(),
                                base.costNoByDeptKey(),
                                base.costNosByDeptIoe(),
                                withCode,
                                base.existingPlanTypes(),
                                base.existingCostRateByCostNo(),
                                base.existingItemRateByItemNo(),
                                base.allProjectNos(),
                                base.allCostNos()));

        service.commit(commitRequestWithoutDecision(), "999999");

        verify(costRepository, never()).findCurrentVersionsForUpdate("COST-2026-0055");
    }

    /**
     * {@code fillCostBudgetUnitCodes} 자신의 카탈로그 방어가 실제로 실행되는 유일한 조건을 재현합니다.
     *
     * <p>{@link #미등록_사업코드는_채우지_않는다}는 검증기를 통째로 목으로 비워 "검증기가 통과시켰다"만 가정하지만, 실제로는 사업코드 카탈로그가 채워져 있으면
     * {@code MigrationValidator.validateCostRowAlways}가 매칭 행에도 사업코드를 항상 검사해 미등록 값을 {@code
     * CODE_UNRESOLVED} BLOCKER로 막는다({@code MigrationValidatorTest.매칭행의_미등록_사업코드도_블로커다}가 그 사실을
     * 고정한다). 그 검사를 실제로 우회하는 유일한 조건은 {@code MigrationCellChecks.resolveCodeCell}의 {@code
     * codeCatalog.isEmpty()} 조기 반환 — 사업코드 공통코드 그룹 자체가 로드되지 않은 예외 상황뿐이다.
     *
     * <p>그래서 이 테스트는 목이 아닌 REAL {@link MigrationValidator} 인스턴스로 서비스를 조립해 그 조건을 그대로 재현한다. 카탈로그가 정말
     * 비어 있어야만 하므로 부서·비목은 실제로 해석되게(조직 인덱스에 IT기획부를 심어) 두고 사업코드 카탈로그만 빈 맵으로 만든다 — 그래야 abusCode 검사만
     * 우회되고 다른 이유로 BLOCKER가 나 커밋 자체가 막히는 일이 없다. 검증기를 통과한 뒤 {@code fillCostBudgetUnitCodes} 자신의
     * {@code containsKey} 검사가 실제로 실행돼 {@code BG_UNT_ABUS_C}를 채우지 않는지 확인한다 — 이 컬럼은 {@code
     * VARCHAR2(3)}이라 이 방어가 없으면 등록되지 않은 원문이 그대로 저장을 시도해 {@code ORA-12899}로 이어질 수 있다. 실 Oracle에서
     * "카탈로그가 채워진 환경에서는 검증기가 더 앞에서 막는다"는 반대 사실은 {@code
     * MigrationImportIt.미등록_사업코드는_검증에서_차단되어_채워지지_않는다}(통합 테스트)가 고정한다.
     */
    @Test
    @DisplayName("사업코드 카탈로그가 비어 있으면 검증기를 통과하되 fillCostBudgetUnitCodes도 채우지 않는다")
    void 사업코드_카탈로그가_비면_검증을_통과하되_채우지도_않는다() {
        when(yearSnapshot.load(anyString())).thenReturn(costSnapshotMatchingWithDept("180"));
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(org("180", "IT기획부")), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of("유지보수료", "011"));
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        // 사업코드 공통코드 그룹 자체가 로드되지 않은 예외 상황 재현 — resolveCodeCell을 실제로 조기 반환시키는 유일한 조건
        when(catalogReader.abusUnitNameByCode()).thenReturn(Map.of());
        when(catalogReader.generalExpenseRate()).thenReturn(BigDecimal.valueOf(100));
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));
        Bcostm matched = Bcostm.builder().costBgNo("COST-2026-0055").bgSno(1).lstYn("Y").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0055"))
                .thenReturn(List.of(matched));

        MigrationImportService service =
                serviceWith(List.of(new CostSheetAdapter()), new MigrationValidator());

        service.commit(commitRequestWithoutDecision(), "999999");

        assertThat(matched.getBgUntAbusC()).isNull();
    }

    // ------------------------------------------------------------------
    // 픽스처
    // ------------------------------------------------------------------

    /** 마지막 {@code applyItemRates} 호출에서 그 원장의 편성 항목을 꺼냅니다. */
    private BudgetWorkDto.ItemRate appliedRateOf(String orcPkVl) {
        ArgumentCaptor<BudgetWorkDto.ItemApplyRequest> captor =
                ArgumentCaptor.forClass(BudgetWorkDto.ItemApplyRequest.class);
        verify(budgetRateApplicationService).applyItemRates(captor.capture());
        return captor.getValue().items().stream()
                .filter(item -> orcPkVl.equals(item.orcPkVl()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("편성 목록에 " + orcPkVl + "이 없습니다"));
    }

    /** 전산일반관리비 어댑터만 등록한 서비스. */
    private MigrationImportService service() {
        when(yearSnapshot.load(anyString())).thenReturn(TestSnapshots.empty("2026"));
        stubLookupIndex();
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
        return serviceWith(List.of(new CostSheetAdapter()));
    }

    /** 자본예산 어댑터만 등록하고 기존 사업 2건이 있는 연도 스냅샷을 쓰는 서비스. */
    private MigrationImportService capitalService() {
        when(yearSnapshot.load(anyString())).thenReturn(capitalSnapshot());
        stubLookupIndex();
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));
        return serviceWith(List.of(new CapitalProjectSheetAdapter()));
    }

    private MigrationImportService serviceWith(List<SheetAdapter> adapters) {
        return serviceWith(adapters, validator);
    }

    /**
     * 검증기를 지정해 서비스를 조립합니다. 대부분의 테스트는 목 {@link #validator}를 그대로 쓰지만, {@link
     * #사업코드_카탈로그가_비면_검증을_통과하되_채우지도_않는다}처럼 REAL {@link MigrationValidator} 인스턴스가 필요한 테스트는 이 오버로드로 바꿔
     * 낀다.
     */
    private MigrationImportService serviceWith(
            List<SheetAdapter> adapters, MigrationValidator validatorToUse) {
        return new MigrationImportService(
                adapters,
                validatorToUse,
                new MigrationMatchDiagnostics(new MigrationLedgerMatcher()),
                new MigrationAllocationPlanner(),
                yearSnapshot,
                orgIdentityResolver,
                catalogReader,
                approvalStamper,
                costService,
                costRepository,
                projectService,
                projectRepository,
                budgetRateApplicationService,
                planService,
                // 매핑 판정은 실물로 돌리고 기록만 목으로 관측한다
                new PlanAdjustmentProgressRecorder(bprojaSyncService),
                approvalWriteGuard,
                entityManager);
    }

    private void stubLookupIndex() {
        when(orgIdentityResolver.snapshot())
                .thenReturn(OrgIdentityResolver.Index.of(List.of(), List.of()));
        when(catalogReader.ioeCodeByName()).thenReturn(Map.of("유지보수료", "011"));
        when(catalogReader.xcrByCurrency()).thenReturn(Map.of());
        // 사업코드 카탈로그 — Step 5b가 쓰기 전에 이 코드표와 대조한다
        when(catalogReader.abusUnitNameByCode()).thenReturn(Map.of("571", "정보화"));
        when(catalogReader.generalExpenseRate()).thenReturn(BigDecimal.valueOf(100));
    }

    /**
     * 사업 2건이 있는 연도 스냅샷.
     *
     * <ul>
     *   <li>PRJ-2026-0001 `웹한글기안기도입` — 품목 GCL-1(비목 106, 1,406백만원), 기존 편성행 없음
     *   <li>PRJ-2026-0009 — 품목 GCL-9(비목 103), 기존 편성률 55. 종합본에 없는 사업이다
     * </ul>
     */
    private MigrationYearSnapshot.Data capitalSnapshot() {
        Map<String, String> byName = new LinkedHashMap<>();
        byName.put("웹한글기안기도입", "PRJ-2026-0001");
        Map<String, List<MigrationYearSnapshot.RequestItem>> items = new LinkedHashMap<>();
        items.put(
                "PRJ-2026-0001",
                List.of(
                        new MigrationYearSnapshot.RequestItem(
                                "GCL-1", 1, "106", new BigDecimal("1406000000"))));
        items.put(
                "PRJ-2026-0009",
                List.of(
                        new MigrationYearSnapshot.RequestItem(
                                "GCL-9", 1, "103", new BigDecimal("500000000"))));
        return new MigrationYearSnapshot.Data(
                "2026",
                byName,
                Map.of("PRJ-2026-0001", "웹한글기안기도입"),
                Map.of(),
                items,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of("GCL-9", new BigDecimal("55")),
                List.of("PRJ-2026-0001", "PRJ-2026-0009"),
                List.of());
    }

    /** {@link #commitRequestWithoutDecision}의 행이 정확히 매칭되는 전산업무비 스냅샷. */
    private MigrationYearSnapshot.Data costSnapshotMatching() {
        String key = MigrationYearSnapshot.costDeptKey("2026", null, "011", "커브", "올인원워크스페이스");
        Map<String, MigrationYearSnapshot.CostRef> costByNo = new LinkedHashMap<>();
        costByNo.put(
                "COST-2026-0055",
                new MigrationYearSnapshot.CostRef(
                        "COST-2026-0055",
                        1,
                        "011",
                        new BigDecimal("15401000"),
                        "올인원워크스페이스",
                        "올인원워크스페이스 / 커브"));
        Map<String, String> byKey = new LinkedHashMap<>();
        byKey.put(key, "COST-2026-0055");
        Map<String, String> bgUntAbusC = new LinkedHashMap<>();
        bgUntAbusC.put("COST-2026-0055", null);
        return new MigrationYearSnapshot.Data(
                "2026",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                costByNo,
                byKey,
                Map.of(),
                bgUntAbusC,
                Set.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of("COST-2026-0055"));
    }

    /**
     * {@link #costSnapshotMatching}과 같은 원장이지만 부서 기준 자연키의 부서코드를 지정합니다. 조직 인덱스에 실제 부서를 심어 REAL {@link
     * MigrationValidator}로 부서명을 해석시키는 테스트가 씁니다 — 그 경로에서는 미해석 부서코드(null)로는 매칭 키가 맞지 않습니다.
     */
    private MigrationYearSnapshot.Data costSnapshotMatchingWithDept(String deptCode) {
        String key = MigrationYearSnapshot.costDeptKey("2026", deptCode, "011", "커브", "올인원워크스페이스");
        Map<String, MigrationYearSnapshot.CostRef> costByNo = new LinkedHashMap<>();
        costByNo.put(
                "COST-2026-0055",
                new MigrationYearSnapshot.CostRef(
                        "COST-2026-0055",
                        1,
                        "011",
                        new BigDecimal("15401000"),
                        "올인원워크스페이스",
                        "올인원워크스페이스 / 커브"));
        Map<String, String> byKey = new LinkedHashMap<>();
        byKey.put(key, "COST-2026-0055");
        Map<String, String> bgUntAbusC = new LinkedHashMap<>();
        bgUntAbusC.put("COST-2026-0055", null);
        return new MigrationYearSnapshot.Data(
                "2026",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                costByNo,
                byKey,
                Map.of(),
                bgUntAbusC,
                Set.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of("COST-2026-0055"));
    }

    private static CorgnI org(String code, String name) {
        return CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    /** 사업·전산업무비 목록만 갈아 끼운 연도 스냅샷. */
    private MigrationYearSnapshot.Data snapshot(
            Map<String, List<MigrationYearSnapshot.RequestItem>> itemsByProject,
            Map<String, MigrationYearSnapshot.CostRef> costByNo,
            Map<String, BigDecimal> costRates,
            Map<String, BigDecimal> itemRates) {
        return new MigrationYearSnapshot.Data(
                "2026",
                Map.of(),
                Map.of(),
                Map.of(),
                itemsByProject,
                costByNo,
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                costRates,
                itemRates,
                List.copyOf(itemsByProject.keySet()),
                List.copyOf(costByNo.keySet()));
    }

    /** 자본예산 시트 한 행 — 사업명 `웹한글기안기도입`, 기타무형 1,406백만원, 조정비율 0.7. */
    private static MigrationDto.SheetPayload capitalSheet() {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("projectName", "웹한글기안기도입");
        cells.put("swAmount", "1406");
        cells.put("adjustRate", "0.7");
        return new MigrationDto.SheetPayload(
                SheetKind.CAPITAL_PROJECT,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, cells)));
    }

    /** 같은 자본예산 행에 일반관리비 열까지 채운 시트. MIG-23① 진단 픽스처. */
    private static MigrationDto.SheetPayload capitalSheetWithGeneralAmount() {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("projectName", "웹한글기안기도입");
        cells.put("swAmount", "1406");
        cells.put("generalAmount", "100");
        cells.put("adjustRate", "0.7");
        return new MigrationDto.SheetPayload(
                SheetKind.CAPITAL_PROJECT,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, cells)));
    }

    private static MigrationDto.CommitRequest capitalRequest(
            List<MigrationDto.CellOverride> overrides) {
        return new MigrationDto.CommitRequest(List.of(capitalSheet()), overrides);
    }

    /**
     * 전산일반관리비 시트 한 행.
     *
     * @param decision 행 결정 보정값. null이면 결정을 보내지 않습니다
     */
    private static MigrationDto.CommitRequest costRequest(String decision) {
        Map<String, String> cells =
                new LinkedHashMap<>(
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
        List<MigrationDto.CellOverride> overrides = new ArrayList<>();
        if (decision != null) {
            overrides.add(
                    new MigrationDto.CellOverride(SheetKind.COST, 2, RowDecision.COLUMN, decision));
        }
        return new MigrationDto.CommitRequest(
                List.of(
                        new MigrationDto.SheetPayload(
                                SheetKind.COST,
                                "2026",
                                List.of(new MigrationDto.NormalizedRow(2, cells)))),
                overrides);
    }

    /** 원장을 새로 만들기로 결정한 전산일반관리비 요청. */
    private static MigrationDto.CommitRequest commitRequest() {
        return costRequest("CREATE_NEW");
    }

    /** 결정을 보내지 않은 전산일반관리비 요청. 매칭 결과에 따라 진단이 갈립니다. */
    private static MigrationDto.CommitRequest commitRequestWithoutDecision() {
        return costRequest(null);
    }

    /** 이 행을 편성하지 않기로 결정한 전산일반관리비 요청. */
    private static MigrationDto.CommitRequest skipDecisionRequest() {
        return costRequest("SKIP");
    }

    @ParameterizedTest(name = "사업진행 \"{0}\"은 상태코드 {1}로 기록한다")
    @CsvSource({"진행(품의),71", "진행(계약),75", "취소(연기),00"})
    @DisplayName("조정 시트의 사업진행을 사업 전용 key로 BPROJA에 기록한다")
    void 사업진행을_상태코드로_기록한다(String label, String expectedCode) {
        commitPlanAdjustmentWithProgress(label);

        // 사업 자신의 행(예산편성 상태)도, 부문계획 문서 행도 아닌 전용 key를 쓴다(MIG-01)
        verify(bprojaSyncService).upsert("PRJ-2026-0005", "ADJ-PRJ-2026-0005", expectedCode);
    }

    @Test
    @DisplayName("모르는 사업진행 값은 상태로 접지 않고 스냅샷에만 남긴다")
    void 모르는_사업진행_값은_기록하지_않는다() {
        commitPlanAdjustmentWithProgress("알 수 없는 값");

        // 임의 코드로 접으면 화면에 사실과 다른 단계가 켜진다
        verify(bprojaSyncService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("사업진행 값이 비면 아무것도 기록하지 않는다")
    void 사업진행이_비면_기록하지_않는다() {
        commitPlanAdjustmentWithProgress("");

        verify(bprojaSyncService, never()).upsert(any(), any(), any());
    }

    /** 사업진행 값 하나만 다른 부문계획 조정 커밋을 실행한다. */
    private void commitPlanAdjustmentWithProgress(String progressLabel) {
        SheetAdapter planAdapter = Mockito.mock(SheetAdapter.class);
        when(planAdapter.supports()).thenReturn(SheetKind.PLAN_ADJUSTMENT);
        PlanIntent intent =
                new PlanIntent(
                        "문자메시지안심마크도입",
                        new BigDecimal("1000000"),
                        null,
                        null,
                        new BigDecimal("500000"),
                        "202603",
                        Map.of("progressLabel", progressLabel));
        when(planAdapter.adapt(any(), any()))
                .thenReturn(new AdapterOutput(List.of(), List.of(), List.of(intent), List.of()));

        when(yearSnapshot.load(anyString()))
                .thenReturn(
                        TestSnapshots.snapshotWithProjectName(
                                "2026", "문자메시지안심마크도입", "PRJ-2026-0005"));
        stubLookupIndex();
        when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(planService.createPlanForMigration(
                        eq("2026"),
                        eq(com.kdb.it.domain.budget.plan.PlanType.ADJUSTMENT.code()),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn("PLN-2026-0009");
        when(budgetRateApplicationService.applyItemRates(any()))
                .thenReturn(new BudgetWorkDto.ApplyResponse("ok", 0, null));

        MigrationImportService service = serviceWith(List.of(planAdapter));
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(new MigrationDto.NormalizedRow(2, Map.of())))),
                        List.of()),
                "999999");
    }
}
