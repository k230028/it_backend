package com.kdb.it.domain.deliberation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DeliberationDetailProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired DeliberationRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 최종미삭제플래그행과긴의견필드를정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String projectNo = "BE03-DP-" + suffix;
        String docNo = "BE03-DL-" + suffix;
        String opinion = "의".repeat(1000);
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 9, 10);

        entityManager.persist(project(projectNo, now));
        entityManager.persist(deliberation(docNo, 100, "N", "N", projectNo, "구의견", now));
        entityManager.persist(deliberation(docNo, 101, "Y", "N", projectNo, opinion, now));
        entityManager.persist(deliberation(docNo, 200, "Y", "Y", projectNo, "삭제의견", now));
        entityManager.flush();
        entityManager.clear();

        DeliberationDetailRow row = repository.findCurrentDetail(docNo).orElseThrow();

        assertThat(row.docVrsSno()).isEqualTo(101);
        assertThat(row.tgtNm()).isEqualTo("BE03 심의 대상 사업");
        assertThat(row.opnnCone()).isEqualTo(opinion).hasSize(1000);
        assertThat(row.taskDbrTc()).isEqualTo("01");
        assertThat(row.apvTrdnRsnCone()).isEqualTo("승인 사유");
        assertThat(DeliberationDetailRow.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "taskDbrTc",
                        "taskDbrRltTc",
                        "taskDbrDt",
                        "taskDbrTod",
                        "taskDbrOmtYn",
                        "taskDbrOmtRsn",
                        "opnnCone",
                        "apvTrdnRsnCone",
                        "reqUsid",
                        "reqDtm");
    }

    private Bprojm project(String projectNo, LocalDateTime now) {
        return Bprojm.builder()
                .abusMngNo(projectNo)
                .sno(1)
                .abusNm("BE03 심의 대상 사업")
                .abusTc("0")
                .lstYn("Y")
                .bseYy("2026")
                .delYn("N")
                .fstEnrDtm(now)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE03-TEST")
                .build();
    }

    private Bdelim deliberation(
            String docNo,
            int version,
            String latest,
            String deleted,
            String projectNo,
            String opinion,
            LocalDateTime now) {
        return Bdelim.builder()
                .docMngNo(docNo)
                .docVrsSno(version)
                .lstYn(latest)
                .ioeC("100")
                .cncdRfrNo(projectNo)
                .stsTc("65")
                .reqCone("BE03 심의 요청")
                .taskDbrTc("01")
                .taskDbrRltTc("02")
                .taskDbrDt("20260721")
                .taskDbrTod("03")
                .taskDbrOmtYn("N")
                .taskDbrOmtRsn("생략하지 않음")
                .opnnCone(opinion)
                .apvTrdnRsnCone("승인 사유")
                .delYn(deleted)
                .fstEnrDtm(now)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
