package com.kdb.it.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.payment.entity.Bpaymm;
import com.kdb.it.domain.payment.entity.Bpaymt;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentDetailProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired PaymentRepository repository;
    @Autowired PaymentLineRepository lineRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 최종미삭제마스터와활성지급명세의긴필드를정확히매핑한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String projectNo = "BE03-PP-" + suffix;
        String docNo = "BE03-PY-" + suffix;
        String request = "요".repeat(300);
        String opinion = "의".repeat(1000);
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 9, 20);

        entityManager.persist(project(projectNo, now));
        entityManager.persist(payment(docNo, 100, "N", "N", projectNo, "구버전", now));
        entityManager.persist(payment(docNo, 101, "Y", "N", projectNo, request, now));
        entityManager.persist(payment(docNo, 200, "Y", "Y", projectNo, "삭제버전", now));
        entityManager.persist(line(docNo, 101, 1, "N", opinion, now));
        entityManager.persist(line(docNo, 101, 2, "Y", "삭제 명세", now));
        entityManager.flush();
        entityManager.clear();

        PaymentDetailRow row = repository.findCurrentDetail(docNo).orElseThrow();
        List<PaymentLineView> lines = lineRepository
                .findLineViewsByDocMngNoAndDocVrsSnoAndDelYn(docNo, row.docVrsSno(), "N");

        assertThat(row.docVrsSno()).isEqualTo(101);
        assertThat(row.tgtNm()).isEqualTo("BE03 지급 대상 사업");
        assertThat(row.reqCone()).isEqualTo(request).hasSize(300);
        assertThat(row.cttAmt()).isEqualByComparingTo(new BigDecimal("987654.321"));
        assertThat(PaymentDetailRow.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("docMngNo", "docVrsSno", "ioeC", "cncdRfrNo", "tgtNm",
                        "stsTc", "reqCone", "cttNm", "cttAmt", "reqUsid", "reqDtm");
        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.dfrTod()).isEqualTo(1);
            assertThat(line.opnnCone()).isEqualTo(opinion).hasSize(1000);
        });
        assertThat(PaymentLineView.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("dfrTod", "dfrAmt", "dfrDt", "dfrMplDt", "opnnCone");
    }

    private Bprojm project(String projectNo, LocalDateTime now) {
        return Bprojm.builder().abusMngNo(projectNo).sno(1).abusNm("BE03 지급 대상 사업")
                .lstYn("Y").bseYy("2026").delYn("N")
                .fstEnrDtm(now).fstEnrUsid("BE03-TEST").lstChgDtm(now).lstChgUsid("BE03-TEST").build();
    }

    private Bpaymm payment(String docNo, int version, String latest, String deleted,
                           String projectNo, String request, LocalDateTime now) {
        return Bpaymm.builder().docMngNo(docNo).docVrsSno(version).lstYn(latest)
                .ioeC("100").cncdRfrNo(projectNo).stsTc("85").reqCone(request)
                .cttNm("BE03 지급 계약").cttAmt(new BigDecimal("987654.321")).delYn(deleted)
                .fstEnrDtm(now).fstEnrUsid("BE03-TEST").lstChgDtm(now).lstChgUsid("BE03-TEST").build();
    }

    private Bpaymt line(String docNo, int version, int turn, String deleted,
                        String opinion, LocalDateTime now) {
        return Bpaymt.builder().docMngNo(docNo).docVrsSno(version).dfrTod(turn)
                .dfrAmt(new BigDecimal("123.456")).dfrDt("20260721").dfrMplDt("20260731")
                .opnnCone(opinion).delYn(deleted).fstEnrDtm(now).fstEnrUsid("BE03-TEST")
                .lstChgDtm(now).lstChgUsid("BE03-TEST").build();
    }
}
