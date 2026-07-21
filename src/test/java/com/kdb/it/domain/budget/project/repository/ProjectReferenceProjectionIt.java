package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectReferenceProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 고유사업에서현재활성사업명만정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String projectNo = "BE03-PNM-" + suffix;
        String deletedNo = "BE03-PND-" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 11, 0);

        entityManager.persist(project(projectNo, 1, "구버전 사업", "N", "N", now));
        entityManager.persist(project(projectNo, 2, "BE03 최신 사업", "Y", "N", now));
        entityManager.persist(project(deletedNo, 1, "삭제 사업", "Y", "Y", now));
        entityManager.flush();
        entityManager.clear();

        ProjectRepository.ProjectNameView view = repository
                .findNameViewByAbusMngNoAndLstYnAndDelYn(projectNo, "Y", "N")
                .orElseThrow();

        assertThat(view.getAbusMngNo()).isEqualTo(projectNo);
        assertThat(view.getAbusNm()).isEqualTo("BE03 최신 사업");
        assertThat(repository.findNameViewByAbusMngNoAndLstYnAndDelYn(deletedNo, "Y", "N")).isEmpty();
        assertThat(ProjectRepository.ProjectNameView.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder("getAbusMngNo", "getAbusNm");
    }

    private Bprojm project(
            String projectNo, int sno, String name, String lstYn, String delYn, LocalDateTime now) {
        return Bprojm.builder()
                .abusMngNo(projectNo)
                .sno(sno)
                .abusNm(name)
                .lstYn(lstYn)
                .bseYy("2026")
                .delYn(delYn)
                .fstEnrDtm(now)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
