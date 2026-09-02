package com.kdb.it.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * HTML 새니타이징 유틸리티
 *
 * <p>Tiptap 에디터에서 생성한 Rich Text(HTML)를 서버 측에서 새니타이징합니다. 프론트엔드(DOMPurify)와 함께 이중 방어 체계를 구성합니다.
 *
 * <p>{@code <script>}, {@code onclick} 등 위험 요소는 자동으로 제거됩니다.
 *
 * <p>허용 태그 목록:
 *
 * <ul>
 *   <li>블록: {@code p(style), blockquote, h1~h6(style,id), pre, ol, ul(data-type),
 *       li(data-type,data-checked), div(data-type,data-latex,class)}
 *   <li>인라인: {@code strong, em, u, s, br,
 *       span(data-type,data-file-id,data-file-name,data-file-size,data-latex), code, mark, sub,
 *       sup}
 *   <li>테이블: {@code table, thead, tbody, tfoot, tr, th, td, colgroup, col}
 *   <li>체크리스트: {@code label, input(type,checked)}
 *   <li>미디어/링크: {@code a(href,target,rel), img(src,alt,style,data-scene,data-align,width,height),
 *       figure(data-type)}
 *   <li>수식: {@code math-field(read-only,class,contenteditable,tabindex,style)}
 * </ul>
 */
public final class HtmlSanitizer {

    /** Tiptap 에디터 허용 태그/속성 기반 Safelist (불변 싱글턴) */
    private static final Safelist QUILL_SAFELIST = createQuillSafelist();

    /**
     * 상대 경로 URL의 프로토콜 검사용 기준 URI
     *
     * <p>Jsoup은 프로토콜 제한 속성({@code img[src]}, {@code a[href]})을 기준 URI로 절대화한 뒤 허용 프로토콜과 비교하므로, 기준
     * URI가 비어 있으면 {@code /api/files/{id}/preview} 같은 동일 출처 상대 경로가 절대화에 실패해 속성째 제거됩니다. 프론트엔드는 {@code
     * NUXT_PUBLIC_API_BASE}가 비어 있는 dev/운영에서 상대 경로를 저장하므로 기준 URI를 고정값으로 제공합니다.
     *
     * <p>{@link Safelist#preserveRelativeLinks(boolean)}를 함께 켜서 저장 값은 상대 경로 원문 그대로 유지합니다. 이 값은 검사용
     * 기준일 뿐 결과 HTML에는 나타나지 않습니다.
     */
    private static final String RELATIVE_URL_BASE_URI = "https://it-portal.invalid/";

    /** 유틸리티 클래스이므로 인스턴스 생성 방지 */
    private HtmlSanitizer() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    /**
     * Tiptap 리치에디터 허용 태그/속성 Safelist 생성
     *
     * <p>{@link Safelist#none()} 기반으로 시작하여 Tiptap 에디터에서 사용하는 태그와 속성만 허용 목록에 추가합니다.
     *
     * @return Tiptap 리치에디터 전용 Safelist
     */
    private static Safelist createQuillSafelist() {
        return new Safelist()
                // 상대 경로 URL을 절대 URL로 재작성하지 않고 원문 그대로 저장 (RELATIVE_URL_BASE_URI 참조)
                .preserveRelativeLinks(true)
                // ── 블록 요소 ──
                .addTags(
                        "p",
                        "br",
                        "blockquote",
                        "pre",
                        "div",
                        "h1",
                        "h2",
                        "h3",
                        "h4",
                        "h5",
                        "h6",
                        "ol",
                        "ul",
                        "li")
                // pre/code: class 허용 (FR-03: CodeBlockLowlight가 language-xxx class 주입)
                .addAttributes("pre", "class")
                .addAttributes("code", "class")
                // div: data-type, data-latex, class 허용 (FR-07: BlockMathExtension 블록 수식 보존)
                .addAttributes("div", "data-type", "data-latex", "class")
                // p: style 허용 (FR-01: 텍스트 정렬 style="text-align:center" 보존)
                .addAttributes("p", "style")
                // h1~h6: style 허용 (정렬), id 허용 (목차 앵커)
                .addAttributes("h1", "style", "id")
                .addAttributes("h2", "style", "id")
                .addAttributes("h3", "style", "id")
                .addAttributes("h4", "style", "id")
                .addAttributes("h5", "style", "id")
                .addAttributes("h6", "style", "id")
                // ul: data-type 허용 (FR-02: taskList 구분용 data-type="taskList")
                .addAttributes("ul", "data-type")
                // li: data-type, data-checked 허용 (FR-02: taskItem 구분 및 체크 상태)
                .addAttributes("li", "data-type", "data-checked")

                // ── 인라인 서식 요소 ──
                .addTags("strong", "em", "u", "s", "span", "code", "mark", "sub", "sup")
                .addAttributes("mark", "style", "data-color")
                // span: style, class 속성 허용 (글자색·폰트패밀리 등 서식)
                // data-type, data-file-id, data-file-name, data-file-size: 첨부파일 노드 (FR-05) 보존
                // data-latex: 인라인 수식 LaTeX 내용 보존 (FR-07: InlineMathExtension)
                // data-comment-id: 사전협의 인라인 코멘트 마크 ID 보존
                // data-resolved: 사전협의 인라인 코멘트 해결 여부 보존
                .addAttributes(
                        "span",
                        "style",
                        "class",
                        "data-type",
                        "data-file-id",
                        "data-file-name",
                        "data-file-size",
                        "data-mention-eno",
                        "data-latex",
                        "data-comment-id",
                        "data-resolved")

                // ── 체크리스트 요소 (FR-02: Tiptap TaskItem 구조) ──
                // <label><input type="checkbox"></label><div>내용</div>
                .addTags("label", "input")
                .addAttributes("input", "type", "checked", "disabled")

                // ── 테이블 요소 ──
                .addTags("table", "thead", "tbody", "tfoot", "tr", "th", "td", "colgroup", "col")
                // 테이블 관련 스타일/속성 허용 (셀 병합, 너비, 배경색, 정렬 등)
                .addAttributes(
                        "table", "style", "class", "border", "cellpadding", "cellspacing", "width")
                .addAttributes("thead", "style", "class")
                .addAttributes("tbody", "style", "class")
                .addAttributes("tfoot", "style", "class")
                .addAttributes("tr", "style", "class")
                // th/td: colwidth 추가 (Tiptap 표 열 너비 보존)
                .addAttributes(
                        "th",
                        "colspan",
                        "rowspan",
                        "colwidth",
                        "style",
                        "class",
                        "width",
                        "height",
                        "align",
                        "valign")
                .addAttributes(
                        "td",
                        "colspan",
                        "rowspan",
                        "colwidth",
                        "style",
                        "class",
                        "width",
                        "height",
                        "align",
                        "valign")
                .addAttributes("col", "span", "style", "width")
                .addAttributes("colgroup", "span")

                // ── 링크 ──
                // target="_blank", rel="noopener noreferrer" 허용
                .addTags("a")
                .addAttributes("a", "href", "target", "rel")
                .addProtocols("a", "href", "http", "https", "mailto")

                // ── Excalidraw / 미디어 래퍼 ──
                .addTags("figure")
                .addAttributes(
                        "figure", "data-type", "data-scene", "data-attachment-id", "class", "style")

                // ── 이미지 ──
                // style 허용 (ResizableImage width), data-align 허용 (정렬)
                .addTags("img")
                .addAttributes(
                        "img", "src", "alt", "style", "data-scene", "data-align", "width", "height")
                .addProtocols("img", "src", "http", "https", "data")

                // ── 수식 (MathLive) ──
                .addTags("math-field")
                .addAttributes(
                        "math-field", "read-only", "class", "contenteditable", "tabindex", "style");
    }

    /**
     * HTML 문자열에서 위험 요소를 제거하고 안전한 HTML만 반환
     *
     * <p>{@code null} 또는 빈 문자열은 입력값 그대로 반환합니다. Tiptap 에디터 허용 태그/속성 외의 모든 요소는 자동 제거됩니다. (예: {@code
     * <script>}, {@code onclick}, {@code onerror} 등)
     *
     * @param html 새니타이징할 HTML 문자열
     * @return 안전한 HTML 문자열 (위험 요소 제거됨)
     */
    public static String sanitize(String html) {
        // null 또는 빈 문자열은 그대로 반환
        if (html == null || html.isEmpty()) {
            return html;
        }
        // 댓글과 과거 게시글의 태그 없는 평문은 HTML 직렬화 대상이 아니다. Jsoup.clean()을
        // 거치면 '&'가 '&amp;'로 바뀌어 Vue 보간 화면에서 엔티티 문자열이 그대로 노출된다.
        // 태그가 없으면 실행 가능한 HTML도 없으므로 원문을 보존한다.
        if (!html.contains("<")) {
            return html;
        }
        // prettyPrint=false: Jsoup 자동 줄바꿈/공백 삽입 방지 (표 구조 및 공백 보존)
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, RELATIVE_URL_BASE_URI, QUILL_SAFELIST, outputSettings);
    }
}
