package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.util.List;

/** 경상사업과 전산업무비가 공유하는 담당자 누락을 보정합니다. */
final class FormResponsibleFallback {

    private FormResponsibleFallback() {}

    /**
     * 경상사업({@code odnYn='Y'})과 전산업무비 중 한쪽에만 담당자가 있으면 그 값을 반대쪽의 누락분에 채운다.
     *
     * <p>양쪽 모두 담당자가 있거나 양쪽 모두 없으면 아무것도 하지 않는다. 보정에 쓰는 값은 각 목록에서 처음 발견한 담당자 하나다.
     *
     * @param output 어댑터 산출물. 담당자 필드를 제자리에서 수정한다
     */
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
