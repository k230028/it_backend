package com.kdb.it.common.system.tiptap.service;

import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
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
        var response = service.resolve(java.util.List.of("not-a-valid-token"));
        assertThat(response.results().get("not-a-valid-token").status()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("resolve — 데이터 없으면 MISSING 반환")
    void resolve_noData_returnsMissing() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(null, null));

        var response = service.resolve(java.util.List.of("2026.itBudget.requestAmount"));
        assertThat(response.results().get("2026.itBudget.requestAmount").status()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("resolve — 정상 토큰은 OK + 포맷된 값 반환 (억원 단위)")
    void resolve_okToken_returnsFormattedValue() {
        when(budgetStatusRepository.aggregateByCategory(2026, "IT_BUDGET"))
                .thenReturn(new com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount(
                        90_000_000_000L, 85_000_000_000L));

        var response = service.resolve(java.util.List.of("2026.itBudget.requestAmount"));
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

        var response = service.resolve(java.util.List.of("2026.itBudget.allocationRate"));
        assertThat(response.results().get("2026.itBudget.allocationRate").value()).isEqualTo("85.3%");
    }
}
