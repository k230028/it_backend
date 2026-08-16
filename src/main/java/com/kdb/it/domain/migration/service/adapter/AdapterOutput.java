package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import java.util.List;

/**
 * 어댑터 변환 결과입니다. 비어 있는 목록은 그 어댑터가 그 종류를 만들지 않는다는 뜻입니다.
 *
 * <p>{@code costs}·{@code projects}의 원소 순서는 {@code sheet.rows()} 순서와 1:1로 같습니다. 오케스트레이터가 인덱스로 행과
 * 짝지어 {@code CREATE_NEW} 결정이 난 행만 골라 쓰기 때문에, 어댑터는 행을 건너뛰지 않고 **모든 행에 대해** 생성요청을 만듭니다.
 *
 * @param costs 전산업무비 생성요청 (행 순서 유지)
 * @param projects 사업 생성요청 (품목 포함, 행 순서 유지)
 * @param plans 부문계획 조정 의도
 * @param allocations 편성 배분 의도 — 매칭·배분 단계에 넘깁니다
 */
public record AdapterOutput(
        List<CostDto.CreateRequest> costs,
        List<ProjectDto.CreateRequest> projects,
        List<PlanIntent> plans,
        List<AllocationIntent> allocations) {

    /** 아무것도 만들지 않은 결과입니다. */
    public static AdapterOutput empty() {
        return new AdapterOutput(List.of(), List.of(), List.of(), List.of());
    }
}
