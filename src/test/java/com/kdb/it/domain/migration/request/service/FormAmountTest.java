package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.AmountUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormAmountTest {

    @Test
    @DisplayName("자릿수 구분 쉼표만 있는 값을 그대로 읽는다")
    void readsPlainNumber() {
        assertThat(FormAmount.parse("1,669").value()).isEqualByComparingTo("1669");
        assertThat(FormAmount.parse("1,669").unit()).isNull();
        assertThat(FormAmount.parse("41868816").value()).isEqualByComparingTo("41868816");
    }

    @Test
    @DisplayName("단위 표기를 값과 함께 읽는다")
    void readsUnitSuffix() {
        // 실측: 산업기술리서치센터 ③은 시트 머리말이 `천원`인데 이 칸만 원으로 적었다
        assertThat(FormAmount.parse("41,868,816원").value()).isEqualByComparingTo("41868816");
        assertThat(FormAmount.parse("41,868,816원").unit()).isEqualTo(AmountUnit.WON);
        // 실측: 리스크관리부 1-2
        assertThat(FormAmount.parse("2,122백만원").value()).isEqualByComparingTo("2122");
        assertThat(FormAmount.parse("2,122백만원").unit()).isEqualTo(AmountUnit.MILLION);
        assertThat(FormAmount.parse("500천원").unit()).isEqualTo(AmountUnit.THOUSAND);
    }

    @Test
    @DisplayName("`백만원`을 `원`으로 잘라 읽지 않는다")
    void prefersLongestUnitWord() {
        // 짧은 표기를 먼저 보면 백만원이 원으로 잡혀 금액이 1/1,000,000이 된다
        assertThat(FormAmount.parse("2,122백만원").unit()).isNotEqualTo(AmountUnit.WON);
        assertThat(FormAmount.parse("500천원").unit()).isNotEqualTo(AmountUnit.WON);
    }

    @Test
    @DisplayName("통화코드와 괄호 주석을 떼고 읽는다")
    void stripsCurrencyCodeAndAnnotation() {
        // 실측: 상하이지점 ②의 소요예산 칸
        BigDecimal value = FormAmount.parse("20,000USD\n(원화기준 28.6백만원)").value();
        assertThat(value).isEqualByComparingTo("20000");
        // 괄호 안의 단위 표기를 주워 오면 안 된다 — 그 값은 환산 참고일 뿐이다
        assertThat(FormAmount.parse("20,000USD\n(원화기준 28.6백만원)").unit()).isNull();
    }

    @Test
    @DisplayName("숫자가 없으면 값이 없는 것으로 본다")
    void returnsNullWhenNoNumber() {
        assertThat(FormAmount.parse(null)).isNull();
        assertThat(FormAmount.parse("")).isNull();
        assertThat(FormAmount.parse("해당없음")).isNull();
        assertThat(FormAmount.parse("-")).isNull();
        // 번호로 묶인 금액 칸은 통째로 읽지 않는다 — FormEnumeration이 항목별로 나눈 뒤에 읽는다
        assertThat(FormAmount.parse("① 44,267\n② 73,723③ 8,065")).isNull();
    }

    @Test
    @DisplayName("값만 필요한 자리에서는 단위를 보지 않는다")
    void readsValueOnly() {
        assertThat(FormAmount.value("80,000")).isEqualByComparingTo("80000");
        assertThat(FormAmount.value("없음")).isNull();
    }
}
