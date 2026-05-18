package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 예산 편성 결과 집계 쿼리 리포지토리
 *
 * <p>결재완료 원본 데이터를 단일 집계 쿼리로 조회하여
 * {@code BudgetWorkService.getSummary()} 내 N+1 쿼리를 제거합니다.</p>
 */
public interface BudgetWorkQueryRepository {

    /**
     * 결재완료 전산업무비(BCOSTM) 요청금액을 비목코드(ioeC)별로 집계합니다.
     *
     * @param bgYy 예산연도
     * @return ioeC → itMngcBgAmt 합계 맵
     */
    Map<String, BigDecimal> findApprovedCostAmountByIoeC(String bgYy);

    /**
     * 결재완료 품목(BITEMM) 요청금액을 품목구분(ioeC)별로 집계합니다.
     *
     * <p>환율(xcr)이 있으면 {@code gclAmt * xcr}, 없으면 {@code gclAmt}로 합산합니다.</p>
     *
     * @param bgYy 예산연도
     * @return ioeC → (gclAmt * xcr) 합계 맵
     */
    Map<String, BigDecimal> findApprovedItemAmountByGclDtt(String bgYy);
}
