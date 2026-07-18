package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
     * @param bgYy   예산연도
     * @param srcPks 선택 원본 PK(costBgNo) 한정 집합. null/빈 값이면 전체(연도 기준).
     * @return ioeC → itMngcBgAmt 합계 맵
     */
    Map<String, BigDecimal> findApprovedCostAmountByIoeC(String bgYy, Collection<String> srcPks);

    /**
     * 결재완료 품목(BITEMM) 요청금액을 품목구분(ioeC)별로 집계합니다.
     *
     * <p>BITEMM.amt는 저장 시점에 원화로 환산된 금액이므로 환율을 다시 곱하지 않고 합산합니다.</p>
     *
     * @param bgYy   예산연도
     * @param srcPks 선택 원본 PK(gclMngNo) 한정 집합. null/빈 값이면 전체(연도 기준).
     * @return ioeC → gclAmt 합계 맵
     */
    Map<String, BigDecimal> findApprovedItemAmountByGclDtt(String bgYy, Collection<String> srcPks);

    /**
     * 결재완료 원본의 PK 집합을 조회합니다.
     *
     * <p>결재완료 전산업무비(BCOSTM) {@code costBgNo}와 결재완료 정보화사업(BPROJM) 소속
     * 품목(BITEMM) {@code gclMngNo}의 합집합입니다. BBUGTM 편성액 집계 시 이 집합에 속한
     * 원본(=BBUGTM.pkColNm)만 포함시켜, 미결재·취소된 원본의 stale 편성액이 합계를
     * 부풀리는 것을 방지하는 화이트리스트로 사용합니다.</p>
     *
     * @param bgYy 예산연도
     * @return 결재완료 원본 PK 집합 (BCOSTM.costBgNo ∪ BITEMM.gclMngNo)
     */
    Set<String> findApprovedSourcePks(String bgYy);
}
