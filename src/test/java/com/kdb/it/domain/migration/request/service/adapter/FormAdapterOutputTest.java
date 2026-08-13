package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormAdapterOutputTest {

    private static RequestFormDto.FormDiagnostic diagnostic(String field) {
        return RequestFormDto.FormDiagnostic.of(
                null, null, field, RequestFormDiagnosticCode.OPTIONAL_MISSING, "경고", List.of());
    }

    private static ProjectDto.CreateRequest project(String name) {
        ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
        request.setAbusNm(name);
        return request;
    }

    private static CostDto.CreateRequest cost(String name) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setCttNm(name);
        return request;
    }

    @Test
    @DisplayName("빈 산출물은 목록이 비고 단위 제안값이 없다")
    void emptyHasNothing() {
        FormAdapterOutput empty = FormAdapterOutput.empty();

        assertThat(empty.projects()).isEmpty();
        assertThat(empty.costs()).isEmpty();
        assertThat(empty.diagnostics()).isEmpty();
        assertThat(empty.suggestedGeneralExpenseUnit()).isNull();
    }

    @Test
    @DisplayName("사업·전산업무비·진단을 순서대로 이어 붙인다")
    void mergeConcatenatesInOrder() {
        FormAdapterOutput first =
                new FormAdapterOutput(
                        List.of(project("사업1")),
                        List.of(cost("계약1")),
                        List.of(diagnostic("a")),
                        null);
        FormAdapterOutput second =
                new FormAdapterOutput(
                        List.of(project("사업2")),
                        List.of(cost("계약2")),
                        List.of(diagnostic("b")),
                        null);

        FormAdapterOutput merged = first.merge(second);

        assertThat(merged.projects())
                .extracting(ProjectDto.CreateRequest::getAbusNm)
                .containsExactly("사업1", "사업2");
        assertThat(merged.costs())
                .extracting(CostDto.CreateRequest::getCttNm)
                .containsExactly("계약1", "계약2");
        assertThat(merged.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::field)
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("단위 제안값은 먼저 정해진 쪽을 남긴다")
    void mergeKeepsFirstNonNullUnit() {
        FormAdapterOutput withUnit =
                new FormAdapterOutput(List.of(), List.of(), List.of(), AmountUnit.THOUSAND);
        FormAdapterOutput withoutUnit = FormAdapterOutput.empty();

        assertThat(withUnit.merge(withoutUnit).suggestedGeneralExpenseUnit())
                .isEqualTo(AmountUnit.THOUSAND);
        assertThat(withoutUnit.merge(withUnit).suggestedGeneralExpenseUnit())
                .isEqualTo(AmountUnit.THOUSAND);
        assertThat(withoutUnit.merge(withoutUnit).suggestedGeneralExpenseUnit()).isNull();
    }

    @Test
    @DisplayName("합친 결과는 불변이라 원본을 바꾸지 않는다")
    void mergeReturnsImmutableCopies() {
        FormAdapterOutput first =
                new FormAdapterOutput(List.of(project("사업1")), List.of(), List.of(), null);

        FormAdapterOutput merged = first.merge(FormAdapterOutput.empty());

        assertThat(first.projects()).hasSize(1);
        assertThat(merged.projects()).hasSize(1).isNotSameAs(first.projects());
    }
}
