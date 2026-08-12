package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostRepresentativeSelector;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetRateApplicationService;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.adapter.AdapterContext;
import com.kdb.it.domain.migration.service.adapter.AdapterOutput;
import com.kdb.it.domain.migration.service.adapter.PlanIntent;
import com.kdb.it.domain.migration.service.adapter.RateIntent;
import com.kdb.it.domain.migration.service.adapter.SheetAdapter;
import com.kdb.it.exception.CustomGeneralException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수기 엑셀 이관의 사전검증과 확정 반영을 조율합니다.
 *
 * <p>dry-run 결과를 서버에 보관하지 않으므로 확정 반영은 클라이언트가 보낸 값을 신뢰하지 않고 같은 검증을 다시 돌립니다. BLOCKER가 하나라도 있으면 아무 원장도
 * 쓰지 않고 실패합니다 — {@link #commit}은 재검증 결과에 BLOCKER가 남으면 어댑터 맵({@link #adapters})에 손을 대기 전에 예외를 던지고
 * 반환하므로, 이 클래스에는 검증을 거치지 않고 어댑터에 도달하는 경로가 없습니다. {@link SheetAdapter}는 이름 해석에 실패한 값을 "이미 코드값"으로 가정하고
 * 넘어가는데, 이는 검증이 먼저 걸러냈다는 전제 위에서만 안전합니다.
 *
 * <p>반영 순서가 중요합니다(§7: 일반관리비 → 자본예산 → 위임예산 → 부문계획 → 편성률 단일 적용). 부문계획 조정은 자본예산이 만든 품목을 버전 교체하므로 원장
 * 단계의 마지막이며, 편성행은 {@code applyItemRates} 단일 호출이 전담합니다 — 이 메서드는 연도 전체를 재작성하고 삭제 이력을 남기지 않으므로 두 번
 * 호출하면 첫 결과가 흔적 없이 사라집니다.
 */
@Service
@Slf4j
public class MigrationImportService {

    private final Map<SheetKind, SheetAdapter> adapters = new EnumMap<>(SheetKind.class);
    private final MigrationValidator validator;
    private final MigrationYearSnapshot yearSnapshot;
    private final OrgIdentityResolver orgIdentityResolver;
    private final MigrationIoeCatalogReader catalogReader;
    private final MigrationApprovalStamper approvalStamper;
    private final CostService costService;
    private final CostRepository costRepository;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final BudgetRateApplicationService budgetRateApplicationService;
    private final ProjectItemRepository projectItemRepository;
    private final PlanService planService;

    /**
     * 어댑터를 시트 종류별로 색인해 둡니다.
     *
     * @param sheetAdapters 등록된 어댑터 전체 (Spring이 주입)
     */
    public MigrationImportService(
            List<SheetAdapter> sheetAdapters,
            MigrationValidator validator,
            MigrationYearSnapshot yearSnapshot,
            OrgIdentityResolver orgIdentityResolver,
            MigrationIoeCatalogReader catalogReader,
            MigrationApprovalStamper approvalStamper,
            CostService costService,
            CostRepository costRepository,
            ProjectService projectService,
            ProjectRepository projectRepository,
            BudgetRateApplicationService budgetRateApplicationService,
            ProjectItemRepository projectItemRepository,
            PlanService planService) {
        for (SheetAdapter adapter : sheetAdapters) {
            adapters.put(adapter.supports(), adapter);
        }
        this.validator = validator;
        this.yearSnapshot = yearSnapshot;
        this.orgIdentityResolver = orgIdentityResolver;
        this.catalogReader = catalogReader;
        this.approvalStamper = approvalStamper;
        this.costService = costService;
        this.costRepository = costRepository;
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.budgetRateApplicationService = budgetRateApplicationService;
        this.projectItemRepository = projectItemRepository;
        this.planService = planService;
    }

    /**
     * 올린 시트를 검증해 행별 진단을 돌려줍니다. 아무것도 저장하지 않습니다.
     *
     * @param request 시트 목록과 보정값
     * @return 진단 목록과 요약
     * @throws IllegalArgumentException 시트 목록이 비었거나 지원하지 않는 시트 종류가 온 경우
     */
    @Transactional(readOnly = true)
    public MigrationDto.DryRunResponse dryRun(MigrationDto.DryRunRequest request) {
        requireSupported(request.sheets());
        String bseYy = request.sheets().get(0).bseYy();
        Map<String, String> overrides = foldOverrides(request.overrides());
        List<MigrationDto.CellDiagnostic> diagnostics =
                validator.validate(
                        request.sheets(), lookupIndex(), yearSnapshot.load(bseYy), overrides);

        int totalRows = request.sheets().stream().mapToInt(s -> s.rows().size()).sum();
        int blockers =
                (int)
                        diagnostics.stream()
                                .filter(d -> d.severity() == MigrationDto.Severity.BLOCKER)
                                .count();
        return new MigrationDto.DryRunResponse(
                diagnostics,
                new MigrationDto.Summary(totalRows, blockers, diagnostics.size() - blockers));
    }

    /**
     * 보정값을 반영해 원장과 결재 받이를 만들고 편성률을 적용합니다.
     *
     * <p>전 과정이 하나의 트랜잭션입니다. 검증에서 BLOCKER가 남거나 어느 단계에서든 예외가 나면 전부 롤백됩니다.
     *
     * @param request 시트 목록과 보정값
     * @param actorEno 업로드 사용자 사번
     * @return 반영 건수와 생성한 관리번호
     * @throws CustomGeneralException 검증에 BLOCKER가 남은 경우
     */
    @Transactional
    public MigrationDto.CommitResponse commit(MigrationDto.CommitRequest request, String actorEno) {
        requireSupported(request.sheets());
        String bseYy = request.sheets().get(0).bseYy();
        Map<String, String> overrides = foldOverrides(request.overrides());
        MigrationYearSnapshot.Data snapshot = yearSnapshot.load(bseYy);
        MigrationLookupIndex index = lookupIndex();

        // 1단계: 재검증 — BLOCKER가 남으면 아무것도 쓰지 않는다. 이 메서드에서 어댑터 맵(adapters)에
        // 처음 접근하는 지점은 이 return문 다음이므로, BLOCKER가 있는 한 어댑터에 도달할 방법이 없다.
        List<MigrationDto.CellDiagnostic> diagnostics =
                validator.validate(request.sheets(), index, snapshot, overrides);
        long blockers =
                diagnostics.stream()
                        .filter(d -> d.severity() == MigrationDto.Severity.BLOCKER)
                        .count();
        if (blockers > 0) {
            throw new CustomGeneralException(
                    "해결되지 않은 오류가 " + blockers + "건 있어 반영할 수 없습니다. 미리보기에서 보정해 주세요.");
        }

        AdapterContext ctx = new AdapterContext(bseYy, index, snapshot, overrides, actorEno);

        // 2단계: 이관 대상이 아닌 기존 편성행의 편성률을 유지하도록 미리 모아 둔다 (§7 5단계 준비).
        // 사업의 편성률은 BBUGTM에 사업관리번호로 걸린 행이 없어(키가 품목관리번호다, §3.5) 스냅샷이
        // 품목 편성행에서 역산해 준다. 이 값을 잘못 읽으면 applyItemRates가 연도 전체를 재작성하면서
        // 기존 사업 전부를 기본값 100%로 올려 버리고, 벌크 논리삭제라 BBUGT_L에도 흔적이 남지 않는다.
        List<BudgetWorkDto.ItemRate> rateItems = new ArrayList<>();
        for (String projectNo : snapshot.allProjectNos()) {
            MigrationYearSnapshot.ProjectRate rate = snapshot.existingProjectRateOf(projectNo);
            rateItems.add(
                    new BudgetWorkDto.ItemRate(
                            "BPROJM",
                            projectNo,
                            rate == null ? MigrationYearSnapshot.DEFAULT_RATE : rate.assetRate(),
                            rate == null ? MigrationYearSnapshot.DEFAULT_RATE : rate.costRate()));
        }
        for (String costNo : snapshot.allCostNos()) {
            Integer rate = snapshot.existingCostRateOf(costNo);
            rateItems.add(
                    new BudgetWorkDto.ItemRate("BCOSTM", costNo, orDefault(rate), orDefault(rate)));
        }

        // 3단계: 어댑터 순서대로 원장 생성. 부문계획은 자본예산이 만든 품목을 교체하므로 마지막에 처리한다
        List<String> createdIds = new ArrayList<>();
        Map<String, String> projectNoByName =
                new LinkedHashMap<>(snapshot.projectNoByNormalizedName());
        Map<String, String> costNoByNaturalKey = new LinkedHashMap<>();
        List<RateIntent> rateIntents = new ArrayList<>();
        List<PlanIntent> planIntents = new ArrayList<>();
        int costCount = 0;
        int projectCount = 0;
        int itemCount = 0;

        for (SheetKind kind :
                List.of(
                        SheetKind.COST,
                        SheetKind.CAPITAL_PROJECT,
                        SheetKind.DELEGATED_BUDGET,
                        SheetKind.PLAN_ADJUSTMENT)) {
            for (MigrationDto.SheetPayload sheet : request.sheets()) {
                if (sheet.kind() != kind) {
                    continue;
                }
                AdapterOutput output = adapters.get(kind).adapt(sheet, ctx);
                rateIntents.addAll(output.rates());
                planIntents.addAll(output.plans());

                for (CostDto.CreateRequest cost : output.costs()) {
                    String costNo = costService.createCost(cost, true);
                    // 결재 받이의 원천 일련번호(fntTbCrySno)는 BbugtmRepositoryImpl의 집계 조인이
                    // Cappla.fntTbCrySno = Bcostm.bgSno로 맞춰 보므로, 하드코딩한 1이 아니라 방금 저장된
                    // 행의 실제 bgSno를 다시 읽어 넘긴다.
                    Bcostm createdCost =
                            CostRepresentativeSelector.pick(
                                    costRepository.findByCostBgNoAndDelYn(costNo, "N"));
                    costNoByNaturalKey.put(
                            MigrationYearSnapshot.costNaturalKey(
                                    bseYy,
                                    cost.getBgUntAbusC(),
                                    cost.getIoeC(),
                                    cost.getCttOppNm(),
                                    cost.getCttNm()),
                            costNo);
                    approvalStamper.stamp(
                            "BCOSTM",
                            costNo,
                            createdCost.getBgSno(),
                            bseYy + "년 전산일반관리비 이관",
                            actorEno,
                            bseYy);
                    createdIds.add(costNo);
                    costCount++;
                }
                for (ProjectDto.CreateRequest project : output.projects()) {
                    String projectNo = projectService.createProject(project, true);
                    // 결재 받이의 원천 일련번호는 Cappla.fntTbCrySno = Bprojm.sno로 맞춰 보므로, 방금
                    // 저장된 행의 실제 sno를 다시 읽어 넘긴다 (전산업무비와 같은 이유).
                    Bprojm createdProject =
                            projectRepository
                                    .findByAbusMngNoAndDelYn(projectNo, "N")
                                    .orElseThrow(
                                            () ->
                                                    new CustomGeneralException(
                                                            "이관 직후 생성된 사업을 다시 찾지 못했습니다: "
                                                                    + projectNo));
                    projectNoByName.put(
                            MigrationYearSnapshot.normalizeName(project.getAbusNm()), projectNo);
                    approvalStamper.stamp(
                            "BPROJM",
                            projectNo,
                            createdProject.getSno(),
                            bseYy + "년 정보화사업 이관",
                            actorEno,
                            bseYy);
                    createdIds.add(projectNo);
                    projectCount++;
                    itemCount += project.getItems() == null ? 0 : project.getItems().size();
                }
            }
        }

        // 4단계: 부문계획 — 대상 사업의 품목을 조정 금액으로 버전 교체 (원장 단계의 마지막)
        for (PlanIntent intent : planIntents) {
            itemCount += replaceItems(intent, projectNoByName, bseYy);
        }
        String planReqDocNo =
                planIntents.isEmpty()
                        ? null
                        : createAdjustmentPlan(planIntents, projectNoByName, bseYy);

        // 5단계: 편성률 단일 적용 — 이관분 편성률로 기존 항목을 덮어쓴다 (같은 원천은 교체, 중복 추가 아님)
        for (RateIntent intent : rateIntents) {
            String pk =
                    "BPROJM".equals(intent.orcTb())
                            ? projectNoByName.get(intent.naturalKeyOrPk())
                            : costNoByNaturalKey.get(intent.naturalKeyOrPk());
            if (pk == null) {
                log.warn("편성률 대상 PK를 찾지 못해 건너뜁니다: {} {}", intent.orcTb(), intent.naturalKeyOrPk());
                continue;
            }
            rateItems.removeIf(
                    existing ->
                            existing.orcTb().equals(intent.orcTb())
                                    && existing.orcPkVl().equals(pk));
            rateItems.add(
                    new BudgetWorkDto.ItemRate(
                            intent.orcTb(), pk, intent.percent(), intent.percent()));
        }
        BudgetWorkDto.ApplyResponse applied =
                budgetRateApplicationService.applyItemRates(
                        new BudgetWorkDto.ItemApplyRequest(bseYy, rateItems));

        return new MigrationDto.CommitResponse(
                costCount,
                projectCount,
                itemCount,
                applied.totalRecords(),
                planReqDocNo,
                createdIds);
    }

    /**
     * 부문계획 조정 금액으로 대상 사업의 품목을 버전 교체합니다.
     *
     * <p>기존 활성 품목을 {@code DEL_YN='Y'}로 닫고(요청 금액은 이력으로 남습니다) 조정 금액으로 새 품목을 만듭니다. 조정액은 비율 곱이 아니라 확정
     * 금액이라 편성률로는 재현되지 않기 때문입니다(§5.4).
     *
     * @return 새로 만든 품목 수
     */
    private int replaceItems(PlanIntent intent, Map<String, String> projectNoByName, String bseYy) {
        String projectNo = projectNoByName.get(intent.normalizedProjectName());
        if (projectNo == null) {
            log.warn("부문계획 조정 대상 사업을 찾지 못해 건너뜁니다: {}", intent.normalizedProjectName());
            return 0;
        }
        for (Bitemm existing :
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y")) {
            existing.delete();
        }
        List<ProjectDto.BitemmDto> items = buildAdjustedItems(intent, bseYy);
        projectService.replaceItemsForMigration(projectNo, items);
        return items.size();
    }

    /**
     * 조정 금액이 있는 항목만 품목으로 만듭니다.
     *
     * <p>비목 기본값은 {@link MigrationIoeCodes} 상수를 그대로 참조합니다 — 자본예산 어댑터가 만든 품목과 같은 비목이어야 조정이 같은 비목의 품목을
     * 교체합니다. 리터럴을 여기 다시 적으면 한쪽만 바뀌었을 때 조용히 어긋납니다.
     */
    private List<ProjectDto.BitemmDto> buildAdjustedItems(PlanIntent intent, String bseYy) {
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        addAdjustedItem(
                items,
                intent.devAmount(),
                MigrationIoeCodes.IOE_DEV,
                "개발비",
                intent.paymentYm(),
                bseYy);
        addAdjustedItem(
                items,
                intent.hwAmount(),
                MigrationIoeCodes.IOE_HW,
                "기계장치",
                intent.paymentYm(),
                bseYy);
        addAdjustedItem(
                items,
                intent.swAmount(),
                MigrationIoeCodes.IOE_SW,
                "기타무형자산",
                intent.paymentYm(),
                bseYy);
        return items;
    }

    private void addAdjustedItem(
            List<ProjectDto.BitemmDto> items,
            BigDecimal amount,
            String ioeC,
            String label,
            String paymentYm,
            String bseYy) {
        if (amount == null) {
            return;
        }
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC(ioeC);
        item.setGclNm(label);
        item.setCurC("KRW");
        item.setAmt(amount);
        item.setBseYm(paymentYm);
        item.setXcrBseDt(bseYy + "0101");
        items.add(item);
    }

    /**
     * 조정 계획({@code BPLANM} + {@code BPLANA})을 만듭니다.
     *
     * <p>{@code PlanIntent}는 이 오케스트레이션 서비스만 아는 타입이므로, {@code PlanService}가 이 도메인을 역참조하지 않도록 여기서 원시
     * 타입(사업관리번호·자본예산 합계·스냅샷 필드 맵)으로 분해해 넘긴다.
     */
    private String createAdjustmentPlan(
            List<PlanIntent> intents, Map<String, String> projectNoByName, String bseYy) {
        List<String> projectNos = new ArrayList<>();
        List<BigDecimal> capitalAmounts = new ArrayList<>();
        List<BigDecimal> generalAmounts = new ArrayList<>();
        Map<String, Map<String, String>> snapshotFieldsByProject = new LinkedHashMap<>();
        for (PlanIntent intent : intents) {
            String projectNo = projectNoByName.get(intent.normalizedProjectName());
            if (projectNo == null) {
                continue;
            }
            projectNos.add(projectNo);
            capitalAmounts.add(
                    sumAmounts(intent.devAmount(), intent.hwAmount(), intent.swAmount()));
            generalAmounts.add(sumAmounts(intent.generalAmount()));
            snapshotFieldsByProject.put(projectNo, intent.snapshotFields());
        }
        if (projectNos.isEmpty()) {
            return null;
        }
        return planService.createPlanForMigration(
                bseYy, "조정", projectNos, capitalAmounts, generalAmounts, snapshotFieldsByProject);
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
                catalogReader.edrtCapitalCodeByName());
    }

    /**
     * 시트 목록이 반영 가능한 형태인지 확인합니다.
     *
     * <p>예산연도는 반드시 전 시트가 같아야 합니다. 이 서비스는 {@code sheets.get(0).bseYy()} 하나를 연도 스냅샷·중복 판정·편성률 적용의
     * 기준으로 쓰므로, 시트마다 연도가 다르면 두 번째 시트 이후는 **다른 연도의 스냅샷으로 검증되고 첫 시트의 연도로 저장**됩니다.
     *
     * @throws IllegalArgumentException 시트가 없거나, 지원하지 않는 종류이거나, 예산연도가 섞인 경우
     */
    private void requireSupported(List<MigrationDto.SheetPayload> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("올린 시트가 없습니다.");
        }
        String bseYy = sheets.get(0).bseYy();
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (!adapters.containsKey(sheet.kind())) {
                throw new IllegalArgumentException("지원하지 않는 시트 종류입니다: " + sheet.kind());
            }
            if (bseYy == null || !bseYy.equals(sheet.bseYy())) {
                throw new IllegalArgumentException(
                        "시트마다 예산연도가 다릅니다: " + bseYy + ", " + sheet.bseYy());
            }
        }
    }

    /** 기존 편성률이 없으면 100으로 둡니다. */
    private static int orDefault(Integer rate) {
        return rate == null ? 100 : rate;
    }
}
