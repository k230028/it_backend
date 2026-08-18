package com.kdb.it.common.approval.mail;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * 결재요청 메일 HTML 조립 헬퍼.
 *
 * <p>메일 클라이언트가 {@code <style>} 블록을 자주 제거하므로 모든 서식을 인라인 {@code style} 속성으로 넣는다. 색은 PDF 신청서 테마({@code
 * approvalFormPdfTheme.ts})와 같은 값을 쓴다.
 *
 * <p>본문은 GWE 전문 {@code CONTENTS} 필드(4000바이트) 안에 들어가야 하므로 조립 중 바이트를 세야 한다. 길이 계산은 전문 문자셋과 같은 UTF-8
 * 기준이다.
 */
final class MailHtml {

    /** 타이틀 바·제목 색 (PDF 신청서와 동일). */
    static final String PRIMARY = "#1e3a8a";

    /** 표 머리글 배경색. */
    static final String HEADER_BG = "#f3f4f6";

    /** 표 테두리색. */
    static final String BORDER = "#d1d5db";

    private static final String CELL_BASE =
            "border:1px solid " + BORDER + ";padding:6px 8px;font-size:13px;";

    private MailHtml() {}

    /** HTML 특수문자 이스케이프. null은 빈 문자열로 접는다. */
    static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** 금액 표기 — 천 단위 구분자와 원 단위 (PDF 총괄표와 동일). null은 0으로 본다. */
    static String amount(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return NumberFormat.getNumberInstance(Locale.KOREA).format(safe) + " 원";
    }

    /** UTF-8 바이트 길이. 전문 문자셋과 같은 기준으로 예산을 센다. */
    static int utf8Length(String html) {
        return html == null ? 0 : html.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 머리글 셀. */
    static String labelCell(String text) {
        return "<th style=\""
                + CELL_BASE
                + "background:"
                + HEADER_BG
                + ";text-align:center;font-weight:600;\">"
                + escape(text)
                + "</th>";
    }

    /** 좌측 정렬 본문 셀. */
    static String textCell(String text) {
        return "<td style=\"" + CELL_BASE + "\">" + escape(text) + "</td>";
    }

    /** 우측 정렬 금액 셀. */
    static String amountCell(String text) {
        return "<td style=\""
                + CELL_BASE
                + "text-align:right;white-space:nowrap;\">"
                + escape(text)
                + "</td>";
    }

    /** 행 조립. 인자는 이미 셀 HTML이어야 한다. */
    static String row(String... cells) {
        return "<tr>" + String.join("", cells) + "</tr>";
    }

    /** 표 조립. 인자는 이미 행 HTML이어야 한다. */
    static String table(String bodyRows) {
        return "<table style=\"border-collapse:collapse;width:100%;margin:0 0 14px;\">"
                + bodyRows
                + "</table>";
    }

    /** 구분 제목. */
    static String sectionTitle(String text) {
        return "<div style=\"font-size:14px;font-weight:700;color:"
                + PRIMARY
                + ";margin:0 0 6px;\">"
                + escape(text)
                + "</div>";
    }
}
