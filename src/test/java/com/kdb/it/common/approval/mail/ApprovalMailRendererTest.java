package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.notification.dispatcher.MailPayload;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 결재요청 메일 렌더링을 검증한다. DB 없이 도는 순수 단위 테스트다. */
class ApprovalMailRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApprovalMailRenderer renderer = new ApprovalMailRenderer(objectMapper);

    private static final String SNAPSHOT =
            """
            {
              "projects": [
                {"abusNm": "소규모 개선", "odnYn": "N", "totRqmAmt": 1000,
                 "assetBg": 400, "costBg": 600},
                {"abusNm": "차세대 시스템", "odnYn": "N", "totRqmAmt": 3000,
                 "assetBg": 2000, "costBg": 1000},
                {"abusNm": "2026년 경상사업", "odnYn": "Y", "totRqmAmt": 500,
                 "assetBg": 100, "costBg": 400}
              ],
              "costs": [
                {"cttNm": "유지보수 계약", "costTotXpAmt": 800, "assetBg": 300}
              ]
            }
            """;

    private static ApprovalMailContext context(String detailJson) {
        return context("전산예산 신청서", detailJson);
    }

    private static ApprovalMailContext context(String title, String detailJson) {
        return new ApprovalMailContext(
                "APF-2026-0001",
                title,
                LocalDate.of(2026, 8, 18),
                "홍길동",
                "IT기획부",
                "https://it.kdb.co.kr/approval/APF-2026-0001",
                detailJson);
    }

    private MailPayload render(String detailJson) throws Exception {
        return render("전산예산 신청서", detailJson);
    }

    private MailPayload render(String title, String detailJson) throws Exception {
        String json = renderer.renderPayloadJson(context(title, detailJson));
        assertThat(json).isNotNull();
        return objectMapper.readValue(json, MailPayload.class);
    }

    @Test
    @DisplayName("제목은 포탈 접두어와 결재 요청 문구를 붙인다")
    void subject_hasPortalPrefix() throws Exception {
        assertThat(render(SNAPSHOT).subject()).isEqualTo("[IT정보화포탈] 전산예산 신청서 결재 요청");
    }

    @Test
    @DisplayName("신청서 개요 6개 항목이 모두 본문에 나타난다")
    void html_containsOverview() throws Exception {
        String html = render(SNAPSHOT).html();

        assertThat(html)
                .contains("전산예산 신청서")
                .contains("APF-2026-0001")
                .contains("2026-08-18")
                .contains("홍길동")
                .contains("IT기획부")
                .contains("https://it.kdb.co.kr/approval/APF-2026-0001");
    }

    @Test
    @DisplayName("상세 보기 버튼은 오른쪽 정렬하고 바로가기 아이콘을 붙인다")
    void html_linkButtonIsRightAlignedWithIcon() throws Exception {
        String html = render(SNAPSHOT).html();

        assertThat(html).contains("text-align:right;").contains("신청서 상세 보기 ↗");
    }

    @Test
    @DisplayName("표는 셀 상하 여백을 line-height로 넓힌다")
    void html_tableHasVerticalCellSpacing() throws Exception {
        // 셀마다 padding 인라인 스타일을 붙이면 4000바이트 예산에서 목록 건수가 크게 줄어든다.
        assertThat(render(SNAPSHOT).html()).contains("line-height:1.9;");
    }

    @Test
    @DisplayName("구분별 합계와 총합계가 스냅샷 값과 일치한다")
    void html_containsTotals() throws Exception {
        String html = render(SNAPSHOT).html();

        // 정보화사업 4,000 / 전산업무비 800(자본예산 300, 일반관리비 500) / 경상사업 500 / 합계 5,300
        assertThat(html)
                .contains("4,000 원")
                .contains("800 원")
                .contains("300 원")
                .contains("500 원")
                .contains("5,300 원");
        assertThat(html).contains("정보화사업").contains("전산업무비").contains("경상사업").contains("합계");
        // "500 원"은 경상사업 총액과 전산업무비 일반관리비(800-300)에 동시에 매칭돼 모호하므로,
        // 전산업무비 합계 행 전체를 그대로 대조해 일반관리비 파생값을 못 박아 검증한다.
        assertThat(html)
                .contains(
                        MailHtml.row(
                                MailHtml.textCell("전산업무비"),
                                MailHtml.amountCell("1건"),
                                MailHtml.amountCell("800 원"),
                                MailHtml.amountCell("300 원"),
                                MailHtml.amountCell("500 원")));
    }

    @Test
    @DisplayName("목록은 구분 안에서 총 예산 내림차순으로, 구분 사이에서는 정보화사업→전산업무비→경상사업 순으로 정렬한다")
    void html_listSortedByTotalDesc() throws Exception {
        String html = render(SNAPSHOT).html();

        assertThat(html.indexOf("차세대 시스템")).isLessThan(html.indexOf("소규모 개선"));
        assertThat(html.indexOf("차세대 시스템"))
                .isLessThan(html.indexOf("유지보수 계약"))
                .isLessThan(html.indexOf("2026년 경상사업"));
        assertThat(html.indexOf("유지보수 계약")).isLessThan(html.indexOf("2026년 경상사업"));
    }

    @Test
    @DisplayName("목록은 구분 열을 가진 표 하나로 합친다")
    void html_listIsSingleTableWithCategoryColumn() throws Exception {
        String html = render(SNAPSHOT).html();

        assertThat(html).contains("신청 사업 목록").contains("사업명/계약명");
        // 구분별로 표를 나누면 머리글이 반복된다. 합친 표는 목록 머리글이 한 번만 나온다.
        assertThat(html.split("사업명/계약명", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("유지보수 계약");
    }

    @Test
    @DisplayName("스냅샷 전체가 비면 총괄표 자체를 생략한다")
    void html_allEmptySnapshot_omitsSummaryEntirely() throws Exception {
        String html = render("{\"projects\": [], \"costs\": []}").html();

        assertThat(html).doesNotContain("정보화사업").doesNotContain("전산업무비").doesNotContain("경상사업");
        assertThat(html).contains("APF-2026-0001");
    }

    @Test
    @DisplayName("항목이 없는 구분만 총괄표·목록에서 생략한다")
    void html_omitsEmptyCategory() throws Exception {
        String html =
                render(
                                "{\"projects\": [{\"abusNm\": \"단독 사업\", \"odnYn\": \"N\","
                                        + " \"totRqmAmt\": 100, \"assetBg\": 50, \"costBg\": 50}],"
                                        + " \"costs\": []}")
                        .html();

        assertThat(html).contains("정보화사업").contains("단독 사업");
        assertThat(html).doesNotContain("전산업무비");
    }

    @Test
    @DisplayName("항목이 예산을 채울 만큼 많아도 목록 packer가 JSON 오버헤드를 미리 반영해 폴백 없이 실린다")
    void html_staysWithinBudget() throws Exception {
        // 총액이 인덱스와 함께 오름차순이 되도록 만든다 — 목록에 원본 순서 그대로 실리면(정렬 삭제 회귀)
        // 총액이 가장 큰 마지막 항목(299번)이 예산 밖으로 밀려 빠지므로, 그 항목의 존재 여부로 정렬을 가른다.
        //
        // 회귀(2da0942c) 당시에는 항목을 이만큼 채우면 packer가 본문 바이트만으로 예산 상한 바로 아래까지
        // 채워, renderPayloadJson을 거칠 때 봉투·이스케이프 오버헤드 때문에 JSON 레벨 가드에 걸려 매번
        // null이 됐다. 이 테스트는 그 오버헤드를 packer가 미리 반영해 renderPayloadJson(공개 API)을 거쳐도
        // 폴백하지 않고 실제로 실림을 검증한다.
        String manyProjects =
                IntStream.range(0, 300)
                        .mapToObj(
                                i ->
                                        ("{\"abusNm\": \"매우 긴 이름을 가진 정보화사업 항목 %d\","
                                                        + " \"odnYn\": \"N\", \"totRqmAmt\": %d,"
                                                        + " \"assetBg\": 1, \"costBg\": 1}")
                                                .formatted(i, i + 1))
                        .collect(Collectors.joining(","));

        String json =
                renderer.renderPayloadJson(
                        context("{\"projects\": [" + manyProjects + "], \"costs\": []}"));

        assertThat(json).isNotNull();
        assertThat(json.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ApprovalMailRenderer.CONTENTS_BUDGET_BYTES);
        String html = objectMapper.readValue(json, MailPayload.class).html();
        assertThat(html).contains("외 ").contains("건");
        // 총액이 가장 큰 항목(299번, totRqmAmt=300)이 정렬로 맨 앞에 와야 예산 안에 실린다.
        assertThat(html).contains("매우 긴 이름을 가진 정보화사업 항목 299");
        // packer가 예산을 지키려고 지나치게 보수적으로 굴어 목록을 텅 비우는 회귀(빈 목록도 테스트를
        // 통과시킨다)를 잡기 위해, 실제로 쓸모 있는 건수가 실렸는지 못박는다.
        long rows =
                IntStream.range(0, 300)
                        .filter(i -> html.contains("매우 긴 이름을 가진 정보화사업 항목 " + i))
                        .count();
        assertThat(rows).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("잘림 안내조차 예산을 넘기면 안내를 생략하고 4000바이트를 지킨다")
    void html_noticeItselfOverflowsBudget_omitsNotice() throws Exception {
        // detailUrl은 개요의 상세보기 버튼과 잘림 안내(moreLink)에 각각 한 번씩, 총 두 번 담긴다.
        // 900자 이상이면 두 번 담긴 URL만으로도 고정 개요·합계(~2.2KB)를 더해 4000바이트를 넘어선다.
        String longDetailUrl = "https://it.kdb.co.kr/approval/APF-2026-0001?ref=" + "x".repeat(900);
        ApprovalMailContext context =
                new ApprovalMailContext(
                        "APF-2026-0001",
                        "전산예산 신청서",
                        LocalDate.of(2026, 8, 18),
                        "홍길동",
                        "IT기획부",
                        longDetailUrl,
                        SNAPSHOT);

        String json = renderer.renderPayloadJson(context);
        assertThat(json).isNotNull();
        String html = objectMapper.readValue(json, MailPayload.class).html();

        assertThat(html.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ApprovalMailRenderer.CONTENTS_BUDGET_BYTES);
        // 예산 초과분이 잘림 안내 자체를 밀어냈는지(가드가 실제로 작동했는지)까지 못박는다.
        // 이 단언이 없으면 안내가 URL 패딩이 짧아 애초에 트리거되지 않는 경우도 통과해 회귀를 못 잡는다.
        assertThat(html).doesNotContain("외 ");
    }

    @Test
    @DisplayName("제목만으로도 예산을 넘기면 잘림 없이 null을 반환해 폴백을 유도한다")
    void renderPayloadJson_titleAloneOverflowsBudget_returnsNull() {
        // 제목 컬럼의 실제 상한은 255자(한글 기준 UTF-8 765바이트)이지만, 실측 결과 개요·총괄표 등 필수 영역은
        // 약 2.3~3.1KB에 그쳐 255자 제목만으로는 4000바이트를 넘기지 못한다(정상 스냅샷 기준 최대 약 3.1KB).
        // 애플리케이션 검증을 우회한 값(레거시 데이터 등)이 들어와도 렌더러가 안전해야 하므로, 실제 컬럼 상한을
        // 크게 웃도는 길이로 가드를 확실히 넘겨 검증한다.
        String longTitle = "가".repeat(1000);
        ApprovalMailContext context =
                new ApprovalMailContext(
                        "APF-2026-0001",
                        longTitle,
                        LocalDate.of(2026, 8, 18),
                        "홍길동",
                        "IT기획부",
                        "https://it.kdb.co.kr/approval/APF-2026-0001",
                        SNAPSHOT);

        assertThat(renderer.renderPayloadJson(context)).isNull();
    }

    @Test
    @DisplayName("사업명의 HTML 특수문자가 이스케이프된다")
    void html_escapesItemNames() throws Exception {
        String html =
                render(
                                "{\"projects\": [{\"abusNm\": \"<script>alert(1)</script>\","
                                        + " \"odnYn\": \"N\", \"totRqmAmt\": 1, \"assetBg\": 1,"
                                        + " \"costBg\": 0}], \"costs\": []}")
                        .html();

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("스냅샷이 없거나 깨졌으면 개요만 렌더링한다")
    void html_missingOrBrokenSnapshot_rendersOverviewOnly() throws Exception {
        for (String broken : new String[] {null, "", "  ", "{broken JSON"}) {
            String html = render(broken).html();

            assertThat(html).contains("APF-2026-0001").contains("전산예산 신청서");
            assertThat(html).doesNotContain("정보화사업");
        }
    }

    @Test
    @DisplayName("이름·부서가 비어도 렌더링이 계속된다")
    void html_nullOptionalFields() throws Exception {
        ApprovalMailContext context =
                new ApprovalMailContext(
                        "APF-1", "제목", null, null, null, "https://x/approval/APF-1", SNAPSHOT);

        String json = renderer.renderPayloadJson(context);

        assertThat(json).isNotNull();
        assertThat(objectMapper.readValue(json, MailPayload.class).html()).contains("APF-1");
    }

    @Test
    @DisplayName("context가 null이면 예외 없이 null을 반환한다")
    void renderPayloadJson_nullContext_returnsNull() {
        assertThat(renderer.renderPayloadJson(null)).isNull();
    }

    @Test
    @DisplayName("상세보기 URL만으로도 예산을 넘기면 목록 packer와 무관하게 null을 반환해 폴백을 유도한다")
    void renderPayloadJson_detailUrlAloneOverflowsBudget_returnsNull() {
        // detailUrl은 개요의 상세보기 버튼(및 목록이 잘리면 안내 문구)에 실리는 고정 영역이라
        // 목록 packer가 손댈 수 없다. packer가 목록을 통째로 비워도(entries가 있어도 없어도) 이 필드
        // 하나만으로 이미 예산을 넘기면 렌더러는 여전히 null을 돌려줘야 한다.
        String hugeDetailUrl =
                "https://it.kdb.co.kr/approval/APF-2026-0001?ref=" + "x".repeat(5000);
        ApprovalMailContext context =
                new ApprovalMailContext(
                        "APF-2026-0001",
                        "전산예산 신청서",
                        LocalDate.of(2026, 8, 18),
                        "홍길동",
                        "IT기획부",
                        hugeDetailUrl,
                        SNAPSHOT);

        assertThat(renderer.renderPayloadJson(context)).isNull();
    }

    @Test
    @DisplayName("긴 제목도 제목 필드 폭 안에서 결재 요청 문구가 끝까지 살아남는다")
    void subject_longTitle_keepsApprovalSuffixWithinFieldBudget() throws Exception {
        // Capplm.dcdReqTtl 상한(255자)을 웃도는 길이로도 접미어가 잘리지 않는지 확인한다.
        String longTitle = "제목".repeat(200);
        String subject = render(longTitle, SNAPSHOT).subject();

        assertThat(subject.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(200); // GWE 전문 SUBJECT 필드 폭
        assertThat(subject).startsWith("[IT정보화포탈] ").endsWith(" 결재 요청");
    }
}
