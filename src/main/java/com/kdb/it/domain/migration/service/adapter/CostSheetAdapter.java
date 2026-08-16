package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 전산일반관리비 편성 요구서 `전체취합(국내외)` 시트를 {@code BCOSTM} 생성요청으로 바꿉니다 (§5.2).
 *
 * <p>이 시트에는 담당자 열이 없어 업로드 사용자 사번을 담당자로 넣습니다. {@code XCR}은 설정하지 않습니다 — {@code CostService}가 {@code
 * XcrLookupService}로 다시 조회해 덮어쓰기 때문입니다(§3.7).
 */
@Component
public class CostSheetAdapter implements SheetAdapter {

    @Override
    public SheetKind supports() {
        return SheetKind.COST;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        List<CostDto.CreateRequest> costs = new ArrayList<>();
        List<AllocationIntent> allocations = new ArrayList<>();

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String currency = AdapterSupport.cellOf(sheet, row, "currency", ctx);
            String ioeC = resolveIoe(sheet, row, ctx);
            String deptCode =
                    AdapterSupport.resolveOrgCode(
                            AdapterSupport.cellOf(sheet, row, "deptName", ctx), ctx);
            String teamCode =
                    AdapterSupport.resolveOrgCode(
                            AdapterSupport.cellOf(sheet, row, "teamName", ctx), ctx);
            String vendor = AdapterSupport.cellOf(sheet, row, "vendorName", ctx);
            String contractName = AdapterSupport.cellOf(sheet, row, "requestDetail", ctx);
            String abusCode = AdapterSupport.cellOf(sheet, row, "abusCode", ctx);

            CostDto.CreateRequest request = new CostDto.CreateRequest();
            request.setBseYy(ctx.bseYy());
            request.setBgUntAbusC(abusCode);
            request.setIoeC(ioeC);
            request.setAbusTc(
                    AdapterSupport.abusTc(AdapterSupport.cellOf(sheet, row, "abusTcLabel", ctx)));
            request.setCttOppNm(vendor);
            request.setCttNm(contractName);
            request.setSectSysUtzYn(
                    AdapterSupport.flag(AdapterSupport.cellOf(sheet, row, "securityFlag", ctx)));
            request.setTmnYn(
                    AdapterSupport.flag(AdapterSupport.cellOf(sheet, row, "terminalFlag", ctx)));
            request.setCostSvnDpmC(deptCode);
            request.setSvnTemC(teamCode);
            request.setCurC(currency.isBlank() ? "KRW" : currency);
            request.setFcAmt(
                    AdapterSupport.foreignAmount(
                            AdapterSupport.cellOf(sheet, row, "fcAmount", ctx), currency));
            request.setCostTotXpAmt(
                    AdapterSupport.amount(
                            AdapterSupport.cellOf(sheet, row, "krwAmount", ctx), SheetKind.COST));
            request.setXcrBseDt(ctx.bseYy() + "0101");
            request.setIndRsn(AdapterSupport.cellOf(sheet, row, "remark", ctx));
            request.setCgprId(ctx.actorEno());
            costs.add(request);

            BigDecimal requestAmount = request.getCostTotXpAmt();
            BigDecimal base = requestAmount == null ? BigDecimal.ZERO : requestAmount;
            BigDecimal rate = ctx.index().generalExpenseRate();

            allocations.add(
                    new AllocationIntent(
                            sheet.kind(),
                            row.excelRow(),
                            "BCOSTM",
                            AllocationIntent.MatchKey.ofCost(deptCode, ioeC, vendor, contractName),
                            Map.of(
                                    "costAmount",
                                    base.multiply(rate)
                                            .divide(
                                                    BigDecimal.valueOf(100),
                                                    3,
                                                    RoundingMode.HALF_UP)),
                            base));
        }
        return new AdapterOutput(costs, List.of(), List.of(), allocations);
    }

    /** 보정값이 있으면 그 코드값을, 없으면 비목명으로 코드를 찾습니다. 못 찾으면 null(검증이 이미 막았어야 합니다). */
    private String resolveIoe(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, AdapterContext ctx) {
        String raw = AdapterSupport.cellOf(sheet, row, "ioeName", ctx);
        String mapped = ctx.index().ioeCodeByName().get(raw);
        return mapped != null ? mapped : (raw.isBlank() ? null : raw);
    }
}
