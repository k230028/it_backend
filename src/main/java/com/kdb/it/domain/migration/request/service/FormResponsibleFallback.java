package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.util.List;

/** 경상사업과 전산업무비가 공유하는 담당자 누락을 보정합니다. */
final class FormResponsibleFallback {

    private FormResponsibleFallback() {}

    static void apply(FormAdapterOutput output) {
        List<ProjectDto.CreateRequest> recurringProjects =
                output.projects().stream()
                        .filter(project -> "Y".equals(project.getOdnYn()))
                        .toList();
        String recurringResponsible =
                recurringProjects.stream()
                        .map(ProjectDto.CreateRequest::getUsid)
                        .filter(FormResponsibleFallback::hasText)
                        .findFirst()
                        .orElse(null);
        String expenseResponsible =
                output.costs().stream()
                        .map(CostDto.CreateRequest::getCgprId)
                        .filter(FormResponsibleFallback::hasText)
                        .findFirst()
                        .orElse(null);

        if (recurringResponsible == null && expenseResponsible != null) {
            recurringProjects.stream()
                    .filter(project -> !hasText(project.getUsid()))
                    .forEach(project -> project.setUsid(expenseResponsible));
        } else if (expenseResponsible == null && recurringResponsible != null) {
            output.costs().stream()
                    .filter(cost -> !hasText(cost.getCgprId()))
                    .forEach(cost -> cost.setCgprId(recurringResponsible));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
