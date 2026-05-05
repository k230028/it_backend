package com.kdb.it.domain.budget.status.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.status.repository.BudgetStatusQueryRepository;

/**
 * BudgetStatusService 단위 테스트
 *
 * <p>
 * 예산 현황 서비스의 3개 조회 메서드가 BudgetStatusQueryRepository에 정확히
 * 위임되는지 검증합니다. Oracle DB 없이 Mock 리포지토리로 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetStatusServiceTest {

    @Mock
    private BudgetStatusQueryRepository budgetStatusQueryRepository;

    @InjectMocks
    private BudgetStatusService budgetStatusService;

    @Test
    @DisplayName("getProjectStatus: 예산년도를 전달하여 정보화사업 현황 목록을 반환한다")
    void getProjectStatus_bgYy전달_리포지토리위임후반환() {
        // given
        String bgYy = "2026";
        List<BudgetStatusDto.ProjectResponse> mockResult = List.of();
        given(budgetStatusQueryRepository.findProjectStatus(bgYy)).willReturn(mockResult);

        // when
        List<BudgetStatusDto.ProjectResponse> result = budgetStatusService.getProjectStatus(bgYy);

        // then
        assertThat(result).isSameAs(mockResult);
        verify(budgetStatusQueryRepository).findProjectStatus(bgYy);
    }

    @Test
    @DisplayName("getCostStatus: 예산년도를 전달하여 전산업무비 현황 목록을 반환한다")
    void getCostStatus_bgYy전달_리포지토리위임후반환() {
        // given
        String bgYy = "2026";
        List<BudgetStatusDto.CostResponse> mockResult = List.of();
        given(budgetStatusQueryRepository.findCostStatus(bgYy)).willReturn(mockResult);

        // when
        List<BudgetStatusDto.CostResponse> result = budgetStatusService.getCostStatus(bgYy);

        // then
        assertThat(result).isSameAs(mockResult);
        verify(budgetStatusQueryRepository).findCostStatus(bgYy);
    }

    @Test
    @DisplayName("getOrdinaryStatus: 예산년도를 전달하여 경상사업 현황 목록을 반환한다")
    void getOrdinaryStatus_bgYy전달_리포지토리위임후반환() {
        // given
        String bgYy = "2026";
        List<BudgetStatusDto.OrdinaryResponse> mockResult = List.of();
        given(budgetStatusQueryRepository.findOrdinaryStatus(bgYy)).willReturn(mockResult);

        // when
        List<BudgetStatusDto.OrdinaryResponse> result = budgetStatusService.getOrdinaryStatus(bgYy);

        // then
        assertThat(result).isSameAs(mockResult);
        verify(budgetStatusQueryRepository).findOrdinaryStatus(bgYy);
    }

    @Test
    @DisplayName("getProjectStatus: 연도가 다르면 해당 연도로만 리포지토리를 호출한다")
    void getProjectStatus_다른연도전달_해당연도로조회() {
        // given
        String bgYy = "2025";
        given(budgetStatusQueryRepository.findProjectStatus(bgYy)).willReturn(List.of());

        // when
        budgetStatusService.getProjectStatus(bgYy);

        // then
        verify(budgetStatusQueryRepository).findProjectStatus("2025");
    }
}
