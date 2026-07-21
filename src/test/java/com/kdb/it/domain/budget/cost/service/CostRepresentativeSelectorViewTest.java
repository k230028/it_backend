package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.cost.repository.CostRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CostRepresentativeSelectorViewTest {

    private record Row(String costBgNo, Integer bgSno, String lstYn, String cttNm)
            implements CostRepository.CostRepresentativeView {
        @Override public String getCostBgNo() { return costBgNo; }
        @Override public Integer getBgSno() { return bgSno; }
        @Override public String getLstYn() { return lstYn; }
        @Override public String getCttNm() { return cttNm; }
    }

    @Test
    void 최신표시행을우선한다() {
        var old = new Row("BG-DUP", 1, "N", "구버전");
        var latest = new Row("BG-DUP", 2, "Y", "최신");

        assertThat(CostRepresentativeSelector.pickView(List.of(old, latest))).isSameAs(latest);
    }

    @Test
    void 프로젝션은정확히네필드만노출한다() {
        assertThat(CostRepository.CostRepresentativeView.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder("getCostBgNo", "getBgSno", "getLstYn", "getCttNm");
    }
}
