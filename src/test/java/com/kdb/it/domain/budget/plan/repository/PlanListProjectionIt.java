package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PlanListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired BplanmRepository repository;

    @Test
    void 목록프로젝션이엔티티목록과동일하다() {
        List<Bplanm> entities = repository.findAllByDelYnOrderByFstEnrDtmDesc("N");
        List<BplanmRepository.PlanListView> views = repository.findListViewsByDelYnOrderByFstEnrDtmDesc("N");

        assertThat(views).hasSameSizeAs(entities);
        Map<String, Bplanm> byReqDocNo = entities.stream()
                .collect(Collectors.toMap(Bplanm::getReqDocNo, Function.identity()));
        for (BplanmRepository.PlanListView view : views) {
            Bplanm entity = byReqDocNo.get(view.getReqDocNo());
            assertThat(entity).isNotNull();
            assertThat(view.getReqDocNo()).isEqualTo(entity.getReqDocNo());
            assertThat(view.getItPtlPlnTpC()).isEqualTo(entity.getItPtlPlnTpC());
            assertThat(view.getBseYy()).isEqualTo(entity.getBseYy());
            assertThat(view.getAduTotAmt()).isEqualTo(entity.getAduTotAmt());
            assertThat(view.getCpitBgApvAmt()).isEqualTo(entity.getCpitBgApvAmt());
            assertThat(view.getTotXpAmt()).isEqualTo(entity.getTotXpAmt());
            assertThat(view.getFstEnrDtm()).isEqualTo(entity.getFstEnrDtm());
            assertThat(view.getFstEnrUsid()).isEqualTo(entity.getFstEnrUsid());
            assertThat(view.getRedtConeInf()).isEqualTo(entity.getRedtConeInf());
        }
        assertThat(BplanmRepository.PlanListView.class.getDeclaredMethods()).hasSize(9);
    }
}
