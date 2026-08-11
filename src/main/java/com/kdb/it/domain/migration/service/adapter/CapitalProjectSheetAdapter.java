package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationValidator;
import com.kdb.it.domain.migration.service.MigrationYearSnapshot;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 자본예산 편성 요구서 `1-1. 26년정보화사업(전산예산반영)` 시트를 사업·품목 생성요청으로 바꿉니다 (§5.3).
 *
 * <p>품목 비목은 엑셀에 국내/국외·일반/감리 구분이 없어 기본값(개발비 103, 기계장치 101, 기타무형 106)을 넣고, 미리보기에서 보정할 수 있게
 * `{금액컬럼}IoeC` 형태의 보정 키를 인정합니다. 예를 들어 개발비 비목을 감리(104)로 바꾸려면 컬럼 `devAmountIoeC`에 `104`를 보정합니다.
 */
@Component
public class CapitalProjectSheetAdapter implements SheetAdapter {

    /** 개발비 기본 비목 — 개발비(일반). 감리/컨설팅은 104. */
    static final String IOE_DEV = "103";

    /** 기계장치 기본 비목 — 국내기계장치. 국외는 102. */
    static final String IOE_HW = "101";

    /** 기타무형자산 기본 비목 — 국내기타무형자산(일반). 국외는 105, SW라이선스는 107. */
    static final String IOE_SW = "106";

    @Override
    public SheetKind supports() {
        return SheetKind.CAPITAL_PROJECT;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        List<ProjectDto.CreateRequest> projects = new ArrayList<>();
        List<RateIntent> rates = new ArrayList<>();

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String projectName = AdapterSupport.cellOf(sheet, row, "projectName", ctx);
            String deptCode = resolveOrg(sheet, row, "deptName", ctx);

            ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
            request.setBseYy(ctx.bseYy());
            request.setAbusNm(projectName);
            request.setBzTpC(AdapterSupport.cellOf(sheet, row, "projectType", ctx));
            request.setAbusCone(AdapterSupport.cellOf(sheet, row, "projectOutline", ctx));
            request.setPrlmHrkOgzCCone(AdapterSupport.cellOf(sheet, row, "headquarters", ctx));
            request.setSvnDpmC(deptCode);
            request.setDvmDpmC(resolveOrg(sheet, row, "itTeamName", ctx));
            request.setUsid(resolveUser(sheet, row, "managerName", deptCode, ctx));
            request.setTlrUsid(resolveUser(sheet, row, "teamLeaderName", deptCode, ctx));
            request.setSttDtm(
                    AdapterSupport.ymToFirstDay(AdapterSupport.cellOf(sheet, row, "startYm", ctx)));
            request.setEndDtm(
                    AdapterSupport.ymToLastDay(AdapterSupport.cellOf(sheet, row, "endYm", ctx)));
            request.setExePttYn(AdapterSupport.cellOf(sheet, row, "feasibility", ctx));
            request.setAbusTc(
                    AdapterSupport.abusTc(AdapterSupport.cellOf(sheet, row, "progressLabel", ctx)));
            request.setOdnYn("N");
            request.setItems(items(sheet, row, ctx));
            projects.add(request);

            rates.add(
                    new RateIntent(
                            "BPROJM",
                            MigrationYearSnapshot.normalizeName(projectName),
                            AdapterSupport.ratePercent(
                                    AdapterSupport.cellOf(sheet, row, "adjustRate", ctx))));
        }
        return new AdapterOutput(List.of(), projects, List.of(), rates);
    }

    /** 개발비·기계장치·기타무형 세 열 중 금액이 0보다 큰 것만 품목으로 만듭니다. */
    private List<ProjectDto.BitemmDto> items(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, AdapterContext ctx) {
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        addItem(items, sheet, row, ctx, "devAmount", IOE_DEV, "개발비");
        addItem(items, sheet, row, ctx, "hwAmount", IOE_HW, "기계장치");
        addItem(items, sheet, row, ctx, "swAmount", IOE_SW, "기타무형자산");
        return items;
    }

    private void addItem(
            List<ProjectDto.BitemmDto> items,
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AdapterContext ctx,
            String amountColumn,
            String defaultIoeC,
            String itemLabel) {
        BigDecimal amount =
                AdapterSupport.amount(
                        AdapterSupport.cellOf(sheet, row, amountColumn, ctx), sheet.kind());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String ioeOverride =
                ctx.overrides()
                        .get(
                                MigrationValidator.overrideKey(
                                        sheet.kind(), row.excelRow(), amountColumn + "IoeC"));
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC(ioeOverride != null ? ioeOverride : defaultIoeC);
        item.setGclNm(itemLabel);
        item.setCurC("KRW");
        item.setAmt(amount);
        item.setXcrBseDt(ctx.bseYy() + "0101");
        items.add(item);
    }

    /** 보정값이 있으면 그 조직코드를, 없으면 이름으로 해석합니다. 미해석이면 null. */
    private String resolveOrg(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            AdapterContext ctx) {
        String raw = AdapterSupport.cellOf(sheet, row, column, ctx);
        OrgIdentityResolver.Resolution resolution = ctx.index().org().resolveOrg(raw);
        if (resolution.code() != null) {
            return resolution.code();
        }
        return ctx.index().org().orgNameOf(raw) != null ? raw : null;
    }

    /** 보정값이 있으면 그 사번을, 없으면 이름(+부서 힌트)으로 해석합니다. 미해석이면 null. */
    private String resolveUser(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            String deptHint,
            AdapterContext ctx) {
        String raw = AdapterSupport.cellOf(sheet, row, column, ctx);
        OrgIdentityResolver.Resolution resolution = ctx.index().org().resolveUser(raw, deptHint);
        if (resolution.code() != null) {
            return resolution.code();
        }
        return ctx.index().org().teamOfUser(raw) != null ? raw : null;
    }
}
