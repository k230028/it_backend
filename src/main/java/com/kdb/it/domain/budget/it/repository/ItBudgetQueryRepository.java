package com.kdb.it.domain.budget.it.repository;

import com.kdb.it.domain.budget.it.dto.ItBudgetDto;

import java.util.List;

/**
 * 정보기술부문 예산 집계 쿼리 인터페이스
 *
 * <p>
 * IT/정보보호 구분 비목별 편성요청액·편성액 집계 쿼리를 정의합니다.
 * BITEMM + BCOSTM 요청금액과 BBUGTM 편성액을 infPrtYn 기준으로 분리합니다.
 * </p>
 */
public interface ItBudgetQueryRepository {

    /**
     * 비목별 IT/정보보호 구분 편성요청액·편성액 집계
     *
     * <p>
     * 편성요청액: BITEMM(→BPROJM 연도필터) + BCOSTM, infPrtYn 기준 분리.
     * 편성액: BBUGTM(orcTb='BITEMM'→BITEMM, orcTb='BCOSTM'→BCOSTM), infPrtYn 기준 분리.
     * 금액 단위: 천원 (원 합계 / 1000, TRUNCATE).
     * </p>
     *
     * @param bgYy 예산년도 (예: "2026")
     * @return 비목별 집계 행 목록 (ioeCode 오름차순)
     */
    List<ItBudgetDto.CategoryRow> findSummary(String bgYy);
}
