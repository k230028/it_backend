package com.kdb.it.domain.budget.document.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocVersionCodec} 단위 테스트
 *
 * <p>
 * 문서버전이 NUMBER(9,0) 정수 컬럼에 절삭 없이 저장되도록 하는 ×100 / ÷100 변환 규약을 검증합니다.
 * </p>
 */
class DocVersionCodecTest {

    @Test
    @DisplayName("toStored: 화면 소수 버전을 × 100 정수로 변환한다")
    void toStored_multipliesByHundred() {
        assertThat(DocVersionCodec.toStored(new BigDecimal("0.01"))).isEqualByComparingTo("1");
        assertThat(DocVersionCodec.toStored(new BigDecimal("0.02"))).isEqualByComparingTo("2");
        assertThat(DocVersionCodec.toStored(new BigDecimal("1.00"))).isEqualByComparingTo("100");
        assertThat(DocVersionCodec.toStored(new BigDecimal("1.01"))).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("toDisplay: 저장 정수 버전을 ÷ 100 소수로 변환한다")
    void toDisplay_dividesByHundred() {
        assertThat(DocVersionCodec.toDisplay(new BigDecimal("1"))).isEqualByComparingTo("0.01");
        assertThat(DocVersionCodec.toDisplay(new BigDecimal("2"))).isEqualByComparingTo("0.02");
        assertThat(DocVersionCodec.toDisplay(new BigDecimal("100"))).isEqualByComparingTo("1.00");
        assertThat(DocVersionCodec.toDisplay(new BigDecimal("101"))).isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("핵심 회귀: 0.01 단위 증가가 서로 다른 저장 정수로 매핑되어 PK 충돌을 막는다")
    void increment_mapsToDistinctStoredIntegers() {
        // 최초 버전(0.01)과 다음 버전(0.02)은 저장 정수가 1, 2로 달라야 한다.
        // (NUMBER(9,0) 절삭 시 둘 다 0이 되어 PK가 충돌하던 버그 방지)
        BigDecimal first = DocVersionCodec.toStored(new BigDecimal("0.01"));
        BigDecimal second = DocVersionCodec.toStored(new BigDecimal("0.02"));
        assertThat(first).isNotEqualByComparingTo(second);
        assertThat(first).isEqualByComparingTo("1");
        assertThat(second).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("round-trip: toDisplay(toStored(v)) == v")
    void roundTrip_isStable() {
        for (String v : new String[]{"0.01", "0.02", "0.10", "1.00", "1.01", "9.99"}) {
            BigDecimal display = new BigDecimal(v);
            assertThat(DocVersionCodec.toDisplay(DocVersionCodec.toStored(display)))
                    .isEqualByComparingTo(display);
        }
    }

    @Test
    @DisplayName("null 입력은 null을 반환한다")
    void nullSafe() {
        assertThat(DocVersionCodec.toStored(null)).isNull();
        assertThat(DocVersionCodec.toDisplay(null)).isNull();
    }
}
