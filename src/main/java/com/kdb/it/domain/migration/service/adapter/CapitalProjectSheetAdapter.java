package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationIoeCodes;
import com.kdb.it.domain.migration.service.MigrationValidator;
import com.kdb.it.domain.migration.service.MigrationYearSnapshot;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 자본예산 편성 요구서 `1-1. 26년정보화사업(전산예산반영)` 시트를 사업·품목 생성요청으로 바꿉니다 (§5.3).
 *
 * <p>품목 비목은 엑셀에 국내/국외·일반/감리 구분이 없어 기본값({@link MigrationIoeCodes})을 넣고, 미리보기에서 보정할 수 있게 `{금액컬럼}IoeC`
 * 형태의 보정 키를 인정합니다. 예를 들어 개발비 비목을 감리(104)로 바꾸려면 컬럼 `devAmountIoeC`에 `104`를 보정합니다.
 *
 * <p>추진가능성(`EXE_PTT_YN` 1자)과 전결권(`IT_PTL_EDRT_TC` 2자)은 엑셀에 **라벨**로 적혀 있고 물리 컬럼은 코드값만 담을 수 있으므로 공통코드
 * 라벨→코드 변환을 거칩니다. 원문을 그대로 대입하면 `추진계획 검토중`(8자)에서 `ORA-12899`가 납니다. 변환에 실패한 값은 검증기가 이미
 * `CODE_UNRESOLVED`로 막았어야 하며, 여기서는 null로 두어 저장되지 않게 합니다.
 *
 * <p>배분 의도의 목표액은 자본 세 열에 더해 `일반관리비` 열까지 냅니다(설계 §3.4의 네 번째 그룹). 1단계 편성요청서 반입이 일반관리비 계열 품목도 같은 {@code
 * BITEMM}에 담으므로, 이 열을 내지 않으면 그 품목들만 조정비율을 못 받고 기본 편성률 100%로 조용히 편성됩니다. <b>열이 비어 있으면 키를 넣지 않아</b> 기존
 * 편성률이 그대로 유지됩니다.
 *
 * <p>다만 {@code items()}는 일반관리비 품목을 만들지 않습니다 — 엑셀에 비목 구분이 없어 {@code 001}·{@code 007}·{@code 013} 중
 * 어느 것인지 정할 근거가 없습니다. 그래서 {@code CREATE_NEW}로 원장을 새로 만드는 예외 경로에서 일반관리비 열이 채워져 있으면 배분 대상 품목이 없어 그 열의
 * 목표액이 반영되지 않습니다({@code MigrationImportService}의 생성 후 재계산이 BLOCKER로 잡아 로그로 남깁니다). 정상 경로(1단계가 만든 원장에
 * 매칭)에서는 그 품목이 이미 있으므로 해당하지 않습니다.
 */
@Component
public class CapitalProjectSheetAdapter implements SheetAdapter {

    @Override
    public SheetKind supports() {
        return SheetKind.CAPITAL_PROJECT;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        List<ProjectDto.CreateRequest> projects = new ArrayList<>();
        List<AllocationIntent> allocations = new ArrayList<>();

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String projectName = AdapterSupport.cellOf(sheet, row, "projectName", ctx);
            String deptCode = orgCode(sheet, row, "deptName", ctx);
            String itOrgCode = orgCode(sheet, row, "itTeamName", ctx);

            ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
            request.setBseYy(ctx.bseYy());
            request.setAbusNm(projectName);
            request.setBzTpC(AdapterSupport.cellOf(sheet, row, "projectType", ctx));
            request.setAbusCone(AdapterSupport.cellOf(sheet, row, "projectOutline", ctx));
            request.setPrlmHrkOgzCCone(AdapterSupport.cellOf(sheet, row, "headquarters", ctx));
            request.setSvnDpmC(deptCode);
            request.setSvnTemC(orgCode(sheet, row, "teamName", ctx));
            // 담당IT팀 한 열이 개발부서·개발팀 두 컬럼을 채운다 (§5.3) — 조직 코드 체계가 부서·팀을 나누지 않는다
            request.setDvmDpmC(itOrgCode);
            request.setDvmTemC(itOrgCode);
            request.setUsid(resolveUser(sheet, row, "managerName", deptCode, ctx));
            request.setTlrUsid(resolveUser(sheet, row, "teamLeaderName", deptCode, ctx));
            request.setSttDtm(
                    AdapterSupport.ymToFirstDay(AdapterSupport.cellOf(sheet, row, "startYm", ctx)));
            request.setEndDtm(
                    AdapterSupport.ymToLastDay(AdapterSupport.cellOf(sheet, row, "endYm", ctx)));
            request.setExePttYn(
                    AdapterSupport.resolveCode(
                            ctx.index().exePttCodeByName(),
                            AdapterSupport.cellOf(sheet, row, "feasibility", ctx)));
            request.setEdrtTc(
                    AdapterSupport.resolveCode(
                            ctx.index().edrtCodeByName(),
                            AdapterSupport.cellOf(sheet, row, "delegationLabel", ctx)));
            request.setAbusTc(
                    AdapterSupport.abusTc(AdapterSupport.cellOf(sheet, row, "progressLabel", ctx)));
            request.setOdnYn("N");
            request.setItems(items(sheet, row, ctx));
            projects.add(request);

            BigDecimal rate =
                    AdapterSupport.rateFraction(
                            AdapterSupport.cellOf(sheet, row, "adjustRate", ctx));
            // 검증기가 malformed 비율을 BLOCKER로 막지만, 직접 호출되는 어댑터 경로도 1배로 오염시키지 않는다.
            if (rate == null) {
                rate = BigDecimal.ZERO;
            }
            BigDecimal dev = amountOf(sheet, row, ctx, "devAmount");
            BigDecimal hw = amountOf(sheet, row, ctx, "hwAmount");
            BigDecimal sw = amountOf(sheet, row, ctx, "swAmount");

            Map<String, BigDecimal> targets = new LinkedHashMap<>();
            targets.put("devAmount", dev.multiply(rate));
            targets.put("hwAmount", hw.multiply(rate));
            targets.put("swAmount", sw.multiply(rate));

            // 자본 세 그룹 밖 품목(1단계가 BITEMM에 함께 담은 일반관리비 계열 001·007·013 등)의 목표액이다.
            // 열이 비어 있으면 키를 넣지 않는다 — 설계 §3.4가 정한 "그 열이 비어 있으면 기존 편성률을 그대로
            // 유지"다. 0원을 넣으면 그 품목들이 조용히 0원으로 편성된다.
            BigDecimal general = declaredGeneral(sheet, row, ctx);
            if (general != null) {
                targets.put("generalAmount", general.multiply(rate));
            }

            allocations.add(
                    new AllocationIntent(
                            sheet.kind(),
                            row.excelRow(),
                            "BPROJM",
                            AllocationIntent.MatchKey.ofProjectName(
                                    MigrationYearSnapshot.normalizeName(projectName)),
                            targets,
                            // 기준액 대사(AMOUNT_ADJUSTED)의 상대는 targets가 고른 품목들의 요청 합계다.
                            // 일반관리비 열을 목표에 넣은 행은 그 품목들도 합계에 들어오므로 기준액에도 더한다.
                            general == null
                                    ? dev.add(hw).add(sw)
                                    : dev.add(hw).add(sw).add(general)));
        }
        return new AdapterOutput(List.of(), projects, List.of(), allocations);
    }

    /**
     * 종합본이 적어 낸 일반관리비 기준액을 원 단위로 읽습니다.
     *
     * @return 기준액. <b>열이 비어 있으면 null</b>이며, 호출자는 이때 목표액 키를 아예 넣지 않아 기존 편성률을 보존합니다(설계 §3.4)
     */
    private BigDecimal declaredGeneral(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, AdapterContext ctx) {
        String cell = AdapterSupport.cellOf(sheet, row, "generalAmount", ctx);
        return cell.isBlank() ? null : amountOf(sheet, row, ctx, "generalAmount");
    }

    /** 금액 셀을 원 단위로 읽습니다. 비었거나 음수면 0원입니다. */
    private BigDecimal amountOf(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AdapterContext ctx,
            String column) {
        BigDecimal amount =
                AdapterSupport.amount(AdapterSupport.cellOf(sheet, row, column, ctx), sheet.kind());
        return (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) ? BigDecimal.ZERO : amount;
    }

    /** 개발비·기계장치·기타무형 세 열 중 금액이 0보다 큰 것만 품목으로 만듭니다. */
    private List<ProjectDto.BitemmDto> items(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, AdapterContext ctx) {
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        addItem(items, sheet, row, ctx, "devAmount", MigrationIoeCodes.IOE_DEV, "개발비");
        addItem(items, sheet, row, ctx, "hwAmount", MigrationIoeCodes.IOE_HW, "기계장치");
        addItem(items, sheet, row, ctx, "swAmount", MigrationIoeCodes.IOE_SW, "기타무형자산");
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

    /** 셀(또는 보정값)을 조직코드로 해석합니다. 미해석이면 null. */
    private String orgCode(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            AdapterContext ctx) {
        return AdapterSupport.resolveOrgCode(AdapterSupport.cellOf(sheet, row, column, ctx), ctx);
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
