package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 메일 HTML 조립 헬퍼를 검증한다. */
class MailHtmlTest {

    @Test
    @DisplayName("HTML 특수문자를 이스케이프한다")
    void escape_specialCharacters() {
        assertThat(MailHtml.escape("<b>A&B</b>"))
                .isEqualTo("&lt;b&gt;A&amp;B&lt;/b&gt;")
                .doesNotContain("<b>");
        assertThat(MailHtml.escape("\"인용\"")).contains("&quot;");
        assertThat(MailHtml.escape(null)).isEmpty();
    }

    @Test
    @DisplayName("금액은 천 단위 구분자와 원 단위로 표기한다")
    void amount_formatsWithSeparator() {
        assertThat(MailHtml.amount(new BigDecimal("1234567"))).isEqualTo("1,234,567 원");
        assertThat(MailHtml.amount(BigDecimal.ZERO)).isEqualTo("0 원");
        assertThat(MailHtml.amount(null)).isEqualTo("0 원");
    }

    @Test
    @DisplayName("바이트 길이는 UTF-8 기준이다")
    void utf8Length_countsBytes() {
        assertThat(MailHtml.utf8Length("가")).isEqualTo(3);
        assertThat(MailHtml.utf8Length("ab")).isEqualTo(2);
        assertThat(MailHtml.utf8Length(null)).isZero();
    }

    @Test
    @DisplayName("셀과 행은 인라인 스타일을 붙여 만든다")
    void cellsAndRow_carryInlineStyle() {
        String row = MailHtml.row(MailHtml.labelCell("구분"), MailHtml.textCell("정보화사업"));

        assertThat(row).startsWith("<tr>").endsWith("</tr>");
        assertThat(row).contains("style=").contains(MailHtml.HEADER_BG);
        assertThat(row).contains("구분").contains("정보화사업");
    }

    @Test
    @DisplayName("표는 인라인 스타일을 가진 table 요소로 감싼다")
    void table_wrapsRows() {
        String table = MailHtml.table(MailHtml.row(MailHtml.textCell("값")));

        assertThat(table).startsWith("<table").endsWith("</table>");
        assertThat(table).contains(MailHtml.BORDER).contains("값");
    }

    @Test
    @DisplayName("셀 내용도 이스케이프된다")
    void cells_escapeContent() {
        assertThat(MailHtml.textCell("<script>"))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("테두리·여백은 표가 주고 셀은 반복하지 않는다")
    void cells_doNotRepeatBorderStyle() {
        // 셀마다 인라인 테두리를 반복하면 필수 항목만으로 4000바이트 예산을 넘는다(실측 5007바이트).
        assertThat(MailHtml.textCell("값")).doesNotContain("border").doesNotContain("padding");
        assertThat(MailHtml.amountCell("1 원")).doesNotContain("border").doesNotContain("padding");
        assertThat(MailHtml.labelCell("구분")).doesNotContain("border").doesNotContain("padding");

        String table = MailHtml.table(MailHtml.row(MailHtml.textCell("값")));
        assertThat(table).contains("cellpadding=").contains("border=");
    }

    @Test
    @DisplayName("5열 표 한 행이 200바이트를 넘지 않는다")
    void row_staysCheap() {
        String row =
                MailHtml.row(
                        MailHtml.textCell("정보화사업"),
                        MailHtml.textCell("차세대 통합 시스템 구축 사업"),
                        MailHtml.amountCell("3,000 원"));

        assertThat(MailHtml.utf8Length(row)).isLessThanOrEqualTo(200);
    }
}
