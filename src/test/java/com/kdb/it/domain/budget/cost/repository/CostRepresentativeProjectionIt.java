package com.kdb.it.domain.budget.cost.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.service.CostRepresentativeSelector;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CostRepresentativeProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired CostRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 중복이력의네필드매핑과최신대표행선택을검증한다() {
        String costBgNo =
                ("BG-DUP-" + UUID.randomUUID().toString().replace("-", "")).substring(0, 15);
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);
        entityManager.persist(cost(costBgNo, 1, "N", "구버전", now));
        entityManager.persist(cost(costBgNo, 2, "Y", "최신", now));
        entityManager.flush();
        entityManager.clear();

        List<CostRepository.CostRepresentativeView> views =
                repository.findRepresentativeViewsByCostBgNoInAndDelYn(List.of(costBgNo), "N");

        assertThat(views).hasSize(2);
        assertThat(views)
                .extracting(
                        view -> view.getCostBgNo(),
                        view -> view.getBgSno(),
                        view -> view.getLstYn(),
                        view -> view.getCttNm())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(costBgNo, 1, "N", "구버전"),
                        org.assertj.core.groups.Tuple.tuple(costBgNo, 2, "Y", "최신"));
        assertThat(CostRepresentativeSelector.pickView(views).getCttNm()).isEqualTo("최신");
        assertThat(CostRepository.CostRepresentativeView.class.getDeclaredMethods()).hasSize(4);
    }

    private Bcostm cost(String costBgNo, int sno, String lstYn, String name, LocalDateTime now) {
        return Bcostm.builder()
                .costBgNo(costBgNo)
                .bgSno(sno)
                .lstYn(lstYn)
                .cttNm(name)
                .bseYy("2026")
                .delYn("N")
                .fstEnrDtm(now)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
