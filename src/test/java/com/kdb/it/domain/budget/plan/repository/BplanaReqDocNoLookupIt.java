package com.kdb.it.domain.budget.plan.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** BPLANA 요청문서번호 역방향 조회의 결과 계약을 실제 Oracle에서 검증한다. */
class BplanaReqDocNoLookupIt extends AbstractOracleRepositoryTest {

    @Autowired BplanaRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("요청문서번호와 삭제여부로 연결된 활성 사업만 조회한다")
    void 요청문서번호_역방향조회_활성행만반환() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String reqDocNo = "BE14-PLN-" + suffix;
        entityManager.persist(relation("BE14-PRJ-A-" + suffix, reqDocNo, "N"));
        entityManager.persist(relation("BE14-PRJ-B-" + suffix, reqDocNo, "N"));
        entityManager.persist(relation("BE14-PRJ-D-" + suffix, reqDocNo, "Y"));
        entityManager.flush();
        entityManager.clear();

        List<Bplana> rows = repository.findAllByReqDocNoAndDelYn(reqDocNo, "N");

        assertThat(rows)
                .extracting(Bplana::getPrjMngNo)
                .containsExactlyInAnyOrder("BE14-PRJ-A-" + suffix, "BE14-PRJ-B-" + suffix);
    }

    private Bplana relation(String prjMngNo, String reqDocNo, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 10, 0);
        return Bplana.builder()
                .prjMngNo(prjMngNo)
                .reqDocNo(reqDocNo)
                .delYn(delYn)
                .fstEnrDtm(now)
                .fstEnrUsid("BE14-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE14-TEST")
                .build();
    }
}
