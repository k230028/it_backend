package com.kdb.it.domain.budget.project.repository;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProjectItemBudgetProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectItemRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 고유사업의활성품목만정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String projectNo = "BE03-PRJ-" + suffix;
        String activeGcl = "BE03I" + suffix;
        String historicalGcl = "BE03H" + suffix;
        String deletedGcl = "BE03D" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);

        entityManager.persist(item(activeGcl, projectNo, "101", "123.000", "23.000", "N", now));
        entityManager.persist(
                item(historicalGcl, projectNo, "103", "777.000", "77.000", "N", "N", now));
        entityManager.persist(item(deletedGcl, projectNo, "102", "999.000", "99.000", "Y", now));
        entityManager.flush();
        entityManager.clear();

        List<ProjectItemRepository.ProjectItemBudgetView> views =
                repository.findBudgetViewsByAbusMngNoInAndDelYn(List.of(projectNo), "N");

        assertThat(views)
                .singleElement()
                .satisfies(
                        view -> {
                            assertThat(view.getGclMngNo()).isEqualTo(activeGcl);
                            assertThat(view.getAbusMngNo()).isEqualTo(projectNo);
                            assertThat(view.getIoeC()).isEqualTo("101");
                            assertThat(view.getAmt()).isEqualByComparingTo("123.000");
                            assertThat(view.getMplAmt()).isEqualByComparingTo("23.000");
                            assertThat(view.getCurC()).isEqualTo("KRW");
                            assertThat(view.getXcr()).isNull();
                        });
        assertThat(views)
                .extracting(view -> view.getGclMngNo())
                .doesNotContain(deletedGcl, historicalGcl);
        assertThat(declaredMethodNames(ProjectItemRepository.ProjectItemBudgetView.class))
                .hasSize(7);
    }

    private Bitemm item(
            String gclMngNo,
            String projectNo,
            String ioeC,
            String amt,
            String mplAmt,
            String delYn,
            LocalDateTime now) {
        return item(gclMngNo, projectNo, ioeC, amt, mplAmt, delYn, "Y", now);
    }

    private Bitemm item(
            String gclMngNo,
            String projectNo,
            String ioeC,
            String amt,
            String mplAmt,
            String delYn,
            String lstYn,
            LocalDateTime now) {
        return Bitemm.builder()
                .gclMngNo(gclMngNo)
                .sno(1)
                .abusMngNo(projectNo)
                .fntTbCrySno(1)
                .dfrCleC("0")
                .ioeC(ioeC)
                .lstYn(lstYn)
                .curC("KRW")
                .amt(new BigDecimal(amt))
                .mplAmt(new BigDecimal(mplAmt))
                .delYn(delYn)
                .fstEnrDtm(now)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
