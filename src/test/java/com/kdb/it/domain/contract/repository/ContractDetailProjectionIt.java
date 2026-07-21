package com.kdb.it.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContractDetailProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ContractRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 최종미삭제플래그행과긴상세필드를정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String projectNo = "BE03-CP-" + suffix;
        String docNo = "BE03-CT-" + suffix;
        String request = "요".repeat(300);
        String reason = "사".repeat(1000);
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 9, 0);

        entityManager.persist(project(projectNo, now));
        entityManager.persist(contract(docNo, 100, "N", "N", projectNo, "구버전", "구사유", now));
        entityManager.persist(contract(docNo, 101, "Y", "N", projectNo, request, reason, now));
        entityManager.persist(contract(docNo, 200, "Y", "Y", projectNo, "삭제버전", "삭제사유", now));
        entityManager.flush();
        entityManager.clear();

        ContractDetailRow row = repository.findCurrentDetail(docNo).orElseThrow();

        assertThat(row.docVrsSno()).isEqualTo(101);
        assertThat(row.tgtNm()).isEqualTo("BE03 집행 대상 사업");
        assertThat(row.reqCone()).isEqualTo(request).hasSize(300);
        assertThat(row.cttManrRsn()).isEqualTo(reason).hasSize(1000);
        assertThat(row.cttAmt()).isEqualByComparingTo(new BigDecimal("123456.789"));
        assertThat(ContractDetailRow.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("docMngNo", "docVrsSno", "ioeC", "cncdRfrNo", "tgtNm",
                        "stsTc", "reqCone", "itPtlCttManrC", "cttManrRsn", "cttNm",
                        "cttAmt", "cttOppNm", "cttDt", "reqUsid", "reqDtm");
    }

    private Bprojm project(String projectNo, LocalDateTime now) {
        return Bprojm.builder().abusMngNo(projectNo).sno(1).abusNm("BE03 집행 대상 사업")
                .lstYn("Y").bseYy("2026").delYn("N")
                .fstEnrDtm(now).fstEnrUsid("BE03-TEST").lstChgDtm(now).lstChgUsid("BE03-TEST").build();
    }

    private Bcontm contract(String docNo, int version, String latest, String deleted,
                            String projectNo, String request, String reason, LocalDateTime now) {
        return Bcontm.builder().docMngNo(docNo).docVrsSno(version).lstYn(latest)
                .ioeC("100").cncdRfrNo(projectNo).stsTc("75").reqCone(request)
                .itPtlCttManrC("01").cttManrRsn(reason).cttNm("BE03 계약")
                .cttAmt(new BigDecimal("123456.789")).cttOppNm("BE03 상대").cttDt("20260721")
                .delYn(deleted).fstEnrDtm(now).fstEnrUsid("BE03-TEST")
                .lstChgDtm(now).lstChgUsid("BE03-TEST").build();
    }
}
