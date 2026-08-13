package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
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
        long multiplier = resolveMultiplier(context, rows, diagnostics);

        List<CostDto.CreateRequest> costs = new ArrayList<>();
        for (GeneralExpenseRow row : rows) {
            costs.add(toCreateRequest(row, context, multiplier, diagnostics));
        }
        return new FormAdapterOutput(
                List.of(), List.copyOf(costs), List.copyOf(diagnostics), multiplier);
    }

    /** 사용자가 지정한 배수를 우선하고, 없으면 제안값을 계산해 확인 경고를 남깁니다. */
    private long resolveMultiplier(
            FormAdapterContext context,
            List<GeneralExpenseRow> rows,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Long specified = context.entry().generalExpenseMultiplier();
        if (specified != null) return specified;

        long suggested = AmountUnitResolver.suggestGeneralExpenseMultiplier(krwAnnualAmounts(rows));
        diagnostics.add(
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.GENERAL_EXPENSE,
                        null,
                        "generalExpenseMultiplier",
                        RequestFormDiagnosticCode.UNIT_UNCERTAIN,
                        "금액 단위를 %s 단위로 추정했습니다. 확인해 주세요.".formatted(unitLabel(suggested)),
                        List.of()));
        return suggested;
    }

    private CostDto.CreateRequest toCreateRequest(
            GeneralExpenseRow row,
            FormAdapterContext context,
            long multiplier,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setBseYy(context.bseYy());
        request.setCttNm(row.contractName());
        request.setCttOppNm(row.counterparty());
        request.setIndRsn(row.remarks());
        request.setCgprId(context.actorEno());
        request.setCostSvnDpmC(context.resolvedDeptCode());
        request.setBgUntAbusC(context.entry().bgUntAbusC());
        request.setTmnYn("N");
        request.setXcrBseDt(context.bseYy() + "0101");
        request.setDfrCleC(row.monthly() != null ? CYCLE_MONTHLY : CYCLE_YEARLY);

        applyIoe(row, context, request, diagnostics);
        applyCurrencyAndAmount(row, request, multiplier);
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

        IoeHierarchyIndex.Resolution resolution =
                context.ioeIndex().resolveByDetail(row.midCategory(), row.detailName());
        if (resolution.code() != null) {
            request.setIoeC(resolution.code());
            return;
        }
        boolean ambiguous = resolution.isAmbiguous();
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
                        resolution.candidates()));
    }

    /**
     * 통화와 금액을 채웁니다.
     *
     * <p>외화 행은 `FC_AMT`만 채우고 원화금액과 환율은 비워 둡니다. 서버 {@code BudgetAmountCalculator}가 `FC_AMT × Ccodem
     * 환율`로 재계산하므로 여기서 채우면 그 값이 그대로 버려집니다.
     */
    private void applyCurrencyAndAmount(
            GeneralExpenseRow row, CostDto.CreateRequest request, long multiplier) {
        String currency = row.currency();
        request.setCurC(currency);
        if (row.annual() == null) return;

        if ("KRW".equalsIgnoreCase(currency)) {
            request.setCostTotXpAmt(AmountUnitResolver.applyMultiplier(row.annual(), multiplier));
            request.setFcAmt(null);
            return;
        }
        long foreignMultiplier = "JPY".equalsIgnoreCase(currency) ? JPY_MULTIPLIER : 1L;
        request.setFcAmt(AmountUnitResolver.applyMultiplier(row.annual(), foreignMultiplier));
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
        return RequestFormDto.FormDiagnostic.of(
                FormSheetKind.GENERAL_EXPENSE, row.excelRow(), field, code, message, candidates);
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

    private static String unitLabel(long multiplier) {
        if (multiplier == AmountUnitResolver.UNIT_MILLION) return "백만원";
        if (multiplier == AmountUnitResolver.UNIT_THOUSAND) return "천원";
        return "원";
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
