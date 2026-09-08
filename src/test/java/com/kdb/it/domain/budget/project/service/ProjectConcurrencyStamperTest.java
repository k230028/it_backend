package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProjectConcurrencyStamper 단위 테스트
 *
 * <p>스탬프가 품목 입력 순서와 금액·수량 스케일에 흔들리지 않고, 업무 내용 변경에만 반응하는지 검증합니다. DB 없이 실행됩니다.
 */
class ProjectConcurrencyStamperTest {

    private final ProjectConcurrencyStamper stamper =
            new ProjectConcurrencyStamper(
                    new ItBudgetCanonicalJson(
                            new ObjectMapper().registerModule(new JavaTimeModule())));

    private static Bprojm.BprojmBuilder<?, ?> projectBuilder() {
        return Bprojm.builder()
                .abusMngNo("PRJ-2026-0001")
                .sno(2)
                .abusNm("차세대 시스템")
                .abusTc("10")
                .sttDtm(LocalDate.of(2026, 1, 1))
                .endDtm(LocalDate.of(2026, 12, 31))
                .totRqmAmt(new BigDecimal("1000"))
                .bseYy("2026")
                .lstYn("Y");
    }

    private static Bprojm project() {
        return projectBuilder().build();
    }

    private static Bitemm.BitemmBuilder<?, ?> itemBuilder(
            String mngNo, Integer sno, String name, String amount) {
        return Bitemm.builder()
                .gclMngNo(mngNo)
                .sno(sno)
                .abusMngNo("PRJ-2026-0001")
                .fntTbCrySno(2)
                .gclNm(name)
                .qty(new BigDecimal("1"))
                .curC("KRW")
                .amt(new BigDecimal(amount))
                .mplAmt(BigDecimal.ZERO);
    }

    private static Bitemm item(String mngNo, Integer sno, String name, String amount) {
        return itemBuilder(mngNo, sno, name, amount).build();
    }

    @Test
    @DisplayName("스탬프는 64자리 소문자 16진수다")
    void stampIsLowercaseHex() {
        assertThat(stamper.stamp(project(), List.of())).matches("[a-f0-9]{64}");
    }

    @Test
    @DisplayName("품목 입력 순서가 달라도 스탬프는 같다")
    void itemOrderDoesNotChangeStamp() {
        Bitemm first = item("GCL-1", 1, "서버", "500");
        Bitemm second = item("GCL-2", 2, "스토리지", "500");
        assertThat(stamper.stamp(project(), List.of(first, second)))
                .isEqualTo(stamper.stamp(project(), List.of(second, first)));
    }

    @Test
    @DisplayName("금액·수량 스케일 차이는 충돌이 아니다")
    void scaleDifferenceDoesNotChangeStamp() {
        Bprojm scaled =
                projectBuilder()
                        .totRqmAmt(new BigDecimal("1000.000"))
                        .mplAmt(new BigDecimal("0.0"))
                        .build();
        Bprojm plain =
                projectBuilder().totRqmAmt(new BigDecimal("1000")).mplAmt(BigDecimal.ZERO).build();
        Bitemm scaledItem =
                itemBuilder("GCL-1", 1, "서버", "500.000")
                        .qty(new BigDecimal("2.0"))
                        .mplAmt(new BigDecimal("0.000"))
                        .build();
        Bitemm plainItem = itemBuilder("GCL-1", 1, "서버", "500").qty(new BigDecimal("2")).build();

        assertThat(stamper.stamp(scaled, List.of(scaledItem)))
                .isEqualTo(stamper.stamp(plain, List.of(plainItem)));
    }

    @Test
    @DisplayName("감사 필드와 결재 상태만 바뀐 경우는 충돌이 아니다")
    void auditAndApprovalFieldsAreIgnored() {
        String reference = stamper.stamp(project(), List.of());
        Bprojm touched =
                projectBuilder()
                        .lstChgDtm(LocalDateTime.of(2026, 9, 8, 15, 0))
                        .lstChgUsid("20002")
                        .fstEnrDtm(LocalDateTime.of(2026, 1, 1, 9, 0))
                        .fstEnrUsid("10001")
                        .build();
        Bprojm draft = projectBuilder().lstYn("N").build();
        Bitemm touchedItem =
                itemBuilder("GCL-1", 1, "서버", "500")
                        .lstChgDtm(LocalDateTime.of(2026, 9, 8, 15, 0))
                        .lstChgUsid("20002")
                        .build();

        assertThat(stamper.stamp(touched, List.of())).isEqualTo(reference);
        assertThat(stamper.stamp(draft, List.of())).isEqualTo(reference);
        assertThat(stamper.stamp(project(), List.of(touchedItem)))
                .isEqualTo(stamper.stamp(project(), List.of(item("GCL-1", 1, "서버", "500"))));
    }

    @Test
    @DisplayName("부모 필드·품목 추가·품목 삭제·품목 수정은 각각 다른 스탬프를 만든다")
    void businessChangesChangeStamp() {
        String reference = stamper.stamp(project(), List.of(item("GCL-1", 1, "서버", "500")));

        Bprojm renamed = projectBuilder().abusNm("다른 이름").build();
        assertThat(stamper.stamp(renamed, List.of(item("GCL-1", 1, "서버", "500"))))
                .isNotEqualTo(reference);
        assertThat(
                        stamper.stamp(
                                project(),
                                List.of(
                                        item("GCL-1", 1, "서버", "500"),
                                        item("GCL-2", 2, "스토리지", "100"))))
                .isNotEqualTo(reference);
        assertThat(stamper.stamp(project(), List.of())).isNotEqualTo(reference);
        assertThat(stamper.stamp(project(), List.of(item("GCL-1", 1, "서버-수정", "500"))))
                .isNotEqualTo(reference);
    }
}
