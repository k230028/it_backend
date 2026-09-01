package com.kdb.it.domain.budget.common.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 예산 목록 스코프별 버전 노출 판정과 결재상태 정규화 규칙을 검증합니다. */
class BudgetListVersionScopeTest {

    @Test
    @DisplayName("라벨로 들어온 결재상태를 코드로 정규화한다")
    void normalizesLabelToCode() {
        assertThat(BudgetListVersionScope.normalize("결재중")).isEqualTo("1");
        assertThat(BudgetListVersionScope.normalize("결재완료")).isEqualTo("2");
        assertThat(BudgetListVersionScope.normalize("반려")).isEqualTo("3");
        assertThat(BudgetListVersionScope.normalize("회수")).isEqualTo("4");
    }

    @Test
    @DisplayName("이미 코드이거나 미상신 키워드면 그대로 둔다")
    void keepsCodeAndNoneAsIs() {
        assertThat(BudgetListVersionScope.normalize("1")).isEqualTo("1");
        assertThat(BudgetListVersionScope.normalize("none")).isEqualTo("none");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("값이 없으면 정규화하지 않고 그대로 반환한다")
    void keepsBlankAsIs(String apfSts) {
        assertThat(BudgetListVersionScope.normalize(apfSts)).isEqualTo(apfSts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "1", "3", "4", "결재중", "반려", "회수"})
    @DisplayName("미상신·결재중·반려·회수 스코프는 재상신 초안까지 노출한다")
    void includesDraftsForOpenScopes(String apfSts) {
        assertThat(BudgetListVersionScope.includesDrafts(apfSts)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "결재완료", "0", "수기등록"})
    @DisplayName("결재완료·수기등록 스코프는 최종본만 노출해 과거 승인본 중복을 막는다")
    void excludesDraftsForCompletedScope(String apfSts) {
        assertThat(BudgetListVersionScope.includesDrafts(apfSts)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("결재상태 필터가 없는 일반 목록은 최종본만 노출한다")
    void excludesDraftsWhenNoFilter(String apfSts) {
        assertThat(BudgetListVersionScope.includesDrafts(apfSts)).isFalse();
    }

    @Test
    @DisplayName("알 수 없는 값은 최종본만 노출하는 쪽으로 처리한다")
    void excludesDraftsForUnknownValue() {
        assertThat(BudgetListVersionScope.includesDrafts("9")).isFalse();
    }
}
