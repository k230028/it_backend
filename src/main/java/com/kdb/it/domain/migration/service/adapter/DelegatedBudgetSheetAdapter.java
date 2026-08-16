package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationAmounts;
import com.kdb.it.domain.migration.service.MigrationIoeCodes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 자본예산 편성 요구서 `2. 위임예산(경상)` 시트를 부점별 경상사업과 품목으로 바꿉니다 (§5.5).
 *
 * <p>부점명이 병합·공백 셀이라 프론트 파서가 forward-fill을 미리 채워 보내지만, 어댑터도 방어적으로 직전 행 값을 이어 씁니다. 이 시트에는 사업명·주관부서·
 * 담당자·기간이 없어 규칙으로 생성합니다 — 사업명은 `{연도}년 {부점명} 위임예산(경상)`, 기간은 해당 연도 전체, 담당자·IT담당자는 담당자 열이 없어 업로드 사용자
 * 사번을 그대로 넣습니다. 원화환산액이 이미 원 단위({@link com.kdb.it.domain.migration.service.MigrationAmounts})라 금액
 * 배수를 곱하지 않습니다. {@code XCR}은 설정하지 않습니다 — {@code ProjectService}가 다시 조회해 덮어쓰기 때문입니다.
 */
@Component
public class DelegatedBudgetSheetAdapter implements SheetAdapter {

    @Override
    public SheetKind supports() {
        return SheetKind.DELEGATED_BUDGET;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        // 부점명 등장 순서를 유지해야 사업 생성 순서가 엑셀과 같아진다
        Map<String, List<ProjectDto.BitemmDto>> itemsByBranch = new LinkedHashMap<>();
        Map<String, Integer> firstExcelRowByBranch = new LinkedHashMap<>();
        Map<String, BigDecimal> krwTotalByBranch = new LinkedHashMap<>();
        String currentBranch = null;

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String branch = branchLabel(AdapterSupport.cellOf(sheet, row, "branchName", ctx), ctx);
            if (!branch.isBlank()) {
                currentBranch = branch;
            }
            if (currentBranch == null) {
                // 첫 행부터 부점명이 비면 귀속시킬 사업이 없다 — 검증이 이미 막았어야 한다
                continue;
            }
            List<ProjectDto.BitemmDto> items =
                    itemsByBranch.computeIfAbsent(currentBranch, key -> new ArrayList<>());
            firstExcelRowByBranch.putIfAbsent(currentBranch, row.excelRow());
            String currency = AdapterSupport.cellOf(sheet, row, "currency", ctx);
            String itemName = AdapterSupport.cellOf(sheet, row, "itemName", ctx);
            addItem(items, sheet, row, ctx, currency, itemName, "hw");
            addItem(items, sheet, row, ctx, currency, itemName, "sw");
            krwTotalByBranch.merge(currentBranch, rowKrwTotal(sheet, row, ctx), BigDecimal::add);
        }

        List<ProjectDto.CreateRequest> projects = new ArrayList<>();
        List<AllocationIntent> allocations = new ArrayList<>();
        itemsByBranch.forEach(
                (branch, items) -> {
                    String projectName = ctx.bseYy() + "년 " + branch + " 위임예산(경상)";
                    ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
                    request.setBseYy(ctx.bseYy());
                    request.setAbusNm(projectName);
                    request.setOdnYn("Y");
                    request.setAbusTc("20");
                    String branchDeptCode = resolveOrg(branch, ctx);
                    request.setSvnDpmC(branchDeptCode);
                    request.setUsid(ctx.actorEno());
                    request.setDvmUsid(ctx.actorEno());
                    int year = Integer.parseInt(ctx.bseYy());
                    request.setSttDtm(LocalDate.of(year, 1, 1));
                    request.setEndDtm(LocalDate.of(year, 12, 31));
                    request.setItems(items);
                    projects.add(request);

                    // 위임예산 시트에는 조정비율 열이 없고 금액이 이미 원 단위 확정값이다. 편성률 100%가
                    // 곧 "적어 낸 금액 그대로"이므로 목표액을 금액 합계로 둔다(종전 RateIntent(…, 100)과 같다).
                    BigDecimal groupTotalKrw =
                            krwTotalByBranch.getOrDefault(branch, BigDecimal.ZERO);
                    allocations.add(
                            new AllocationIntent(
                                    sheet.kind(),
                                    firstExcelRowByBranch.get(branch),
                                    "BPROJM",
                                    AllocationIntent.MatchKey.ofOrdinaryDept(branchDeptCode),
                                    Map.of("costAmount", groupTotalKrw),
                                    groupTotalKrw));
                });
        return new AdapterOutput(List.of(), projects, List.of(), allocations);
    }

    /** 한 행의 HW·SW 원화환산액 합계입니다. 비었거나 음수인 항목은 0으로 접습니다. */
    private BigDecimal rowKrwTotal(
            MigrationDto.SheetPayload sheet, MigrationDto.NormalizedRow row, AdapterContext ctx) {
        return positiveKrw(sheet, row, ctx, "hwKrwAmount")
                .add(positiveKrw(sheet, row, ctx, "swKrwAmount"));
    }

    private BigDecimal positiveKrw(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AdapterContext ctx,
            String column) {
        BigDecimal amount =
                AdapterSupport.amount(AdapterSupport.cellOf(sheet, row, column, ctx), sheet.kind());
        return (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) ? BigDecimal.ZERO : amount;
    }

    /**
     * HW·SW 한쪽의 품목을 만듭니다. 금액이 0 이하(빈 쌍)면 품목을 만들지 않습니다.
     *
     * @param prefix `hw` 또는 `sw` — 컬럼 id 접두어이자 비목 선택 기준
     */
    private void addItem(
            List<ProjectDto.BitemmDto> items,
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            AdapterContext ctx,
            String currency,
            String itemName,
            String prefix) {
        BigDecimal krw =
                AdapterSupport.amount(
                        AdapterSupport.cellOf(sheet, row, prefix + "KrwAmount", ctx), sheet.kind());
        if (krw == null || krw.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC(
                "hw".equals(prefix)
                        ? MigrationIoeCodes.IOE_HW_OVERSEA
                        : MigrationIoeCodes.IOE_SW_OVERSEA);
        item.setGclNm(itemName);
        item.setQty(
                MigrationAmounts.number(AdapterSupport.cellOf(sheet, row, prefix + "Qty", ctx)));
        item.setCurC(currency.isBlank() ? "KRW" : currency);
        item.setFcAmt(
                AdapterSupport.foreignAmount(
                        AdapterSupport.cellOf(sheet, row, prefix + "FcAmount", ctx), currency));
        item.setAmt(krw);
        item.setXcrBseDt(ctx.bseYy() + "0101");
        items.add(item);
    }

    /**
     * 부점명 셀을 사람이 읽는 부점명으로 정규화합니다.
     *
     * <p>부점명 셀에 보정이 걸리면 {@code cellOf}가 조직**코드**를 돌려줍니다. 그 값을 그대로 쓰면 사업명이 `2026년 0910 위임예산(경상)`이
     * 되고, 같은 부점의 다른 행(원본 이름)과 그룹이 갈려 한 부점이 두 사업으로 쪼개집니다. {@code ABUS_NM}은 중복 판정과 부문계획 매칭의 자연키이므로
     * 반드시 사람이 읽는 부점명이어야 합니다.
     *
     * @param raw 셀 값 또는 보정값
     * @param ctx 어댑터 컨텍스트
     * @return 조직코드로 해석되면 그 조직명, 아니면 입력 그대로
     */
    private String branchLabel(String raw, AdapterContext ctx) {
        String name = ctx.index().org().orgNameOf(raw);
        return name != null ? name : raw;
    }

    /** 보정값이 있으면 그 조직코드를, 없으면 이름으로 해석합니다. 미해석이면 null. */
    private String resolveOrg(String branch, AdapterContext ctx) {
        return AdapterSupport.resolveOrgCode(branch, ctx);
    }
}
