package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectItemBudgetProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectItemRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 고유사업의활성품목만정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String projectNo = "BE03-PRJ-" + suffix;
        String activeGcl = "BE03I" + suffix;
        String deletedGcl = "BE03D" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);

        entityManager.persist(item(activeGcl, projectNo, "101", "123.000", "23.000", "N", now));
        entityManager.persist(item(deletedGcl, projectNo, "102", "999.000", "99.000", "Y", now));
        entityManager.flush();
        entityManager.clear();

        List<ProjectItemRepository.ProjectItemBudgetView> views =
                repository.findBudgetViewsByAbusMngNoInAndDelYn(List.of(projectNo), "N");

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.getGclMngNo()).isEqualTo(activeGcl);
            assertThat(view.getAbusMngNo()).isEqualTo(projectNo);
            assertThat(view.getIoeC()).isEqualTo("101");
            assertThat(view.getAmt()).isEqualByComparingTo("123.000");
            assertThat(view.getMplAmt()).isEqualByComparingTo("23.000");
        });
        assertThat(views).extracting(ProjectItemRepository.ProjectItemBudgetView::getGclMngNo)
                .doesNotContain(deletedGcl);
        assertThat(ProjectItemRepository.ProjectItemBudgetView.class.getDeclaredMethods()).hasSize(5);
    }

    private Bitemm item(
            String gclMngNo, String projectNo, String ioeC,
            String amt, String mplAmt, String delYn, LocalDateTime now) {
        return Bitemm.builder()
                .gclMngNo(gclMngNo)
                .sno(1)
                .abusMngNo(projectNo)
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .lstYn("Y")
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
