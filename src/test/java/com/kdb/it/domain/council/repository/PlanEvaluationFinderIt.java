package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 사업별 평가의견 파생 조회의 활성행 필터 계약을 실제 Oracle에서 검증한다. */
class PlanEvaluationFinderIt extends AbstractOracleRepositoryTest {

    @Autowired PlanEvaluationRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("협의회별·위원별 조회가 활성 평가의견만 반환한다")
    void 평가조회_활성행필터() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String asctId = "BE02-ASCT-" + suffix;
        String firstEno = "BE02-E1-" + suffix;
        String secondEno = "BE02-E2-" + suffix;
        entityManager.persist(evaluation(asctId, firstEno, "BE02-PRJ-1", "Y", "N"));
        entityManager.persist(evaluation(asctId, firstEno, "BE02-PRJ-2", "N", "N"));
        entityManager.persist(evaluation(asctId, secondEno, "BE02-PRJ-1", "Y", "N"));
        entityManager.persist(evaluation(asctId, secondEno, "BE02-PRJ-2", "Y", "Y"));
        entityManager.flush();
        entityManager.clear();

        List<Bplevm> all = repository.findByItPtlAsctIdAndDelYn(asctId, "N");
        List<Bplevm> mine =
                repository.findByItPtlAsctIdAndEnoAndDelYn(asctId, secondEno, "N");

        assertThat(all).hasSize(3);
        assertThat(mine).singleElement().extracting(Bplevm::getPprtYn).isEqualTo("Y");
    }

    private Bplevm evaluation(
            String asctId, String eno, String projectNo, String pprtYn, String delYn) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 13, 0);
        return Bplevm.builder()
                .itPtlAsctId(asctId)
                .eno(eno)
                .abusMngNo(projectNo)
                .pprtYn(pprtYn)
                .evalOpnn("평가 사유")
                .delYn(delYn)
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE02-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("BE02-TEST")
                .build();
    }
}
