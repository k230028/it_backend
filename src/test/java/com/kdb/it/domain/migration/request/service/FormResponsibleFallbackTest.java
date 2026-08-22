package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormResponsibleFallbackTest {

    @Test
    @DisplayName("경상사업 담당자만 없으면 전산업무비 담당자를 채운다")
    void fillsRecurringResponsibleFromGeneralExpense() {
        ProjectDto.CreateRequest recurring = recurringProject(null);
        CostDto.CreateRequest expense = cost("김담당");
        FormAdapterOutput output = output(List.of(recurring), List.of(expense));

        FormResponsibleFallback.apply(output);

        assertThat(recurring.getUsid()).isEqualTo("김담당");
        assertThat(expense.getCgprId()).isEqualTo("김담당");
    }

    @Test
    @DisplayName("전산업무비 담당자만 없으면 경상사업 담당자를 모든 행에 채운다")
    void fillsGeneralExpenseResponsibleFromRecurring() {
        ProjectDto.CreateRequest recurring = recurringProject("박실무");
        CostDto.CreateRequest first = cost(null);
        CostDto.CreateRequest second = cost(null);
        FormAdapterOutput output = output(List.of(recurring), List.of(first, second));

        FormResponsibleFallback.apply(output);

        assertThat(List.of(first, second))
                .extracting(CostDto.CreateRequest::getCgprId)
                .containsOnly("박실무");
    }

    @Test
    @DisplayName("양쪽 담당자가 모두 있으면 각 시트의 값을 유지한다")
    void preservesBothResponsibleValues() {
        ProjectDto.CreateRequest recurring = recurringProject("경상담당");
        CostDto.CreateRequest expense = cost("업무비담당");
        FormAdapterOutput output = output(List.of(recurring), List.of(expense));

        FormResponsibleFallback.apply(output);

        assertThat(recurring.getUsid()).isEqualTo("경상담당");
        assertThat(expense.getCgprId()).isEqualTo("업무비담당");
    }

    private static ProjectDto.CreateRequest recurringProject(String responsible) {
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setOdnYn("Y");
        project.setUsid(responsible);
        return project;
    }

    private static CostDto.CreateRequest cost(String responsible) {
        CostDto.CreateRequest cost = new CostDto.CreateRequest();
        cost.setCgprId(responsible);
        return cost;
    }

    private static FormAdapterOutput output(
            List<ProjectDto.CreateRequest> projects, List<CostDto.CreateRequest> costs) {
        return new FormAdapterOutput(projects, costs, List.of(), null);
    }
}
