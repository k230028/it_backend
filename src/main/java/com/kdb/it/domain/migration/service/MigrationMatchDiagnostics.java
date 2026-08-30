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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 매칭·배분 계열 진단을 만듭니다.
 *
 * <p>{@link MigrationValidator}에 넣지 않은 이유는 검증 규칙(값이 올바른가)과 매칭 판정(어느 원장을 가리키는가)이 판단의 종류가 다르기 때문입니다.
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

    /** 자본 세 그룹 밖 품목(일반관리비 계열)의 목표액 컬럼. 진단 좌표로도 씁니다. */
    private static final String GENERAL_AMOUNT_COLUMN = "generalAmount";

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
     * {@code SKIP}이(원장을 만들 수 있는 시트라면 {@code CREATE_NEW}도) 붙습니다 — 후보가 비면 화면에 드롭다운이 그려지지 않아 손댈 방법이
     * 없습니다.
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
        MigrationDiagnosticCode code =
                ambiguous
                        ? MigrationDiagnosticCode.LEDGER_AMBIGUOUS
                        : MigrationDiagnosticCode.LEDGER_NOT_MATCHED;
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
                                RowDecision.decisionCandidates(match.candidates(), sheet.kind()))));
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
                // RequestItem의 amount는 null이면 0원이라는 계약이다 (배분기도 같은 규칙으로 합산한다)
                ledgerBase =
                        ledgerBase.add(item.amount() == null ? BigDecimal.ZERO : item.amount());
            }
            if (planner.allocate(items, entry.getValue())
                    instanceof MigrationAllocationPlanner.Allocation.BaseZero) {
                out.add(baseZero(sheet, row, entry.getKey()));
            }
        }
        addAmountAdjusted(out, sheet, row, intent.declaredBase(), ledgerBase);
        addGeneralRateDefaulted(out, sheet, row, intent, all, snapshot);
        return out;
    }

    /**
     * 일반관리비 열이 비어 있어 그 품목이 기본 편성률 100%로 떨어지는 경우를 알립니다 (MIG-23②).
     *
     * <p>설계 §3.4는 "일반관리비 열이 비어 있으면 기존 편성률을 그대로 유지"라고 정했습니다. 그 규칙은 <b>유지할 기존값이 있을 때만</b> 성립합니다. 기존
     * 편성행이 없으면 {@code MigrationImportService.itemRates}가 그 비목 칸을 채우지 않고, {@code applyItemRates}의
     * {@code DEFAULT_DUP_RT = 100}이 실립니다 — 조정비율 0.7 사업이어도 일반관리비 품목만 100%로 편성됩니다.
     *
     * <p>판정 단위는 <b>품목이 아니라 비목코드</b>입니다. {@code itemRates}는 비목코드 칸에 편성률을 담고, 같은 비목의 품목 중 하나라도 기존
     * 편성률이 있으면 그 값이 보존되기 때문입니다. 그래서 "기존 편성률을 가진 품목이 하나도 없는 비목"이 있을 때만 경고합니다.
     *
     * <p>자본예산 시트만 대상입니다. 위임예산의 {@code costAmount}는 그 사업의 모든 품목이 배분 대상이라 자본 계열 밖 품목도 이미 편성률을 받고,
     * 일반관리비 열이라는 개념 자체가 없어 손댈 곳 없는 경고가 됩니다.
     */
    private void addGeneralRateDefaulted(
            List<MigrationDto.CellDiagnostic> out,
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AllocationIntent intent,
            List<MigrationYearSnapshot.RequestItem> all,
            MigrationYearSnapshot.Data snapshot) {
        if (sheet.kind() != SheetKind.CAPITAL_PROJECT
                || intent.targetByColumn().containsKey(GENERAL_AMOUNT_COLUMN)) {
            return;
        }
        // 비목코드 → 그 비목의 품목 중 기존 편성률을 가진 것이 있는지
        Map<String, Boolean> ratedByIoeC = new LinkedHashMap<>();
        for (MigrationYearSnapshot.RequestItem item :
                MigrationAllocationPlanner.itemsOutsideCapitalGroups(all)) {
            if (item.ioeC() == null) {
                continue;
            }
            boolean rated = snapshot.existingItemRateByItemNo().get(item.gclMngNo()) != null;
            ratedByIoeC.merge(item.ioeC(), rated, Boolean::logicalOr);
        }
        if (ratedByIoeC.containsValue(Boolean.FALSE)) {
            out.add(
                    MigrationDiagnostics.warning(
                            sheet,
                            row,
                            GENERAL_AMOUNT_COLUMN,
                            MigrationDiagnosticCode.GENERAL_RATE_DEFAULTED,
                            "일반관리비 열이 비어 있고 기존 편성률도 없어 일반관리비 품목이 100%로 편성됩니다. 조정비율을 적용하려면 일반관리비 열을 채워"
                                    + " 주세요."));
        }
    }

    /**
     * 원장을 만들 수 없는 시트에 {@code CREATE_NEW} 결정이 온 행의 진단을 만듭니다.
     *
     * <p>그 행은 편성 대상에서 빠지므로 조정이 통째로 사라집니다. 반영 자체를 막지는 않습니다 — 나머지 행은 정상이고, 관리자가 결정을 지우면 그대로 매칭 경로로
     * 돌아갑니다.
     *
     * @param sheet 시트 페이로드
     * @param excelRow 엑셀 행 번호
     * @return WARNING 진단
     */
    public MigrationDto.CellDiagnostic createNotSupported(
            MigrationDto.SheetPayload sheet, int excelRow) {
        return MigrationDiagnostics.warning(
                sheet,
                rowAt(sheet, excelRow),
                RowDecision.COLUMN,
                MigrationDiagnosticCode.CREATE_NOT_SUPPORTED,
                "이 시트는 원장을 새로 만들 수 없어 '새로 만들고 편성' 결정이 반영되지 않습니다. 이 행의 조정은 빠집니다 — 결정을 지우거나 대상 원장을 골라"
                        + " 주세요.");
    }

    /**
     * 원장을 새로 만드는 행의 일반관리비 목표액이 반영되지 않는다는 진단을 만듭니다 (MIG-23①).
     *
     * <p>{@code CapitalProjectSheetAdapter.items()}는 자본 3열만 품목으로 만듭니다 — 엑셀에 비자본 비목 구분(001 전산임차료·007
     * 국외전산용역비·013 국외회선사용료 등)이 없어 어느 비목으로 만들지 정할 근거가 없기 때문입니다. 그래서 {@code CREATE_NEW} 행에 일반관리비 열이
     * 채워져 있어도 그 목표액을 담을 품목이 없습니다.
     *
     * <p>반영을 막지는 않습니다 — 자본 3열은 정상적으로 만들어지고, 일반관리비만 빠집니다.
     *
     * @param sheet 시트 페이로드
     * @param excelRow 엑셀 행 번호
     * @return WARNING 진단
     */
    public MigrationDto.CellDiagnostic generalAmountNotCreatable(
            MigrationDto.SheetPayload sheet, int excelRow) {
        return MigrationDiagnostics.warning(
                sheet,
                rowAt(sheet, excelRow),
                GENERAL_AMOUNT_COLUMN,
                MigrationDiagnosticCode.GENERAL_AMOUNT_NOT_CREATABLE,
                "이 행은 원장을 새로 만들므로 일반관리비 목표액을 담을 품목이 없습니다. 일반관리비 편성은 편성요청서로 만든 원장에 매칭했을 때만 반영됩니다.");
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
        String rawRate = MigrationDiagnostics.cell(row, "adjustRate", overrides, sheet);
        BigDecimal rate = AdapterSupport.rateFraction(rawRate);
        if (!rawRate.isBlank() && rate == null) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            "adjustRate",
                            MigrationDiagnosticCode.RATE_UNPARSEABLE,
                            "조정비율 '" + rawRate + "'을 숫자로 읽지 못했습니다. 값을 고친 뒤 다시 검증해 주세요.",
                            List.of()));
            return out;
        }

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
                                MigrationDiagnosticCode.RATE_RECONCILE_MISMATCH,
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
        return MigrationAllocationPlanner.itemsForColumn(column, all);
    }

    private MigrationDto.CellDiagnostic baseZero(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, String column) {
        return MigrationDiagnostics.blocker(
                sheet,
                row,
                column,
                MigrationDiagnosticCode.ITEM_BASE_ZERO,
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
                            MigrationDiagnosticCode.AMOUNT_ADJUSTED,
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
