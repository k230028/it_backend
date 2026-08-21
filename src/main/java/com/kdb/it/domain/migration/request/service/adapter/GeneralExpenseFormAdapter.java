package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDecisionKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.AmountUnitResolver;
import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 ③ `전산 일반관리비 편성요청서`를 전산업무비 생성 요청으로 바꿉니다.
 *
 * <p>A열(비목명)·B열(세부비목)은 병합이라 빈 행이 위 값을 이어받습니다. 비목은 두 값을 쌍으로 묶어 {@code CO_CDVA_SPS} 계층과 맞춥니다.
 *
 * <p>금액 단위는 자동 판정이 불가능합니다 — 헤더는 `천원`인데 원 단위로 적어 내는 부점이 있고 대사할 상대 시트가 없습니다. 배수가 지정되지 않으면 제안값을 계산해
 * 돌려주고 `UNIT_UNCERTAIN` 경고를 남깁니다.
 */
@Component
@RequiredArgsConstructor
public class GeneralExpenseFormAdapter implements FormSheetAdapter {

    /** 지급주기: 월. 월간 금액이 적혀 있으면 월납으로 봅니다. */
    private static final String CYCLE_MONTHLY = "M";

    /** 지급주기: 년. 월간이 비어 있으면 연납으로 봅니다. */
    private static final String CYCLE_YEARLY = "Y";

    /** JPY만 양식이 천엔 단위라 엔으로 폅니다. 그 밖의 외화는 통화 기본 단위 그대로입니다. */
    private static final long JPY_MULTIPLIER = 1_000L;

    /** 전산제비 단일 건의 정상 범위 상한. 초과하면 단위 오기로 보고 1/1000로 보정합니다. */
    private static final BigDecimal GENERAL_IT_EXPENSE_LIMIT = new BigDecimal("10000000000");

    private static final BigDecimal UNIT_TYPO_DIVISOR = new BigDecimal("1000");

    /** 증감사유 물리 컬럼의 최대 길이. */
    private static final int INCREASE_REASON_LIMIT = 200;

    private final SheetAnchorScanner scanner;
    private final MigrationIoeCatalogReader catalogReader;
    private final FormApproverReader approverReader;

    @Override
    public FormSheetKind trigger() {
        return FormSheetKind.GENERAL_EXPENSE;
    }

    @Override
    public FormAdapterOutput adapt(FormAdapterContext context) {
        Sheet sheet = context.sheets().get(FormSheetKind.GENERAL_EXPENSE);
        if (sheet == null) return FormAdapterOutput.empty();

        Optional<SheetAnchorScanner.HeaderMap> header =
                scanner.findHeader(
                        sheet, 0, columnAliases(), "expense", "contractName", "currency", "annual");
        if (header.isEmpty()) {
            return new FormAdapterOutput(
                    List.of(),
                    List.of(),
                    List.of(
                            RequestFormDto.FormDiagnostic.of(
                                    FormSheetKind.GENERAL_EXPENSE,
                                    null,
                                    null,
                                    RequestFormDiagnosticCode.ANCHOR_NOT_FOUND,
                                    "전산 일반관리비 시트에서 표 헤더를 찾지 못했습니다. 양식이 변형되었는지 확인해 주세요.",
                                    List.of())),
                    null);
        }

        List<GeneralExpenseRow> rows =
                new GeneralExpenseRowReader(sheet, header.get(), scanner).readAll();
        if (rows.isEmpty()) return FormAdapterOutput.empty();

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        // 통화를 먼저 확정한다. 단위 판정이 "원화 행이 있는가"를 근거로 삼으므로 순서를 뒤집을 수 없다
        List<MigrationDto.Candidate> currencyCandidates =
                catalogReader.candidates(CommonCodeGroups.CURRENCY, false);
        Map<Integer, String> currencies =
                resolveCurrencies(rows, context, currencyCandidates, diagnostics);
        AmountUnit unit = resolveUnit(context, rows, currencies, diagnostics);
        Optional<GeneralExpenseRow> unitTypoSource =
                rows.stream()
                        .filter(row -> "KRW".equals(currencies.get(row.excelRow())))
                        .filter(row -> isOversizedGeneralItExpense(row, unit.toWon(amountOf(row))))
                        .findFirst();
        boolean adjustSheetUnit = unitTypoSource.isPresent();
        unitTypoSource.ifPresent(
                row ->
                        diagnostics.add(
                                diagnostic(
                                        row,
                                        "costTotXpAmt",
                                        RequestFormDiagnosticCode.SUBSTITUTE_DROPPED,
                                        "전산제비 단일 건이 100억원을 초과하여 금액 단위 오타로 보고 같은 시트의 모든 원화 행을 1/1000로 보정했습니다.",
                                        List.of())));
        String responsible =
                FormPersonNames.fit(
                        approverReader.author(sheet),
                        "담당자",
                        FormSheetKind.GENERAL_EXPENSE,
                        diagnostics);
        List<CostDto.CreateRequest> costs = new ArrayList<>();
        for (GeneralExpenseRow row : rows) {
            costs.add(
                    toCreateRequest(
                            row,
                            context,
                            currencies.get(row.excelRow()),
                            unit,
                            adjustSheetUnit,
                            responsible,
                            diagnostics));
        }
        return new FormAdapterOutput(List.of(), List.copyOf(costs), List.copyOf(diagnostics), unit);
    }

    /**
     * 행마다 통화를 확정합니다.
     *
     * <p>보정값 → 시트값 순으로 보고, 공통코드 {@code CUR_C}의 코드값 집합에 없으면(빈칸 포함) 확정하지 않고 BLOCKER 진단을 냅니다. 빈칸을 원화로
     * 추정하지 않는 이유는 그 추정이 틀리면 금액이 들어가는 컬럼과 단위 경고 여부가 함께 틀리기 때문입니다. 양식에 `통화 구분` 칸이 있으므로 빈칸은 정상 기재가 아니라
     * 누락입니다.
     *
     * @param rows 시트 ③ 데이터 행
     * @param context 어댑터 실행 맥락 (보정값을 읽습니다)
     * @param candidates 통화 공통코드 후보. 진단에 그대로 실어 화면에서 고르게 합니다
     * @param diagnostics 진단 누적 목록. 미해석 행마다 1건씩 더합니다
     * @return 엑셀 행 번호 → 확정 통화. 확정하지 못한 행은 키가 없습니다
     */
    private Map<Integer, String> resolveCurrencies(
            List<GeneralExpenseRow> rows,
            FormAdapterContext context,
            List<MigrationDto.Candidate> candidates,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Set<String> codes = new LinkedHashSet<>();
        for (MigrationDto.Candidate candidate : candidates) codes.add(candidate.code());

        Map<Integer, String> resolved = new LinkedHashMap<>();
        for (GeneralExpenseRow row : rows) {
            String raw =
                    context.override(FormSheetKind.GENERAL_EXPENSE, row.excelRow(), "curC")
                            .orElseGet(row::currency);
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (codes.contains(normalized)) {
                resolved.put(row.excelRow(), normalized);
                continue;
            }
            diagnostics.add(
                    diagnostic(
                            row,
                            "curC",
                            RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            normalized.isEmpty()
                                    ? "통화 구분이 비어 있습니다. 통화를 골라 주세요."
                                    : "통화 구분 `%s`를 통화코드로 해석하지 못했습니다. 골라 주세요.".formatted(raw.trim()),
                            candidates));
        }
        return resolved;
    }

    /**
     * 사용자가 지정한 배수를 우선하고, 없으면 제안값을 계산해 확인 경고를 남깁니다.
     *
     * <p>전 행의 통화가 확정되었고 그중 원화가 하나도 없으면 <b>경고를 내지 않습니다.</b> 이 배수는 원화 행에만 걸리고 외화 행은 통화 기본 단위 그대로
     * {@code FC_AMT}로 가므로, 원화 행이 없는 시트(국외 점포 실측)에서는 어떤 값을 골라도 결과가 같습니다. 고를 이유가 없는 확인을 묻지 않습니다.
     *
     * <p>반대로 <b>미해석 행이 하나라도 남아 있으면 경고를 냅니다.</b> 그 행은 사람이 통화를 고치면 원화가 될 수 있어 "원화 행 없음"이 성립하지 않습니다. 이
     * 구분을 빼면 통화 칸이 빈 제출본에서 사전검증에 단위 확인이 뜨지 않고, 보정 후 반영에서 배수가 확인 없이 추정 적용됩니다.
     *
     * @param currencies {@link #resolveCurrencies} 결과. 엑셀 행 번호 → 확정 통화
     * @return 적용할 배수. 전 행의 통화가 확정되고 그중 원화 행이 없으면 {@code WON}(어느 값이든 결과가 같습니다)
     */
    private AmountUnit resolveUnit(
            FormAdapterContext context,
            List<GeneralExpenseRow> rows,
            Map<Integer, String> currencies,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        AmountUnit specified = context.entry().generalExpenseUnit();
        if (specified != null) return specified;
        AmountUnit declared = declaredUnit(context.sheets().get(FormSheetKind.GENERAL_EXPENSE));
        if (declared != null) return declared;

        List<BigDecimal> krwAmounts = krwAnnualAmounts(rows, currencies);
        // 통화가 미해석인 행은 보정 뒤 원화가 될 수 있다. "원화 행이 없다"고 단정할 수 있는 것은
        // 전 행의 통화가 확정된 때뿐이다. 이 조건을 빼면 통화 칸이 빈 제출본에서 사전검증에 단위
        // 확인이 뜨지 않고, 사용자가 통화를 KRW로 고쳐 반영하는 순간 배수가 확인 없이 추정 적용된다.
        if (krwAmounts.isEmpty() && currencies.size() == rows.size()) return AmountUnit.WON;

        AmountUnit suggested = AmountUnitResolver.suggestGeneralExpenseUnit(krwAmounts);
        diagnostics.add(
                RequestFormDto.FormDiagnostic.decide(
                        FormSheetKind.GENERAL_EXPENSE,
                        null,
                        "generalExpenseUnit",
                        null,
                        RequestFormDiagnosticCode.UNIT_UNCERTAIN,
                        "금액 단위를 %s 단위로 추정했습니다. 확인해 주세요.".formatted(suggested.label()),
                        List.of(),
                        RequestFormDecisionKind.AMOUNT_UNIT));
        return suggested;
    }

    /** 시트가 명시한 KRW 금액 단위를 찾습니다. 명시값은 금액 크기 추정보다 우선합니다. */
    private AmountUnit declaredUnit(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), 20);
        for (int row = 0; row <= lastRow; row++) {
            var current = sheet.getRow(row);
            if (current == null) continue;
            for (int column = 0; column < current.getLastCellNum(); column++) {
                String text = scanner.text(sheet, row, column).replaceAll("\\s+", "");
                if (!text.contains("단위")) continue;
                if (text.contains("백만원")) return AmountUnit.MILLION;
                if (text.contains("천원")) return AmountUnit.THOUSAND;
                if (text.contains("원")) return AmountUnit.WON;
            }
        }
        return null;
    }

    private CostDto.CreateRequest toCreateRequest(
            GeneralExpenseRow row,
            FormAdapterContext context,
            String currency,
            AmountUnit unit,
            boolean adjustSheetUnit,
            String responsible,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setBseYy(context.bseYy());
        request.setCttNm(row.contractName());
        request.setCttOppNm(row.counterparty());
        applyIncreaseReason(row, request, diagnostics);
        request.setCgprId(responsible);
        request.setCostSvnDpmC(context.resolvedDeptCode());
        request.setBgUntAbusC(context.entry().bgUntAbusC());
        request.setTmnYn("N");
        request.setXcrBseDt(context.bseYy() + "0101");
        request.setDfrCleC(row.monthly() != null ? CYCLE_MONTHLY : CYCLE_YEARLY);

        applyIoe(row, context, request, diagnostics);
        applyCurrencyAndAmount(row, currency, request, unit, adjustSheetUnit);
        applyFlags(row, request, diagnostics);
        return request;
    }

    /** 증감사유가 길이 제한을 넘을 때만 공백을 제거하고 물리 컬럼 길이에 맞춥니다. */
    private void applyIncreaseReason(
            GeneralExpenseRow row,
            CostDto.CreateRequest request,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (row.remarks() == null) {
            request.setIndRsn(null);
            return;
        }
        if (row.remarks().length() <= INCREASE_REASON_LIMIT) {
            request.setIndRsn(row.remarks());
            return;
        }
        String normalized = row.remarks().replaceAll("[\\s\\u00A0\\u3000]+", "");
        if (normalized.length() <= INCREASE_REASON_LIMIT) {
            request.setIndRsn(normalized);
            return;
        }
        request.setIndRsn(normalized.substring(0, INCREASE_REASON_LIMIT));
        diagnostics.add(
                diagnostic(
                        row,
                        "indRsn",
                        RequestFormDiagnosticCode.SUBSTITUTE_DROPPED,
                        "비고의 공백을 제거해도 %d자를 넘어 앞 %d자만 반입합니다."
                                .formatted(normalized.length(), INCREASE_REASON_LIMIT),
                        List.of()));
    }

    private void applyIoe(
            GeneralExpenseRow row,
            FormAdapterContext context,
            CostDto.CreateRequest request,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.GENERAL_EXPENSE, row.excelRow(), "ioeC");
        if (override.isPresent()) {
            if (context.ioeIndex().exists(override.get())) {
                request.setIoeC(override.get());
                return;
            }
            diagnostics.add(
                    diagnostic(
                            row,
                            "ioeC",
                            RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            "보정한 비목코드 `%s`가 존재하지 않습니다.".formatted(override.get()),
                            List.of()));
            return;
        }

        IoeHierarchyIndex.Resolution byPair =
                context.ioeIndex().resolveByDetail(row.midCategory(), row.detailName());
        if (byPair.code() != null) {
            request.setIoeC(byPair.code());
            return;
        }

        // (중분류, 세부) 쌍이 빗나가면 A열·B열을 각각 중분류로 한 번 더 본다. 부점이 세부비목 칸에 중분류를
        // 그대로 적어 내는 경우가 있고(런던 실측: `Machinery`), 그때는 통화의 국내·국외 구분이 두 번째 열쇠가
        // 된다. B열을 먼저 보는 이유는 그쪽이 더 구체적이기 때문이다.
        boolean domestic = !context.foreignBranch();
        for (String groupLabel : List.of(row.detailName(), row.midCategory())) {
            IoeHierarchyIndex.Resolution byGroup =
                    context.ioeIndex().resolveByGroup(groupLabel, domestic);
            if (byGroup.code() == null) continue;
            request.setIoeC(byGroup.code());
            // 쌍으로 확정한 게 아니라 중분류로 좁힌 값이므로 확인을 요청한다. 반영은 막지 않는다.
            // 단 대조표에 등록된 어휘(`Machinery` 등)가 후보 하나로 좁혀졌으면 묻지 않는다 — 확인된 대응이라
            // 추측이 아니고, 고를 대안도 없어서 경고가 사용자에게 줄 선택지가 없다. 경상(②)·자본(1-2)
            // 어댑터도 대안이 있을 때만 경고한다.
            boolean confirmed =
                    FormLexicon.hasIoeAlias(groupLabel) && byGroup.candidates().isEmpty();
            if (confirmed) return;
            diagnostics.add(
                    diagnostic(
                            row,
                            "ioeC",
                            RequestFormDiagnosticCode.CODE_DEFAULTED,
                            "비목 `%s`를 중분류 `%s`로 보고 `%s`로 정했습니다. 다른 비목이면 골라 주세요."
                                    .formatted(row.detailName(), groupLabel, byGroup.label()),
                            IoeCandidates.orAll(byGroup, context)));
            return;
        }

        IoeHierarchyIndex.Resolution fallback =
                context.ioeIndex().resolveByGroup(row.midCategory(), domestic);
        List<MigrationDto.Candidate> candidates =
                fallback.candidates().isEmpty() ? byPair.candidates() : fallback.candidates();
        if (candidates.isEmpty()) candidates = context.ioeIndex().allCandidates();
        boolean ambiguous = byPair.isAmbiguous() || !candidates.isEmpty();
        diagnostics.add(
                diagnostic(
                        row,
                        "ioeC",
                        ambiguous
                                ? RequestFormDiagnosticCode.CODE_AMBIGUOUS
                                : RequestFormDiagnosticCode.CODE_UNRESOLVED,
                        ambiguous
                                ? "비목 `%s`에 해당하는 코드가 여럿입니다. 하나를 골라 주세요.".formatted(row.detailName())
                                : "비목 `%s`를 찾지 못했습니다. 기존 비목 중에서 골라 주세요."
                                        .formatted(row.detailName()),
                        candidates));
    }

    /**
     * 통화와 금액을 채웁니다.
     *
     * <p>외화 행은 `FC_AMT`만 채우고 원화금액과 환율은 비워 둡니다. 서버 {@code BudgetAmountCalculator}가 `FC_AMT × Ccodem
     * 환율`로 재계산하므로 여기서 채우면 그 값이 그대로 버려집니다.
     *
     * @param currency 확정 통화. null이면 미해석 행이므로 아무것도 채우지 않습니다 (이미 BLOCKER 진단이 나가 파일이 차단됩니다)
     */
    private void applyCurrencyAndAmount(
            GeneralExpenseRow row,
            String currency,
            CostDto.CreateRequest request,
            AmountUnit unit,
            boolean adjustSheetUnit) {
        if (currency == null) return;
        request.setCurC(currency);
        BigDecimal amount = row.annual() != null ? row.annual() : row.monthly();
        if (amount == null) return;

        if ("KRW".equals(currency)) {
            BigDecimal won = unit.toWon(amount);
            if (adjustSheetUnit) won = won.divide(UNIT_TYPO_DIVISOR);
            request.setCostTotXpAmt(won);
            request.setFcAmt(null);
            return;
        }
        BigDecimal foreignAmount =
                "JPY".equals(currency)
                        ? amount.multiply(BigDecimal.valueOf(JPY_MULTIPLIER))
                        : amount;
        request.setFcAmt(foreignAmount);
        request.setCostTotXpAmt(null);
        request.setXcr(null);
    }

    private static boolean isOversizedGeneralItExpense(GeneralExpenseRow row, BigDecimal won) {
        if (won == null) return false;
        String expense =
                SheetAnchorScanner.normalize(FormLexicon.canonicalIoeName(row.midCategory()));
        return "전산제비".equals(expense) && won.compareTo(GENERAL_IT_EXPENSE_LIMIT) > 0;
    }

    private static BigDecimal amountOf(GeneralExpenseRow row) {
        return row.annual() != null ? row.annual() : row.monthly();
    }

    private void applyFlags(
            GeneralExpenseRow row,
            CostDto.CreateRequest request,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> infoSec = FormLexicon.toYn(row.infoSec());
        if (infoSec.isPresent()) {
            request.setSectSysUtzYn(infoSec.get());
        } else {
            diagnostics.add(
                    diagnostic(
                            row,
                            "sectSysUtzYn",
                            RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            "정보보호 여부 표기 `%s`를 해석하지 못했습니다.".formatted(row.infoSec()),
                            List.of()));
        }
        request.setAbusTc(
                FormLexicon.toAbusTc(row.continued(), row.isNew())
                        .orElse(CodeDefaults.NOT_APPLICABLE));
    }

    private RequestFormDto.FormDiagnostic diagnostic(
            GeneralExpenseRow row,
            String field,
            RequestFormDiagnosticCode code,
            String message,
            List<MigrationDto.Candidate> candidates) {
        return RequestFormDto.FormDiagnostic.about(
                FormSheetKind.GENERAL_EXPENSE,
                row.excelRow(),
                field,
                row.contractName(),
                code,
                message,
                candidates);
    }

    private static List<BigDecimal> krwAnnualAmounts(
            List<GeneralExpenseRow> rows, Map<Integer, String> currencies) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (GeneralExpenseRow row : rows) {
            if ("KRW".equals(currencies.get(row.excelRow()))) {
                BigDecimal amount = row.annual() != null ? row.annual() : row.monthly();
                if (amount != null) amounts.add(amount);
            }
        }
        return amounts;
    }

    private static Map<String, List<String>> columnAliases() {
        return FormLexicon.columnAliases(
                Map.of(
                        "expense", "비 목 명",
                        "contractName", "계약명 / 건명",
                        "currency", "통화 구분",
                        "monthly", "월간",
                        "annual", "연간",
                        "counterparty", "상대처",
                        "continued", "계속",
                        "isNew", "신규",
                        "infoSec", "정보보호 관련여부",
                        "remarks", "비고(증감사유, 적용환율 등)"));
    }
}
