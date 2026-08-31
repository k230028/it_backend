package com.kdb.it.domain.budget.cost.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class CostActiveQueryContractTest {

    @Test
    @DisplayName("일반 비용 단건 조회는 LST_YN Y 최종본 저장소 메서드로 위임한다")
    void 일반비용단건조회는_최종본저장소메서드로위임한다() {
        CostRepository repository = mock(CostRepository.class, Answers.CALLS_REAL_METHODS);
        List<Bcostm> finalCosts = List.of(Bcostm.builder().costBgNo("COST-1").bgSno(2).build());
        given(repository.findByCostBgNoAndDelYnAndLstYn("COST-1", "N", "Y")).willReturn(finalCosts);

        List<Bcostm> result = repository.findByCostBgNoAndDelYn("COST-1", "N");

        assertThat(result).isSameAs(finalCosts);
        verify(repository).findByCostBgNoAndDelYnAndLstYn("COST-1", "N", "Y");
    }

    @Test
    @DisplayName("일반 비용 목록과 일괄 조회는 LST_YN Y 최종본 저장소 메서드로 위임한다")
    void 일반비용목록과일괄조회는_최종본저장소메서드로위임한다() {
        CostRepository repository = mock(CostRepository.class, Answers.CALLS_REAL_METHODS);
        List<Bcostm> finalCosts = List.of(Bcostm.builder().costBgNo("COST-1").bgSno(2).build());
        List<String> costIds = List.of("COST-1");
        given(repository.findAllByDelYnAndLstYn("N", "Y")).willReturn(finalCosts);
        given(repository.findByCostBgNoInAndDelYnAndLstYn(costIds, "N", "Y"))
                .willReturn(finalCosts);

        assertThat(repository.findAllByDelYn("N")).isSameAs(finalCosts);
        assertThat(repository.findByCostBgNoInAndDelYn(costIds, "N")).isSameAs(finalCosts);

        verify(repository).findAllByDelYnAndLstYn("N", "Y");
        verify(repository).findByCostBgNoInAndDelYnAndLstYn(costIds, "N", "Y");
    }
}
