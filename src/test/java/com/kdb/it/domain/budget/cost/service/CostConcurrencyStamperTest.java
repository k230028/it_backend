package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CostConcurrencyStamper 단위 테스트
 *
 * <p>스탬프가 단말 입력 순서와 금액 스케일에 흔들리지 않고, 업무 내용 변경에만 반응하는지 검증합니다. DB 없이 실행됩니다.
 */
class CostConcurrencyStamperTest {

    private final CostConcurrencyStamper stamper =
            new CostConcurrencyStamper(new ItBudgetCanonicalJson(new ObjectMapper()));

    private static Bcostm cost() {
        return Bcostm.builder()
                .costBgNo("COST_2026_0001")
                .bgSno(2)
                .ioeC("IOE_SEVS")
                .cttNm("서버 유지보수")
                .costTotXpAmt(new BigDecimal("1000"))
                .curC("KRW")
                .bseYy("2026")
                .build();
    }

    private static Btermm terminal(String mngNo, Integer sno, String name) {
        return Btermm.builder()
                .tmnMngNo(mngNo)
                .sno(sno)
                .spfTmnNm(name)
                .termRqmBgAmt(new BigDecimal("500"))
                .build();
    }

    @Test
    @DisplayName("단말 입력 순서가 달라도 스탬프는 같다")
    void terminalOrderDoesNotChangeStamp() {
        Btermm first = terminal("TMN-1", 1, "단말A");
        Btermm second = terminal("TMN-2", 1, "단말B");
        assertThat(stamper.stamp(cost(), List.of(first, second)))
                .isEqualTo(stamper.stamp(cost(), List.of(second, first)));
    }

    @Test
    @DisplayName("단말 목록이 null이면 빈 목록과 같은 스탬프를 낸다")
    void nullTerminalsAreTreatedAsEmpty() {
        assertThat(stamper.stamp(cost(), null)).isEqualTo(stamper.stamp(cost(), List.of()));
    }

    @Test
    @DisplayName("금액 스케일 차이는 충돌이 아니다")
    void moneyScaleDoesNotChangeStamp() {
        String scaled =
                stamper.stamp(
                        Bcostm.builder()
                                .costBgNo("COST_2026_0001")
                                .bgSno(2)
                                .costTotXpAmt(new BigDecimal("1000.000"))
                                .build(),
                        List.of());
        String plain =
                stamper.stamp(
                        Bcostm.builder()
                                .costBgNo("COST_2026_0001")
                                .bgSno(2)
                                .costTotXpAmt(new BigDecimal("1000"))
                                .build(),
                        List.of());
        assertThat(scaled).isEqualTo(plain);
    }

    @Test
    @DisplayName("부모 업무 필드가 바뀌면 스탬프가 달라진다")
    void parentFieldChangeChangesStamp() {
        String before = stamper.stamp(cost(), List.of());
        String after =
                stamper.stamp(
                        Bcostm.builder()
                                .costBgNo("COST_2026_0001")
                                .bgSno(2)
                                .ioeC("IOE_SEVS")
                                .cttNm("서버 유지보수 연장")
                                .costTotXpAmt(new BigDecimal("1000"))
                                .curC("KRW")
                                .bseYy("2026")
                                .build(),
                        List.of());
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("단말이 추가되면 스탬프가 달라진다")
    void addedTerminalChangesStamp() {
        assertThat(stamper.stamp(cost(), List.of(terminal("TMN-1", 1, "단말A"))))
                .isNotEqualTo(
                        stamper.stamp(
                                cost(),
                                List.of(terminal("TMN-1", 1, "단말A"), terminal("TMN-2", 1, "단말B"))));
    }

    @Test
    @DisplayName("단말이 삭제되면 스탬프가 달라진다")
    void removedTerminalChangesStamp() {
        assertThat(
                        stamper.stamp(
                                cost(),
                                List.of(terminal("TMN-1", 1, "단말A"), terminal("TMN-2", 1, "단말B"))))
                .isNotEqualTo(stamper.stamp(cost(), List.of(terminal("TMN-1", 1, "단말A"))));
    }

    @Test
    @DisplayName("감사 필드만 다르면 충돌이 아니다")
    void auditOnlyChangeDoesNotChangeStamp() {
        assertThat(
                        stamper.stamp(
                                Bcostm.builder()
                                        .costBgNo("COST_2026_0001")
                                        .bgSno(2)
                                        .cttNm("서버 유지보수")
                                        .lstChgUsid("EMP-999")
                                        .lstChgDtm(LocalDateTime.of(2026, 9, 8, 14, 25))
                                        .build(),
                                List.of()))
                .isEqualTo(
                        stamper.stamp(
                                Bcostm.builder()
                                        .costBgNo("COST_2026_0001")
                                        .bgSno(2)
                                        .cttNm("서버 유지보수")
                                        .lstChgUsid("EMP-001")
                                        .lstChgDtm(LocalDateTime.of(2026, 1, 1, 0, 0))
                                        .build(),
                                List.of()));
    }

    @Test
    @DisplayName("결재 상태(LST_YN)가 달라도 충돌이 아니다")
    void approvalStateDoesNotChangeStamp() {
        assertThat(
                        stamper.stamp(
                                Bcostm.builder()
                                        .costBgNo("COST_2026_0001")
                                        .bgSno(2)
                                        .cttNm("서버 유지보수")
                                        .lstYn("Y")
                                        .build(),
                                List.of()))
                .isEqualTo(
                        stamper.stamp(
                                Bcostm.builder()
                                        .costBgNo("COST_2026_0001")
                                        .bgSno(2)
                                        .cttNm("서버 유지보수")
                                        .lstYn("N")
                                        .build(),
                                List.of()));
    }

    /* 해시 입력 record에서 필드가 하나라도 빠지면 그 필드의 lost update가 조용히 되살아난다.
    아래 두 파라미터 테스트는 자리를 지키는 행의 필드를 하나씩만 바꿔 스탬프가 달라지는지 확인하고,
    뒤따르는 두 테스트가 record 구성요소 전체를 케이스가 덮는지 reflection으로 대조한다.
    필드를 지우면 해당 케이스가 실패하고, 필드를 추가하면 커버리지 대조가 실패한다. */

    private static Bcostm.BcostmBuilder<?, ?> fullCostBuilder() {
        return Bcostm.builder()
                .costBgNo("COST_2026_0001")
                .bgSno(2)
                .ioeC("IOE_SEVS")
                .cttNm("서버 유지보수")
                .cttOppNm("공급사A")
                .costTotXpAmt(new BigDecimal("1000"))
                .fcAmt(new BigDecimal("100"))
                .dfrCleC("01")
                .fstDfrDt("20260301")
                .curC("KRW")
                .xcr(new BigDecimal("1"))
                .xcrBseDt("20260301")
                .sectSysUtzYn("N")
                .indRsn("도입 사유")
                .cgprId("10001")
                .cgprNm("담당자")
                .prlmHrkOgzCCone("ORG1")
                .costSvnDpmC("D01")
                .svnTemC("T01")
                .svnDpmNm("정보기술부")
                .svnTemNm("개발팀")
                .bseYy("2026")
                .bgUntAbusC("BU")
                .tmnYn("Y")
                .abusTc("20")
                .cncdRfrNo("COST-REF");
    }

    private static Btermm.BtermmBuilder<?, ?> fullTerminalBuilder() {
        return Btermm.builder()
                .tmnMngNo("TMN-1")
                .sno(1)
                .spfTmnNm("단말A")
                .tmnKdTc("K1")
                .nsfUsgCone("금융망 용도")
                .tmnClsfC("C1")
                .termRqmBgAmt(new BigDecimal("500"))
                .fcAmt(new BigDecimal("50"))
                .curC("KRW")
                .xcr(new BigDecimal("1"))
                .xcrBseDt("20260301")
                .dfrCleC("01")
                .indRsn("단말 사유")
                .cgprId("20001")
                .cgprNm("단말담당")
                .termSvnDpmC("D01")
                .svnDpmNm("정보기술부")
                .termSvnTemC("T01")
                .svnTemNm("개발팀")
                .rmk("비고");
    }

    private static Bcostm fullCost(Consumer<Bcostm.BcostmBuilder<?, ?>> mutation) {
        Bcostm.BcostmBuilder<?, ?> builder = fullCostBuilder();
        mutation.accept(builder);
        return builder.build();
    }

    private static Btermm fullTerminal(Consumer<Btermm.BtermmBuilder<?, ?>> mutation) {
        Btermm.BtermmBuilder<?, ?> builder = fullTerminalBuilder();
        mutation.accept(builder);
        return builder.build();
    }

    private static Consumer<Bcostm.BcostmBuilder<?, ?>> onCost(
            Consumer<Bcostm.BcostmBuilder<?, ?>> mutation) {
        return mutation;
    }

    private static Consumer<Btermm.BtermmBuilder<?, ?>> onTerminal(
            Consumer<Btermm.BtermmBuilder<?, ?>> mutation) {
        return mutation;
    }

    private static Stream<Arguments> parentFieldMutations() {
        return Stream.of(
                Arguments.of("costBgNo", onCost(b -> b.costBgNo("COST_2026_0002"))),
                Arguments.of("bgSno", onCost(b -> b.bgSno(3))),
                Arguments.of("ioeC", onCost(b -> b.ioeC("IOE_HW"))),
                Arguments.of("cttNm", onCost(b -> b.cttNm("서버 교체"))),
                Arguments.of("cttOppNm", onCost(b -> b.cttOppNm("공급사B"))),
                Arguments.of("costTotXpAmt", onCost(b -> b.costTotXpAmt(new BigDecimal("2000")))),
                Arguments.of("fcAmt", onCost(b -> b.fcAmt(new BigDecimal("200")))),
                Arguments.of("dfrCleC", onCost(b -> b.dfrCleC("02"))),
                Arguments.of("fstDfrDt", onCost(b -> b.fstDfrDt("20260401"))),
                Arguments.of("curC", onCost(b -> b.curC("USD"))),
                Arguments.of("xcr", onCost(b -> b.xcr(new BigDecimal("2")))),
                Arguments.of("xcrBseDt", onCost(b -> b.xcrBseDt("20260401"))),
                Arguments.of("sectSysUtzYn", onCost(b -> b.sectSysUtzYn("Y"))),
                Arguments.of("indRsn", onCost(b -> b.indRsn("다른 도입 사유"))),
                Arguments.of("cgprId", onCost(b -> b.cgprId("10002"))),
                Arguments.of("cgprNm", onCost(b -> b.cgprNm("다른담당자"))),
                Arguments.of("prlmHrkOgzCCone", onCost(b -> b.prlmHrkOgzCCone("ORG2"))),
                Arguments.of("costSvnDpmC", onCost(b -> b.costSvnDpmC("D02"))),
                Arguments.of("svnTemC", onCost(b -> b.svnTemC("T02"))),
                Arguments.of("svnDpmNm", onCost(b -> b.svnDpmNm("여신부"))),
                Arguments.of("svnTemNm", onCost(b -> b.svnTemNm("운영팀"))),
                Arguments.of("bseYy", onCost(b -> b.bseYy("2027"))),
                Arguments.of("bgUntAbusC", onCost(b -> b.bgUntAbusC("BV"))),
                Arguments.of("tmnYn", onCost(b -> b.tmnYn("N"))),
                Arguments.of("abusTc", onCost(b -> b.abusTc("10"))),
                Arguments.of("cncdRfrNo", onCost(b -> b.cncdRfrNo("COST-REF2"))));
    }

    private static Stream<Arguments> terminalFieldMutations() {
        return Stream.of(
                Arguments.of("tmnMngNo", onTerminal(b -> b.tmnMngNo("TMN-9"))),
                Arguments.of("sno", onTerminal(b -> b.sno(2))),
                Arguments.of("spfTmnNm", onTerminal(b -> b.spfTmnNm("단말Z"))),
                Arguments.of("tmnKdTc", onTerminal(b -> b.tmnKdTc("K2"))),
                Arguments.of("nsfUsgCone", onTerminal(b -> b.nsfUsgCone("다른 용도"))),
                Arguments.of("tmnClsfC", onTerminal(b -> b.tmnClsfC("C2"))),
                Arguments.of(
                        "termRqmBgAmt", onTerminal(b -> b.termRqmBgAmt(new BigDecimal("600")))),
                Arguments.of("fcAmt", onTerminal(b -> b.fcAmt(new BigDecimal("60")))),
                Arguments.of("curC", onTerminal(b -> b.curC("USD"))),
                Arguments.of("xcr", onTerminal(b -> b.xcr(new BigDecimal("2")))),
                Arguments.of("xcrBseDt", onTerminal(b -> b.xcrBseDt("20260401"))),
                Arguments.of("dfrCleC", onTerminal(b -> b.dfrCleC("02"))),
                Arguments.of("indRsn", onTerminal(b -> b.indRsn("다른 단말 사유"))),
                Arguments.of("cgprId", onTerminal(b -> b.cgprId("20002"))),
                Arguments.of("cgprNm", onTerminal(b -> b.cgprNm("다른단말담당"))),
                Arguments.of("termSvnDpmC", onTerminal(b -> b.termSvnDpmC("D02"))),
                Arguments.of("svnDpmNm", onTerminal(b -> b.svnDpmNm("여신부"))),
                Arguments.of("termSvnTemC", onTerminal(b -> b.termSvnTemC("T02"))),
                Arguments.of("svnTemNm", onTerminal(b -> b.svnTemNm("운영팀"))),
                Arguments.of("rmk", onTerminal(b -> b.rmk("다른 비고"))));
    }

    @ParameterizedTest(name = "부모 업무 필드 {0}")
    @MethodSource("parentFieldMutations")
    @DisplayName("부모 해시 입력 필드는 하나씩 바꿔도 모두 스탬프를 바꾼다")
    void parentViewFieldChangeChangesStamp(
            String field, Consumer<Bcostm.BcostmBuilder<?, ?>> mutation) {
        List<Btermm> terminals = List.of(fullTerminal(builder -> {}));
        assertThat(stamper.stamp(fullCost(mutation), terminals))
                .as("부모 필드 %s가 스탬프 입력에서 빠졌습니다", field)
                .isNotEqualTo(stamper.stamp(fullCost(builder -> {}), terminals));
    }

    @ParameterizedTest(name = "단말 필드 {0}")
    @MethodSource("terminalFieldMutations")
    @DisplayName("자리를 지키는 단말 행의 필드를 하나씩 바꿔도 모두 스탬프를 바꾼다")
    void terminalViewFieldChangeChangesStamp(
            String field, Consumer<Btermm.BtermmBuilder<?, ?>> mutation) {
        Bcostm cost = fullCost(builder -> {});
        assertThat(stamper.stamp(cost, List.of(fullTerminal(mutation))))
                .as("단말 필드 %s가 스탬프 입력에서 빠졌습니다", field)
                .isNotEqualTo(stamper.stamp(cost, List.of(fullTerminal(builder -> {}))));
    }

    @Test
    @DisplayName("부모 해시 입력 record의 모든 구성요소에 회귀 케이스가 있다")
    void parentFieldMutationsCoverEveryViewComponent() {
        assertThat(caseNames(parentFieldMutations()))
                .containsExactlyInAnyOrderElementsOf(viewComponents("ParentView"));
    }

    @Test
    @DisplayName("단말 해시 입력 record의 모든 구성요소에 회귀 케이스가 있다")
    void terminalFieldMutationsCoverEveryViewComponent() {
        assertThat(caseNames(terminalFieldMutations()))
                .containsExactlyInAnyOrderElementsOf(viewComponents("TerminalView"));
    }

    private static List<String> caseNames(Stream<Arguments> cases) {
        return cases.map(argument -> (String) argument.get()[0]).toList();
    }

    /**
     * 스탬프 입력 record의 구성요소 이름을 읽는다.
     *
     * <p>record가 private이라 컴파일 시점에 참조할 수 없으므로 같은 패키지에서 reflection으로 읽는다. 이름을 하드코딩하면 필드 추가를 놓친다.
     *
     * @param simpleName {@code ParentView} 또는 {@code TerminalView}
     * @return 선언된 구성요소 이름 목록
     */
    private static List<String> viewComponents(String simpleName) {
        try {
            Class<?> view =
                    Class.forName(
                            CostConcurrencyStamper.class.getName() + "$" + simpleName,
                            false,
                            CostConcurrencyStamper.class.getClassLoader());
            return Arrays.stream(view.getRecordComponents()).map(RecordComponent::getName).toList();
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(simpleName + " record를 찾을 수 없습니다.", ex);
        }
    }

    @Test
    @DisplayName("스탬프는 64자리 소문자 16진수다")
    void stampIsLowercaseSha256Hex() {
        assertThat(stamper.stamp(cost(), List.of())).matches("[a-f0-9]{64}");
    }
}
