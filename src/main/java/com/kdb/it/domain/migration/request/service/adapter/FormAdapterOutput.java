package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.ArrayList;
import java.util.List;

/**
 * 어댑터 1회 실행의 산출물입니다.
 *
 * <p>원장을 직접 만들지 않고 <b>기존 서비스에 넘길 생성 요청</b>만 조립합니다. 새 INSERT 경로를 만들면 채번·조직명 스냅샷·감사로그를 전부 다시 구현해야
 * 합니다.
 *
 * @param projects 생성할 사업 요청 (품목 포함)
 * @param costs 생성할 전산업무비 요청
 * @param diagnostics 어댑터가 낸 해석 진단
 * @param suggestedGeneralExpenseMultiplier 시트 ③ 단위 제안값. 시트 ③이 없으면 null
 */
public record FormAdapterOutput(
        List<ProjectDto.CreateRequest> projects,
        List<CostDto.CreateRequest> costs,
        List<RequestFormDto.FormDiagnostic> diagnostics,
        Long suggestedGeneralExpenseMultiplier) {

    /**
     * 산출물이 없는 결과를 만듭니다.
     *
     * @return 빈 산출물
     */
    public static FormAdapterOutput empty() {
        return new FormAdapterOutput(List.of(), List.of(), List.of(), null);
    }

    /**
     * 다른 어댑터의 산출물과 합칩니다.
     *
     * @param other 합칠 산출물
     * @return 합쳐진 산출물. 단위 제안값은 null이 아닌 쪽을 남깁니다
     */
    public FormAdapterOutput merge(FormAdapterOutput other) {
        List<ProjectDto.CreateRequest> mergedProjects = new ArrayList<>(projects);
        mergedProjects.addAll(other.projects());
        List<CostDto.CreateRequest> mergedCosts = new ArrayList<>(costs);
        mergedCosts.addAll(other.costs());
        List<RequestFormDto.FormDiagnostic> mergedDiagnostics = new ArrayList<>(diagnostics);
        mergedDiagnostics.addAll(other.diagnostics());
        return new FormAdapterOutput(
                List.copyOf(mergedProjects),
                List.copyOf(mergedCosts),
                List.copyOf(mergedDiagnostics),
                suggestedGeneralExpenseMultiplier != null
                        ? suggestedGeneralExpenseMultiplier
                        : other.suggestedGeneralExpenseMultiplier());
    }
}
