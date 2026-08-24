package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** 활성 품목과 지급금액으로 정보화사업 금액을 계산합니다. */
@Component
public class ProjectAmountCalculator {

    private static final int AMOUNT_SCALE = 3;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.999");

    /**
     * 현재 요청금액, 예정금액, 지급금액과 총소요금액을 계산합니다.
     *
     * @param activeItems 활성 품목 목록
     * @param paidAmt 기 지급금액. null이면 0으로 처리합니다.
     * @return 계산된 사업 금액 요약
     * @throws IllegalArgumentException 외화 품목의 환율이 없거나 0 이하인 경우
     */
    public ProjectAmountSummary calculate(Collection<Bitemm> activeItems, BigDecimal paidAmt) {
        BigDecimal currentRequestAmt = BigDecimal.ZERO;
        BigDecimal plannedAmt = BigDecimal.ZERO;

        for (Bitemm item : activeItems) {
            currentRequestAmt = currentRequestAmt.add(orZero(item.getAmt()));
            plannedAmt = plannedAmt.add(toPlannedKrw(item));
        }

        currentRequestAmt = normalizeAmount(currentRequestAmt, "당해 요청금액");
        plannedAmt = normalizeAmount(plannedAmt, "예정금액");
        BigDecimal normalizedPaidAmt = normalizeAmount(paidAmt, "지급금액");
        BigDecimal totalRequiredAmt =
                normalizeAmount(currentRequestAmt.add(plannedAmt).add(normalizedPaidAmt), "총소요금액");
        return new ProjectAmountSummary(
                currentRequestAmt, plannedAmt, normalizedPaidAmt, totalRequiredAmt);
    }

    /**
     * 품목의 예정금액을 원화로 환산합니다.
     *
     * @param item 활성 품목
     * @return 원화 기준 예정금액
     * @throws IllegalArgumentException 외화 품목의 환율이 없거나 0 이하인 경우
     */
    BigDecimal toPlannedKrw(Bitemm item) {
        BigDecimal plannedAmt = orZero(item.getMplAmt());
        if (!isForeignCurrency(item)) {
            return normalizeAmount(plannedAmt, "예정금액");
        }

        BigDecimal xcr = item.getXcr();
        if (xcr == null || xcr.signum() <= 0) {
            throw new IllegalArgumentException("외화 품목의 유효한 환율이 필요합니다.");
        }
        return normalizeAmount(plannedAmt.multiply(xcr), "예정금액");
    }

    private boolean isForeignCurrency(Bitemm item) {
        return item.getCurC() != null && !"KRW".equalsIgnoreCase(item.getCurC());
    }

    private BigDecimal orZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private BigDecimal normalizeAmount(BigDecimal amount, String fieldLabel) {
        BigDecimal normalized = orZero(amount).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(fieldLabel + "이 저장 가능한 NUMBER(18,3) 범위를 넘습니다.");
        }
        return normalized;
    }
}
