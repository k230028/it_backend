package com.kdb.it.common.approval.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader.ParsedSnapshot;
import java.math.BigDecimal;
import java.util.List;

/**
 * 결재요청 메일이 읽는 신청서 상세 스냅샷.
 *
 * <p>공통 reader가 검증한 v2 payload 또는 기존 v1 배열을 총괄표 모델로 변환한다. 모르는 필드를 무시하는 규칙은 v1 변환에만 적용하며, v2는
 * reader의 깊은 구조·무결성 검증을 먼저 통과해야 한다.
 *
 * @param projects 정보화사업·경상사업 목록 (경상 구분은 {@code odnYn})
 * @param costs 전산업무비 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalMailSnapshot(List<ProjectItem> projects, List<CostItem> costs) {

    /** 누락 배열 보정 — 스냅샷 일부가 비어도 렌더링이 계속되도록 빈 목록으로 접는다. */
    public ApprovalMailSnapshot {
        projects = projects == null ? List.of() : List.copyOf(projects);
        costs = costs == null ? List.of() : List.copyOf(costs);
    }

    /** 스냅샷이 없거나 읽지 못했을 때 쓰는 빈 값. */
    public static ApprovalMailSnapshot empty() {
        return new ApprovalMailSnapshot(List.of(), List.of());
    }

    /** 공통 reader가 한 번 읽고 검증한 문서만 메일 요약으로 변환한다. v1의 얕은 변환 계약은 유지한다. */
    public static ApprovalMailSnapshot from(ParsedSnapshot parsed) {
        if (parsed.version() == 1) return parsed.legacyValue(ApprovalMailSnapshot.class);
        var payload = parsed.payload();
        return new ApprovalMailSnapshot(
                payload.projects().stream()
                        .map(
                                project ->
                                        new ProjectItem(
                                                project.name(),
                                                project.ordinaryYn(),
                                                project.currentRequestAmount(),
                                                project.assetBudget(),
                                                project.costBudget()))
                        .toList(),
                payload.costs().stream()
                        .map(
                                cost ->
                                        new CostItem(
                                                cost.name(),
                                                cost.totalAmount(),
                                                cost.assetBudget()))
                        .toList());
    }

    /** null 금액을 0으로 접는다. 스냅샷은 미입력 금액을 null로 남긴다. */
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 사업 한 건.
     *
     * @param abusNm 사업명
     * @param odnYn 경상사업여부 ("Y"면 경상사업)
     * @param totRqmAmt 총 예산
     * @param assetBg 자본예산
     * @param costBg 일반관리비
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectItem(
            String abusNm,
            String odnYn,
            BigDecimal totRqmAmt,
            BigDecimal assetBg,
            BigDecimal costBg) {

        /** 경상사업 여부. */
        public boolean ordinary() {
            return "Y".equals(odnYn);
        }

        /** 총 예산. */
        public BigDecimal total() {
            return zeroIfNull(totRqmAmt);
        }

        /** 자본예산. */
        public BigDecimal asset() {
            return zeroIfNull(assetBg);
        }

        /** 일반관리비. */
        public BigDecimal cost() {
            return zeroIfNull(costBg);
        }
    }

    /**
     * 전산업무비 한 건.
     *
     * @param cttNm 계약명
     * @param costTotXpAmt 전산업무비예산금액(총 예산)
     * @param assetBg 자본예산
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostItem(String cttNm, BigDecimal costTotXpAmt, BigDecimal assetBg) {

        /** 총 예산. */
        public BigDecimal total() {
            return zeroIfNull(costTotXpAmt);
        }

        /** 자본예산. */
        public BigDecimal asset() {
            return zeroIfNull(assetBg);
        }

        /** 일반관리비 — 전산업무비는 별도 컬럼이 없어 총 예산에서 자본예산을 뺀다(PDF 총괄표와 동일). */
        public BigDecimal cost() {
            return total().subtract(asset());
        }
    }
}
