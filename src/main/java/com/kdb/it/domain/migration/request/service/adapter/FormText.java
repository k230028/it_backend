package com.kdb.it.domain.migration.request.service.adapter;

/** 편성요청서에서 읽은 업무 문자열을 필드 용도에 맞게 정규화합니다. */
final class FormText {

    private FormText() {}

    /** 이름 필드의 연속 개행을 공백 하나로 바꾸고 앞뒤 공백을 제거합니다. */
    static String singleLineName(String value) {
        return value == null ? null : value.replaceAll("\\R+", " ").trim();
    }

    /**
     * 여러 줄 본문을 리치텍스트 필드가 줄바꿈 그대로 보여 주는 HTML로 바꿉니다.
     *
     * <p>사업범위 같은 칸은 Tiptap이 편집하고 화면이 {@code v-html}로 그리는 <b>HTML 필드</b>입니다. 엑셀에서 읽은 개행 문자를 그대로 담으면
     * HTML에서는 공백 하나로 접혀 여러 줄이 한 줄로 붙어 보이고, 그 상태로 수정 화면을 열면 에디터가 한 문단으로 정규화해 줄바꿈이 영구히 사라집니다. 그래서 반입
     * 시점에 개행을 {@code <br>}로 바꿔 둡니다.
     *
     * <p>본문은 사용자가 적은 평문이라 {@code <}·{@code &} 같은 글자가 마크업으로 읽히지 않게 이스케이프합니다. 여는 것은 이 메서드가 넣는 {@code
     * <br>}뿐입니다.
     *
     * @param value 엑셀에서 읽은 평문. null·공백이면 그대로 돌려줍니다
     * @return 개행을 {@code <br>}로 바꾸고 나머지를 이스케이프한 HTML
     */
    static String multiLineRichText(String value) {
        if (value == null || value.isBlank()) return value;
        StringBuilder html = new StringBuilder();
        for (String line : value.split("\\R", -1)) {
            if (!html.isEmpty()) html.append("<br>");
            html.append(escapeHtml(line));
        }
        return html.toString();
    }

    /** 평문을 HTML 텍스트 노드로 안전하게 옮깁니다. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
