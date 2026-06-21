package com.kdb.it.common.system.tiptap.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveResponse;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.status.repository.BudgetStatusQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiptapVariableServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private BudgetStatusQueryRepository budgetStatusRepository;

    private TiptapVariableService service;

    @BeforeEach
    void setUp() {
        // Task 4 채워질 예정 — 현재 호출 없음
        service = new TiptapVariableService(new TiptapTokenParser(), projectRepository, budgetStatusRepository);
    }

    @Test
    @DisplayName("metadata — 4개 카테고리(전산/자본/일반관리비/사업별) 반환")
    void getMetadata_returnsFourCategories() {
        when(projectRepository.findActiveProjectRefs()).thenReturn(List.of(
                new Bprojm.Ref("PROJ001", "차세대 시스템 구축")
        ));

        MetadataResponse response = service.getMetadata();

        assertThat(response.categories()).extracting("code")
                .containsExactly("IT_BUDGET", "CAP_BUDGET", "OPEX", "PROJ");
    }

    @Test
    @DisplayName("metadata — PROJ 카테고리는 사업 목록 포함, 나머지는 null")
    void getMetadata_projectsOnlyOnProjCategory() {
        when(projectRepository.findActiveProjectRefs()).thenReturn(List.of(
                new Bprojm.Ref("PROJ001", "차세대 시스템 구축")
        ));

        MetadataResponse response = service.getMetadata();

        var byCode = response.categories().stream()
                .collect(Collectors.toMap(c -> c.code(), c -> c));
        assertThat(byCode.get("IT_BUDGET").projects()).isNull();
        assertThat(byCode.get("PROJ").projects()).hasSize(1);
        assertThat(byCode.get("PROJ").projects().get(0).code()).isEqualTo("PROJ001");
    }

    @Test
    @DisplayName("metadata — 모든 카테고리에 3개 항목")
    void getMetadata_eachCategoryHasThreeItems() {
        when(projectRepository.findActiveProjectRefs()).thenReturn(List.of());

        MetadataResponse response = service.getMetadata();

        response.categories().forEach(c ->
                assertThat(c.items()).extracting("key")
                        .containsExactly("requestAmount", "allocatedAmount", "allocationRate"));
    }

    @Test
    @DisplayName("metadata — years는 현재 연도 ±2 범위")
    void getMetadata_yearsAreCurrentPlusMinusTwo() {
        when(projectRepository.findActiveProjectRefs()).thenReturn(List.of());

        MetadataResponse response = service.getMetadata();
        int now = Year.now().getValue();

        response.categories().forEach(c ->
                assertThat(c.years()).containsExactly(now - 2, now - 1, now, now + 1, now + 2));
    }

    @Test
    @DisplayName("resolve — 잘못된 토큰은 INVALID 반환")
    void resolve_invalidToken_returnsInvalid() {
        var response = service.resolve(java.util.List.of("not-a-valid-token"), null);
        assertThat(response.results().get("not-a-valid-token").status()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("resolve — 데이터 없으면 MISSING 반환")
    void resolve_noData_returnsMissing() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(null, null));

        var response = service.resolve(java.util.List.of("2026.itBudget.requestAmount"), null);
        assertThat(response.results().get("2026.itBudget.requestAmount").status()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("resolve — 정상 토큰은 OK + 포맷된 값 반환 (억원 단위)")
    void resolve_okToken_returnsFormattedValue() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(
                        90_000_000_000L, 85_000_000_000L));

        var response = service.resolve(java.util.List.of("2026.itBudget.requestAmount"), null);
        var resolved = response.results().get("2026.itBudget.requestAmount");
        assertThat(resolved.status()).isEqualTo("OK");
        assertThat(resolved.value()).isEqualTo("900억원");
    }

    @Test
    @DisplayName("resolve — 편성률은 % 단위로 포맷")
    void resolve_allocationRate_returnsPercent() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(
                        100_000_000_000L, 85_300_000_000L));

        var response = service.resolve(java.util.List.of("2026.itBudget.allocationRate"), null);
        assertThat(response.results().get("2026.itBudget.allocationRate").value()).isEqualTo("85.3%");
    }

    @Test
    @DisplayName("resolve — 사업별 편성액은 프로젝트 집계에서 만원 단위로 포맷한다")
    void resolve_projectAllocatedAmount_formatsManWon() {
        when(budgetStatusRepository.aggregateByProject(2026, "PRJ001"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(50_000L, 20_000L));

        // PROJ 토큰은 관리자/부서매니저만 허용되므로 관리자 사용자로 호출한다.
        CustomUserDetails admin = mock(CustomUserDetails.class);
        given(admin.isAdmin()).willReturn(true);
        var response = service.resolve(List.of("2026.proj.PRJ001.allocatedAmount"), admin);

        assertThat(response.results().get("2026.proj.PRJ001.allocatedAmount").value()).isEqualTo("2만원");
    }

    @Test
    @DisplayName("resolve — 천원 단위 요청액은 원 단위로 포맷한다")
    void resolve_smallRequestAmount_formatsWon() {
        when(budgetStatusRepository.aggregateByCategory(2026, "OPEX"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(9_999L, 1L));

        var response = service.resolve(List.of("2026.opex.requestAmount"), null);

        assertThat(response.results().get("2026.opex.requestAmount").value()).isEqualTo("9999원");
    }

    @Test
    @DisplayName("resolve — 편성액이 없으면 MISSING을 반환한다")
    void resolve_allocatedAmountNull_returnsMissing() {
        when(budgetStatusRepository.aggregateByCategory(2026, "CAP_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(100L, null));

        var response = service.resolve(List.of("2026.capBudget.allocatedAmount"), null);

        assertThat(response.results().get("2026.capBudget.allocatedAmount").status()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("resolve — 요청액이 0인 편성률은 MISSING을 반환한다")
    void resolve_zeroRequestRate_returnsMissing() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(0L, 10L));

        var response = service.resolve(List.of("2026.itBudget.allocationRate"), null);

        assertThat(response.results().get("2026.itBudget.allocationRate").status()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("resolve: 동일 (year,category) 항목 3종은 집계 쿼리를 1회만 호출한다(인트라요청 메모이즈)")
    void resolve_memoizesAggregatePerRequest() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(
                        90_000_000_000L, 76_000_000_000L));

        service.resolve(List.of(
                "2026.itBudget.requestAmount",
                "2026.itBudget.allocatedAmount",
                "2026.itBudget.allocationRate"), null);

        org.mockito.Mockito.verify(budgetStatusRepository, org.mockito.Mockito.times(1))
                .aggregateByCategory(2026, "IT_BUDGET");
    }

    @Test
    @DisplayName("resolve: 일반 사용자가 사업(PROJ) 토큰을 요청하면 FORBIDDEN을 반환한다")
    void resolve_일반사용자_PROJ토큰_FORBIDDEN() {
        // Arrange
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.isAdmin()).willReturn(false);
        given(user.isDeptManager()).willReturn(false);

        // Act
        ResolveResponse res = service.resolve(List.of("2026.proj.P001.allocationRate"), user);

        // Assert
        assertThat(res.results().get("2026.proj.P001.allocationRate").status()).isEqualTo("FORBIDDEN");
    }
}
