package com.kdb.it.common.i18n.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.i18n.model.SupportedLanguage;
import org.junit.jupiter.api.Test;

class TranslationTargetKeyTest {

    @Test
    void 공통코드키는_각_값의_길이와_콜론과_값을_연결한다() {
        assertThat(TranslationTargetKey.code("ABUS_TC", "10", "20260101"))
                .isEqualTo("7:ABUS_TC2:108:20260101");
    }

    @Test
    void 콜론이_포함된_원본도_서로_다른_키를_만든다() {
        assertThat(TranslationTargetKey.code("A:B", "C", "20260101"))
                .isNotEqualTo(TranslationTargetKey.code("A", "B:C", "20260101"));
    }

    @Test
    void 메뉴키는_원본_ID를_그대로_사용한다() {
        assertThat(TranslationTargetKey.menu("MNU0000001")).isEqualTo("MNU0000001");
    }

    @Test
    void 생성된_키가_255자를_넘으면_거부한다() {
        String longValue = "A".repeat(240);

        assertThatThrownBy(() -> TranslationTargetKey.code(longValue, "10", "20260101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void 미지원언어는_사용자조회에서_한국어로_정규화한다() {
        assertThat(SupportedLanguage.normalize(" FR ")).isEqualTo(SupportedLanguage.KO);
        assertThat(SupportedLanguage.normalize(" EN ")).isEqualTo(SupportedLanguage.EN);
        assertThat(SupportedLanguage.normalize(null)).isEqualTo(SupportedLanguage.KO);
    }

    @Test
    void 관리자저장은_미지원언어를_거부한다() {
        assertThatThrownBy(() -> SupportedLanguage.requireSupported("fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 언어");
    }
}
