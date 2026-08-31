package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** 조정 계획의 기준 계획 탐색 조건과 동률 정렬을 실제 Oracle에서 검증한다. */
class CouncilBaselineLookupIt extends AbstractOracleRepositoryTest {

    @Autowired CouncilRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("동률 등록시각에서는 협의회ID 내림차순으로 기준 계획을 선택한다")
    void 기준계획탐색_동률타이브레이크() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 11, 0);
        String year = "2126";
        String planA = "BE13-PLN-A-" + suffix;
        String planB = "BE13-PLN-B-" + suffix;
        entityManager.persist(plan(planA, year, "신규", createdAt));
        entityManager.persist(plan(planB, year, "신규", createdAt));
        entityManager.persist(council("BE13-ASCT-1-" + suffix, planA, "13", createdAt));
        entityManager.persist(council("BE13-ASCT-2-" + suffix, planB, "13", createdAt));
        entityManager.flush();
        entityManager.clear();

        List<String> result =
                repository.findBaselineReqDocNos(
                        "02", "13", year, "신규", "BE13-CURRENT-" + suffix, PageRequest.of(0, 1));

        assertThat(result).containsExactly(planB);
    }

    @Test
    @DisplayName("현재 계획 자신과 미완료 협의회는 기준 계획 후보에서 제외한다")
    void 기준계획탐색_현재계획과미완료제외() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 12, 0);
        String current = "BE13-PLN-C-" + suffix;
        entityManager.persist(plan(current, "2126", "신규", createdAt));
        entityManager.persist(council("BE13-ASCT-3-" + suffix, current, "13", createdAt));
        entityManager.persist(
                council("BE13-ASCT-4-" + suffix, current, "07", createdAt.plusHours(1)));
        entityManager.flush();
        entityManager.clear();

        assertThat(
                        repository.findBaselineReqDocNos(
                                "02", "13", "2126", "신규", current, PageRequest.of(0, 1)))
                .isEmpty();
    }

    private Bplanm plan(String reqDocNo, String year, String planType, LocalDateTime createdAt) {
        return Bplanm.builder()
                .reqDocNo(reqDocNo)
                .sno(1)
                .lstYn("Y")
                .svnDpmC("900")
                .bseYy(year)
                .itPtlPlnTpC(planType)
                .redtConeInf("{}")
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE13-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("BE13-TEST")
                .build();
    }

    private Basctm council(String asctId, String reqDocNo, String status, LocalDateTime createdAt) {
        return Basctm.builder()
                .itPtlAsctId(asctId)
                .abusMngNo(reqDocNo)
                .itPtlAsctDbrTc("02")
                .itPtlAsctPrgStsTc(status)
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE13-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("BE13-TEST")
                .build();
    }
}
