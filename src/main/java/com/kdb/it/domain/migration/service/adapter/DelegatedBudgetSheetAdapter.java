package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationAmounts;
import com.kdb.it.domain.migration.service.MigrationIoeCodes;
import com.kdb.it.domain.migration.service.MigrationValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 자본예산 편성 요구서 `2. 위임예산(경상)` 시트를 부점별 경상사업과 품목으로 바꿉니다 (§5.5).
 *
 * <p>부점명이 병합·공백 셀이라 프론트 파서가 forward-fill을 미리 채워 보내지만, 어댑터도 방어적으로 직전 행 값을 이어 씁니다. 이 시트에는 사업명·주관부서·
 * 담당자·기간이 없어 규칙으로 생성합니다 — 사업명은 `{연도}년 {부점명} 위임예산(경상)`, 기간은 해당 연도 전체, 담당자·IT담당자는 담당자 열이 없어 업로드 사용자
 * 사번을 그대로 넣습니다. 원화환산액이 이미 원 단위({@link com.kdb.it.domain.migration.service.MigrationAmounts})라 금액
 * 배수를 곱하지 않습니다. {@code XCR}은 설정하지 않습니다 — {@code ProjectService}가 다시 조회해 덮어쓰기 때문입니다.
 *
 * <p>다른 세 어댑터와 달리 이 어댑터의 {@link AdapterOutput}은 **행 단위가 아니라 부점 그룹 단위**입니다 — forward-fill로 묶은 부점
 * 그룹마다 사업·배분 의도를 하나씩 냅니다. {@code costs}/{@code projects}와 {@code allocations}는 서로 인덱스 평행이지만 {@code
 * sheet.rows()}와는 1:1이 아니므로, 행 번호로 역인덱싱하면 안 됩니다.
 */
@Component
public class DelegatedBudgetSheetAdapter implements SheetAdapter {

    @Override
    public SheetKind supports() {
        return SheetKind.DELEGATED_BUDGET;
    }

    @Override
    public AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx) {
        // 부점 등장 순서를 유지해야 사업 생성 순서가 엑셀과 같아진다
        Map<String, List<ProjectDto.BitemmDto>> itemsByBranch = new LinkedHashMap<>();
        Map<String, Integer> firstExcelRowByBranch = new LinkedHashMap<>();
        Map<String, BigDecimal> krwTotalByBranch = new LinkedHashMap<>();
        Map<String, String> labelByBranch = new LinkedHashMap<>();
        Map<String, String> deptCodeByBranch = new LinkedHashMap<>();
        String currentBranch = null;

        for (MigrationDto.NormalizedRow row : sheet.rows()) {
            String raw = AdapterSupport.cellOf(sheet, row, "branchName", ctx);
            if (!raw.isBlank()) {
                currentBranch = groupKey(raw, ctx);
                labelByBranch.putIfAbsent(currentBranch, branchLabel(raw, ctx));
                deptCodeByBranch.putIfAbsent(currentBranch, resolveOrg(raw, ctx));
            }
            if (currentBranch == null) {
                // 첫 행부터 부점명이 비면 귀속시킬 사업이 없다 — 검증이 이미 막았어야 한다
                continue;
            }
            String branch = Objects.requireNonNull(currentBranch);
            List<ProjectDto.BitemmDto> items =
                    itemsByBranch.computeIfAbsent(branch, key -> new ArrayList<>());
            firstExcelRowByBranch.putIfAbsent(branch, row.excelRow());
            String currency = AdapterSupport.cellOf(sheet, row, "currency", ctx);
            String itemName = AdapterSupport.cellOf(sheet, row, "itemName", ctx);
            addItem(items, sheet, row, ctx, currency, itemName, "hw");
            addItem(items, sheet, row, ctx, currency, itemName, "sw");
            krwTotalByBranch.merge(branch, rowKrwTotal(sheet, row, ctx), BigDecimal::add);
        }

        List<ProjectDto.CreateRequest> projects = new ArrayList<>();
        List<AllocationIntent> allocations = new ArrayList<>();
        itemsByBranch.forEach(
                (branch, items) -> {
                    String label = labelByBranch.getOrDefault(branch, branch);
                    String projectName = ctx.bseYy() + "년 " + label + " 위임예산(경상)";
                    ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
                    request.setBseYy(ctx.bseYy());
                    request.setAbusNm(projectName);
                    request.setOdnYn("Y");
                    request.setAbusTc("20");
                    String branchDeptCode = deptCodeByBranch.get(branch);
                    request.setSvnDpmC(branchDeptCode);
                    // 미리보기에서 부점 담당자를 골랐으면 그 사번을 쓴다 (MIG-03).
                    // 시트에 담당자 열이 없어 필수가 아니며, 미지정은 검증기가 WARNING으로 알리고
                    // 여기서는 종전대로 업로드 사용자로 채운다.
                    String owner =
                            ownerOverride(ctx, firstExcelRowByBranch.get(branch))
                                    .orElse(ctx.actorEno());
                    request.setUsid(owner);
                    request.setDvmUsid(owner);
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

    /**
     * 부점 그룹의 담당자 보정값을 읽습니다 (MIG-03).
     *
     * <p>담당자는 그룹 단위 값이라 보정도 그룹의 첫 행에 붙습니다 — 검증기가 경고를 낸 행과 같은 좌표여야 사용자가 고른 값이 여기로 들어옵니다.
     *
     * @param ctx 어댑터 실행 맥락
     * @param firstExcelRow 그룹의 첫 엑셀 행 번호
     * @return 고른 사번. 지정하지 않았으면 빈 Optional
     */
    private Optional<String> ownerOverride(AdapterContext ctx, Integer firstExcelRow) {
        if (firstExcelRow == null) {
            return Optional.empty();
        }
        String value =
                ctx.overrides()
                        .get(
                                MigrationValidator.overrideKey(
                                        SheetKind.DELEGATED_BUDGET,
                                        firstExcelRow,
                                        MigrationColumns.DELEGATED_OWNER_OVERRIDE));
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
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
     * 부점 그룹키를 정합니다 (MIG-04).
     *
     * <p><b>표기가 아니라 해석된 조직코드로 묶습니다.</b> 표기로 묶으면 같은 부점이 두 사업으로 쪼개집니다 — 보정을 건 행은 {@code cellOf}가
     * 조직코드를 돌려주고 {@link #branchLabel}이 그것을 조직 <b>정식명</b>으로 바꾸는데, 보정을 걸지 않은 형제 행은 엑셀 원문 표기를 그대로 씁니다.
     * {@code OrgIdentityResolver.resolveOrg}의 부분 일치 단계가 엑셀 `런던`을 정식명 `런던지점`으로 확정하는 경우처럼 둘이 다르면 그룹이
     * 갈립니다. 두 그룹 모두 주관부서가 같은 코드로 해석되므로 검증도 통과해 조용히 지나갑니다.
     *
     * @param raw 부점명 셀 값 또는 보정값 (공백이 아님)
     * @param ctx 어댑터 컨텍스트
     * @return 조직코드. 해석되지 않으면 표기 그대로(검증이 BLOCKER로 막을 상태)
     */
    private String groupKey(String raw, AdapterContext ctx) {
        String code = resolveOrg(raw, ctx);
        return code != null ? code : raw;
    }

    /**
     * 사업명에 쓸 부점 표시명을 정합니다.
     *
     * <p>부점명 셀에 보정이 걸리면 {@code cellOf}가 조직**코드**를 돌려줍니다. 그 값을 그대로 쓰면 사업명이 `2026년 0910 위임예산(경상)`이
     * 되므로 조직명으로 되돌립니다. {@code ABUS_NM}은 사람이 읽는 이름이어야 합니다.
     *
     * <p>표시명은 그룹 <b>안에서 먼저 등장한 행</b>의 것만 씁니다({@code labelByBranch.putIfAbsent}) — 그룹핑 자체는 {@link
     * #groupKey}가 조직코드로 하므로 표시명이 행마다 달라도 사업은 하나입니다.
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
