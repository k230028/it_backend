package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.common.util.ListPageParams;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import java.util.List;

/**
 * 전산관리비(Bcostm) 커스텀 리포지토리 인터페이스
 *
 * <p>Spring Data JPA의 기본 메서드로 처리하기 어려운 복잡한 동적 쿼리를 위한 커스텀 인터페이스입니다. {@link CostRepositoryImpl}에서
 * QueryDSL로 구현됩니다.
 *
 * <p>사용 패턴: {@link CostRepository}가 이 인터페이스를 상속하므로, {@code
 * costRepository.searchByCondition(condition)}과 같이 직접 사용 가능합니다.
 */
public interface CostRepositoryCustom {

    /**
     * 검색 조건으로 전산관리비 목록 조회 (동적 쿼리)
     *
     * <p>{@link CostDto.SearchCondition}의 필드 값에 따라 동적으로 WHERE 절을 구성합니다. 모든 조건이 null이면 {@code
     * DEL_YN='N'}인 전체 전산관리비를 반환합니다.
     *
     * <p>{@code apfSts} 처리 방식:
     *
     * <ul>
     *   <li>{@code "none"}: CAPPLA 연결이 없는 전산관리비 (NOT EXISTS 서브쿼리)
     *   <li>그 외 값: 해당 전산관리비의 최신 CAPPLA 연결 신청서의 결재상태가 일치하는 경우 (EXISTS + MAX 서브쿼리)
     * </ul>
     *
     * <p>구현: {@link CostRepositoryImpl#searchByCondition(CostDto.SearchCondition)}
     *
     * @param condition 검색 조건 DTO (apfSts, costSvnDpmC, svnTemC, sectSysUtzYn, bseYy)
     * @return 조건에 맞는 전산관리비 목록 (DEL_YN='N' 필터 항상 적용)
     */
    List<Bcostm> searchByCondition(CostDto.SearchCondition condition);

    /**
     * 검색 조건에 해당하는 전산관리비를 지정한 페이지 구간만 조회합니다.
     *
     * <p>페이지를 지정하지 않은 {@link #searchByCondition(CostDto.SearchCondition)}은 목록 상한까지 조회하는 이 메서드의 특수한
     * 경우입니다.
     *
     * @param condition 검색 조건 DTO
     * @param paging 페이지 파라미터 (미지정이면 상한까지)
     * @return 해당 구간의 전산관리비 목록 (DEL_YN='N' 필터 항상 적용)
     * @throws IllegalArgumentException 페이지 파라미터가 유효 범위를 벗어난 경우
     */
    List<Bcostm> searchByCondition(CostDto.SearchCondition condition, ListPageParams paging);

    /**
     * 검색 조건에 해당하는 전산관리비 건수 (COUNT 쿼리, 전체 적재 회피)
     *
     * <p>{@link #searchByCondition(CostDto.SearchCondition)}와 동일한 WHERE 조건을 사용하므로 {@code
     * searchByCondition(condition).size()}와 결과가 정확히 일치합니다. 미상신 건수 배지 등 건수만 필요한 경로에서 엔티티 전체 적재를 회피하기
     * 위해 사용합니다.
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 전산관리비 건수 (DEL_YN='N' 필터 항상 적용)
     */
    long countBySearchCondition(CostDto.SearchCondition condition);

    /**
     * 목록 경량 프로젝션 조회(#7) — {@link #searchByCondition}와 동일 WHERE, select만 목록 표시 컬럼으로 축소(미표시
     * 환산/외화/연기/담당자 컬럼 제외).
     *
     * <p>행 집합은 {@code searchByCondition}과 동일하나 목록에 불필요한 컬럼을 적재하지 않는다. 상세는 기존 엔티티 조회 경로를 유지한다(결정 B).
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 전산관리비 경량 목록 행 (DEL_YN='N' 필터 항상 적용)
     */
    List<CostDto.CostListRow> searchListByCondition(CostDto.SearchCondition condition);
}
