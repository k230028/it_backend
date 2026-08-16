package com.kdb.it.domain.migration.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationLookupIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import com.kdb.it.domain.migration.service.TestSnapshots;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자본예산 시트 → BPROJM·BITEMM 변환 규칙을 고정합니다 (§5.3). */
class CapitalProjectSheetAdapterTest {

    private final CapitalProjectSheetAdapter adapter = new CapitalProjectSheetAdapter();

    @Test
    @DisplayName("백만원 단위 금액을 원 단위로 올린다")
    void 백만원을_원으로_올린다() {
        ProjectDto.CreateRequest project = adaptSingle(cells());

        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getAmt)
                .containsExactlyInAnyOrder(
                        new BigDecimal("16888000000.000"),
                        new BigDecimal("2821000000.000"),
                        new BigDecimal("4576000000.000"));
    }

    @Test
    @DisplayName("금액이 0이거나 빈 항목은 품목을 만들지 않는다")
    void 금액이_없는_항목은_품목을_만들지_않는다() {
        Map<String, String> cells = cells();
        cells.put("devAmount", "");
        cells.put("hwAmount", "0");
        cells.put("swAmount", "1406");

        assertThat(adaptSingle(cells).getItems()).hasSize(1);
    }

    @Test
    @DisplayName("품목 비목 기본값은 개발비 103·기계장치 101·기타무형 106이다")
    void 품목비목_기본값을_넣는다() {
        assertThat(adaptSingle(cells()).getItems())
                .extracting(ProjectDto.BitemmDto::getIoeC)
                .containsExactlyInAnyOrder("103", "101", "106");
    }

    @Test
    @DisplayName("비목 보정값이 오면 해당 항목의 비목을 바꾼다")
    void 비목보정값을_적용한다() {
        Map<String, String> overrides =
                Map.of(
                        com.kdb.it.domain.migration.service.MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, "devAmountIoeC"),
                        "104");

        AdapterOutput out = adapter.adapt(sheet(cells()), context(overrides));

        assertThat(out.projects().get(0).getItems())
                .filteredOn(i -> "104".equals(i.getIoeC()))
                .hasSize(1);
    }

    @Test
    @DisplayName("'26.05 형태 기간을 해당 월 1일·말일로 바꾼다")
    void 연월표기를_날짜로_바꾼다() {
        Map<String, String> cells = cells();
        cells.put("startYm", "'26.05");
        cells.put("endYm", "'26.12");

        ProjectDto.CreateRequest project = adaptSingle(cells);

        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("파싱 불가 기간은 null로 둔다")
    void 파싱불가_기간은_null이다() {
        Map<String, String> cells = cells();
        cells.put("startYm", "미정");

        assertThat(adaptSingle(cells).getSttDtm()).isNull();
    }

    @Test
    @DisplayName("담당자·팀장 이름을 사번으로 바꾸고 IT부서·주관부서 코드를 채운다")
    void 담당자와_부서를_해석한다() {
        ProjectDto.CreateRequest project = adaptSingle(cells());

        assertThat(project.getUsid()).isEqualTo("100001");
        assertThat(project.getTlrUsid()).isEqualTo("100002");
        assertThat(project.getSvnDpmC()).isEqualTo("0210");
    }

    @Test
    @DisplayName("경상여부는 N, 주관본부는 문자열 그대로 넣는다")
    void 경상여부와_주관본부를_채운다() {
        ProjectDto.CreateRequest project = adaptSingle(cells());

        assertThat(project.getOdnYn()).isEqualTo("N");
        assertThat(project.getPrlmHrkOgzCCone()).isEqualTo("글로벌사업부문");
    }

    @Test
    @DisplayName("조정비율 0.7을 비목그룹별 목표액 배수로 반영한다")
    void 조정비율을_목표액_배수로_반영한다() {
        Map<String, String> cells = cells();
        cells.put("adjustRate", "0.7");

        AdapterOutput out = adapter.adapt(sheet(cells), context(Map.of()));

        AllocationIntent intent = out.allocations().get(0);
        assertThat(intent.orcTb()).isEqualTo("BPROJM");
        assertThat(intent.matchKey().type()).isEqualTo(AllocationIntent.MatchKey.Type.PROJECT_NAME);
        assertThat(intent.matchKey().normalizedName()).isEqualTo("글로벌표준뱅킹시스템재구축");
        assertThat(intent.targetByColumn().get("devAmount")).isEqualByComparingTo("11821600000");
        assertThat(intent.targetByColumn().get("hwAmount")).isEqualByComparingTo("1974700000");
        assertThat(intent.targetByColumn().get("swAmount")).isEqualByComparingTo("3203200000");
    }

    @Test
    @DisplayName("조정비율이 비면 배수 1로 두어 원본 금액을 그대로 목표액으로 낸다")
    void 조정비율이_없으면_배수1이다() {
        Map<String, String> cells = cells();
        cells.put("adjustRate", "");

        AllocationIntent intent =
                adapter.adapt(sheet(cells), context(Map.of())).allocations().get(0);

        assertThat(intent.targetByColumn().get("devAmount")).isEqualByComparingTo("16888000000");
        assertThat(intent.targetByColumn().get("hwAmount")).isEqualByComparingTo("2821000000");
        assertThat(intent.targetByColumn().get("swAmount")).isEqualByComparingTo("4576000000");
    }

    @Test
    @DisplayName("adapt_조정비율을_곱한_비목그룹별_목표액을_낸다")
    void adapt_조정비율을_곱한_비목그룹별_목표액을_낸다() {
        MigrationDto.SheetPayload sheet =
                sheetOf(
                        Map.of(
                                "projectName", "웹한글 기안기 도입",
                                "devAmount", "",
                                "hwAmount", "",
                                "swAmount", "1406",
                                "adjustRate", "0.7",
                                "swAdjustAmount", "984"));

        AdapterOutput output = adapter.adapt(sheet, context());

        AllocationIntent intent = output.allocations().get(0);
        assertThat(intent.orcTb()).isEqualTo("BPROJM");
        assertThat(intent.matchKey().type()).isEqualTo(AllocationIntent.MatchKey.Type.PROJECT_NAME);
        assertThat(intent.matchKey().normalizedName()).isEqualTo("웹한글기안기도입");
        // 백만원 단위 × 조정비율 0.7
        assertThat(intent.targetByColumn().get("swAmount")).isEqualByComparingTo("984200000");
        assertThat(intent.targetByColumn().get("devAmount")).isEqualByComparingTo("0");
        assertThat(intent.declaredBase()).isEqualByComparingTo("1406000000");
    }

    @Test
    @DisplayName("adapt_원장_생성요청은_행_순서를_그대로_유지한다")
    void adapt_원장_생성요청은_행_순서를_그대로_유지한다() {
        MigrationDto.SheetPayload sheet =
                sheetOf(
                        Map.of("projectName", "사업 가", "swAmount", "100", "adjustRate", "1"),
                        Map.of("projectName", "사업 나", "swAmount", "200", "adjustRate", "1"));

        AdapterOutput output = adapter.adapt(sheet, context());

        assertThat(output.projects()).hasSize(2);
        assertThat(output.projects().get(0).getAbusNm()).isEqualTo("사업 가");
        assertThat(output.allocations()).hasSize(2);
        assertThat(output.allocations().get(1).matchKey().normalizedName()).isEqualTo("사업나");
    }

    /**
     * `EXE_PTT_YN`은 `VARCHAR2(1)`이라 엑셀 라벨을 그대로 대입하면 실 데이터(`추진계획 검토중`, 8자)에서 ORA-12899가 난다. 라벨을
     * 코드값으로 바꿔야 한다.
     */
    @Test
    @DisplayName("추진가능성 라벨을 EXE_PTT_YN 코드값으로 바꾼다")
    void 추진가능성을_코드값으로_바꾼다() {
        Map<String, String> cells = cells();
        cells.put("feasibility", "미정(검토중)");

        assertThat(adaptSingle(cells).getExePttYn()).isEqualTo("2");
    }

    @Test
    @DisplayName("해석되지 않는 추진가능성은 원문을 쓰지 않고 null로 둔다")
    void 미해석_추진가능성은_null이다() {
        Map<String, String> cells = cells();
        cells.put("feasibility", "추진계획 검토중");

        assertThat(adaptSingle(cells).getExePttYn()).isNull();
    }

    @Test
    @DisplayName("전결권 라벨을 IT_PTL_EDRT_TC 코드값으로 바꿔 저장한다")
    void 전결권을_코드값으로_바꾼다() {
        Map<String, String> cells = cells();
        cells.put("delegationLabel", "부문장");

        assertThat(adaptSingle(cells).getEdrtTc()).isEqualTo("22");
    }

    /** 검증이 BLOCKER로 강제하는 값이 저장되지 않으면 사용자가 고칠 이유가 없어진다 (IMPORTANT-4). */
    @Test
    @DisplayName("담당팀은 SVN_TEM_C, 담당IT팀은 DVM_DPM_C·DVM_TEM_C에 담는다")
    void 팀코드를_모두_담는다() {
        Map<String, String> cells = cells();
        cells.put("itTeamName", "글로벌IT혁신팀");

        ProjectDto.CreateRequest project = adaptSingle(cells);

        assertThat(project.getSvnTemC()).isEqualTo("0211");
        assertThat(project.getDvmDpmC()).isEqualTo("0211");
        assertThat(project.getDvmTemC()).isEqualTo("0211");
    }

    private ProjectDto.CreateRequest adaptSingle(Map<String, String> cells) {
        return adapter.adapt(sheet(cells), context(Map.of())).projects().get(0);
    }

    private static MigrationDto.SheetPayload sheet(Map<String, String> cells) {
        return new MigrationDto.SheetPayload(
                SheetKind.CAPITAL_PROJECT,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, cells)));
    }

    private static AdapterContext context() {
        return context(Map.of());
    }

    private static AdapterContext context(Map<String, String> overrides) {
        return new AdapterContext(
                "2026",
                new MigrationLookupIndex(
                        OrgIdentityResolver.Index.of(
                                List.of(org("0210", "글로벌사업부"), org("0211", "글로벌IT혁신팀")),
                                List.of(
                                        user("100001", "장원섭", "차장", "0210"),
                                        user("100002", "이효재", "팀장", "0210"))),
                        Map.of(),
                        Map.of(),
                        Map.of("571", "운영시스템 유지보수"),
                        Map.of("확정", "1", "미정(검토중)", "2"),
                        Map.of("부문장", "22", "이사회", "25"),
                        BigDecimal.valueOf(100)),
                TestSnapshots.empty("2026"),
                overrides,
                "999999");
    }

    private MigrationDto.SheetPayload sheetOf(Map<String, String>... rows) {
        List<MigrationDto.NormalizedRow> normalized = new ArrayList<>();
        int excelRow = 2;
        for (Map<String, String> row : rows) {
            Map<String, String> cells = new LinkedHashMap<>();
            for (String column : MigrationColumns.of(SheetKind.CAPITAL_PROJECT)) {
                cells.put(column, row.getOrDefault(column, ""));
            }
            normalized.add(new MigrationDto.NormalizedRow(excelRow++, cells));
        }
        return new MigrationDto.SheetPayload(SheetKind.CAPITAL_PROJECT, "2026", normalized);
    }

    private static CorgnI org(String code, String name) {
        return CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    private static CuserI user(String eno, String name, String title, String bbrC) {
        return CuserI.builder().eno(eno).usrNm(name).ptCNm(title).bbrC(bbrC).temC("0211").build();
    }

    /** 전 컬럼을 채운 뒤 검사 대상만 덮어씁니다. 엑셀 3행(글로벌 표준 뱅킹시스템)을 재현합니다. */
    private static Map<String, String> cells() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column : MigrationColumns.of(SheetKind.CAPITAL_PROJECT)) {
            cells.put(column, "");
        }
        cells.put("projectName", "글로벌 표준 뱅킹시스템 재구축");
        cells.put("projectType", "글로벌 뱅킹");
        cells.put("progressLabel", "계속");
        cells.put("projectOutline", "글로벌네트워크 표준뱅킹시스템 재구축");
        cells.put("headquarters", "글로벌사업부문");
        cells.put("deptName", "글로벌사업부");
        cells.put("teamName", "글로벌IT혁신팀");
        cells.put("managerName", "장원섭 차장");
        cells.put("teamLeaderName", "이효재 팀장");
        cells.put("itTeamName", "글로벌개발팀");
        cells.put("feasibility", "확정");
        cells.put("startYm", "'24.08");
        cells.put("endYm", "'27.04");
        cells.put("devAmount", "16888");
        cells.put("hwAmount", "2821");
        cells.put("swAmount", "4576");
        cells.put("adjustRate", "1");
        return cells;
    }
}
