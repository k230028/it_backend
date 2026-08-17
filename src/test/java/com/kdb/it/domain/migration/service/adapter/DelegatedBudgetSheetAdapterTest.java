package com.kdb.it.domain.migration.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationLookupIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import com.kdb.it.domain.migration.service.TestSnapshots;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 위임예산 시트 → 경상 사업·품목 변환 규칙을 고정합니다 (§5.5). */
class DelegatedBudgetSheetAdapterTest {

    private final DelegatedBudgetSheetAdapter adapter = new DelegatedBudgetSheetAdapter();

    @Test
    @DisplayName("부점별로 사업 1건을 만들고 사업명·경상여부·기간을 규칙대로 채운다")
    void 부점별_경상사업을_만든다() {
        AdapterOutput out = adapter.adapt(sheet(londonRows()), context());

        assertThat(out.projects()).hasSize(2);
        assertThat(out.projects())
                .extracting(ProjectDto.CreateRequest::getAbusNm)
                .containsExactly("2026년 런던 위임예산(경상)", "2026년 런던 PF 위임예산(경상)");
        assertThat(out.projects())
                .allSatisfy(
                        p -> {
                            assertThat(p.getOdnYn()).isEqualTo("Y");
                            assertThat(p.getAbusTc()).isEqualTo("20");
                            assertThat(p.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
                            assertThat(p.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
                            assertThat(p.getUsid()).isEqualTo("999999");
                        });
    }

    @Test
    @DisplayName("원화환산액은 이미 원 단위라 배수를 곱하지 않는다")
    void 원화환산액을_그대로_쓴다() {
        ProjectDto.BitemmDto item =
                adapter.adapt(sheet(londonRows()), context()).projects().get(0).getItems().get(0);

        assertThat(item.getAmt()).isEqualByComparingTo(new BigDecimal("44919320.16"));
    }

    @Test
    @DisplayName("HW 행은 국외기계장치 102, SW 행은 국외기타무형자산 105로 만든다")
    void 하드웨어와_소프트웨어_비목을_구분한다() {
        List<ProjectDto.BitemmDto> items =
                adapter.adapt(sheet(londonRows()), context()).projects().get(0).getItems();

        assertThat(items).extracting(ProjectDto.BitemmDto::getIoeC).containsExactly("102", "105");
    }

    @Test
    @DisplayName("수량·통화·외화금액을 품목에 옮긴다")
    void 수량과_외화금액을_옮긴다() {
        ProjectDto.BitemmDto item =
                adapter.adapt(sheet(londonRows()), context()).projects().get(0).getItems().get(0);

        assertThat(item.getQty()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(item.getCurC()).isEqualTo("GBP");
        assertThat(item.getFcAmt()).isEqualByComparingTo(new BigDecimal("23346.84"));
    }

    @Test
    @DisplayName("부점명이 빈 행은 직전 행 부점을 이어 쓴다")
    void 부점명_공백행은_직전값을_잇는다() {
        List<MigrationDto.NormalizedRow> rows =
                List.of(
                        row(2, hwCells("런던", "데스크탑(고사양)", "12", "23346.84", "44919320.16")),
                        row(3, hwCells("", "데스크탑(일반사양)", "82", "68569.22", "131927179.28")));

        AdapterOutput out = adapter.adapt(sheet(rows), context());

        assertThat(out.projects()).hasSize(1);
        assertThat(out.projects().get(0).getItems()).hasSize(2);
    }

    @Test
    @DisplayName("HW·SW 금액이 모두 0인 행은 품목을 만들지 않는다")
    void 금액이_없는_행은_품목을_만들지_않는다() {
        Map<String, String> cells = hwCells("런던", "빈 항목", "0", "0", "0");

        AdapterOutput out = adapter.adapt(sheet(List.of(row(2, cells))), context());

        assertThat(out.projects().get(0).getItems()).isEmpty();
    }

    @Test
    @DisplayName("부점별로 HW·SW 원화합계를 목표액으로 하는 AllocationIntent를 남긴다")
    void 부점별_편성배분의도를_남긴다() {
        AdapterOutput out = adapter.adapt(sheet(londonRows()), context());

        assertThat(out.allocations()).hasSize(2);
        AllocationIntent london = out.allocations().get(0);
        assertThat(london.orcTb()).isEqualTo("BPROJM");
        assertThat(london.matchKey().type())
                .isEqualTo(AllocationIntent.MatchKey.Type.ORDINARY_DEPT);
        assertThat(london.matchKey().deptCode()).isEqualTo("0910");
        // 런던 그룹: hw(row2) 44919320.16 + sw(row3) 102308469.12
        assertThat(london.targetByColumn().get("costAmount")).isEqualByComparingTo("147227789.28");
        assertThat(london.declaredBase()).isEqualByComparingTo("147227789.28");

        AllocationIntent londonPf = out.allocations().get(1);
        assertThat(londonPf.matchKey().deptCode()).isEqualTo("0911");
        assertThat(londonPf.targetByColumn().get("costAmount")).isEqualByComparingTo("3098409.6");
    }

    @Test
    @DisplayName("부점명 셀이 이미 조직코드 형태로 오면(보정값) 그 코드를 그대로 주관부서로 쓴다")
    void 부점명이_이미_코드형태면_그대로_쓴다() {
        // "0910"은 이름으로는 매칭되지 않지만 org 카탈로그에 실재하는 코드다 — resolveOrg 1~3단계
        // 실패 후 orgNameOf(branch) fallback으로 확정되어야 한다 (MIG-04와 같은 경로).
        AdapterOutput out =
                adapter.adapt(
                        sheet(List.of(row(2, hwCells("0910", "데스크탑", "1", "100", "100000")))),
                        context());

        assertThat(out.projects()).hasSize(1);
        assertThat(out.projects().get(0).getSvnDpmC()).isEqualTo("0910");
    }

    @Test
    @DisplayName("부점명이 이름으로도 코드로도 해석되지 않으면 주관부서를 null로 둔다")
    void 부점명이_전혀_해석되지_않으면_null이다() {
        AdapterOutput out =
                adapter.adapt(
                        sheet(List.of(row(2, hwCells("없는부점", "데스크탑", "1", "100", "100000")))),
                        context());

        assertThat(out.projects()).hasSize(1);
        assertThat(out.projects().get(0).getSvnDpmC()).isNull();
    }

    @Test
    @DisplayName("HW·SW 금액 셀이 비어 있으면(파싱 불가) 품목을 만들지 않는다")
    void 금액셀이_비어있으면_품목을_만들지_않는다() {
        Map<String, String> cells = hwCells("런던", "빈 항목", "0", "0", "");

        AdapterOutput out = adapter.adapt(sheet(List.of(row(2, cells))), context());

        assertThat(out.projects().get(0).getItems()).isEmpty();
    }

    private static List<MigrationDto.NormalizedRow> londonRows() {
        return List.of(
                row(2, hwCells("런던", "데스크탑(고사양)", "12", "23346.84", "44919320.16")),
                row(3, swCells("런던", "MS오피스", "99", "53174.88", "102308469.12")),
                row(4, hwCells("런던 PF", "내부망 PC", "2", "1610.4", "3098409.6")));
    }

    /**
     * 부점명에 보정을 걸면 {@code cellOf}가 조직**코드**를 돌려준다. 그 값을 사업명에 그대로 쓰면 `2026년 0910 위임예산(경상)`이 되고, 같은
     * 부점의 다른 행(원본 이름)과 그룹이 갈려 한 부점이 두 사업으로 쪼개진다. {@code ABUS_NM}은 중복 판정과 부문계획 매칭의 자연키다
     * (IMPORTANT-6).
     */
    @Test
    @DisplayName("부점명 보정값(조직코드)도 사업명은 사람이 읽는 부점명으로 만들고 같은 그룹에 붙인다")
    void 부점명_보정값도_사업명은_부점명이다() {
        Map<String, String> overrides =
                Map.of(
                        com.kdb.it.domain.migration.service.MigrationValidator.overrideKey(
                                SheetKind.DELEGATED_BUDGET, 3, "branchName"),
                        "0910");

        AdapterOutput out =
                adapter.adapt(
                        sheet(
                                List.of(
                                        row(2, hwCells("런던", "데스크탑", "1", "1000", "1924000")),
                                        row(3, swCells("", "MS오피스", "1", "1000", "1924000")))),
                        contextWith(overrides));

        assertThat(out.projects())
                .singleElement()
                .satisfies(
                        project -> {
                            assertThat(project.getAbusNm()).isEqualTo("2026년 런던 위임예산(경상)");
                            assertThat(project.getSvnDpmC()).isEqualTo("0910");
                            assertThat(project.getItems()).hasSize(2);
                        });
    }

    /**
     * MIG-04 잔여 경로: 엑셀 표기와 조직 정식명이 다르면 보정 행이 조용히 다른 그룹으로 갈렸다.
     *
     * <p>{@code resolveOrg}의 3단계(부분 일치)는 엑셀 `런던`을 조직 `런던지점`(0910)으로 확정한다. 그래서 보정을 걸지 않은 행의 그룹키는 엑셀
     * 원문 `런던`, 보정을 건 행은 {@code orgNameOf("0910")}이 돌려주는 정식명 `런던지점`이 되어 <b>같은 부점이 두 사업으로 쪼개졌다</b>. 두
     * 그룹 모두 {@code svnDpmC}가 0910으로 해석되므로 검증도 통과해 조용히 지나간다 — 정식명과 엑셀 표기가 같았던 기존 테스트로는 드러나지 않는 경로다.
     *
     * <p>그룹은 표기가 아니라 <b>해석된 조직코드</b>로 묶어야 한다.
     */
    @Test
    @DisplayName("엑셀 표기와 조직 정식명이 달라도 보정 행을 같은 부점 그룹에 붙인다")
    void 표기가_달라도_해석된_조직코드로_묶는다() {
        Map<String, String> overrides =
                Map.of(
                        com.kdb.it.domain.migration.service.MigrationValidator.overrideKey(
                                SheetKind.DELEGATED_BUDGET, 3, "branchName"),
                        "0910");

        AdapterOutput out =
                adapter.adapt(
                        sheet(
                                List.of(
                                        row(2, hwCells("런던", "데스크탑", "1", "1000", "1924000")),
                                        row(3, swCells("", "MS오피스", "1", "1000", "1924000")))),
                        contextWith(overrides, List.of(org("0910", "런던지점"))));

        assertThat(out.projects())
                .singleElement()
                .satisfies(
                        project -> {
                            assertThat(project.getSvnDpmC()).isEqualTo("0910");
                            assertThat(project.getItems()).hasSize(2);
                        });
        assertThat(out.allocations()).singleElement();
    }

    private static MigrationDto.NormalizedRow row(int excelRow, Map<String, String> cells) {
        return new MigrationDto.NormalizedRow(excelRow, cells);
    }

    private static MigrationDto.SheetPayload sheet(List<MigrationDto.NormalizedRow> rows) {
        return new MigrationDto.SheetPayload(SheetKind.DELEGATED_BUDGET, "2026", rows);
    }

    private static AdapterContext context() {
        return contextWith(Map.of());
    }

    private static AdapterContext contextWith(Map<String, String> overrides) {
        return contextWith(overrides, List.of(org("0910", "런던"), org("0911", "런던 PF")));
    }

    /** 조직 카탈로그를 바꿔야 하는 테스트용. 엑셀 표기와 정식명이 다른 상황을 만든다. */
    private static AdapterContext contextWith(Map<String, String> overrides, List<CorgnI> orgs) {
        return new AdapterContext(
                "2026",
                new MigrationLookupIndex(
                        OrgIdentityResolver.Index.of(orgs, List.of()),
                        Map.of(),
                        Map.of("GBP", new BigDecimal("1924"))),
                TestSnapshots.empty("2026"),
                overrides,
                "999999");
    }

    private static CorgnI org(String code, String name) {
        return CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    private static Map<String, String> hwCells(
            String branch, String item, String qty, String fc, String krw) {
        Map<String, String> cells = baseCells(branch, item);
        cells.put("hwQty", qty);
        cells.put("hwFcAmount", fc);
        cells.put("hwKrwAmount", krw);
        return cells;
    }

    private static Map<String, String> swCells(
            String branch, String item, String qty, String fc, String krw) {
        Map<String, String> cells = baseCells(branch, item);
        cells.put("swQty", qty);
        cells.put("swFcAmount", fc);
        cells.put("swKrwAmount", krw);
        return cells;
    }

    private static Map<String, String> baseCells(String branch, String item) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.DELEGATED_BUDGET)) {
            cells.put(column, "");
        }
        cells.put("branchName", branch);
        cells.put("itemName", item);
        cells.put("currency", "GBP");
        return cells;
    }
}
