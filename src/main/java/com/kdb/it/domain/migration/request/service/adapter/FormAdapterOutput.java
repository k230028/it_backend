package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.ArrayList;
import java.util.List;

/**
 * 어댑터 1회 실행의 산출물입니다.
 *
 * <p>원장을 직접 만들지 않고 <b>기존 서비스에 넘길 생성 요청</b>만 조립합니다. 새 INSERT 경로를 만들면 채번·조직명 스냅샷·감사로그를 전부 다시 구현해야
 * 합니다.
 *
 * <p>{@code projectAmounts}는 {@code projects}와 <b>인덱스가 대응하는 병렬 목록</b>입니다. 생성자가 길이 일치를 강제하고 {@link
 * #merge}가 두 목록을 같은 순서로 이어 붙여 대응을 유지합니다. 요청 DTO에 금액 필드를 얹지 않는 이유는 그러면 공개 API로 스냅샷을 주입할 수 있게 되기
 * 때문입니다.
 *
 * @param projects 생성할 사업 요청 (품목 포함)
 * @param costs 생성할 전산업무비 요청
 * @param diagnostics 어댑터가 낸 해석 진단
 * @param suggestedGeneralExpenseUnit 시트 ③ 단위 제안값. 시트 ③이 없으면 null
 * @param projectAmounts 사업별 1-1 선언 금액. {@code projects}와 길이·순서가 같습니다
 */
public record FormAdapterOutput(
        List<ProjectDto.CreateRequest> projects,
        List<CostDto.CreateRequest> costs,
        List<RequestFormDto.FormDiagnostic> diagnostics,
        AmountUnit suggestedGeneralExpenseUnit,
        List<ProjectAmounts> projectAmounts) {

    public FormAdapterOutput {
        if (projectAmounts.size() != projects.size()) {
            throw new IllegalArgumentException(
                    "선언 금액 목록은 사업 목록과 길이가 같아야 합니다: projects=%d, projectAmounts=%d"
                            .formatted(projects.size(), projectAmounts.size()));
        }
    }

    /**
     * 선언 금액을 내지 않는 어댑터용 생성자입니다.
     *
     * <p>사업마다 {@link ProjectAmounts#none()}을 채웁니다. 1-1 선언 금액은 정보화사업 어댑터만 산출하므로, 나머지 어댑터가 빈 목록을 손으로
     * 만들지 않게 합니다.
     *
     * @param projects 생성할 사업 요청
     * @param costs 생성할 전산업무비 요청
     * @param diagnostics 어댑터가 낸 해석 진단
     * @param suggestedGeneralExpenseUnit 시트 ③ 단위 제안값. 없으면 null
     */
    public FormAdapterOutput(
            List<ProjectDto.CreateRequest> projects,
            List<CostDto.CreateRequest> costs,
            List<RequestFormDto.FormDiagnostic> diagnostics,
            AmountUnit suggestedGeneralExpenseUnit) {
        this(
                projects,
                costs,
                diagnostics,
                suggestedGeneralExpenseUnit,
                projects.stream().map(project -> ProjectAmounts.none()).toList());
    }

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
        List<ProjectAmounts> mergedAmounts = new ArrayList<>(projectAmounts);
        mergedAmounts.addAll(other.projectAmounts());
        return new FormAdapterOutput(
                List.copyOf(mergedProjects),
                List.copyOf(mergedCosts),
                List.copyOf(mergedDiagnostics),
                suggestedGeneralExpenseUnit != null
                        ? suggestedGeneralExpenseUnit
                        : other.suggestedGeneralExpenseUnit(),
                List.copyOf(mergedAmounts));
    }
}
