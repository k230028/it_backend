package com.kdb.it.domain.migration.request.service.adapter;

import java.math.BigDecimal;

/**
 * 편성요청서 1-1이 선언한 사업 단위 금액입니다.
 *
 * <p>품목 합계에서 파생하지 않고 <b>시트에 적힌 값</b>에서 산출합니다. 총 예산은 전체기간 기준이라 예산연도 품목 합계보다 크고, 기 지급예산은 그 차액에서 예산연도
 * 이후 계획분을 뺀 값입니다. 두 성질 모두 품목 합산으로는 얻을 수 없어 별도 통로로 나릅니다.
 *
 * <p>환산 근거를 확보하지 못한 파일은 {@link #none()}을 실어 보내고, 반입은 기존 동작(품목 합계 스냅샷)을 그대로 씁니다.
 *
 * @param totRqmAmt 총소요금액 — 1-1 `총 사업금액(전체기간)` (원 단위)
 * @param mplAmt 예정금액 — 1-1 요약표 `'26년도 이후` 합계 (원 단위)
 * @param dfrAmt 지급금액 — `총 사업금액 − '26년도 이후 − '26년도 합계` (원 단위)
 */
public record ProjectAmounts(BigDecimal totRqmAmt, BigDecimal mplAmt, BigDecimal dfrAmt) {

    /** 선언 금액을 산출하지 못한 상태. 3필드 모두 null이라 공유 인스턴스로 둡니다. */
    private static final ProjectAmounts NONE = new ProjectAmounts(null, null, null);

    /**
     * 선언 금액이 없는 자리표시자를 반환합니다.
     *
     * @return 3필드가 모두 null인 불변 인스턴스
     */
    public static ProjectAmounts none() {
        return NONE;
    }

    /**
     * 기록할 선언 금액이 있는지 판정합니다.
     *
     * <p>세 값은 항상 함께 정해지거나 함께 비므로 대표값 하나만 봅니다.
     *
     * @return 산출에 성공했으면 true
     */
    public boolean isPresent() {
        return totRqmAmt != null;
    }
}
