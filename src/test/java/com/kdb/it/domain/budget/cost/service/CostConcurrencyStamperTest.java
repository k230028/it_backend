package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("스탬프는 64자리 소문자 16진수다")
    void stampIsLowercaseSha256Hex() {
        assertThat(stamper.stamp(cost(), List.of())).matches("[a-f0-9]{64}");
    }
}
