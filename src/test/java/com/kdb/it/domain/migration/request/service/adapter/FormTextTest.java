package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 편성요청서에서 읽은 문자열의 용도별 정규화를 고정합니다.
 *
 * <p>사업범위처럼 화면이 {@code v-html}로 그리고 Tiptap이 편집하는 칸은 개행 문자를 그대로 담으면 한 줄로 접혀 보입니다. 그 변환 규칙을 이 테스트가
 * 지킵니다 — 어느 시트를 거쳐 들어오든 같은 결과여야 합니다.
 */
class FormTextTest {

    @Test
    @DisplayName("여러 줄 본문의 개행을 <br>로 바꾼다")
    void turnsLineBreaksIntoBrTags() {
        assertThat(FormText.multiLineRichText("1행\n2행")).isEqualTo("1행<br>2행");
    }

    @Test
    @DisplayName("CRLF·CR도 같은 줄바꿈으로 다룬다")
    void handlesAllLineBreakForms() {
        assertThat(FormText.multiLineRichText("1행\r\n2행\r3행")).isEqualTo("1행<br>2행<br>3행");
    }

    @Test
    @DisplayName("빈 줄도 줄바꿈으로 남긴다")
    void keepsBlankLines() {
        // 문단 사이 빈 줄은 작성자가 의도한 간격이라 접지 않는다
        assertThat(FormText.multiLineRichText("1행\n\n2행")).isEqualTo("1행<br><br>2행");
    }

    @Test
    @DisplayName("본문의 HTML 특수문자를 이스케이프한다")
    void escapesHtmlSpecialCharacters() {
        // 평문에 섞인 <, &, "가 마크업으로 읽히면 화면에서 글자가 사라지거나 태그가 깨진다
        assertThat(FormText.multiLineRichText("a < b & \"c\""))
                .isEqualTo("a &lt; b &amp; &quot;c&quot;");
    }

    @Test
    @DisplayName("줄바꿈이 없으면 원문 그대로 둔다")
    void leavesSingleLineUntouched() {
        assertThat(FormText.multiLineRichText("고장기기 교체")).isEqualTo("고장기기 교체");
    }

    @Test
    @DisplayName("null·공백은 값 없음 그대로 돌려준다")
    void passesThroughNullAndBlank() {
        // 빈 문자열을 마크업으로 부풀리면 "값 없음" 판정이 흔들린다
        assertThat(FormText.multiLineRichText(null)).isNull();
        assertThat(FormText.multiLineRichText("   ")).isEqualTo("   ");
    }
}
