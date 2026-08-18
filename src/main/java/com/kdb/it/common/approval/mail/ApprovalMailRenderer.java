package com.kdb.it.common.approval.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.mail.ApprovalMailSnapshot.CostItem;
import com.kdb.it.common.approval.mail.ApprovalMailSnapshot.ProjectItem;
import com.kdb.it.common.notification.dispatcher.MailPayload;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 결재요청 메일의 제목과 본문 HTML을 만든다.
 *
 * <p>신청서 개요와 총괄표 합계는 반드시 넣고, 구분별 목록은 남는 바이트 예산 안에서만 싣는다. 예산은 GWE 전문 {@code CONTENTS} 필드 폭과 같은 UTF-8
 * 4000바이트다.
 *
 * <p>리포지토리를 주입받지 않는다. 필요한 값은 {@link ApprovalMailContext}로 모두 받으므로 DB 없이 단위 테스트할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalMailRenderer {

    /**
     * 페이로드 JSON 바이트 예산 — GWE 전문 CONTENTS 필드 폭과 같다. 본문 HTML이 아니라 {@code {"subject":...,"html":...}}
     * 직렬화 결과 전체에 적용한다.
     */
    public static final int CONTENTS_BUDGET_BYTES = 4000;

    /** 제목 필드 예산 — GWE 전문 SUBJECT 필드 폭과 같은 UTF-8 200바이트. */
    private static final int SUBJECT_BUDGET_BYTES = 200;

    private static final String SUBJECT_PREFIX = "[IT정보화포탈] ";
    private static final String SUBJECT_SUFFIX = " 결재 요청";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    /**
     * 메일 페이로드 JSON을 만듭니다.
     *
     * <p>저장 대상은 본문 HTML이 아니라 이 메서드가 반환하는 직렬화된 JSON이다. 봉투({"subject":...,"html":...})와 본문 안 큰따옴표의
     * 백슬래시 이스케이프(\")가 본문 바이트 위에 추가로 붙으므로, {@link #html} 목록 packer는 후보를 추가할 때마다 이 envelope까지 포함해 직접
     * 재직렬화한 크기로 예산을 판단한다(과거에는 본문 바이트만 보고 채운 뒤 사후에 JSON 크기를 검사해, 목록이 예산을 꽉 채우는 흔한 경우 항상 초과해 폴백하는 회귀가
     * 있었다). 아래 검사는 그래서 평소에는 걸리지 않아야 하는 최종 방어선이다 — 개요·총괄표처럼 목록 packer가 손댈 수 없는 필수 영역만으로 이미 예산을 넘는
     * 극단값(예: 매우 긴 제목)에 대비한 안전망이며 WARN은 그 경우를 진단하기 위한 것이다.
     *
     * @param context 렌더링 입력. {@code null}이면 렌더링을 시도하지 않는다.
     * @return {@code {"subject":...,"html":...}} JSON. {@code context}가 null이거나, 렌더링이 실패하거나, 직렬화된
     *     JSON이 {@link #CONTENTS_BUDGET_BYTES}를 넘으면 {@code null}(호출자는 기존 기본 본문으로 폴백)
     */
    public String renderPayloadJson(ApprovalMailContext context) {
        if (context == null) {
            return null;
        }
        try {
            String subject = subject(context);
            String html = html(context, subject);
            MailPayload payload = new MailPayload(subject, html);
            String json = objectMapper.writeValueAsString(payload);
            int jsonBytes = MailHtml.utf8Length(json);
            if (jsonBytes > CONTENTS_BUDGET_BYTES) {
                log.warn(
                        "결재요청 메일 페이로드 JSON이 예산을 초과해 렌더링을 포기합니다: apfMngNo={}, 크기={}바이트",
                        context.apfMngNo(),
                        jsonBytes);
                return null;
            }
            return json;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("결재요청 메일 렌더링 실패: apfMngNo={}, 사유={}", context.apfMngNo(), e.toString());
            return null;
        }
    }

    /**
     * 메일 제목 — 포탈 접두어와 결재 요청 문구를 붙인다.
     *
     * <p>전문 SUBJECT 필드는 200바이트라 EAI 전송 계층({@code GwePayloadSection.lpadFit})이 넘치면 꼬리부터 자른다. 접두어 뒤에
     * 제목을 그대로 붙이면 제목이 길 때 " 결재 요청" 문구까지 잘려나가 결재 요청임을 알 수 없는 제목이 남는다. 그래서 접두어·접미어를 뺀 나머지 바이트만 제목에
     * 배정해 여기서 미리 잘라, 접미어가 항상 살아남게 한다.
     */
    private String subject(ApprovalMailContext context) {
        return SUBJECT_PREFIX + fitTitleForSubject(text(context.title())) + SUBJECT_SUFFIX;
    }

    /** 제목을 SUBJECT 필드 예산에서 접두어·접미어를 뺀 나머지 바이트로 자른다. */
    private static String fitTitleForSubject(String title) {
        int titleBudget =
                SUBJECT_BUDGET_BYTES
                        - MailHtml.utf8Length(SUBJECT_PREFIX)
                        - MailHtml.utf8Length(SUBJECT_SUFFIX);
        return MailHtml.truncateUtf8(title, titleBudget);
    }

    /**
     * 본문 HTML — 개요와 합계는 필수, 목록은 남는 예산만큼.
     *
     * @param subject 이미 조립된 메일 제목. 목록 packer가 후보 크기를 잴 때 {@link MailPayload} envelope에 그대로 실어
     *     재직렬화하므로 여기서 다시 계산하지 않고 전달받는다.
     */
    private String html(ApprovalMailContext context, String subject)
            throws JsonProcessingException {
        ApprovalMailSnapshot snapshot = parseSnapshot(context);
        List<ProjectItem> regular =
                sortedProjects(snapshot.projects().stream().filter(p -> !p.ordinary()).toList());
        List<ProjectItem> ordinary =
                sortedProjects(snapshot.projects().stream().filter(ProjectItem::ordinary).toList());
        List<CostItem> costs =
                snapshot.costs().stream()
                        .sorted(Comparator.comparing(CostItem::total).reversed())
                        .toList();

        StringBuilder body = new StringBuilder();
        body.append(titleBar());
        body.append(overview(context));
        body.append(summary(regular, ordinary, costs));

        List<ListEntry> entries = new ArrayList<>();
        regular.forEach(p -> entries.add(new ListEntry("정보화사업", p.abusNm(), p.total())));
        costs.forEach(c -> entries.add(new ListEntry("전산업무비", c.cttNm(), c.total())));
        ordinary.forEach(p -> entries.add(new ListEntry("경상사업", p.abusNm(), p.total())));
        body.append(itemList(context, subject, entries, body.toString()));

        return wrap(body.toString());
    }

    /** 스냅샷 파싱 — 없거나 깨졌으면 빈 스냅샷으로 접고 총괄표를 생략한다. */
    private ApprovalMailSnapshot parseSnapshot(ApprovalMailContext context) {
        if (!StringUtils.hasText(context.detailJson())) {
            return ApprovalMailSnapshot.empty();
        }
        try {
            return objectMapper.readValue(context.detailJson(), ApprovalMailSnapshot.class);
        } catch (JsonProcessingException e) {
            log.warn(
                    "신청서 스냅샷 파싱 실패 — 총괄표를 생략합니다: apfMngNo={}, 사유={}",
                    context.apfMngNo(),
                    e.getOriginalMessage());
            return ApprovalMailSnapshot.empty();
        }
    }

    /** 총 예산 내림차순 정렬. PDF 총괄표와 같은 순서다. */
    private static List<ProjectItem> sortedProjects(List<ProjectItem> items) {
        return items.stream().sorted(Comparator.comparing(ProjectItem::total).reversed()).toList();
    }

    private String wrap(String body) {
        return "<div style=\"font-family:'Malgun Gothic',sans-serif;color:#111827;"
                + "max-width:720px;\">"
                + body
                + "</div>";
    }

    private String titleBar() {
        return "<div style=\"background:"
                + MailHtml.PRIMARY
                + ";color:#fff;font-size:16px;font-weight:700;padding:10px 12px;"
                + "margin:0 0 14px;\">결재 요청</div>";
    }

    /** 신청서 개요 — 라벨-값 2열 표와 상세 바로가기. */
    private String overview(ApprovalMailContext context) {
        String rows =
                MailHtml.row(MailHtml.labelCell("신청서 제목"), MailHtml.textCell(text(context.title())))
                        + MailHtml.row(
                                MailHtml.labelCell("문서번호"),
                                MailHtml.textCell(text(context.apfMngNo())))
                        + MailHtml.row(
                                MailHtml.labelCell("신청일자"),
                                MailHtml.textCell(
                                        context.requestedDate() == null
                                                ? ""
                                                : context.requestedDate().format(DATE)))
                        + MailHtml.row(
                                MailHtml.labelCell("기안자"),
                                MailHtml.textCell(text(context.requesterName())))
                        + MailHtml.row(
                                MailHtml.labelCell("작성부서"),
                                MailHtml.textCell(text(context.deptName())));
        return MailHtml.sectionTitle("신청서 개요") + MailHtml.table(rows) + linkButton(context);
    }

    private String linkButton(ApprovalMailContext context) {
        return "<div style=\"margin:0 0 18px;\"><a href=\""
                + MailHtml.escape(context.detailUrl())
                + "\" style=\"display:inline-block;background:"
                + MailHtml.PRIMARY
                + ";color:#fff;text-decoration:none;font-size:13px;font-weight:600;"
                + "padding:8px 14px;border-radius:4px;\">신청서 상세 보기</a></div>";
    }

    /** 총괄표 합계 — 항목이 없는 구분은 행을 생략한다. */
    private String summary(
            List<ProjectItem> regular, List<ProjectItem> ordinary, List<CostItem> costs) {
        if (regular.isEmpty() && ordinary.isEmpty() && costs.isEmpty()) {
            return "";
        }
        StringBuilder rows = new StringBuilder();
        rows.append(
                MailHtml.row(
                        MailHtml.labelCell("구분"),
                        MailHtml.labelCell("건수"),
                        MailHtml.labelCell("총 예산"),
                        MailHtml.labelCell("자본예산"),
                        MailHtml.labelCell("일반관리비")));

        BigDecimal[] grand = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        if (!regular.isEmpty()) {
            rows.append(projectSummaryRow("정보화사업", regular, grand));
        }
        if (!costs.isEmpty()) {
            rows.append(costSummaryRow(costs, grand));
        }
        if (!ordinary.isEmpty()) {
            rows.append(projectSummaryRow("경상사업", ordinary, grand));
        }
        int count = regular.size() + ordinary.size() + costs.size();
        rows.append(
                MailHtml.row(
                        MailHtml.labelCell("합계"),
                        MailHtml.amountCell(count + "건"),
                        MailHtml.amountCell(MailHtml.amount(grand[0])),
                        MailHtml.amountCell(MailHtml.amount(grand[1])),
                        MailHtml.amountCell(MailHtml.amount(grand[2]))));
        return MailHtml.sectionTitle("신청내용") + MailHtml.table(rows.toString());
    }

    private String projectSummaryRow(String label, List<ProjectItem> items, BigDecimal[] grand) {
        BigDecimal total = sum(items.stream().map(ProjectItem::total).toList());
        BigDecimal asset = sum(items.stream().map(ProjectItem::asset).toList());
        BigDecimal cost = sum(items.stream().map(ProjectItem::cost).toList());
        accumulate(grand, total, asset, cost);
        return summaryRow(label, items.size(), total, asset, cost);
    }

    private String costSummaryRow(List<CostItem> items, BigDecimal[] grand) {
        BigDecimal total = sum(items.stream().map(CostItem::total).toList());
        BigDecimal asset = sum(items.stream().map(CostItem::asset).toList());
        BigDecimal cost = sum(items.stream().map(CostItem::cost).toList());
        accumulate(grand, total, asset, cost);
        return summaryRow("전산업무비", items.size(), total, asset, cost);
    }

    private static String summaryRow(
            String label, int count, BigDecimal total, BigDecimal asset, BigDecimal cost) {
        return MailHtml.row(
                MailHtml.textCell(label),
                MailHtml.amountCell(count + "건"),
                MailHtml.amountCell(MailHtml.amount(total)),
                MailHtml.amountCell(MailHtml.amount(asset)),
                MailHtml.amountCell(MailHtml.amount(cost)));
    }

    private static void accumulate(
            BigDecimal[] grand, BigDecimal total, BigDecimal asset, BigDecimal cost) {
        grand[0] = grand[0].add(total);
        grand[1] = grand[1].add(asset);
        grand[2] = grand[2].add(cost);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 목록 한 줄. 자본예산·일반관리비는 바로 위 합계 총괄표가 구분별로 이미 보여주므로 목록에서는 생략하고, 그만큼 더 많은 사업을 싣는다.
     *
     * @param category 구분명 (정보화사업/전산업무비/경상사업)
     * @param name 사업명 또는 계약명. null 허용
     * @param total 총 예산
     */
    private record ListEntry(String category, String name, BigDecimal total) {}

    /** 목록 표 머리글 행. */
    private static String listHeaderRow() {
        return MailHtml.row(
                MailHtml.labelCell("구분"),
                MailHtml.labelCell("사업명/계약명"),
                MailHtml.labelCell("총 예산"));
    }

    private static String listRow(ListEntry entry) {
        return MailHtml.row(
                MailHtml.textCell(entry.category()),
                MailHtml.textCell(entry.name() == null ? "" : entry.name()),
                MailHtml.amountCell(MailHtml.amount(entry.total())));
    }

    /**
     * 신청 사업 목록을 남는 예산만큼 싣는다.
     *
     * <p>구분별로 표를 따로 두면 머리글이 세 번 반복되어 예산 대부분을 머리글이 먹는다. 구분 열을 가진 표 하나로 합치고 구분 순서(정보화사업 → 전산업무비 →
     * 경상사업), 구분 안에서는 총 예산 내림차순으로 싣는다.
     *
     * <p>예산 판정은 본문 바이트가 아니라 {@link #fitsBudget}으로 후보를 매번 실제 {@link MailPayload} JSON으로 재직렬화해 잰다.
     * 봉투({"subject":...,"html":...})와 본문 안 큰따옴표의 백슬래시 이스케이프가 본문 바이트 위에 그대로 얹히므로, 본문 바이트만 보고 채우면 그
     * 오버헤드를 사후에야 발견해 예산을 항상 넘기는 회귀가 생긴다(2da0942c). 재직렬화 비용은 후보가 많아야 수십 건이라 무시할 수 있는 수준이고, 손으로 이스케이프
     * 바이트 수를 세는 것보다 Jackson의 실제 이스케이프 규칙과 어긋날 위험이 없다.
     *
     * <p>잘림 안내({@link #moreLink})는 {@code context.detailUrl()}을 그대로 담아 호출자가 준 URL 길이에 따라 바이트 수가
     * 달라지므로, 매 후보마다 남은 건수로 다시 만들지 않고 전체 항목이 잘렸다고 가정한 안내({@code worstNotice})를 매번 함께 실어 예산을 잰다. 항목
     * 수가 가장 클 때 안내 문구도 가장 길므로(자릿수 증가) 이 값이 실제 필요보다 부족해지는 일은 없다.
     *
     * @param context 렌더링 입력 (전체 보기 링크용)
     * @param subject 메일 제목 — 후보 재직렬화 시 envelope에 함께 싣는다
     * @param entries 구분 순서로 이미 정렬된 목록
     * @param usedBody 지금까지 조립한 본문(개요·총괄표 등, {@link #wrap} 적용 전)
     * @return 목록 섹션 HTML. 머리글이나 첫 행조차 못 넣을 예산이면 잘림 안내만 담은 문구, 그 안내조차 못 넣을 예산이면 빈 문자열
     */
    private String itemList(
            ApprovalMailContext context, String subject, List<ListEntry> entries, String usedBody)
            throws JsonProcessingException {
        if (entries.isEmpty()) {
            return "";
        }
        String worstNotice = moreLink(entries.size(), context);
        if (!fitsBudget(subject, usedBody + worstNotice)) {
            // 안내 문구조차 못 실을 정도로 필수 본문이 이미 예산을 채웠으면 목록 섹션 전체를 생략한다.
            return "";
        }
        String header = MailHtml.sectionTitle("신청 사업 목록") + MailHtml.table(listHeaderRow());
        if (!fitsBudget(subject, usedBody + header + worstNotice)) {
            return worstNotice;
        }

        StringBuilder rows = new StringBuilder();
        int taken = 0;
        for (ListEntry entry : entries) {
            String candidateRows = rows + listRow(entry);
            String candidate =
                    usedBody
                            + MailHtml.sectionTitle("신청 사업 목록")
                            + MailHtml.table(listHeaderRow() + candidateRows)
                            + worstNotice;
            if (!fitsBudget(subject, candidate)) {
                break;
            }
            rows.append(listRow(entry));
            taken++;
        }
        if (taken == 0) {
            return worstNotice;
        }
        String section = MailHtml.sectionTitle("신청 사업 목록") + MailHtml.table(listHeaderRow() + rows);
        if (taken < entries.size()) {
            section += moreLink(entries.size() - taken, context);
        }
        return section;
    }

    /**
     * 후보 본문이 최종 저장 형태({@link MailPayload} JSON) 기준으로 예산 안에 들어오는지 그 자리에서 직접 직렬화해 확인한다.
     *
     * @param subject 메일 제목
     * @param bodyContent {@link #wrap} 적용 전 본문 후보
     * @return 실제로 저장될 JSON이 {@link #CONTENTS_BUDGET_BYTES} 이하이면 {@code true}
     */
    private boolean fitsBudget(String subject, String bodyContent) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(new MailPayload(subject, wrap(bodyContent)));
        return MailHtml.utf8Length(json) <= CONTENTS_BUDGET_BYTES;
    }

    private static String moreLink(int remaining, ApprovalMailContext context) {
        return "<div style=\"font-size:12px;color:#6b7280;margin:-8px 0 14px;\">외 "
                + remaining
                + "건 · <a href=\""
                + MailHtml.escape(context.detailUrl())
                + "\" style=\"color:"
                + MailHtml.PRIMARY
                + ";\">전체 보기</a></div>";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
