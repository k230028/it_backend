package com.kdb.it.common.system.tiptap.service;

import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
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

    private TiptapVariableService service;

    @BeforeEach
    void setUp() {
        // Task 4에서 budgetStatusRepository 인수 추가됨
        service = new TiptapVariableService(new TiptapTokenParser(), projectRepository, null);
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
}
