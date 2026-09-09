package com.kdb.it.domain.budget.status.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({BudgetStatusQueryRepositoryImpl.class, ProjectDescriptionQuery.class})
@DisplayName("예산 현황 Oracle 집계 쿼리")
class BudgetStatusQueryRepositoryOracleIt extends AbstractOracleRepositoryTest {

    @Autowired BudgetStatusQueryRepository repository;
    @Autowired ProjectDescriptionQuery projectDescriptionQuery;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("정보화사업 집계는 CLOB 사업내용이 있어도 실행된다")
    void projectStatus_allowsClobProjectDescription() {
        String description = longDescription("정보화사업");
        Bprojm project = persistProject(false, 1, "Y", description);

        assertThatCode(() -> repository.findProjectStatus("2099")).doesNotThrowAnyException();
        assertThat(repository.findProjectStatus("2099"))
                .filteredOn(row -> row.abusMngNo().equals(project.getAbusMngNo()))
                .singleElement()
                .extracting(row -> row.abusPulConeInf())
                .isEqualTo(description);
    }

    @Test
    @DisplayName("경상사업 집계는 CLOB 사업내용이 있어도 실행된다")
    void ordinaryStatus_allowsClobProjectDescription() {
        String description = longDescription("경상사업");
        Bprojm project = persistProject(true, 1, "Y", description);

        assertThatCode(() -> repository.findOrdinaryStatus("2099")).doesNotThrowAnyException();
        assertThat(repository.findOrdinaryStatus("2099"))
                .filteredOn(row -> row.abusMngNo().equals(project.getAbusMngNo()))
                .singleElement()
                .extracting(row -> row.abusPulConeInf())
                .isEqualTo(description);
    }

    @Test
    @DisplayName("집계가 읽은 과거 순번의 본문은 최신본 전환 뒤에도 정확히 조회한다")
    void descriptionQuery_loadsRequestedHistoricalRevision() {
        Bprojm previous = persistProject(false, 1, "N", "이전 순번 본문");
        persistProject(previous.getAbusMngNo(), false, 2, "Y", "최신 순번 본문");

        var descriptions =
                projectDescriptionQuery.findByRevisionKeys(
                        Set.of(
                                new ProjectDescriptionQuery.ProjectRevisionKey(
                                        previous.getAbusMngNo(), previous.getSno())));

        assertThat(descriptions)
                .containsEntry(
                        new ProjectDescriptionQuery.ProjectRevisionKey(
                                previous.getAbusMngNo(), previous.getSno()),
                        "이전 순번 본문");
    }

    private Bprojm persistProject(boolean ordinary, int sno, String latestYn, String description) {
        return persistProject("BST-" + suffix(), ordinary, sno, latestYn, description);
    }

    private Bprojm persistProject(
            String abusMngNo, boolean ordinary, int sno, String latestYn, String description) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 12, 0);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(abusMngNo)
                        .sno(sno)
                        .abusNm("CLOB 집계 테스트")
                        .abusTc("10")
                        .bseYy("2099")
                        .odnYn(ordinary ? "Y" : "N")
                        .lstYn(latestYn)
                        .abusPulConeInf(description)
                        .delYn("N")
                        .fstEnrDtm(now)
                        .fstEnrUsid("BST-TEST")
                        .lstChgDtm(now)
                        .lstChgUsid("BST-TEST")
                        .build();
        entityManager.persist(project);
        entityManager.flush();
        entityManager.clear();
        return project;
    }

    private String longDescription(String prefix) {
        return prefix + "-" + "사업내용".repeat(1200);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
