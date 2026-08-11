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
 * <p>조정액은 편성요청액에 비율을 곱한 값이 아니라 확정 금액입니다(품의·계약 반영). {@code Bbugtm.asgRt}가 정수라 비율로는 재현되지 않으므로, 대상 사업의
 * {@code BITEMM}을 이 금액으로 버전 교체하고 편성률 100을 적용합니다. 실제 교체는 {@code MigrationImportService}가 수행하고 이 어댑터는
 * 의도만 만듭니다 — 사업·전산업무비 생성요청은 만들지 않습니다.
 *
 * <p>집행 실적 4열과 사업진행·비고는 원장 컬럼에 대응하는 자리가 없어 계획 스냅샷({@code BPLANM.REDT_CONE_INF})에만 남깁니다.
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
        List<RateIntent> rates = new ArrayList<>();

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
                            AdapterSupport.ymToYyyymm(
                                    AdapterSupport.cellOf(sheet, row, "paymentSchedule", ctx)),
                            snapshotFields));

            rates.add(new RateIntent("BPROJM", normalizedName, 100));
        }
        return new AdapterOutput(List.of(), List.of(), plans, rates);
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
