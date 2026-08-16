package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationYearSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 정보기술부문계획 조정 `26년정보화사업(자본예산)` 시트를 계획 조정 의도로 바꿉니다 (§5.4).
 *
 * <p>조정액은 편성요청액에 비율을 곱한 값이 아니라 확정 금액입니다(품의·계약 반영). 요청 원장({@code BITEMM})은 건드리지 않고 이 금액을 목표 편성액으로
 * 넘겨, {@code MigrationAllocationPlanner}가 실효 편성률로 환산합니다. 부서가 제출한 요청 품목이 활성 원장에 그대로 남아야 요청 대비 편성 비교가
 * 성립하기 때문입니다.
 *
 * <p>집행 실적 4열과 사업진행·비고·총사업금액은 원장 컬럼에 대응하는 자리가 없어 계획 스냅샷({@code BPLANM.REDT_CONE_INF})에만 남깁니다.
 * 일반관리비는 원문을 스냅샷에 남기는 동시에 원 단위로 환산해 {@link PlanIntent#generalAmount()}로 넘깁니다 — 계획 마스터의 {@code
 * TOT_XP_AMT}와 {@code ADU_TOT_AMT} 합계가 그 값을 포함해야 하기 때문입니다(§5.4).
 */
@Component
public class PlanAdjustmentSheetAdapter implements SheetAdapter {

    /** 계획 스냅샷에만 남길 컬럼 목록. */
    private static final List<String> SNAPSHOT_ONLY_COLUMNS =
            List.of(
                    "spentBefore",
                    "spent26",
                    "planned26",
                    "plannedAfter27",
                    "progressLabel",
                    "budgetChangeLabel",
                    "remark",
                    "generalAmount",
                    "totalAmount");

    @Override
    public SheetKind supports() {
        return SheetKind.PLAN_ADJUSTMENT;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        List<PlanIntent> plans = new ArrayList<>();
        List<AllocationIntent> allocations = new ArrayList<>();

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String normalizedName =
                    MigrationYearSnapshot.normalizeName(
                            AdapterSupport.cellOf(sheet, row, "projectName", ctx));

            Map<String, String> snapshotFields = new LinkedHashMap<>();
            for (String column : SNAPSHOT_ONLY_COLUMNS) {
                snapshotFields.put(column, AdapterSupport.cellOf(sheet, row, column, ctx));
            }

            plans.add(
                    new PlanIntent(
                            normalizedName,
                            positiveAmount(sheet, row, ctx, "devAmount"),
                            positiveAmount(sheet, row, ctx, "hwAmount"),
                            positiveAmount(sheet, row, ctx, "swAmount"),
                            // 일반관리비는 품목을 만들지 않고 계획 마스터의 TOT_XP_AMT 합계에만 들어간다
                            AdapterSupport.amount(
                                    AdapterSupport.cellOf(sheet, row, "generalAmount", ctx),
                                    sheet.kind()),
                            AdapterSupport.ymToYyyymm(
                                    AdapterSupport.cellOf(sheet, row, "paymentSchedule", ctx)),
                            snapshotFields));

            Map<String, BigDecimal> targets = new LinkedHashMap<>();
            targets.put("devAmount", orZero(positiveAmount(sheet, row, ctx, "devAmount")));
            targets.put("hwAmount", orZero(positiveAmount(sheet, row, ctx, "hwAmount")));
            targets.put("swAmount", orZero(positiveAmount(sheet, row, ctx, "swAmount")));

            allocations.add(
                    new AllocationIntent(
                            sheet.kind(),
                            row.excelRow(),
                            "BPROJM",
                            AllocationIntent.MatchKey.ofProjectName(normalizedName),
                            targets,
                            // 조정본은 확정금액만 주고 요청 기준액이 없어 대사할 상대가 없다
                            null));
        }
        return new AdapterOutput(List.of(), List.of(), plans, allocations);
    }

    /** null 금액을 0원으로 접습니다. 목표액 0은 "그 비목그룹을 0원으로 편성"이라는 뜻이라 생략하지 않습니다. */
    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** 0 이하 금액은 품목을 만들 이유가 없으므로 null로 접습니다. */
    private BigDecimal positiveAmount(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AdapterContext ctx,
            String column) {
        BigDecimal amount =
                AdapterSupport.amount(AdapterSupport.cellOf(sheet, row, column, ctx), sheet.kind());
        return (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) ? null : amount;
    }
}
