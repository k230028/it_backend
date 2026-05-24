package com.kdb.it.domain.budget.cost.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예산 금액 재계산 헬퍼.
 *
 * <p>
 * 외화/원화 행에 대해 클라이언트 입력값을 서버에서 정규화한다.
 * Bcostm(전산관리비 - itMngcBgAmt), Btermm(단말기 - tmlAmt),
 * Bitemm(품목 - gclAmt) 3개 도메인에서 공유 사용된다.
 * </p>
 *
 * <p>정책 (CONTEXT.md 결정 B / C / D):</p>
 * <ul>
 *   <li><b>결정 C — 외화 행</b>: curC != null && curC != "KRW" 이면서
 *       fcAmt != null && xcr > 0 인 경우, 클라이언트가 보낸 원화금액(krwAmt)을
 *       무시하고 <code>fcAmt × xcr</code>로 재계산하여 저장한다.
 *       라운딩은 setScale(3, HALF_UP) (DOMAIN.md 금액 18,3).</li>
 *   <li><b>결정 B — 원화 행</b>: curC == "KRW" 또는 curC == null 인 경우,
 *       클라이언트 krwAmt를 그대로 보존하고 fcAmt는 NULL로 강제한다.</li>
 *   <li><b>결정 D — 데이터 불완전 외화 행</b>: 외화임에도 fcAmt 또는 xcr 이 누락/0 이면
 *       재계산을 포기하고 krwAmt 보존 + fcAmt NULL 강제.</li>
 * </ul>
 *
 * <p>
 * 인자 검증: 음수 fcAmt/xcr는 이 헬퍼가 던지지 않는다. 서비스 레이어 책임.
 * </p>
 */
public final class BudgetAmountCalculator {

    /** 금액 정밀도 스케일 (DOMAIN.md 18,3). */
    private static final int AMOUNT_SCALE = 3;

    private BudgetAmountCalculator() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 외화/원화 행 정규화.
     *
     * @param fcAmt  클라이언트 입력 외화금액 (외화 행에서만 유효)
     * @param krwAmt 클라이언트 입력 원화금액 (정규화 전)
     * @param curC   통화코드 (예: "KRW", "USD", "JPY")
     * @param xcr    환율 (외화 행에서만 유효)
     * @return 길이 2의 BigDecimal 배열.
     *         result[0] = 저장될 원화금액 (외화면 fcAmt × xcr 재계산값, 원화면 krwAmt 보존).
     *         result[1] = 저장될 외화금액 (외화면 fcAmt 보존, 원화/불완전이면 NULL).
     */
    public static BigDecimal[] reconcileAmount(BigDecimal fcAmt, BigDecimal krwAmt, String curC, BigDecimal xcr) {
        boolean isForeign = isForeignRow(curC);
        if (isForeign && fcAmt != null && xcr != null && xcr.signum() > 0) {
            // 결정 C: 외화 정상 → 서버가 재계산
            BigDecimal recomputedKrw = fcAmt.multiply(xcr).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            return new BigDecimal[] { recomputedKrw, fcAmt };
        }
        // 결정 B (원화) / 결정 D (데이터 불완전) → krwAmt 보존 + fcAmt NULL 강제
        return new BigDecimal[] { krwAmt, null };
    }

    /**
     * 외화 행 여부 판정.
     *
     * @param curC 통화코드
     * @return curC가 null이 아니고 "KRW"가 아니면 true
     */
    public static boolean isForeignRow(String curC) {
        return curC != null && !"KRW".equals(curC);
    }
}
