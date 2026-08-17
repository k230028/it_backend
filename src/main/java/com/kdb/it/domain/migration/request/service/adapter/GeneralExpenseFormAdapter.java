package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final SheetAnchorScanner scanner;
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
        AmountUnit unit = resolveUnit(context, rows, diagnostics);
        // 상단 머리말의 작성자가 이 시트의 담당자다. 없으면 비워 둔다
        String author =
                FormPersonNames.fit(
                        approverReader.author(sheet),
                        "작성자",
                        FormSheetKind.GENERAL_EXPENSE,
                        diagnostics);

        List<CostDto.CreateRequest> costs = new ArrayList<>();
        for (GeneralExpenseRow row : rows) {
            costs.add(toCreateRequest(row, context, unit, author, diagnostics));
        }
        return new FormAdapterOutput(List.of(), List.copyOf(costs), List.copyOf(diagnostics), unit);
    }

    /** 사용자가 지정한 배수를 우선하고, 없으면 제안값을 계산해 확인 경고를 남깁니다. */
    private AmountUnit resolveUnit(
            FormAdapterContext context,
            List<GeneralExpenseRow> rows,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        AmountUnit specified = context.entry().generalExpenseUnit();
        if (specified != null) return specified;

        AmountUnit suggested = AmountUnitResolver.suggestGeneralExpenseUnit(krwAnnualAmounts(rows));
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

    private CostDto.CreateRequest toCreateRequest(
            GeneralExpenseRow row,
            FormAdapterContext context,
            AmountUnit unit,
            String author,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setBseYy(context.bseYy());
        request.setCttNm(row.contractName());
        request.setCttOppNm(row.counterparty());
        request.setIndRsn(row.remarks());
        // 적혀 있지 않으면 비워 둔다 — 업로드 사용자를 담당자로 박으면 원장에 사실이 아닌 이름이 남는다.
        // 확인자(주관팀장)는 `BCOSTM`에 담을 컬럼이 없어 반입하지 않는다.
        request.setCgprId(author);
        request.setCostSvnDpmC(context.resolvedDeptCode());
        request.setBgUntAbusC(context.entry().bgUntAbusC());
        request.setTmnYn("N");
        request.setXcrBseDt(context.bseYy() + "0101");
        request.setDfrCleC(row.monthly() != null ? CYCLE_MONTHLY : CYCLE_YEARLY);

        applyIoe(row, context, request, diagnostics);
        applyCurrencyAndAmount(row, request, unit);
        applyFlags(row, request, diagnostics);
        return request;
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
     */
    private void applyCurrencyAndAmount(
            GeneralExpenseRow row, CostDto.CreateRequest request, AmountUnit unit) {
        String currency = row.currency();
        request.setCurC(currency);
        if (row.annual() == null) return;

        if ("KRW".equalsIgnoreCase(currency)) {
            request.setCostTotXpAmt(unit.toWon(row.annual()));
            request.setFcAmt(null);
            return;
        }
        BigDecimal foreignAmount =
                "JPY".equalsIgnoreCase(currency)
                        ? row.annual().multiply(BigDecimal.valueOf(JPY_MULTIPLIER))
                        : row.annual();
        request.setFcAmt(foreignAmount);
        request.setCostTotXpAmt(null);
        request.setXcr(null);
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

    private static List<BigDecimal> krwAnnualAmounts(List<GeneralExpenseRow> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (GeneralExpenseRow row : rows) {
            if ("KRW".equalsIgnoreCase(row.currency()) && row.annual() != null) {
                amounts.add(row.annual());
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
