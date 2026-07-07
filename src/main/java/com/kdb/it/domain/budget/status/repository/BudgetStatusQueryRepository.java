package com.kdb.it.domain.budget.status.repository;

import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount;

import java.util.List;

/**
 * 예산 현황 QueryDSL 쿼리 인터페이스
 *
 * <p>
 * 3개 탭(정보화사업/전산업무비/경상사업)별 전용 쿼리를 정의합니다.
 * 각 쿼리는 DB 레벨에서 조인+피벗을 처리하여 단일 호출로 정제된 데이터를 반환합니다.
 * </p>
 *
 * // Design Ref: §3.4 — BudgetStatusQueryRepository 설계
 */
public interface BudgetStatusQueryRepository {

    /**
     * 정보화사업 예산 현황 조회
     *
     * <p>BPROJM(ODN_YN!='Y', LST_YN='Y') LEFT JOIN BITEMM(품목구분별 피벗) LEFT JOIN BBUGTM(비목별 피벗)</p>
     *
     * @param bgYy 예산년도
     * @return 정보화사업별 편성요청/조정 금액 목록
     */
    List<BudgetStatusDto.ProjectResponse> findProjectStatus(String bgYy);

    /**
     * 전산업무비 예산 현황 조회
     *
     * <p>BCOSTM(LST_YN='Y') LEFT JOIN BBUGTM(비목별 매핑)</p>
     *
     * @param bgYy 예산년도
     * @return 전산업무비별 편성요청/조정 금액 목록
     */
    List<BudgetStatusDto.CostResponse> findCostStatus(String bgYy);

    /**
     * 경상사업 예산 현황 조회
     *
     * <p>BPROJM(ODN_YN='Y', LST_YN='Y') LEFT JOIN BITEMM(기계장치/기타무형자산 분리)</p>
     *
     * @param bgYy 예산년도
     * @return 경상사업별 기계장치/기타무형자산 상세 목록
     */
    List<BudgetStatusDto.OrdinaryResponse> findOrdinaryStatus(String bgYy);

    /**
     * 카테고리·연도 기준 편성요청액·편성액 합계 조회 (Tiptap 변수 해석 전용)
     *
     * <p>카테고리 필터링은 모두 BITEMM 비목구분({@code Ccodem.cTp}, {@code cId='IOE'}) 기준입니다.</p>
     *
     * 편성요청액은 저장 시점에 원화로 환산된 BITEMM({@code amt}), 편성액은 BBUGTM({@code orcTb='BITEMM'}, {@code dupBgAmt}) 합계.
     * <ul>
     *   <li>{@code IT_BUDGET} → 전체 BITEMM 합계 (정보화·경상·일반관리비 모두 포함, 비목 필터 없음)</li>
     *   <li>{@code CAP_BUDGET} → {@code cTp ∈ ('IOE_DVC','IOE_HW','IOE_SW')} 자본예산 항목</li>
     *   <li>{@code OPEX} → {@code cTp ∈ ('IOE_IDR','IOE_SEVS','IOE_XPN','IOE_LEAFE')} 일반관리비 항목</li>
     * </ul>
     * 데이터가 전혀 없으면 {@code AggregatedAmount(null, null)}을 반환합니다.
     *
     * @param year         예산년도 (예: 2026)
     * @param categoryCode 카테고리 코드 ({@code IT_BUDGET} | {@code CAP_BUDGET} | {@code OPEX})
     * @return 편성요청액·편성액 합계 (원 단위)
     */
    AggregatedAmount aggregateByCategory(int year, String categoryCode);

    /**
     * 사업·연도 기준 편성요청액·편성액 합계 조회 (Tiptap 변수 해석 전용)
     *
     * <p>
     * 특정 정보화사업({@code BPROJM.PRJ_MNG_NO=projectCode}, {@code LST_YN='Y'})의
     * BITEMM 금액 합계 + 매핑된 BBUGTM 편성예산을 반환합니다.
     * 데이터가 없으면 {@code AggregatedAmount(null, null)}을 반환합니다.
     * </p>
     *
     * @param year        예산년도 (예: 2026)
     * @param projectCode 정보화사업 관리번호 (예: {@code PRJ-2026-0001})
     * @return 편성요청액·편성액 합계 (원 단위)
     */
    AggregatedAmount aggregateByProject(int year, String projectCode);
}
