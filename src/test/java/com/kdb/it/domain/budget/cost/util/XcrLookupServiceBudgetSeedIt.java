package com.kdb.it.domain.budget.cost.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 2026년 예산환율 시드가 XcrLookupService 조회 규약을 만족하는지 확인합니다.
 *
 * <p>{@code AbstractOracleRepositoryTest}는 {@code @DataJpaTest} 슬라이스라 리포지토리 빈만 자동
 * 등록한다. {@code XcrLookupService}는 일반 {@code @Service}라 슬라이스 컨텍스트에 포함되지 않으므로
 * 이 테스트 클래스에서 명시적으로 임포트한다.
 */
@Tag("it")
@Import(XcrLookupService.class)
class XcrLookupServiceBudgetSeedIt extends AbstractOracleRepositoryTest {

    @Autowired private XcrLookupService xcrLookupService;

    /** 이관 대상 통화 전부가 2026년 기준일로 조회된다. */
    @Test
    @DisplayName("2026년 예산환율 시드로 GBP·AUD·USD·JPY 환율을 조회한다")
    void 예산환율시드로_이관대상_통화환율을_조회한다() {
        LocalDate base = LocalDate.of(2026, 8, 11);

        assertThat(xcrLookupService.resolveXcr("GBP", base))
                .isEqualByComparingTo(new BigDecimal("1924"));
        assertThat(xcrLookupService.resolveXcr("AUD", base))
                .isEqualByComparingTo(new BigDecimal("929"));
        assertThat(xcrLookupService.resolveXcr("USD", base))
                .isEqualByComparingTo(new BigDecimal("1432"));
        assertThat(xcrLookupService.resolveXcr("JPY", base))
                .isEqualByComparingTo(new BigDecimal("9.7"));
    }

    /**
     * 유효기간이 2026년으로 좁혀지면 회귀다 — 조회부(ProjectService/CostService)가 전부
     * LocalDate.now()로 호출하므로 END_DT를 2026년 한정으로 두면 2027년부터 전 통화 저장이
     * IllegalStateException으로 롤백된다. END_DT는 무기한('99991231')을 유지해야 한다.
     */
    @Test
    @DisplayName("2027년 기준일로도 GBP 환율이 조회된다 — 유효기간이 무기한임을 고정한다")
    void 예산환율은_2026년_이후_기준일에도_조회된다() {
        assertThat(xcrLookupService.resolveXcr("GBP", LocalDate.of(2027, 6, 1)))
                .isEqualByComparingTo(new BigDecimal("1924"));
    }

    /** KRW는 조회를 우회해 null을 반환한다 (BudgetAmountCalculator 결정 B의 전제). */
    @Test
    @DisplayName("KRW는 환율 조회를 우회한다")
    void 원화는_환율조회를_우회한다() {
        assertThat(xcrLookupService.resolveXcr("KRW", LocalDate.of(2026, 8, 11))).isNull();
    }

    /** 엑셀 외화열 × 시드 환율이 엑셀 원화열과 일치한다 (AMOUNT_MISMATCH 진단의 근거). */
    @Test
    @DisplayName("GBP 2890 × 1924 = 5560360원 — 엑셀 원화열 5560.36천원과 일치한다")
    void 외화금액과_환율의_곱이_엑셀원화열과_일치한다() {
        BigDecimal xcr = xcrLookupService.resolveXcr("GBP", LocalDate.of(2026, 8, 11));

        assertThat(new BigDecimal("2890").multiply(xcr))
                .isEqualByComparingTo(new BigDecimal("5560360"));
    }
}
