package com.kdb.it.domain.migration.service;

import com.kdb.it.common.approval.service.ApprovalStamper;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostRepresentativeSelector;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.PlanType;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetRateApplicationService;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.RowDecision;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.adapter.AdapterContext;
import com.kdb.it.domain.migration.service.adapter.AdapterOutput;
import com.kdb.it.domain.migration.service.adapter.AllocationIntent;
import com.kdb.it.domain.migration.service.adapter.PlanIntent;
import com.kdb.it.domain.migration.service.adapter.SheetAdapter;
import com.kdb.it.exception.CustomGeneralException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편성요구서 종합·하반기 조정을 기존 원장에 편성하는 흐름을 조율합니다 (설계 §6.2).
 *
 * <p><b>이 화면은 원장을 만드는 화면이 아닙니다.</b> 부점이 제출한 편성요청서가 이미 {@code BPROJM}·{@code BITEMM}·{@code BCOSTM}을
 * 만들어 두었고, 여기서는 종합본의 금액을 그 원장에 <b>편성금액</b>으로 반영합니다. 흐름은 세 단계입니다 — 행을 원장에 붙이고(매칭), 종합본 금액을 요청 품목에
 * 나누고(배분), 그 결과를 실효 편성률로 환산해 한 번에 적용(편성)합니다. 원장을 새로 만드는 것은 관리자가 그 행에 {@code CREATE_NEW}를 명시한
 * 경우뿐입니다.
 *
 * <p>dry-run 결과를 서버에 보관하지 않으므로 확정 반영은 클라이언트가 보낸 값을 신뢰하지 않고 같은 계산({@link #buildPlan})을 다시 돌립니다.
 * BLOCKER가 하나라도 있으면 아무 원장도 쓰지 않고 실패합니다 — {@link #commit}은 재계산 결과에 BLOCKER가 남으면 원장을 쓰는 코드에 닿기 전에 예외를
 * 던집니다. {@link SheetAdapter}는 이름 해석에 실패한 값을 "이미 코드값"으로 가정하고 넘어가는데, 이는 검증이 먼저 걸러냈다는 전제 위에서만 안전합니다.
 *
 * <p>반영 순서가 중요합니다({@link #ADAPTER_ORDER}). 부문계획(하반기 조정)이 마지막이라 같은 사업이 자본예산 시트와 부문계획 시트 양쪽에 나오면 더 최신
 * 판단인 하반기 조정이 이깁니다. 편성행은 {@code applyItemRates} 단일 호출이 전담합니다 — 이 메서드는 연도 전체를 재작성하고 삭제 이력을 남기지 않으므로
 * 두 번 호출하면 첫 결과가 흔적 없이 사라집니다.
 */
@Service
@Slf4j
public class MigrationImportService {

    /** 어댑터 처리 순서 (§7). 부문계획이 마지막이라 하반기 조정이 종합본 편성률을 덮습니다. */
    private static final List<SheetKind> ADAPTER_ORDER =
            List.of(
                    SheetKind.COST,
                    SheetKind.CAPITAL_PROJECT,
                    SheetKind.DELEGATED_BUDGET,
                    SheetKind.PLAN_ADJUSTMENT);

    /**
     * 계획 계산에서 어댑터에 넘기는 작성자 사번입니다.
     *
     * <p>{@link #buildPlan}은 원장을 쓰지 않고 배분 의도와 진단만 읽으므로 작성자 값이 결과에 관여하지 않습니다. 실제 원장을 만드는 단계는 업로드 사용자
     * 사번으로 어댑터를 다시 돌립니다.
     */
    private static final String PLANNING_ACTOR = "PREVIEW";

    private final Map<SheetKind, SheetAdapter> adapters = new EnumMap<>(SheetKind.class);
    private final MigrationValidator validator;
    private final MigrationMatchDiagnostics matchDiagnostics;
    private final MigrationAllocationPlanner allocationPlanner;
    private final MigrationYearSnapshot yearSnapshot;
    private final OrgIdentityResolver orgIdentityResolver;
    private final MigrationIoeCatalogReader catalogReader;
    private final ApprovalStamper approvalStamper;
    private final CostService costService;
    private final CostRepository costRepository;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final BudgetRateApplicationService budgetRateApplicationService;
    private final PlanService planService;
    private final PlanAdjustmentProgressRecorder progressRecorder;
    private final com.kdb.it.domain.budget.common.security.ApprovalWriteGuard approvalWriteGuard;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * 어댑터를 시트 종류별로 색인해 둡니다.
     *
     * @param sheetAdapters 등록된 어댑터 전체 (Spring이 주입)
     */
    public MigrationImportService(
            List<SheetAdapter> sheetAdapters,
            MigrationValidator validator,
            MigrationMatchDiagnostics matchDiagnostics,
            MigrationAllocationPlanner allocationPlanner,
            MigrationYearSnapshot yearSnapshot,
            OrgIdentityResolver orgIdentityResolver,
            MigrationIoeCatalogReader catalogReader,
            ApprovalStamper approvalStamper,
            CostService costService,
            CostRepository costRepository,
            ProjectService projectService,
            ProjectRepository projectRepository,
            BudgetRateApplicationService budgetRateApplicationService,
            PlanService planService,
            PlanAdjustmentProgressRecorder progressRecorder,
            com.kdb.it.domain.budget.common.security.ApprovalWriteGuard approvalWriteGuard,
            jakarta.persistence.EntityManager entityManager) {
        for (SheetAdapter adapter : sheetAdapters) {
            adapters.put(adapter.supports(), adapter);
        }
        this.validator = validator;
        this.matchDiagnostics = matchDiagnostics;
        this.allocationPlanner = allocationPlanner;
        this.yearSnapshot = yearSnapshot;
        this.orgIdentityResolver = orgIdentityResolver;
        this.catalogReader = catalogReader;
        this.approvalStamper = approvalStamper;
        this.costService = costService;
        this.costRepository = costRepository;
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.budgetRateApplicationService = budgetRateApplicationService;
        this.planService = planService;
        this.progressRecorder = progressRecorder;
        this.approvalWriteGuard = approvalWriteGuard;
        this.entityManager = entityManager;
    }

    /**
     * 올린 시트를 매칭·배분까지 계산해 행별 진단을 돌려줍니다. 아무것도 저장하지 않습니다.
     *
     * @param request 시트 목록과 보정값(행 결정 포함)
     * @return 진단 목록과 요약
     * @throws IllegalArgumentException 시트 목록이 비었거나, 지원하지 않는 시트 종류이거나, 예산연도가 섞인 경우
     */
    @Transactional(readOnly = true)
    public MigrationDto.DryRunResponse dryRun(MigrationDto.DryRunRequest request) {
        requireSupported(request.sheets());
        String bseYy = request.sheets().get(0).bseYy();
        Map<String, String> overrides = foldOverrides(request.overrides());
        MigrationYearSnapshot.Data snapshot = yearSnapshot.load(bseYy);
        MigrationLookupIndex index = lookupIndex();

        Plan plan = buildPlan(request.sheets(), index, snapshot, overrides, Map.of());

        int totalRows = request.sheets().stream().mapToInt(s -> s.rows().size()).sum();
        int blockers = (int) blockerCount(plan.diagnostics());
        return new MigrationDto.DryRunResponse(
                plan.diagnostics(),
                new MigrationDto.Summary(totalRows, blockers, plan.diagnostics().size() - blockers),
                MigrationOverrideCatalogs.of(request.sheets(), index));
    }

    /**
     * 보정값과 행 결정을 반영해 편성금액을 계산하고 원장에 적용합니다.
     *
     * <p>전 과정이 하나의 트랜잭션입니다. 검증에서 BLOCKER가 남거나 어느 단계에서든 예외가 나면 전부 롤백됩니다.
     *
     * @param request 시트 목록과 보정값(행 결정 포함)
     * @param actorEno 업로드 사용자 사번
     * @return 반영 건수와 새로 만든 관리번호
     * @throws CustomGeneralException 검증에 BLOCKER가 남은 경우, 또는 이관 직후 생성한 사업을 다시 찾지 못한 경우
     * @throws IllegalArgumentException 시트 목록이 비었거나, 지원하지 않는 시트 종류이거나, 예산연도가 섞인 경우
     */
    @Transactional
    public MigrationDto.CommitResponse commit(MigrationDto.CommitRequest request, String actorEno) {
        requireSupported(request.sheets());
        String bseYy = request.sheets().get(0).bseYy();
        Map<String, String> overrides = foldOverrides(request.overrides());
        MigrationYearSnapshot.Data snapshot = yearSnapshot.load(bseYy);
        MigrationLookupIndex index = lookupIndex();

        // 1단계: 매칭·배분·검증을 dry-run과 같은 계산으로 다시 돌린다.
        // BLOCKER가 남으면 원장에 손대기 전에 예외를 던진다.
        Plan plan = buildPlan(request.sheets(), index, snapshot, overrides, Map.of());
        long blockers = blockerCount(plan.diagnostics());
        if (blockers > 0) {
            throw new CustomGeneralException(
                    "해결되지 않은 오류가 " + blockers + "건 있어 반영할 수 없습니다. 미리보기에서 보정해 주세요.");
        }

        AdapterContext ctx = new AdapterContext(bseYy, index, snapshot, overrides, actorEno);

        // 2단계: CREATE_NEW로 결정한 행만 원장을 만든다.
        // 생성 결과 PK를 (시트, 행)에 기록해 두고 3단계의 배분 재계산에서 그 행의 편성 대상으로 쓴다.
        List<String> createdIds = new ArrayList<>();
        List<PlanIntent> planIntents = new ArrayList<>();
        Map<SheetKind, Map<Integer, String>> createdPkByRow = new EnumMap<>(SheetKind.class);
        int costCount = 0;
        int projectCount = 0;
        int itemCount = 0;

        for (SheetKind kind : ADAPTER_ORDER) {
            for (MigrationDto.SheetPayload sheet : request.sheets()) {
                if (sheet.kind() != kind) {
                    continue;
                }
                SheetAdapter adapter =
                        Objects.requireNonNull(adapters.get(kind), "이관 시트 어댑터가 없습니다: " + kind);
                AdapterOutput output = adapter.adapt(sheet, ctx);
                planIntents.addAll(output.plans());
                Set<Integer> createRows = plan.createNewRows().getOrDefault(kind, Set.of());
                List<AllocationIntent> intents = output.allocations();

                for (int i = 0; i < intents.size(); i++) {
                    AllocationIntent intent = intents.get(i);
                    if (!createRows.contains(intent.excelRow())) {
                        continue;
                    }
                    // 인덱스 접근이 안전한 근거: buildPlan이 생성요청이 실제로 있는 행만 createNewRows에
                    // 넣는다. 그 판정은 PLANNING_ACTOR로 만든 AdapterOutput에 대해 돌았고 여기서 꺼내는
                    // 것은 actorEno로 만든 다른 AdapterOutput이지만, 어댑터는 작성자 사번을 생성요청의
                    // 담당자 필드에만 쓰고 목록 길이는 시트 행(위임예산은 부점 그룹)만으로 정하므로 두
                    // 컨텍스트에서 costs/projects/allocations의 길이와 순서가 같다.
                    String createdPk;
                    if ("BCOSTM".equals(intent.orcTb())) {
                        createdPk = createCost(output.costs().get(i), bseYy, actorEno);
                        costCount++;
                    } else {
                        ProjectDto.CreateRequest projectRequest = output.projects().get(i);
                        createdPk = createProject(projectRequest, bseYy, actorEno);
                        projectCount++;
                        itemCount +=
                                projectRequest.getItems() == null
                                        ? 0
                                        : projectRequest.getItems().size();
                    }
                    createdIds.add(createdPk);
                    createdPkByRow
                            .computeIfAbsent(kind, ignored -> new LinkedHashMap<>())
                            .put(intent.excelRow(), createdPk);
                }
            }
        }

        // 3단계: 새로 만든 원장까지 포함해 스냅샷을 다시 읽고 배분을 완성한다.
        // 방금 만든 품목은 1단계 시점의 스냅샷에 없어 실효 편성률을 계산할 수 없었다.
        Map<String, Map<String, BigDecimal>> allocationsByPk = copyAllocations(plan);
        if (!createdIds.isEmpty()) {
            MigrationYearSnapshot.Data refreshed = yearSnapshot.load(bseYy);
            Plan replanned =
                    buildPlan(request.sheets(), index, refreshed, overrides, createdPkByRow);
            // 재계산 결과를 통째로 갈아 끼우지 않고 덮어쓰기로 합친다. 새 원장이 늘어난 만큼 완화 매칭의
            // 후보가 늘 수 있어 1단계에서 매칭됐던 행이 재계산에서 AMBIGUOUS로 흔들릴 수 있는데,
            // 그때 1단계의 배분까지 함께 사라지면 그 원장이 조용히 기본 편성률로 떨어진다.
            replanned
                    .allocationsByPk()
                    .forEach(
                            (pk, rates) ->
                                    allocationsByPk
                                            .computeIfAbsent(pk, ignored -> new LinkedHashMap<>())
                                            .putAll(rates));
            long replanBlockers = blockerCount(replanned.diagnostics());
            if (replanBlockers > 0) {
                // 1단계에서 BLOCKER가 없었는데 원장을 만든 뒤 생겼다는 뜻이라 반영을 되돌리지는 않되
                // (이미 만든 원장은 요청대로 만든 것이다) 매칭이 흔들린 흔적을 남긴다.
                log.warn("원장 생성 후 배분 재계산에서 BLOCKER가 {}건 발생했습니다 (예산연도={})", replanBlockers, bseYy);
            }
            snapshot = refreshed;
        }

        // 3.5단계: 매칭된 전산업무비 원장의 사업코드가 비어 있으면 종합본 값으로 채운다 (§4.1).
        fillCostBudgetUnitCodes(request.sheets(), plan, snapshot, index, overrides);

        // 4단계: 하반기 조정 계획 문서. 요청 품목(BITEMM)은 건드리지 않는다.
        AdjustmentPlan adjustmentPlan =
                planIntents.isEmpty()
                        ? AdjustmentPlan.none()
                        : createAdjustmentPlan(planIntents, snapshot, bseYy);

        // 5단계: 편성률 단일 적용. items에는 그 연도의 모든 사업 + 모든 전산업무비를 담는다.
        // applyItemRates가 연도 전체를 재작성하므로 빠진 것은 되살아나지 않는다(§5.3).
        BudgetWorkDto.ApplyResponse applied =
                budgetRateApplicationService.applyItemRates(
                        new BudgetWorkDto.ItemApplyRequest(
                                bseYy, itemRates(snapshot, allocationsByPk)));

        return new MigrationDto.CommitResponse(
                costCount,
                projectCount,
                itemCount,
                applied.totalRecords(),
                skippedRateCount(snapshot, allocationsByPk),
                adjustmentPlan.skippedCount(),
                adjustmentPlan.planReqDocNo(),
                createdIds);
    }

    /**
     * 배분은 됐지만 편성률 적용 목록에 실리지 못한 원장 수를 셉니다 (MIG-06).
     *
     * <p>{@link #itemRates}는 스냅샷에 있는 원장만 담습니다. 관리자가 보낸 결정이 그 연도에 없는 PK를 가리키면(오타·다른 연도 원장) 그 행의 배분은
     * 조용히 사라졌고, 사용자는 로그를 봐야 알 수 있었습니다.
     *
     * @param snapshot 편성 대상 연도 스냅샷
     * @param allocationsByPk 이번 반영이 계산한 원장 PK → 비목코드별 편성률
     * @return 스냅샷에 없는 PK 수
     */
    private static int skippedRateCount(
            MigrationYearSnapshot.Data snapshot,
            Map<String, Map<String, BigDecimal>> allocationsByPk) {
        Set<String> known = new LinkedHashSet<>(snapshot.allProjectNos());
        known.addAll(snapshot.allCostNos());
        int skipped = 0;
        for (String pk : allocationsByPk.keySet()) {
            if (!known.contains(pk)) {
                skipped++;
            }
        }
        return skipped;
    }

    /**
     * 조정 계획 생성 결과입니다.
     *
     * @param planReqDocNo 계획요청문서번호. 대상 사업을 하나도 찾지 못했거나 부문계획 시트가 없으면 null
     * @param skippedCount 대상 사업을 찾지 못해 계획에서 빠진 조정 의도 수 (MIG-06)
     */
    private record AdjustmentPlan(String planReqDocNo, int skippedCount) {

        /** 부문계획 시트를 올리지 않은 경우. 건너뛴 것도 없습니다. */
        static AdjustmentPlan none() {
            return new AdjustmentPlan(null, 0);
        }
    }

    /**
     * 반영 계획입니다. dry-run과 commit이 같은 계산을 공유합니다.
     *
     * @param diagnostics 진단 전체
     * @param createNewRows 시트별 {@code CREATE_NEW} 결정 행 번호. 원장 생성 대상이자 {@link
     *     MigrationValidator#validate} 의 생성 전용 검증 범위입니다
     * @param allocationsByPk 원장 PK → 비목코드별 실효 편성률
     * @param matchedPkByRow 시트별 (행 번호 → 매칭된 기존 원장 PK). 새로 만든 원장은 담지 않습니다
     */
    private record Plan(
            List<MigrationDto.CellDiagnostic> diagnostics,
            Map<SheetKind, Set<Integer>> createNewRows,
            Map<String, Map<String, BigDecimal>> allocationsByPk,
            Map<SheetKind, Map<Integer, String>> matchedPkByRow) {}

    /**
     * 시트를 어댑터에 태워 매칭·배분·검증을 계산합니다. 원장을 쓰지 않으므로 dry-run과 commit이 그대로 공유합니다.
     *
     * <p>배분 결과는 원장 PK 단위로 합칩니다 — 한 사업이 자본예산 시트와 부문계획 시트 양쪽에 나오면 <b>나중에 처리한 시트가 이깁니다</b>. {@link
     * #ADAPTER_ORDER}가 부문계획을 마지막에 두므로 하반기 조정의 확정금액이 종합본의 조정비율 결과를 덮습니다. 6월 조정이 더 최신 판단이라 이 방향이
     * 맞습니다.
     *
     * @param sheets 올린 시트 목록
     * @param index 조직·비목·환율 조회 인덱스
     * @param snapshot 예산연도 기존 상태
     * @param overrides 보정값 맵(행 결정 포함)
     * @param createdPkByRow 이미 만든 원장의 (시트, 행) → PK. 원장 생성 전에는 빈 맵이며, 이 맵에 담긴 행은 {@code CREATE_NEW}
     *     결정이어도 그 PK에 배분합니다
     * @return 진단·생성 대상·배분 결과
     */
    private Plan buildPlan(
            List<MigrationDto.SheetPayload> sheets,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides,
            Map<SheetKind, Map<Integer, String>> createdPkByRow) {
        List<MigrationDto.CellDiagnostic> diagnostics = new ArrayList<>();
        Map<SheetKind, Set<Integer>> createNewRows = new EnumMap<>(SheetKind.class);
        Map<SheetKind, Map<Integer, String>> matchedPkByRow = new EnumMap<>(SheetKind.class);
        Map<String, Map<String, BigDecimal>> allocationsByPk = new LinkedHashMap<>();
        AdapterContext ctx =
                new AdapterContext(snapshot.bseYy(), index, snapshot, overrides, PLANNING_ACTOR);

        for (SheetKind kind : ADAPTER_ORDER) {
            for (MigrationDto.SheetPayload sheet : sheets) {
                if (sheet.kind() != kind) {
                    continue;
                }
                SheetAdapter adapter =
                        Objects.requireNonNull(adapters.get(kind), "이관 시트 어댑터가 없습니다: " + kind);
                AdapterOutput output = adapter.adapt(sheet, ctx);
                List<AllocationIntent> intents = output.allocations();
                for (int i = 0; i < intents.size(); i++) {
                    AllocationIntent intent = intents.get(i);
                    MigrationMatchDiagnostics.Resolved resolved =
                            matchDiagnostics.resolve(sheet, intent, snapshot, overrides);
                    diagnostics.addAll(resolved.diagnostics());

                    String pk;
                    if (resolved.action() == RowDecision.Kind.CREATE_NEW) {
                        if (!hasCreateRequest(output, intent, i)) {
                            // 부문계획 시트처럼 원장 생성요청을 내지 않는 어댑터의 행이다.
                            // 만들 것이 없으므로 편성 대상에서도 뺀다 (SKIP과 같은 결과).
                            // 후보 목록이 이 결정을 더 이상 제시하지 않지만(SheetKind.canCreateLedger),
                            // 보정값은 클라이언트가 임의로 보낼 수 있으므로 그 행의 조정이 통째로
                            // 사라진다는 사실을 로그가 아니라 화면에 남긴다.
                            log.warn(
                                    "원장을 만들 수 없는 시트에 CREATE_NEW 결정이 왔습니다 — 편성 대상에서 제외합니다"
                                            + " (시트={}, 행={})",
                                    kind,
                                    intent.excelRow());
                            diagnostics.add(
                                    matchDiagnostics.createNotSupported(sheet, intent.excelRow()));
                            continue;
                        }
                        createNewRows
                                .computeIfAbsent(kind, ignored -> new LinkedHashSet<>())
                                .add(intent.excelRow());
                        // MIG-23① 새로 만드는 원장에는 일반관리비 목표액을 담을 품목이 없다. 자본 3열만
                        // 품목으로 만들어지므로 그 열의 금액은 조용히 빠진다 — 화면에 경고로 남긴다.
                        if (intent.targetByColumn().containsKey("generalAmount")) {
                            diagnostics.add(
                                    matchDiagnostics.generalAmountNotCreatable(
                                            sheet, intent.excelRow()));
                        }
                        pk = createdPkByRow.getOrDefault(kind, Map.of()).get(intent.excelRow());
                        if (pk == null) {
                            // 아직 만들기 전이라 배분할 품목이 스냅샷에 없다. 생성 후 재계산이 채운다.
                            continue;
                        }
                    } else if (resolved.action() == RowDecision.Kind.MATCH) {
                        pk = resolved.pk();
                        matchedPkByRow
                                .computeIfAbsent(kind, ignored -> new LinkedHashMap<>())
                                .put(intent.excelRow(), pk);
                    } else {
                        continue; // SKIP 또는 미결정
                    }

                    diagnostics.addAll(
                            matchDiagnostics.checkAllocation(
                                    sheet, intent, pk, snapshot, allocationPlanner));
                    allocationsByPk
                            .computeIfAbsent(pk, ignored -> new LinkedHashMap<>())
                            .putAll(ratesOf(intent, pk, snapshot));
                }
                for (MigrationDto.NormalizedRow row : sheet.rows()) {
                    diagnostics.addAll(matchDiagnostics.checkRateReconcile(sheet, row, overrides));
                }
            }
        }

        diagnostics.addAll(validator.validate(sheets, index, snapshot, overrides, createNewRows));
        return new Plan(diagnostics, createNewRows, allocationsByPk, matchedPkByRow);
    }

    /**
     * 그 행에 대응하는 원장 생성요청이 있는지 확인합니다.
     *
     * <p>{@code AdapterOutput}은 배분 의도와 생성요청을 인덱스 평행으로 냅니다. 부문계획 어댑터처럼 생성요청을 아예 만들지 않는 시트도 있으므로,
     * {@code CREATE_NEW} 결정을 그대로 믿고 인덱스로 꺼내면 범위를 벗어납니다.
     */
    private boolean hasCreateRequest(AdapterOutput output, AllocationIntent intent, int index) {
        return "BCOSTM".equals(intent.orcTb())
                ? output.costs().size() > index
                : output.projects().size() > index;
    }

    /**
     * 배분 의도를 비목코드별 실효 편성률로 환산합니다.
     *
     * <p>같은 비목코드가 두 번 담기지 않습니다. 정보화사업 의도가 쓰는 네 컬럼의 대상 집합은 서로소입니다 — 비목그룹({@code GROUP_DEV}·{@code
     * GROUP_HW}·{@code GROUP_SW})이 서로소이고 {@code generalAmount}는 그 셋의 여집합입니다. 위임예산 의도는 {@code
     * costAmount} 한 컬럼뿐이라(대상은 그 사업의 모든 품목) 겹칠 상대가 없습니다. 한 대상 집합 안의 품목은 모두 공통 실효율을 받으므로 값도 같습니다.
     *
     * @param intent 배분 의도
     * @param pk 편성 대상 원장 PK
     * @param snapshot 연도 스냅샷
     * @return 비목코드 → 실효 편성률. 배분할 품목이 없거나 배분에 실패한 그룹은 키가 없습니다(진단이 이미 막았습니다)
     */
    private Map<String, BigDecimal> ratesOf(
            AllocationIntent intent, String pk, MigrationYearSnapshot.Data snapshot) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if ("BCOSTM".equals(intent.orcTb())) {
            MigrationYearSnapshot.CostRef ref = snapshot.costOf(pk);
            if (ref == null) {
                return out;
            }
            MigrationAllocationPlanner.Allocation allocation =
                    allocationPlanner.allocate(
                            List.of(
                                    new MigrationYearSnapshot.RequestItem(
                                            ref.costBgNo(), ref.bgSno(), ref.ioeC(), ref.amount())),
                            intent.targetByColumn().get("costAmount"));
            if (allocation instanceof MigrationAllocationPlanner.Allocation.Allocated allocated
                    && ref.ioeC() != null) {
                out.put(ref.ioeC(), allocated.effectiveRate());
            }
            return out;
        }

        List<MigrationYearSnapshot.RequestItem> all = snapshot.itemsOfProject(pk);
        for (Map.Entry<String, BigDecimal> entry : intent.targetByColumn().entrySet()) {
            List<MigrationYearSnapshot.RequestItem> items =
                    MigrationAllocationPlanner.itemsForColumn(entry.getKey(), all);
            if (items.isEmpty()) {
                continue;
            }
            if (allocationPlanner.allocate(items, entry.getValue())
                    instanceof MigrationAllocationPlanner.Allocation.Allocated allocated) {
                for (MigrationAllocationPlanner.ItemAllocation item : allocated.items()) {
                    if (item.ioeC() != null) {
                        out.put(item.ioeC(), item.rate());
                    }
                }
            }
        }
        return out;
    }

    /**
     * 편성률 적용 목록을 만듭니다.
     *
     * <p>그 연도의 <b>모든</b> 사업·전산업무비를 담습니다. 이번 반영이 건드리지 않은 항목은 기존 편성률을 그대로 실어 유지하고, 건드린 항목만 새 비목별 편성률로
     * 덮습니다. 빠진 항목은 {@code applyItemRates}의 연도 전량 재작성에서 되살아나지 않고, 벌크 논리삭제라 {@code BBUGT_L}에도 흔적이 남지
     * 않습니다.
     *
     * <p>{@code assetDupRt}·{@code costDupRt}에 null을 넣으면 {@code applyItemRates}가 기본값 100으로 떨어지지만,
     * {@code ioeRates}가 그 원장에서 편성률을 아는 모든 비목을 덮으므로 실제로 100이 쓰이는 경우는 기존 편성행도 없고 이번 배분도 없는 비목뿐입니다.
     *
     * @param snapshot 편성 대상 연도 스냅샷 (원장을 새로 만들었다면 다시 읽은 것)
     * @param allocationsByPk 이번 반영이 계산한 원장 PK → 비목코드별 편성률
     * @return 편성률 적용 항목. 스냅샷에 없는 PK는 담기지 않습니다
     */
    private List<BudgetWorkDto.ItemRate> itemRates(
            MigrationYearSnapshot.Data snapshot,
            Map<String, Map<String, BigDecimal>> allocationsByPk) {
        List<BudgetWorkDto.ItemRate> out = new ArrayList<>();
        for (String projectNo : snapshot.allProjectNos()) {
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            for (MigrationYearSnapshot.RequestItem item : snapshot.itemsOfProject(projectNo)) {
                BigDecimal existing = snapshot.existingItemRateByItemNo().get(item.gclMngNo());
                if (existing != null && item.ioeC() != null) {
                    putPreservedRate(rates, item.ioeC(), existing, projectNo);
                }
            }
            rates.putAll(allocationsByPk.getOrDefault(projectNo, Map.of()));
            out.add(new BudgetWorkDto.ItemRate("BPROJM", projectNo, null, null, rates));
        }
        for (String costNo : snapshot.allCostNos()) {
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            MigrationYearSnapshot.CostRef ref = snapshot.costOf(costNo);
            BigDecimal existing = snapshot.existingCostRateOf(costNo);
            if (existing != null && ref != null && ref.ioeC() != null) {
                rates.put(ref.ioeC(), existing);
            }
            rates.putAll(allocationsByPk.getOrDefault(costNo, Map.of()));
            out.add(new BudgetWorkDto.ItemRate("BCOSTM", costNo, null, null, rates));
        }
        return out;
    }

    /**
     * 기존 편성률을 비목코드 칸에 보존합니다.
     *
     * <p>{@code ioeRates}는 비목코드 단위라 한 사업에 같은 비목의 품목이 둘 있고 편성률이 서로 다르면 한 값만 남습니다. 이번 반영이 계산한 값은 그룹
     * 공통 실효율이라 언제나 같지만, 이관 이전부터 있던 편성률은 품목마다 다를 수 있습니다(예산작업 화면이 품목 단위로 저장합니다).
     *
     * <p>그럴 때 <b>작은 쪽</b>을 남깁니다. 나중 값이 조용히 이기게 두면 편성률이 올라갈 수도 내려갈 수도 있는데, 이관은 편성금액을 조용히 <b>늘리지
     * 않는</b> 쪽이 안전합니다. 어긋난 사실 자체는 로그로 남겨 예산담당자가 품목별로 다시 지정할 수 있게 합니다.
     */
    private void putPreservedRate(
            Map<String, BigDecimal> rates, String ioeC, BigDecimal existing, String orcPkVl) {
        BigDecimal previous = rates.putIfAbsent(ioeC, existing);
        if (previous == null || previous.compareTo(existing) == 0) {
            return;
        }
        BigDecimal kept = previous.min(existing);
        rates.put(ioeC, kept);
        log.warn(
                "같은 비목의 기존 편성률이 품목마다 달라 낮은 쪽을 유지합니다 (원장={}, 비목={}, {}/{} → {})",
                orcPkVl,
                ioeC,
                previous,
                existing,
                kept);
    }

    /**
     * 매칭된 전산업무비 원장의 빈 사업코드를 종합본 값으로 채웁니다 (§4.1).
     *
     * <p>편성요청서 양식에 사업코드 열이 없어 1단계가 만든 {@code BCOSTM}은 대부분 이 값이 비어 있는데, 예산 집계가 사업코드로 묶이므로 비워 두면 집계에서
     * 빠집니다. 이미 값이 있으면 건드리지 않습니다 — 부서가 적어 낸 값을 종합본이 조용히 바꾸지 않게 합니다.
     *
     * <p>여러 버전 중 대표 행({@code LST_YN='Y'} 우선)만 채웁니다. 과거 버전은 그 시점의 기록이라 소급해 바꾸지 않습니다.
     *
     * <p><b>코드표에 있는 값만 씁니다.</b> {@code MigrationValidator}가 이 값을 {@code validateAlways}에서 검사하므로 여기
     * 닿는 값은 이미 해석된 값이지만, 그 검사는 코드 카탈로그가 비면(코드그룹 미적재 등) 판정 근거가 없어 그대로 통과시킵니다. 그 구멍으로 엑셀 원문이 흘러들면
     * {@code BG_UNT_ABUS_C}가 3자라 flush에서 {@code ORA-12899}가 나거나, 길이가 맞는 오타가 조용히 저장돼 그 전산업무비가 엉뚱한 예산
     * 집계 버킷에 들어갑니다. 쓰기 직전에 한 번 더 막고, 막힌 값은 로그로 남깁니다 — 채우지 않으면 집계에서 빠질 뿐 오염되지는 않습니다.
     */
    private void fillCostBudgetUnitCodes(
            List<MigrationDto.SheetPayload> sheets,
            Plan plan,
            MigrationYearSnapshot.Data snapshot,
            MigrationLookupIndex index,
            Map<String, String> overrides) {
        Map<String, String> requestedCodes = new java.util.TreeMap<>();
        Map<Integer, String> matched = plan.matchedPkByRow().getOrDefault(SheetKind.COST, Map.of());
        if (matched.isEmpty()) {
            return;
        }
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() != SheetKind.COST) {
                continue;
            }
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                String costNo = matched.get(row.excelRow());
                if (costNo == null || snapshot.bgUntAbusCOf(costNo) != null) {
                    continue;
                }
                String abusCode =
                        MigrationDiagnostics.cell(row, "abusCode", overrides, sheet).trim();
                if (abusCode.isBlank()) {
                    continue;
                }
                if (!index.abusUnitNameByCode().containsKey(abusCode)) {
                    log.warn(
                            "코드표에 없는 사업코드라 전산업무비에 채우지 않습니다 (전산업무비={}, 행={}, 값='{}')",
                            costNo,
                            row.excelRow(),
                            abusCode);
                    continue;
                }
                requestedCodes.putIfAbsent(costNo, abusCode);
            }
        }
        for (var entry : requestedCodes.entrySet()) {
            Bcostm target =
                    CostRepresentativeSelector.pick(
                            costRepository.findCurrentVersionsForUpdate(entry.getKey()));
            // 연도 스냅샷이 먼저 읽은 영속 엔티티도 잠금 뒤 최신 DB 값으로 다시 읽는다.
            entityManager.refresh(target);
            approvalWriteGuard.verifyWritable(
                    "BCOSTM", target.getCostBgNo(), target.getBgSno(), "수정");
            target.fillBudgetUnitCodeIfAbsent(entry.getValue());
        }
    }

    /**
     * 전산업무비 원장을 만들고 결재 받이를 찍습니다.
     *
     * <p>결재 받이의 원천 일련번호({@code fntTbCrySno})는 {@code BbugtmRepositoryImpl}의 집계 조인이 {@code
     * Cappla.fntTbCrySno = Bcostm.bgSno}로 맞춰 보므로, 하드코딩한 1이 아니라 방금 저장된 행의 실제 {@code bgSno}를 다시 읽어
     * 넘깁니다.
     *
     * @return 새로 만든 전산업무비관리번호
     */
    private String createCost(CostDto.CreateRequest request, String bseYy, String actorEno) {
        String costNo = costService.createCost(request, true);
        Bcostm created =
                CostRepresentativeSelector.pick(costRepository.findByCostBgNoAndDelYn(costNo, "N"));
        approvalStamper.stamp(
                "BCOSTM", costNo, created.getBgSno(), bseYy + "년 전산일반관리비 이관", actorEno, bseYy);
        return costNo;
    }

    /**
     * 정보화사업 원장을 만들고 결재 받이를 찍습니다.
     *
     * <p>원천 일련번호를 다시 읽는 이유는 {@link #createCost}와 같습니다({@code Cappla.fntTbCrySno = Bprojm.sno}).
     *
     * @return 새로 만든 사업관리번호
     * @throws CustomGeneralException 생성 직후 그 사업을 다시 찾지 못한 경우
     */
    private String createProject(ProjectDto.CreateRequest request, String bseYy, String actorEno) {
        String projectNo = projectService.createProject(request, true);
        Bprojm created =
                projectRepository
                        .findByAbusMngNoAndDelYn(projectNo, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "이관 직후 생성된 사업을 다시 찾지 못했습니다: " + projectNo));
        approvalStamper.stamp(
                "BPROJM", projectNo, created.getSno(), bseYy + "년 정보화사업 이관", actorEno, bseYy);
        return projectNo;
    }

    /**
     * 조정 계획({@code BPLANM} + {@code BPLANA})을 만듭니다.
     *
     * <p>{@code PlanIntent}는 이 오케스트레이션 서비스만 아는 타입이므로, {@code PlanService}가 이 도메인을 역참조하지 않도록 여기서 원시
     * 타입(사업관리번호·자본예산 합계·스냅샷 필드 맵)으로 분해해 넘깁니다.
     *
     * @param snapshot 연도 스냅샷. 원장을 새로 만들었다면 그 사업까지 담고 있는 최신 스냅샷이어야 합니다
     * @return 계획요청문서번호와 건너뛴 건수. 대상 사업을 하나도 찾지 못하면 문서번호는 null
     */
    private AdjustmentPlan createAdjustmentPlan(
            List<PlanIntent> intents, MigrationYearSnapshot.Data snapshot, String bseYy) {
        List<String> projectNos = new ArrayList<>();
        List<BigDecimal> capitalAmounts = new ArrayList<>();
        List<BigDecimal> generalAmounts = new ArrayList<>();
        Map<String, Map<String, String>> snapshotFieldsByProject = new LinkedHashMap<>();
        int skipped = 0;
        for (PlanIntent intent : intents) {
            String projectNo = snapshot.projectNoByName(intent.normalizedProjectName());
            if (projectNo == null) {
                log.warn("부문계획 조정 대상 사업을 찾지 못해 건너뜁니다: {}", intent.normalizedProjectName());
                skipped++;
                continue;
            }
            projectNos.add(projectNo);
            capitalAmounts.add(
                    sumAmounts(intent.devAmount(), intent.hwAmount(), intent.swAmount()));
            generalAmounts.add(sumAmounts(intent.generalAmount()));
            snapshotFieldsByProject.put(projectNo, intent.snapshotFields());
            progressRecorder.record(projectNo, intent.snapshotFields());
        }
        if (projectNos.isEmpty()) {
            return new AdjustmentPlan(null, skipped);
        }
        return new AdjustmentPlan(
                planService.createPlanForMigration(
                        bseYy,
                        // 화면 표시명이 아니라 저장 코드값을 넣는다. IT_PTL_PLN_TP_C는
                        // VARCHAR2(2 BYTE)라 라벨('조정' 6바이트)은 ORA-12899로 실패하고,
                        // 저장돼도 PlanEvaluationService가 "02"와 비교해 조정계획으로 보지 않는다.
                        PlanType.ADJUSTMENT.code(),
                        projectNos,
                        capitalAmounts,
                        generalAmounts,
                        snapshotFieldsByProject),
                skipped);
    }

    /** 배분 결과를 이후 단계에서 합칠 수 있게 깊은 복사합니다. */
    private Map<String, Map<String, BigDecimal>> copyAllocations(Plan plan) {
        Map<String, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        plan.allocationsByPk().forEach((pk, rates) -> out.put(pk, new LinkedHashMap<>(rates)));
        return out;
    }

    /** BLOCKER 진단 수입니다. */
    private static long blockerCount(List<MigrationDto.CellDiagnostic> diagnostics) {
        return diagnostics.stream()
                .filter(d -> d.severity() == MigrationDto.Severity.BLOCKER)
                .count();
    }

    /** null-safe 금액 합산. */
    private static BigDecimal sumAmounts(BigDecimal... amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return total;
    }

    /** 보정값 목록을 {@code MigrationValidator.overrideKey} 키의 맵으로 접습니다. */
    private Map<String, String> foldOverrides(List<MigrationDto.CellOverride> overrides) {
        Map<String, String> out = new LinkedHashMap<>();
        for (MigrationDto.CellOverride override : overrides) {
            out.put(
                    MigrationValidator.overrideKey(
                            override.sheet(), override.excelRow(), override.column()),
                    override.value());
        }
        return out;
    }

    private MigrationLookupIndex lookupIndex() {
        return new MigrationLookupIndex(
                orgIdentityResolver.snapshot(),
                catalogReader.ioeCodeByName(),
                catalogReader.xcrByCurrency(),
                catalogReader.abusUnitNameByCode(),
                catalogReader.exePttCodeByName(),
                catalogReader.edrtCapitalCodeByName(),
                catalogReader.generalExpenseRate());
    }

    /**
     * 시트 목록이 반영 가능한 형태인지 확인합니다.
     *
     * <p>예산연도는 반드시 전 시트가 같아야 합니다. 이 서비스는 {@code sheets.get(0).bseYy()} 하나를 연도 스냅샷·매칭·편성률 적용의 기준으로
     * 쓰므로, 시트마다 연도가 다르면 두 번째 시트 이후는 <b>다른 연도의 스냅샷으로 검증되고 첫 시트의 연도로 저장</b>됩니다.
     *
     * <p>시트 종류도 중복될 수 없습니다 (MIG-20). 시트별 처리 상태({@link Plan#createNewRows}·{@link
     * Plan#matchedPkByRow}, {@code createdPkByRow})가 {@link SheetKind}를 키로 쓰므로, 같은 종류를 두 번 올리면 두
     * 페이로드의 엑셀 행 번호가 같은 키 아래 섞입니다. 현재 화면은 슬롯당 1개만 허용하지만 계약 자체를 좁혀 API 직접 호출도 막습니다.
     *
     * <p>중복 판정은 종류·연도 검사 뒤에 둡니다 — 같은 종류를 다른 연도로 올린 요청은 두 위반에 모두 걸리는데, 연도 혼재가 더 구체적인 안내라 그 메시지를 먼저
     * 냅니다.
     *
     * @throws IllegalArgumentException 시트가 없거나, 지원하지 않는 종류이거나, 예산연도가 섞였거나, 같은 시트 종류가 둘 이상인 경우
     */
    private void requireSupported(List<MigrationDto.SheetPayload> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("올린 시트가 없습니다.");
        }
        String bseYy = sheets.get(0).bseYy();
        Set<SheetKind> seen = EnumSet.noneOf(SheetKind.class);
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (!adapters.containsKey(sheet.kind())) {
                throw new IllegalArgumentException("지원하지 않는 시트 종류입니다: " + sheet.kind());
            }
            if (bseYy == null || !bseYy.equals(sheet.bseYy())) {
                throw new IllegalArgumentException(
                        "시트마다 예산연도가 다릅니다: " + bseYy + ", " + sheet.bseYy());
            }
        }
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (!seen.add(sheet.kind())) {
                throw new IllegalArgumentException("같은 시트 종류를 두 번 올릴 수 없습니다: " + sheet.kind());
            }
        }
    }
}
