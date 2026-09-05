package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 실제 Oracle에서 스냅샷 후보 쿼리와 잠금 쿼리를 검증하고 픽스처를 롤백한다. */
class ItBudgetSourceRepositoryIntegrationTest extends AbstractOracleRepositoryTest {
    @Autowired ProjectRepository projects;
    @Autowired ProjectItemRepository items;
    @Autowired CostRepository costs;
    @Autowired BtermmRepository terminals;
    @Autowired TestEntityManager em;

    @Test
    void projectReadAndLockIncludeDeletedVersionsAndDeletedChildren() {
        var now = LocalDateTime.now();
        String id = "ITB-SNAPSHOT-TEST";
        em.persist(
                Bprojm.builder()
                        .abusMngNo(id)
                        .sno(2)
                        .abusTc("0")
                        .delYn("Y")
                        .fstEnrUsid("TEST")
                        .lstChgUsid("TEST")
                        .fstEnrDtm(now)
                        .lstChgDtm(now)
                        .build());
        em.persist(
                Bprojm.builder()
                        .abusMngNo(id)
                        .sno(1)
                        .abusTc("0")
                        .delYn("N")
                        .fstEnrUsid("TEST")
                        .lstChgUsid("TEST")
                        .fstEnrDtm(now)
                        .lstChgDtm(now)
                        .build());
        em.persist(
                Bitemm.builder()
                        .gclMngNo("ITB-SNAP-I")
                        .sno(1)
                        .abusMngNo(id)
                        .fntTbCrySno(2)
                        .dfrCleC("0")
                        .mplAmt(java.math.BigDecimal.ZERO)
                        .delYn("Y")
                        .fstEnrUsid("TEST")
                        .lstChgUsid("TEST")
                        .fstEnrDtm(now)
                        .lstChgDtm(now)
                        .build());
        em.flush();
        em.clear();
        assertThat(projects.findVersions(List.of(id), List.of(2, 1)))
                .extracting(Bprojm::getSno)
                .containsExactly(1, 2);
        assertThat(projects.findVersionsForUpdate(List.of(id), List.of(2, 1)))
                .extracting(Bprojm::getSno)
                .containsExactly(1, 2);
        assertThat(items.findSourceVersions(List.of(id), List.of(2)))
                .extracting(Bitemm::getDelYn)
                .containsExactly("Y");
    }

    @Test
    void costQueriesCompileAndExecuteWithScalarInParameters() {
        assertThat(costs.findVersions(List.of("ZZ-NONEXIST"), List.of(1))).isEmpty();
        assertThat(costs.findVersionsForUpdate(List.of("ZZ-NONEXIST"), List.of(1))).isEmpty();
        assertThat(terminals.findSourceVersions(List.of("ZZ-NONEXIST"), List.of(1))).isEmpty();
    }
}
