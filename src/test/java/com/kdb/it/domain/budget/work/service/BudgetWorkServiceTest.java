package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기존 호출자가 유지하는 BudgetWorkService 호환 위임 계약을 검증합니다. */
class BudgetWorkServiceTest {

    private final BudgetRateApplicationService rateService =
            mock(BudgetRateApplicationService.class);
    private final BudgetSummaryService summaryService = mock(BudgetSummaryService.class);
    private final BudgetProjectSummaryService projectSummaryService =
            mock(BudgetProjectSummaryService.class);
    private final BudgetWorkService facade =
            new BudgetWorkService(rateService, summaryService, projectSummaryService);

    @Test
    @DisplayName("getIoeCategories - 비목 요약 서비스의 응답을 그대로 반환한다")
    void getIoeCategories_요약서비스위임() {
        List<BudgetWorkDto.IoeCategoryResponse> expected = List.of();
        given(summaryService.getIoeCategories("2026")).willReturn(expected);

        assertThat(facade.getIoeCategories("2026")).isSameAs(expected);
        verify(summaryService).getIoeCategories("2026");
    }

    @Test
    @DisplayName("applyRates - 편성 적용 서비스의 응답을 그대로 반환한다")
    void applyRates_적용서비스위임() {
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of());
        BudgetWorkDto.ApplyResponse expected = new BudgetWorkDto.ApplyResponse("완료", 0, null);
        given(rateService.applyRates(request)).willReturn(expected);

        assertThat(facade.applyRates(request)).isSameAs(expected);
        verify(rateService).applyRates(request);
    }

    @Test
    @DisplayName("applyItemRates - 사업별 편성 적용 서비스의 응답을 그대로 반환한다")
    void applyItemRates_적용서비스위임() {
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of());
        BudgetWorkDto.ApplyResponse expected = new BudgetWorkDto.ApplyResponse("완료", 0, null);
        given(rateService.applyItemRates(request)).willReturn(expected);

        assertThat(facade.applyItemRates(request)).isSameAs(expected);
        verify(rateService).applyItemRates(request);
    }

    @Test
    @DisplayName("getSummary - 전체와 선택 원본 오버로드를 비목 요약 서비스에 위임한다")
    void getSummary_두공개시그니처위임() {
        BudgetWorkDto.SummaryResponse all = summary(BigDecimal.ONE);
        BudgetWorkDto.SummaryResponse selected = summary(BigDecimal.TEN);
        given(summaryService.getSummary("2026")).willReturn(all);
        given(summaryService.getSummary("2026", List.of("SRC-1"))).willReturn(selected);

        assertThat(facade.getSummary("2026")).isSameAs(all);
        assertThat(facade.getSummary("2026", List.of("SRC-1"))).isSameAs(selected);
        verify(summaryService).getSummary("2026");
        verify(summaryService).getSummary("2026", List.of("SRC-1"));
    }

    @Test
    @DisplayName("getProjectSummary - 사업 요약 서비스의 응답을 그대로 반환한다")
    void getProjectSummary_사업요약서비스위임() {
        BudgetWorkDto.ProjectSummaryResponse expected =
                new BudgetWorkDto.ProjectSummaryResponse(
                        List.of(),
                        List.of(),
                        new BudgetWorkDto.SummaryTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        given(projectSummaryService.getProjectSummary("2026")).willReturn(expected);

        assertThat(facade.getProjectSummary("2026")).isSameAs(expected);
        verify(projectSummaryService).getProjectSummary("2026");
    }

    private BudgetWorkDto.SummaryResponse summary(BigDecimal amount) {
        return new BudgetWorkDto.SummaryResponse(
                List.of(), new BudgetWorkDto.SummaryTotals(amount, amount));
    }
}
