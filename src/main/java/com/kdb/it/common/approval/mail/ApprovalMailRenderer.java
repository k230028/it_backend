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

    /** 본문 바이트 예산 — GWE 전문 CONTENTS 필드 폭과 같다. */
    public static final int CONTENTS_BUDGET_BYTES = 4000;

    /** 잘림 안내와 닫는 태그를 넣을 여유. 예산을 꽉 채우고 나서 안내를 못 붙이는 일을 막는다. */
    private static final int TAIL_RESERVE_BYTES = 320;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    /**
     * 메일 페이로드 JSON을 만듭니다.
     *
     * @param context 렌더링 입력
     * @return {@code {"subject":...,"html":...}} JSON. 실패하면 {@code null}(호출자는 기존 기본 본문으로 폴백)
     */
    public String renderPayloadJson(ApprovalMailContext context) {
        try {
            MailPayload payload = new MailPayload(subject(context), html(context));
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("결재요청 메일 렌더링 실패: apfMngNo={}, 사유={}", context.apfMngNo(), e.toString());
            return null;
        }
    }

    /** 메일 제목 — 포탈 접두어와 결재 요청 문구를 붙인다. */
    private String subject(ApprovalMailContext context) {
        return "[IT정보화포탈] %s 결재 요청".formatted(text(context.title()));
    }

    /** 본문 HTML — 개요와 합계는 필수, 목록은 남는 예산만큼. */
    private String html(ApprovalMailContext context) {
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
        body.append(itemList(context, entries, MailHtml.utf8Length(wrap(body.toString()))));

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
     * @param context 렌더링 입력 (전체 보기 링크용)
     * @param entries 구분 순서로 이미 정렬된 목록
     * @param usedBytes 지금까지 조립한 본문의 UTF-8 바이트
     * @return 목록 섹션 HTML. 머리글조차 못 넣을 예산이면 빈 문자열
     */
    private String itemList(ApprovalMailContext context, List<ListEntry> entries, int usedBytes) {
        if (entries.isEmpty()) {
            return "";
        }
        int budget = CONTENTS_BUDGET_BYTES - TAIL_RESERVE_BYTES - usedBytes;
        String shell = MailHtml.sectionTitle("신청 사업 목록") + MailHtml.table(listHeaderRow());
        int consumed = MailHtml.utf8Length(shell);
        if (consumed > budget) {
            return "";
        }

        StringBuilder included = new StringBuilder();
        int taken = 0;
        for (ListEntry entry : entries) {
            String row = listRow(entry);
            int next = MailHtml.utf8Length(row);
            if (consumed + next > budget) {
                break;
            }
            included.append(row);
            consumed += next;
            taken++;
        }
        if (taken == 0) {
            return "";
        }
        String section =
                MailHtml.sectionTitle("신청 사업 목록") + MailHtml.table(listHeaderRow() + included);
        if (taken < entries.size()) {
            section += moreLink(entries.size() - taken, context);
        }
        return section;
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
