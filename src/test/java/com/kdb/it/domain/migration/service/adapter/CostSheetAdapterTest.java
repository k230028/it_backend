package com.kdb.it.domain.migration.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 전산일반관리비 시트 → BCOSTM 생성요청 변환 규칙을 고정합니다 (§5.2). */
class CostSheetAdapterTest {

    private final CostSheetAdapter adapter = new CostSheetAdapter();

    @Test
    @DisplayName("원화 행은 원화열을 ×1000해 원 단위로 올리고 FC_AMT를 비운다")
    void 원화행은_천원을_원으로_올린다() {
        Map<String, String> cells = cells();
        cells.put("currency", "KRW");
        cells.put("fcAmount", "");
        cells.put("krwAmount", "15401");

        CostDto.CreateRequest result = adaptSingle(cells);

        assertThat(result.getCostTotXpAmt()).isEqualByComparingTo(new BigDecimal("15401000"));
        assertThat(result.getFcAmt()).isNull();
        assertThat(result.getCurC()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("외화 행은 FC_AMT에 외화 원금을 넣고 XCR은 서버가 채우도록 비운다")
    void 외화행은_외화원금만_넣는다() {
        Map<String, String> cells = cells();
        cells.put("currency", "GBP");
        cells.put("fcAmount", "2890");
        cells.put("krwAmount", "5560.36");

        CostDto.CreateRequest result = adaptSingle(cells);

        assertThat(result.getFcAmt()).isEqualByComparingTo(new BigDecimal("2890"));
        assertThat(result.getXcr()).isNull();
        assertThat(result.getCostTotXpAmt()).isEqualByComparingTo(new BigDecimal("5560360"));
    }

    @Test
    @DisplayName("JPY 외화열은 천엔이라 FC_AMT를 엔으로 ×1000한다")
    void 엔화는_천엔을_엔으로_올린다() {
        Map<String, String> cells = cells();
        cells.put("currency", "JPY");
        cells.put("fcAmount", "100");
        cells.put("krwAmount", "970");

        assertThat(adaptSingle(cells).getFcAmt()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("구분 라벨을 ABUS_TC 코드값으로 바꾼다")
    void 사업구분라벨을_코드로_바꾼다() {
        Map<String, String> cells = cells();
        cells.put("abusTcLabel", "신규");
        assertThat(adaptSingle(cells).getAbusTc()).isEqualTo("10");

        cells.put("abusTcLabel", "계속");
        assertThat(adaptSingle(cells).getAbusTc()).isEqualTo("20");

        cells.put("abusTcLabel", "");
        assertThat(adaptSingle(cells).getAbusTc()).isEqualTo("0");
    }

    @Test
    @DisplayName("보안·단말 O 표기를 Y로, 빈 값을 N으로 바꾼다")
    void 플래그표기를_YN으로_바꾼다() {
        Map<String, String> cells = cells();
        cells.put("securityFlag", "O");
        cells.put("terminalFlag", "");

        CostDto.CreateRequest result = adaptSingle(cells);

        assertThat(result.getSectSysUtzYn()).isEqualTo("Y");
        assertThat(result.getTmnYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("비목명을 코드로 바꾸고 보정값이 있으면 보정값을 쓴다")
    void 비목은_보정값을_우선한다() {
        Map<String, String> cells = cells();
        cells.put("ioeName", "외주용역비");

        MigrationDto.SheetPayload sheet = sheet(cells);
        Map<String, String> overrides =
                Map.of(
                        com.kdb.it.domain.migration.service.MigrationValidator.overrideKey(
                                SheetKind.COST, 2, "ioeName"),
                        "009");

        AdapterOutput out = adapter.adapt(sheet, context(overrides));

        assertThat(out.costs())
                .singleElement()
                .satisfies(c -> assertThat(c.getIoeC()).isEqualTo("009"));
    }

    @Test
    @DisplayName("부서·팀 코드와 이름 스냅샷을 함께 채운다")
    void 부서팀_코드와_이름을_채운다() {
        CostDto.CreateRequest result = adaptSingle(cells());

        assertThat(result.getCostSvnDpmC()).isEqualTo("0210");
        assertThat(result.getSvnTemC()).isEqualTo("0211");
    }

    @Test
    @DisplayName("담당자 열이 없는 시트라 업로드 사용자 사번을 담당자로 넣는다")
    void 담당자는_업로드사용자다() {
        assertThat(adaptSingle(cells()).getCgprId()).isEqualTo("999999");
    }

    @Test
    @DisplayName("adapt_전산업무비는_부서기준_매칭키와_DUP_IOE_MNGC_편성률을_쓴다")
    void adapt_전산업무비는_부서기준_매칭키와_DUP_IOE_MNGC_편성률을_쓴다() {
        MigrationDto.SheetPayload sheet =
                sheetOf(
                        Map.of(
                                "abusCode", "571",
                                "ioeName", "유지보수료",
                                "deptName", "IT기획부",
                                "vendorName", "커브",
                                "requestDetail", "올인원워크스페이스",
                                "currency", "KRW",
                                "krwAmount", "15401"));

        AdapterOutput output = adapter.adapt(sheet, context(Map.of()));

        AllocationIntent intent = output.allocations().get(0);
        assertThat(intent.orcTb()).isEqualTo("BCOSTM");
        assertThat(intent.matchKey().type())
                .isEqualTo(AllocationIntent.MatchKey.Type.COST_DEPT_KEY);
        assertThat(intent.matchKey().deptCode()).isEqualTo("0210");
        assertThat(intent.matchKey().ioeC()).isEqualTo("011");
        assertThat(intent.matchKey().vendorName()).isEqualTo("커브");
        assertThat(intent.matchKey().contractName()).isEqualTo("올인원워크스페이스");
        // 천원 단위 × DUP_IOE_MNGC(100%)
        assertThat(intent.targetByColumn().get("costAmount")).isEqualByComparingTo("15401000");
    }

    @Test
    @DisplayName("통화가 비어 있으면 원화(KRW)로 기본값을 채운다")
    void 통화가_비어있으면_KRW로_기본값을_채운다() {
        Map<String, String> cells = cells();
        cells.put("currency", "");

        CostDto.CreateRequest result = adaptSingle(cells);

        assertThat(result.getCurC()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("원화금액 셀이 비어 파싱되지 않으면 배분 기준액을 0으로 잡는다")
    void 원화금액이_비어있으면_배분기준액을_0으로_잡는다() {
        // 종합본에 금액이 아직 채워지지 않은 행도 예외 없이 처리돼야 하며, 배분 목표액이 음수/NPE가 아니라 0이어야 한다.
        Map<String, String> cells = cells();
        cells.put("krwAmount", "");

        MigrationDto.SheetPayload sheet = sheet(cells);
        AdapterOutput out = adapter.adapt(sheet, context(Map.of()));

        assertThat(out.costs().get(0).getCostTotXpAmt()).isNull();
        AllocationIntent intent = out.allocations().get(0);
        assertThat(intent.targetByColumn().get("costAmount")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("비목명이 공백이고 카탈로그에도 없으면 비목코드를 null로 둔다")
    void 비목명이_공백이면_비목코드가_null이다() {
        // 검증기가 이미 REQUIRED_MISSING으로 막았어야 하는 값이지만, 어댑터 자체는 NPE 없이 null을 그대로 돌려줘야
        // 상위 저장 경로가 조용히 오염되지 않고 명시적으로 실패한다.
        Map<String, String> cells = cells();
        cells.put("ioeName", "");

        CostDto.CreateRequest result = adaptSingle(cells);

        assertThat(result.getIoeC()).isNull();
    }

    private MigrationDto.SheetPayload sheetOf(Map<String, String>... rows) {
        List<MigrationDto.NormalizedRow> normalized = new ArrayList<>();
        int excelRow = 2;
        for (Map<String, String> row : rows) {
            Map<String, String> cells = new LinkedHashMap<>();
            for (String column : MigrationColumns.of(SheetKind.COST)) {
                cells.put(column, row.getOrDefault(column, ""));
            }
            normalized.add(new MigrationDto.NormalizedRow(excelRow++, cells));
        }
        return new MigrationDto.SheetPayload(SheetKind.COST, "2026", normalized);
    }

    private CostDto.CreateRequest adaptSingle(Map<String, String> cells) {
        return adapter.adapt(sheet(cells), context(Map.of())).costs().get(0);
    }

    private static MigrationDto.SheetPayload sheet(Map<String, String> cells) {
        return new MigrationDto.SheetPayload(
                SheetKind.COST, "2026", List.of(new MigrationDto.NormalizedRow(2, cells)));
    }

    private static AdapterContext context(Map<String, String> overrides) {
        return new AdapterContext(
                "2026",
                new com.kdb.it.domain.migration.service.MigrationLookupIndex(
                        com.kdb.it.domain.migration.service.OrgIdentityResolver.Index.of(
                                List.of(org("0210", "IT기획부"), org("0211", "IT기획팀")), List.of()),
                        Map.of("국내전산임차료", "001", "유지보수료", "011", "외주용역비", "008"),
                        Map.of("GBP", new BigDecimal("1924"), "JPY", new BigDecimal("9.7"))),
                com.kdb.it.domain.migration.service.TestSnapshots.empty("2026"),
                overrides,
                "999999");
    }

    private static com.kdb.it.common.iam.entity.CorgnI org(String code, String name) {
        return com.kdb.it.common.iam.entity.CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    private static Map<String, String> cells() {
        Map<String, String> cells = new HashMap<>();
        cells.put("abusCode", "571");
        cells.put("ioeName", "유지보수료");
        cells.put("abusTcLabel", "계속");
        cells.put("vendorName", "커브");
        cells.put("requestDetail", "올인원워크스페이스");
        cells.put("securityFlag", "");
        cells.put("terminalFlag", "");
        cells.put("deptName", "IT기획부");
        cells.put("teamName", "IT기획팀");
        cells.put("currency", "KRW");
        cells.put("fcAmount", "");
        cells.put("krwAmount", "15401");
        cells.put("remark", "전년도 동일수준");
        return cells;
    }
}
