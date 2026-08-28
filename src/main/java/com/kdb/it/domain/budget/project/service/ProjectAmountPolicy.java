package com.kdb.it.domain.budget.project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 정보화사업 금액의 Oracle NUMBER(18,3) 저장 정책을 한 곳에서 적용합니다. */
final class ProjectAmountPolicy {

    private static final int SCALE = 3;
    private static final BigDecimal MAX_VALUE = new BigDecimal("999999999999999.999");

    private ProjectAmountPolicy() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /** null을 0으로 보고 HALF_UP 반올림한 뒤 NUMBER(18,3) 범위를 검증합니다. */
    static BigDecimal normalize(BigDecimal amount, String fieldLabel) {
        BigDecimal normalized =
                (amount == null ? BigDecimal.ZERO : amount).setScale(SCALE, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException(fieldLabel + "이 저장 가능한 NUMBER(18,3) 범위를 넘습니다.");
        }
        return normalized;
    }

    /** 각 금액을 먼저 NUMBER(18,3) 단위로 반올림한 뒤 합계를 검증합니다. */
    static BigDecimal sumNormalized(
            BigDecimal first, BigDecimal second, BigDecimal third, String fieldLabel) {
        BigDecimal normalizedFirst =
                (first == null ? BigDecimal.ZERO : first).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedSecond =
                (second == null ? BigDecimal.ZERO : second).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedThird =
                (third == null ? BigDecimal.ZERO : third).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal total = normalizedFirst.add(normalizedSecond).add(normalizedThird);
        if (total.abs().compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException(fieldLabel + "이 저장 가능한 NUMBER(18,3) 범위를 넘습니다.");
        }
        return total;
    }
}
