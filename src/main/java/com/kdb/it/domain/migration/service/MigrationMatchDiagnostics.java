package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.RowDecision;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.adapter.AdapterSupport;
import com.kdb.it.domain.migration.service.adapter.AllocationIntent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 매칭·배분 계열 진단을 만듭니다.
 *
 * <p>{@link MigrationValidator}에 넣지 않은 이유는 그쪽이 이미 700줄을 넘고 시트 종류별 분기가 크기 때문입니다. 검증 규칙(값이 올바른가)과 매칭
 * 판정(어느 원장을 가리키는가)은 판단의 종류가 다릅니다.
 */
@Component
@RequiredArgsConstructor
public class MigrationMatchDiagnostics {

    /**
     * 종합본 기준액과 원장 요청 합계의 허용 오차(원). 단위 환산 반올림을 흡수합니다 — 브리프의 예시는 단위가 이미 원인 값끼리 정확히 일치하는 경우라 1원이면
     * 충분합니다.
     */
    private static final BigDecimal AMOUNT_TOLERANCE = BigDecimal.ONE;

    /**
     * 조정열 대사 허용 오차(원). 자본예산 시트의 금액 컬럼은 백만원 단위라({@code AdapterSupport.amount}가 ×1,000,000으로 환산) 엑셀
     * 원문의 1단위(=백만원) 차이가 대사값에서 그대로 백만원 차이로 나타납니다. 반올림으로 흡수해야 할 그 1단위 오차를 허용하기 위해 백만원(1,000,000)을
     * 씁니다.
     */
    private static final BigDecimal RECONCILE_TOLERANCE = new BigDecimal("1000000");

    private final MigrationLedgerMatcher matcher;

    /**
     * 행의 처리 방식을 정합니다.
     *
     * @param pk 매칭·선택된 원장 PK. {@code MATCH}가 아니면 null
     * @param action 처리 방식. 결정되지 않았으면 null
     * @param diagnostics 결정을 요구하는 진단. 결정됐으면 빈 목록
     */
    public record Resolved(
            String pk, RowDecision.Kind action, List<MigrationDto.CellDiagnostic> diagnostics) {}

    /**
     * 배분 의도를 기존 원장에 붙입니다.
     *
     * <p>관리자가 이미 결정한 행은 그 결정을 그대로 따르고, 그렇지 않으면 매처에게 묻습니다. 매칭에 실패하면 결정을 요구하는 BLOCKER를 내며, 후보에는 항상
     * {@code CREATE_NEW}·{@code SKIP}이 붙습니다 — 후보가 비면 화면에 드롭다운이 그려지지 않아 손댈 방법이 없습니다.
     *
     * @param sheet 시트 페이로드 (진단 좌표)
     * @param intent 배분 의도
     * @param snapshot 연도 스냅샷
     * @param overrides 보정값 (결정 포함)
     * @return 처리 방식과 진단
     */
    public Resolved resolve(
            MigrationDto.SheetPayload sheet,
            AllocationIntent intent,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides) {
        RowDecision decided =
                RowDecision.parse(
                        overrides.get(
                                MigrationValidator.overrideKey(
                                        sheet.kind(), intent.excelRow(), RowDecision.COLUMN)));
        if (decided != null) {
            return new Resolved(decided.pk(), decided.kind(), List.of());
        }

        MigrationLedgerMatcher.Match match = match(intent, snapshot);
        if (match.outcome() == MigrationLedgerMatcher.Outcome.MATCHED) {
            return new Resolved(match.pk(), RowDecision.Kind.MATCH, List.of());
        }

        boolean ambiguous = match.outcome() == MigrationLedgerMatcher.Outcome.AMBIGUOUS;
        String code = ambiguous ? "LEDGER_AMBIGUOUS" : "LEDGER_NOT_MATCHED";
        String message =
                ambiguous
                        ? "이 행에 해당하는 원장 후보가 둘 이상입니다. 편성할 대상을 골라 주세요."
                        : "이 행에 해당하는 원장을 찾지 못했습니다. 편성요청서를 먼저 반입했는지 확인하고, 대상을 고르거나 원장을 새로 만들지 정해 주세요.";
        return new Resolved(
                null,
                null,
                List.of(
                        MigrationDiagnostics.blocker(
                                sheet,
                                rowAt(sheet, intent.excelRow()),
                                RowDecision.COLUMN,
                                code,
                                message,
                                RowDecision.decisionCandidates(match.candidates()))));
    }

    /**
     * 배분 가능성과 금액 대사를 확인합니다.
     *
     * @param sheet 시트 페이로드
     * @param intent 배분 의도
     * @param pk 매칭된 원장 PK
     * @param snapshot 연도 스냅샷
     * @param planner 배분기
     * @return 진단 목록. 문제가 없으면 빈 목록
     */
    public List<MigrationDto.CellDiagnostic> checkAllocation(
            MigrationDto.SheetPayload sheet,
            AllocationIntent intent,
            String pk,
            MigrationYearSnapshot.Data snapshot,
            MigrationAllocationPlanner planner) {
        List<MigrationDto.CellDiagnostic> out = new ArrayList<>();
        MigrationDto.NormalizedRow row = rowAt(sheet, intent.excelRow());

        if ("BCOSTM".equals(intent.orcTb())) {
            MigrationYearSnapshot.CostRef ref = snapshot.costOf(pk);
            BigDecimal base = ref == null ? BigDecimal.ZERO : ref.amount();
            BigDecimal target = intent.targetByColumn().getOrDefault("costAmount", BigDecimal.ZERO);
            if (base.compareTo(BigDecimal.ZERO) == 0 && target.compareTo(BigDecimal.ZERO) != 0) {
                out.add(baseZero(sheet, row, "costAmount"));
            }
            addAmountAdjusted(out, sheet, row, intent.declaredBase(), base);
            return out;
        }

        List<MigrationYearSnapshot.RequestItem> all = snapshot.itemsOfProject(pk);
        BigDecimal ledgerBase = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : intent.targetByColumn().entrySet()) {
            List<MigrationYearSnapshot.RequestItem> items = itemsFor(entry.getKey(), all);
            for (MigrationYearSnapshot.RequestItem item : items) {
                ledgerBase = ledgerBase.add(item.amount());
            }
            if (planner.allocate(items, entry.getValue())
                    instanceof MigrationAllocationPlanner.Allocation.BaseZero) {
                out.add(baseZero(sheet, row, entry.getKey()));
            }
        }
        addAmountAdjusted(out, sheet, row, intent.declaredBase(), ledgerBase);
        return out;
    }

    /**
     * 종합본의 조정 3열이 `기준액 × 조정비율`과 맞는지 대사합니다.
     *
     * <p>어긋나면 우리가 계산한 편성액과 예산담당자가 문서에 적어 둔 값이 다르다는 뜻이므로 반영 전에 알립니다. 반영을 막지는 않습니다 — 기준은 `기준액 ×
     * 조정비율`이고(설계 §2.2) 조정열은 참고값입니다.
     *
     * <p>세 컬럼을 {@link LinkedHashMap}으로 순서를 고정해 순회합니다 — {@code Map.of}는 순서를 보장하지 않아 같은 행에서 여러 열이
     * 어긋나면 진단이 나오는 순서가 실행마다 달라져 테스트가 불안정해집니다.
     *
     * @param sheet 시트 페이로드
     * @param row 정규화 행
     * @param overrides 보정값
     * @return 진단 목록. 자본예산 시트가 아니거나 조정열이 비면 빈 목록
     */
    public List<MigrationDto.CellDiagnostic> checkRateReconcile(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides) {
        List<MigrationDto.CellDiagnostic> out = new ArrayList<>();
        if (sheet.kind() != SheetKind.CAPITAL_PROJECT) {
            return out;
        }
        BigDecimal rate =
                AdapterSupport.rateFraction(
                        MigrationDiagnostics.cell(row, "adjustRate", overrides, sheet));

        Map<String, String> baseToAdjustColumn = new LinkedHashMap<>();
        baseToAdjustColumn.put("devAmount", "devAdjustAmount");
        baseToAdjustColumn.put("hwAmount", "hwAdjustAmount");
        baseToAdjustColumn.put("swAmount", "swAdjustAmount");

        for (Map.Entry<String, String> pair : baseToAdjustColumn.entrySet()) {
            String declared = MigrationDiagnostics.cell(row, pair.getValue(), overrides, sheet);
            if (declared.isBlank()) {
                continue;
            }
            BigDecimal base =
                    AdapterSupport.amount(
                            MigrationDiagnostics.cell(row, pair.getKey(), overrides, sheet),
                            sheet.kind());
            BigDecimal declaredAmount = AdapterSupport.amount(declared, sheet.kind());
            if (base == null || declaredAmount == null) {
                continue;
            }
            BigDecimal expected = base.multiply(rate);
            if (expected.subtract(declaredAmount).abs().compareTo(RECONCILE_TOLERANCE) > 0) {
                out.add(
                        MigrationDiagnostics.warning(
                                sheet,
                                row,
                                pair.getValue(),
                                "RATE_RECONCILE_MISMATCH",
                                "엑셀의 조정 금액이 기준액 × 조정비율과 다릅니다. 편성은 기준액 × 조정비율로 계산합니다."));
            }
        }
        return out;
    }

    private MigrationLedgerMatcher.Match match(
            AllocationIntent intent, MigrationYearSnapshot.Data snapshot) {
        AllocationIntent.MatchKey key = intent.matchKey();
        return switch (key.type()) {
            case PROJECT_NAME -> matcher.matchProject(key.normalizedName(), snapshot);
            case ORDINARY_DEPT -> matcher.matchOrdinaryProject(key.deptCode(), snapshot);
            case COST_DEPT_KEY ->
                    matcher.matchCost(
                            snapshot.bseYy(),
                            key.deptCode(),
                            key.ioeC(),
                            key.vendorName(),
                            key.contractName(),
                            snapshot);
        };
    }

    private List<MigrationYearSnapshot.RequestItem> itemsFor(
            String column, List<MigrationYearSnapshot.RequestItem> all) {
        Set<String> group = MigrationAllocationPlanner.groupOf(column);
        return group.isEmpty()
                ? MigrationAllocationPlanner.itemsOutsideCapitalGroups(all)
                : MigrationAllocationPlanner.itemsInGroup(all, group);
    }

    private MigrationDto.CellDiagnostic baseZero(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, String column) {
        return MigrationDiagnostics.blocker(
                sheet,
                row,
                column,
                "ITEM_BASE_ZERO",
                "편성할 금액이 있는데 대상 원장의 요청금액이 0원이라 배분할 수 없습니다. 요청 원장을 확인하거나 이 행을 제외해 주세요.",
                List.of());
    }

    private void addAmountAdjusted(
            List<MigrationDto.CellDiagnostic> out,
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            BigDecimal declaredBase,
            BigDecimal ledgerBase) {
        if (declaredBase == null) {
            return;
        }
        if (declaredBase.subtract(ledgerBase).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            out.add(
                    MigrationDiagnostics.warning(
                            sheet,
                            row,
                            null,
                            "AMOUNT_ADJUSTED",
                            "종합본 금액이 부서가 제출한 요청 금액과 다릅니다. 편성은 종합본 금액을 기준으로 계산합니다."));
        }
    }

    /** 행 번호로 정규화 행을 찾습니다. 배분 의도는 항상 이 시트의 행에서 나오므로 없을 수 없습니다. */
    private MigrationDto.NormalizedRow rowAt(MigrationDto.SheetPayload sheet, int excelRow) {
        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            if (row.excelRow() == excelRow) {
                return row;
            }
        }
        throw new IllegalStateException("배분 의도의 행을 시트에서 찾지 못했습니다: " + excelRow);
    }
}
