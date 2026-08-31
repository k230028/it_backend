package com.kdb.it.domain.budget.plan.repository;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlanListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired BplanmRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 고유픽스처의필드필터정렬이프로젝션에정확히매핑된다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String oldNo = "BE03-PLN-OLD-" + suffix;
        String newNo = "BE03-PLN-NEW-" + suffix;
        String deletedNo = "BE03-PLN-DEL-" + suffix;
        LocalDateTime oldTime = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime newTime = oldTime.plusHours(1);

        entityManager.persist(plan(oldNo, oldTime, "{\"prjSnapshots\":[]}", "N"));
        entityManager.persist(plan(newNo, newTime, "{\"prjSnapshots\":[{\"id\":1}]}", "N"));
        entityManager.persist(plan(deletedNo, newTime.plusHours(2), "{}", "Y"));
        entityManager.flush();
        entityManager.clear();

        List<BplanmRepository.PlanListView> views =
                repository.findListViewsByDelYnOrderByFstEnrDtmDesc("N");
        BplanmRepository.PlanListView newView =
                views.stream()
                        .filter(view -> newNo.equals(view.getReqDocNo()))
                        .findFirst()
                        .orElseThrow();

        assertThat(newView.getItPtlPlnTpC()).isEqualTo("01");
        assertThat(newView.getBseYy()).isEqualTo("2026");
        assertThat(newView.getAduTotAmt()).isEqualByComparingTo("300");
        assertThat(newView.getCpitBgApvAmt()).isEqualByComparingTo("200");
        assertThat(newView.getTotXpAmt()).isEqualByComparingTo("100");
        assertThat(newView.getFstEnrDtm()).isEqualTo(newTime);
        assertThat(newView.getFstEnrUsid()).isEqualTo("BE03-TEST");
        assertThat(newView.getRedtConeInf()).isEqualTo("{\"prjSnapshots\":[{\"id\":1}]}");
        assertThat(views)
                .extracting(view -> view.getReqDocNo())
                .contains(oldNo, newNo)
                .doesNotContain(deletedNo);
        assertThat(views.indexOf(newView))
                .isLessThan(
                        views.indexOf(
                                views.stream()
                                        .filter(view -> oldNo.equals(view.getReqDocNo()))
                                        .findFirst()
                                        .orElseThrow()));
        assertThat(declaredMethodNames(BplanmRepository.PlanListView.class)).hasSize(10);
    }

    private Bplanm plan(
            String reqDocNo,
            LocalDateTime createdAt,
            String snapshot,
            String delYn) {
        return Bplanm.builder()
                .reqDocNo(reqDocNo)
                .itPtlPlnTpC("01")
                .bseYy("2026")
                .aduTotAmt(new BigDecimal("300"))
                .cpitBgApvAmt(new BigDecimal("200"))
                .totXpAmt(new BigDecimal("100"))
                .redtConeInf(snapshot)
                .delYn(delYn)
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
