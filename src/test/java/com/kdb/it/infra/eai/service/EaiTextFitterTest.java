package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 필드 바이트 예산 맞춤이 문자를 쪼개지 않는지 검증한다. */
class EaiTextFitterTest {

    private static final Charset MS949 = Charset.forName("MS949");
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    @DisplayName("예산 이하이면 원본을 그대로 돌려준다")
    void fit_withinBudget_returnsOriginal() {
        String text = "결재요청: 전산예산 신청서";

        assertThat(EaiTextFitter.fit(text, 200, MS949, "SUBJECT")).isEqualTo(text);
        assertThat(EaiTextFitter.fit(text, 200, UTF8, "SUBJECT")).isEqualTo(text);
    }

    @Test
    @DisplayName("null과 빈 문자열은 빈 문자열로 접는다")
    void fit_nullOrEmpty_returnsEmpty() {
        assertThat(EaiTextFitter.fit(null, 200, UTF8, "SUBJECT")).isEmpty();
        assertThat(EaiTextFitter.fit("", 200, UTF8, "SUBJECT")).isEmpty();
    }

    @Test
    @DisplayName("제목 컬럼 100자는 MS949에서 그대로 들어가고 UTF-8에서는 잘린다")
    void fit_titleColumn_dependsOnCharset() {
        String title = "가".repeat(100); // TTL 컬럼 최대 길이

        assertThat(EaiTextFitter.fit(title, 200, MS949, "SUBJECT")).hasSize(100);

        String fitted = EaiTextFitter.fit(title, 200, UTF8, "SUBJECT");
        assertThat(fitted).hasSize(66); // 200 / 3바이트
        assertThat(fitted.getBytes(UTF8).length).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("잘린 결과는 항상 예산 이하이고 문자가 쪼개지지 않는다")
    void fit_neverSplitsCharacter() {
        for (int budget = 1; budget <= 12; budget++) {
            String fitted = EaiTextFitter.fit("가나다라마바사", budget, UTF8, "CONTENTS");

            assertThat(fitted.getBytes(UTF8).length).isLessThanOrEqualTo(budget);
            // 쪼개지지 않았다면 다시 인코딩·디코딩해도 같은 문자열이다.
            assertThat(new String(fitted.getBytes(UTF8), UTF8)).isEqualTo(fitted);
        }
    }

    @Test
    @DisplayName("서로게이트 쌍도 중간에서 끊지 않는다")
    void fit_surrogatePair_isNotSplit() {
        String emoji = "😀😀"; // 이모지 2개, UTF-8 각 4바이트

        String fitted = EaiTextFitter.fit(emoji, 6, UTF8, "CONTENTS");

        assertThat(fitted).isEqualTo("😀");
        assertThat(fitted.getBytes(UTF8).length).isEqualTo(4);
    }
}
