package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 전산관리비(Bcostm) 커스텀 리포지토리 인터페이스
 *
 * <p>Spring Data JPA의 기본 메서드로 처리하기 어려운 복잡한 동적 쿼리를 위한
 * 커스텀 인터페이스입니다. {@link CostRepositoryImpl}에서 QueryDSL로 구현됩니다.</p>
 *
 * <p>사용 패턴: {@link CostRepository}가 이 인터페이스를 상속하므로,
 * {@code costRepository.searchByCondition(condition)}과 같이 직접 사용 가능합니다.</p>
 */
public interface CostRepositoryCustom {

    /**
     * 검색 조건으로 전산관리비 목록 조회 (동적 쿼리)
     *
     * <p>
     * {@link CostDto.SearchCondition}의 필드 값에 따라 동적으로 WHERE 절을 구성합니다.
     * 모든 조건이 null이면 {@code DEL_YN='N'}인 전체 전산관리비를 반환합니다.
     * </p>
     *
     * <p>
     * {@code apfSts} 처리 방식:
     * </p>
     * <ul>
     * <li>{@code "none"}: CAPPLA 연결이 없는 전산관리비 (NOT EXISTS 서브쿼리)</li>
     * <li>그 외 값: 해당 전산관리비의 최신 CAPPLA 연결 신청서의 결재상태가 일치하는 경우 (EXISTS + MAX 서브쿼리)</li>
     * </ul>
     *
     * <p>구현: {@link CostRepositoryImpl#searchByCondition(CostDto.SearchCondition)}</p>
     *
     * @param condition 검색 조건 DTO (apfSts, costSvnDpmC, svnTemC, sectSysUtzYn, bseYy)
     * @return 조건에 맞는 전산관리비 목록 (DEL_YN='N' 필터 항상 적용)
     */
    List<Bcostm> searchByCondition(CostDto.SearchCondition condition);

    /**
     * 검색 조건에 해당하는 전산관리비 건수 (COUNT 쿼리, 전체 적재 회피)
     *
     * <p>{@link #searchByCondition(CostDto.SearchCondition)}와 동일한 WHERE 조건을 사용하므로
     * {@code searchByCondition(condition).size()}와 결과가 정확히 일치합니다. 미상신 건수 배지 등
     * 건수만 필요한 경로에서 엔티티 전체 적재를 회피하기 위해 사용합니다.</p>
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 전산관리비 건수 (DEL_YN='N' 필터 항상 적용)
     */
    long countBySearchCondition(CostDto.SearchCondition condition);

    /**
     * 전년도 예산 합계 일괄 조회 (계속 항목 전용)
     *
     * <p>
     * 주어진 관리번호 목록과 전년도 연도로 TPRMPP_BCOSTM에서 AMT(전산업무비예산금액) 합계를
     * costBgNo별로 집계하여 반환합니다.
     * </p>
     *
     * @param costBgNos 전산관리비 관리번호 목록 (계속 항목만)
     * @param prevYear  전년도 연도 문자열 (예: "2025")
     * @return costBgNo → AMT(전산업무비예산금액) 합계 맵
     */
    Map<String, BigDecimal> sumPrevBgByCostBgNos(List<String> costBgNos, String prevYear);
}
