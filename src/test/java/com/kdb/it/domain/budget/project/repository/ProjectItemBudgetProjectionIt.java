package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectItemBudgetProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectItemRepository repository;

    private record Values(String gclMngNo, String abusMngNo, String ioeC, BigDecimal amt, BigDecimal mplAmt) {
    }

    @Test
    void 사업집합예산프로젝션이엔티티경로와동일하다() {
        List<Bitemm> entities = repository.findAll().stream()
                .filter(item -> "N".equals(item.getDelYn()))
                .toList();
        List<String> keys = entities.stream().map(Bitemm::getAbusMngNo).distinct().toList();
        List<ProjectItemRepository.ProjectItemBudgetView> views = keys.isEmpty()
                ? List.of()
                : repository.findBudgetViewsByAbusMngNoInAndDelYn(keys, "N");

        assertThat(views.stream().map(view -> new Values(
                view.getGclMngNo(), view.getAbusMngNo(), view.getIoeC(), view.getAmt(), view.getMplAmt())).toList())
                .containsExactlyInAnyOrderElementsOf(entities.stream().map(item -> new Values(
                        item.getGclMngNo(), item.getAbusMngNo(), item.getIoeC(), item.getAmt(), item.getMplAmt())).toList());
        assertThat(ProjectItemRepository.ProjectItemBudgetView.class.getDeclaredMethods()).hasSize(5);
    }
}
