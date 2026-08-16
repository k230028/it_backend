package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import java.util.List;

/**
 * 어댑터 변환 결과입니다. 비어 있는 목록은 그 어댑터가 그 종류를 만들지 않는다는 뜻입니다.
 *
 * <p><b>Task 9가 실제로 의존하는 불변식은 {@code allocations}와 {@code costs}/{@code projects}가 서로 인덱스
 * 평행이라는 것입니다</b> — 오케스트레이터가 {@code allocations}의 인덱스로 {@code costs}/{@code projects}의 같은 위치를
 * 짝지어 {@code CREATE_NEW} 결정이 난 항목만 골라 쓰기 때문입니다. 어댑터는 자신이 내는 생성요청 하나마다 정확히 대응하는 배분 의도 하나를
 * 같은 위치에 만들어야 합니다.
 *
 * <p>이 인덱스 평행은 어댑터마다 **단위가 다릅니다**. 정보화사업({@code CapitalProjectSheetAdapter})·전산업무비({@code
 * CostSheetAdapter})는 행 단위라 {@code sheet.rows()} 순서와도 1:1로 같고(모든 행에 대해 건너뛰지 않고 생성요청을 만듭니다),
 * 부문계획({@code PlanAdjustmentSheetAdapter})은 {@code costs}·{@code projects}를 아예 만들지 않습니다. 반면
 * 위임예산({@code DelegatedBudgetSheetAdapter})은 **부점 그룹 단위**입니다 — 부점명으로 forward-fill한 그룹마다 사업·배분 의도를
 * 하나씩 내므로 {@code costs}/{@code projects}·{@code allocations}끼리는 서로 인덱스 평행이지만 {@code sheet.rows()}와는
 * 1:1이 아닙니다. 그래서 {@code sheet.rows()}의 행 번호로 역인덱싱해서는 안 되고, 반드시 {@code costs}/{@code
 * projects}·{@code allocations}를 같은 인덱스로만 짝지어야 합니다.
 *
 * @param costs 전산업무비 생성요청 ({@code allocations}와 인덱스 평행)
 * @param projects 사업 생성요청 (품목 포함, {@code allocations}와 인덱스 평행)
 * @param plans 부문계획 조정 의도
 * @param allocations 편성 배분 의도 — 매칭·배분 단계에 넘깁니다. {@code costs}·{@code projects}와 인덱스 평행입니다
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
