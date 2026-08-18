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
                {"abusNm": "차세대 시스템", "odnYn": "N", "totRqmAmt": 3000,
                 "assetBg": 2000, "costBg": 1000},
                {"abusNm": "소규모 개선", "odnYn": "N", "totRqmAmt": 1000,
                 "assetBg": 400, "costBg": 600},
                {"abusNm": "2026년 경상사업", "odnYn": "Y", "totRqmAmt": 500,
                 "assetBg": 100, "costBg": 400}
              ],
              "costs": [
                {"cttNm": "유지보수 계약", "costTotXpAmt": 800, "assetBg": 300}
              ]
            }
            """;

    private static ApprovalMailContext context(String detailJson) {
        return new ApprovalMailContext(
                "APF-2026-0001",
                "전산예산 신청서",
                LocalDate.of(2026, 8, 18),
                "홍길동",
                "IT기획부",
                "https://it.kdb.co.kr/approval/APF-2026-0001",
                detailJson);
    }

    private MailPayload render(String detailJson) throws Exception {
        String json = renderer.renderPayloadJson(context(detailJson));
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
    @DisplayName("구분별 합계와 총합계가 스냅샷 값과 일치한다")
    void html_containsTotals() throws Exception {
        String html = render(SNAPSHOT).html();

        // 정보화사업 4,000 / 전산업무비 800 / 경상사업 500 / 합계 5,300
        assertThat(html)
                .contains("4,000 원")
                .contains("800 원")
                .contains("500 원")
                .contains("5,300 원");
        assertThat(html).contains("정보화사업").contains("전산업무비").contains("경상사업").contains("합계");
    }

    @Test
    @DisplayName("목록은 구분 안에서 총 예산 내림차순으로 정렬한다")
    void html_listSortedByTotalDesc() throws Exception {
        String html = render(SNAPSHOT).html();

        assertThat(html.indexOf("차세대 시스템")).isLessThan(html.indexOf("소규모 개선"));
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
    @DisplayName("항목이 없는 구분은 합계 행과 목록을 생략한다")
    void html_omitsEmptyCategory() throws Exception {
        String html = render("{\"projects\": [], \"costs\": []}").html();

        assertThat(html).doesNotContain("정보화사업").doesNotContain("전산업무비").doesNotContain("경상사업");
        assertThat(html).contains("APF-2026-0001");
    }

    @Test
    @DisplayName("본문은 UTF-8 4000바이트를 넘지 않고 잘리면 남은 건수를 알린다")
    void html_staysWithinBudget() throws Exception {
        String manyProjects =
                IntStream.range(0, 300)
                        .mapToObj(
                                i ->
                                        ("{\"abusNm\": \"매우 긴 이름을 가진 정보화사업 항목 %d\","
                                                        + " \"odnYn\": \"N\", \"totRqmAmt\": %d,"
                                                        + " \"assetBg\": 1, \"costBg\": 1}")
                                                .formatted(i, 1000 - i))
                        .collect(Collectors.joining(","));
        String html = render("{\"projects\": [" + manyProjects + "], \"costs\": []}").html();

        assertThat(html.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ApprovalMailRenderer.CONTENTS_BUDGET_BYTES);
        assertThat(html).contains("외 ").contains("건");
        // 예산이 실제로 쓰이는지 확인 — 머리글만 넣고 행을 못 싣는 회귀를 잡는다.
        assertThat(html).contains("매우 긴 이름을 가진 정보화사업 항목 0");
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
}
