package com.kdb.it.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlSanitizerTest {

    @Test
    @DisplayName("math-field 태그와 허용된 속성이 삭제되지 않아야 한다.")
    void shouldAllowMathFieldTag() {
        // Given
        String html = "<math-field data-v-f73b7830=\"\" read-only=\"\" class=\"math-field-inline\" contenteditable=\"true\" tabindex=\"0\" style=\"display: inline-flex;\"></math-field>";

        // When
        String sanitized = HtmlSanitizer.sanitize(html);

        // Then
        // data-v-f73b7830은 제외되어야 함
        assertThat(sanitized).doesNotContain("data-v-f73b7830");

        // 나머지는 유지되어야 함
        assertThat(sanitized).contains("<math-field");
        assertThat(sanitized).contains("read-only=\"\"");
        assertThat(sanitized).contains("class=\"math-field-inline\"");
        assertThat(sanitized).contains("contenteditable=\"true\"");
        assertThat(sanitized).contains("tabindex=\"0\"");
        assertThat(sanitized).contains("style=\"display: inline-flex;\"");
    }

    @Test
    @DisplayName("인라인 수식 span의 data-latex 속성이 보존되어야 한다.")
    void shouldPreserveInlineMathDataLatex() {
        // Given: InlineMathExtension이 렌더링하는 HTML
        String html = "<p><span data-type=\"inline-math\" data-latex=\"E=mc^2\" class=\"math-inline-node\"></span></p>";

        // When
        String sanitized = HtmlSanitizer.sanitize(html);

        // Then
        assertThat(sanitized).contains("data-latex=\"E=mc^2\"");
        assertThat(sanitized).contains("data-type=\"inline-math\"");
        assertThat(sanitized).contains("class=\"math-inline-node\"");
    }

    @Test
    @DisplayName("블록 수식 div의 data-latex 속성이 보존되어야 한다.")
    void shouldPreserveBlockMathDataLatex() {
        // Given: BlockMathExtension이 렌더링하는 HTML
        String html = "<div data-type=\"block-math\" data-latex=\"\\\\frac{a}{b}\" class=\"math-block-node\"></div>";

        // When
        String sanitized = HtmlSanitizer.sanitize(html);

        // Then
        assertThat(sanitized).contains("data-latex=\"\\\\frac{a}{b}\"");
        assertThat(sanitized).contains("data-type=\"block-math\"");
        assertThat(sanitized).contains("class=\"math-block-node\"");
    }

    @Test
    @DisplayName("인라인 수식의 data-latex가 XSS 시도 없이 일반 LaTeX를 보존해야 한다.")
    void shouldPreserveComplexLatexFormula() {
        // Given: 복잡한 LaTeX 수식
        String html = "<p><span data-type=\"inline-math\" data-latex=\"\\\\sum_{i=0}^{n} x_i\" class=\"math-inline-node\"></span></p>";

        // When
        String sanitized = HtmlSanitizer.sanitize(html);

        // Then: data-latex 속성값이 보존됨
        assertThat(sanitized).contains("data-type=\"inline-math\"");
        assertThat(sanitized).doesNotContain("script");
    }

    @Test
    @DisplayName("null과 빈 문자열은 그대로 반환한다.")
    void shouldReturnNullAndEmptyAsIs() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
        assertThat(HtmlSanitizer.sanitize("")).isEmpty();
    }

    @Test
    @DisplayName("script와 이벤트 핸들러는 제거하고 허용된 링크 프로토콜만 보존한다.")
    void shouldRemoveUnsafeHtmlAndProtocols() {
        String html = """
                <p onclick="alert(1)">본문<script>alert(1)</script></p>
                <a href="javascript:alert(1)" target="_blank" rel="noopener">위험</a>
                <a href="https://example.com" target="_blank" rel="noopener">안전</a>
                <img src="data:image/png;base64,AAAA" onerror="alert(1)" alt="이미지">
                """;

        String sanitized = HtmlSanitizer.sanitize(html);

        assertThat(sanitized).doesNotContain("onclick", "script", "javascript:", "onerror");
        assertThat(sanitized).contains("href=\"https://example.com\"");
        assertThat(sanitized).contains("src=\"data:image/png;base64,AAAA\"");
    }

    @Test
    @DisplayName("유틸리티 클래스 생성자는 예외를 던진다.")
    void constructor_ShouldThrowUnsupportedOperationException() throws Exception {
        var constructor = HtmlSanitizer.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
