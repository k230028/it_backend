package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.math.BigDecimal;
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

    @Test
    @DisplayName("4-인자 생성자는 사업마다 선언 금액 없음을 채운다")
    void fourArgConstructorFillsNoneForEachProject() {
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(project("사업1"), project("사업2")), List.of(), List.of(), null);

        assertThat(output.projectAmounts()).hasSize(2);
        assertThat(output.projectAmounts()).allMatch(amounts -> !amounts.isPresent());
    }

    @Test
    @DisplayName("선언 금액 목록의 길이가 사업 목록과 다르면 생성 자체를 거부한다")
    void rejectsMismatchedAmountsSize() {
        assertThatThrownBy(
                        () ->
                                new FormAdapterOutput(
                                        List.of(project("사업1")),
                                        List.of(),
                                        List.of(),
                                        null,
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길이가 같아야");
    }

    @Test
    @DisplayName("합칠 때 선언 금액도 사업과 같은 순서로 이어 붙인다")
    void mergeKeepsAmountsAlignedWithProjects() {
        FormAdapterOutput left =
                new FormAdapterOutput(
                        List.of(project("사업1")),
                        List.of(),
                        List.of(),
                        null,
                        List.of(
                                new ProjectAmounts(
                                        new BigDecimal("100"),
                                        new BigDecimal("20"),
                                        new BigDecimal("30"))));
        FormAdapterOutput right =
                new FormAdapterOutput(List.of(project("사업2")), List.of(), List.of(), null);

        FormAdapterOutput merged = left.merge(right);

        assertThat(merged.projects()).hasSize(2);
        assertThat(merged.projectAmounts()).hasSize(2);
        assertThat(merged.projectAmounts().get(0).dfrAmt()).isEqualByComparingTo("30");
        assertThat(merged.projectAmounts().get(1).isPresent()).isFalse();
    }

    @Test
    @DisplayName("빈 산출물의 선언 금액도 비어 있다")
    void emptyHasNoAmounts() {
        assertThat(FormAdapterOutput.empty().projectAmounts()).isEmpty();
    }
}
