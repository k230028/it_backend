package com.kdb.it.domain.budget.work.service;

import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 기존 호출자를 유지하면서 예산작업 책임 빈으로 위임하는 호환 파사드입니다. */
@Service
@RequiredArgsConstructor
public class BudgetWorkService {

    private final BudgetRateApplicationService rateApplicationService;
    private final BudgetSummaryService summaryService;
    private final BudgetProjectSummaryService projectSummaryService;

    /**
     * 편성비목 목록을 조회합니다.
     *
     * @param bgYy 예산연도
     * @return 편성비목 목록
     */
    public List<BudgetWorkDto.IoeCategoryResponse> getIoeCategories(String bgYy) {
        return summaryService.getIoeCategories(bgYy);
    }

    /**
     * 비목별 편성률을 적용합니다.
     *
     * @param request 편성률 적용 요청
     * @return 적용 결과와 요약
     */
    public BudgetWorkDto.ApplyResponse applyRates(BudgetWorkDto.ApplyRequest request) {
        return rateApplicationService.applyRates(request);
    }

    /**
     * 사업별 편성률을 적용합니다.
     *
     * @param request 사업별 편성률 적용 요청
     * @return 적용 결과와 요약
     */
    public BudgetWorkDto.ApplyResponse applyItemRates(BudgetWorkDto.ItemApplyRequest request) {
        return rateApplicationService.applyItemRates(request);
    }

    /**
     * 예산연도 전체 편성 요약을 조회합니다.
     *
     * @param bgYy 예산연도
     * @return 비목별 편성 요약
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy) {
        return summaryService.getSummary(bgYy);
    }

    /**
     * 선택 원본에 한정한 편성 요약을 조회합니다.
     *
     * @param bgYy 예산연도
     * @param srcPks 선택 원본 PK
     * @return 비목별 편성 요약
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy, List<String> srcPks) {
        return summaryService.getSummary(bgYy, srcPks);
    }

    /**
     * 사업별 편성 결과를 조회합니다.
     *
     * @param bgYy 예산연도
     * @return 사업별 편성 결과
     */
    public BudgetWorkDto.ProjectSummaryResponse getProjectSummary(String bgYy) {
        return projectSummaryService.getProjectSummary(bgYy);
    }
}
