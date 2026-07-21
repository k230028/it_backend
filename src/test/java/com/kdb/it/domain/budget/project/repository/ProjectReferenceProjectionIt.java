package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectReferenceProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectRepository repository;

    @Test
    void 현재사업명프로젝션이엔티티경로와동일하다() {
        for (Bprojm entity : repository.findAll().stream()
                .filter(project -> "Y".equals(project.getLstYn()) && "N".equals(project.getDelYn()))
                .toList()) {
            ProjectRepository.ProjectNameView view = repository
                    .findNameViewByAbusMngNoAndLstYnAndDelYn(entity.getAbusMngNo(), "Y", "N")
                    .orElseThrow();
            assertThat(view.getAbusMngNo()).isEqualTo(entity.getAbusMngNo());
            assertThat(view.getAbusNm()).isEqualTo(entity.getAbusNm());
        }
        assertThat(ProjectRepository.ProjectNameView.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("getAbusMngNo", "getAbusNm");
    }
}
