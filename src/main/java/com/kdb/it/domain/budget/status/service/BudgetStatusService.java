package com.kdb.it.domain.budget.status.service;

import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.status.repository.BudgetStatusQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산 현황 서비스
 *
 * <p>3개 탭(정보화사업/전산업무비/경상사업)의 예산 현황 데이터를 제공합니다. 피벗 집계 및 소계/합계/단가 계산은 QueryDSL 구현체에서 처리되며, 서비스는 트랜잭션
 * 관리와 리포지토리 호출을 담당합니다. 조회 전용 트랜잭션에서 탭별 예산 현황 집계를 제공합니다.
 *
 * <p>주의: 클래스 수준 @Transactional(readOnly=true) 적용 중.
 *
 * <p>향후 쓰기 메서드 추가 시 반드시 @Transactional 오버라이드 필요 (readOnly=false).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetStatusService {

    private final BudgetStatusQueryRepository budgetStatusQueryRepository;

    /**
     * 정보화사업 예산 현황 조회
     *
     * @param bgYy 예산년도
     * @return 정보화사업별 편성요청/조정 금액 목록
     */
    public List<BudgetStatusDto.ProjectResponse> getProjectStatus(String bgYy) {
        return budgetStatusQueryRepository.findProjectStatus(bgYy);
    }

    /**
     * 전산업무비 예산 현황 조회
     *
     * @param bgYy 예산년도
     * @return 전산업무비별 편성요청/조정 금액 목록
     */
    public List<BudgetStatusDto.CostResponse> getCostStatus(String bgYy) {
        return budgetStatusQueryRepository.findCostStatus(bgYy);
    }

    /**
     * 경상사업 예산 현황 조회
     *
     * @param bgYy 예산년도
     * @return 경상사업별 기계장치/기타무형자산 상세 목록
     */
    public List<BudgetStatusDto.OrdinaryResponse> getOrdinaryStatus(String bgYy) {
        return budgetStatusQueryRepository.findOrdinaryStatus(bgYy);
    }
}
