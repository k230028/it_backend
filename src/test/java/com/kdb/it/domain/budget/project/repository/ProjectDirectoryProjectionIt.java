package com.kdb.it.domain.budget.project.repository;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

/** 사업 검색 디렉터리 프로젝션이 최종본만, 관리번호 내림차순으로, 상한까지 반환하는지 Oracle에서 검증한다. */
class ProjectDirectoryProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 디렉터리프로젝션은최종본만관리번호내림차순으로상한까지반환한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String first = "BE108-A-" + suffix;
        String second = "BE108-B-" + suffix;
        String third = "BE108-C-" + suffix;
        String deleted = "BE108-D-" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);

        entityManager.persist(project(first, 1, "이전 개정본", "N", "N", now));
        entityManager.persist(project(first, 2, "첫째 최종본", "Y", "N", now));
        entityManager.persist(project(second, 1, "둘째 최종본", "Y", "N", now));
        entityManager.persist(project(third, 1, "셋째 최종본", "Y", "N", now));
        entityManager.persist(project(deleted, 1, "삭제 사업", "Y", "Y", now));
        entityManager.flush();
        entityManager.clear();

        // 정렬·최종본 필터: 상한을 넉넉히 주고 이 테스트가 만든 행만 골라 순서를 본다.
        List<ProjectRepository.ProjectDirectoryView> views =
                repository
                        .findDirectoryViewsByDelYnAndLstYnOrderByAbusMngNoDescSnoDesc(
                                "N", "Y", Limit.of(100_000))
                        .stream()
                        .filter(view -> view.getAbusMngNo().endsWith(suffix))
                        .toList();

        assertThat(views)
                .extracting(ProjectRepository.ProjectDirectoryView::getAbusMngNo)
                .containsExactly(third, second, first);
        assertThat(views.get(2).getSno()).isEqualTo(2);
        assertThat(views.get(2).getAbusNm()).isEqualTo("첫째 최종본");

        // 상한: DB 쿼리 자체에 적용되어 전체 테이블에서 1건만 돌아온다.
        assertThat(
                        repository.findDirectoryViewsByDelYnAndLstYnOrderByAbusMngNoDescSnoDesc(
                                "N", "Y", Limit.of(1)))
                .hasSize(1);

        assertThat(repository.findDirectoryViewByAbusMngNoAndLstYnAndDelYn(first, "Y", "N"))
                .get()
                .satisfies(
                        view -> {
                            assertThat(view.getSno()).isEqualTo(2);
                            assertThat(view.getAbusNm()).isEqualTo("첫째 최종본");
                            assertThat(view.getSvnDpmC()).isEqualTo("D200");
                            assertThat(view.getTlrUsid()).isEqualTo("10002");
                            assertThat(view.getUsid()).isEqualTo("10003");
                        });
        assertThat(repository.findDirectoryViewByAbusMngNoAndLstYnAndDelYn(deleted, "Y", "N"))
                .isEmpty();
        assertThat(declaredMethodNames(ProjectRepository.ProjectDirectoryView.class))
                .containsExactlyInAnyOrder(
                        "getAbusMngNo",
                        "getSno",
                        "getAbusNm",
                        "getOdnYn",
                        "getSvnDpmC",
                        "getSvnDpmNm",
                        "getTlrUsid",
                        "getTlrNm",
                        "getUsid",
                        "getUsrNm");
    }

    private Bprojm project(
            String projectNo, int sno, String name, String lstYn, String delYn, LocalDateTime now) {
        return Bprojm.builder()
                .abusMngNo(projectNo)
                .sno(sno)
                .abusNm(name)
                .abusTc("0")
                .lstYn(lstYn)
                .bseYy("2026")
                .delYn(delYn)
                .svnDpmC("D200")
                .svnDpmNm("리스크관리부")
                .tlrUsid("10002")
                .tlrNm("김팀장")
                .usid("10003")
                .usrNm("이담당")
                .fstEnrDtm(now)
                .fstEnrUsid("BE108-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE108-TEST")
                .build();
    }
}
