package com.kdb.it.common.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * CinfmmRepositoryImpl 벌크 읽음 처리 통합 테스트.
 *
 * <p>로컬 Oracle 스키마의 기존 TPRMPP_CINFMM 컬럼만 사용하며,
 * @DataJpaTest 트랜잭션 롤백으로 테스트 데이터를 남기지 않는다.</p>
 */
@DisplayName("CinfmmRepositoryImpl 벌크 읽음 처리")
class CinfmmRepositoryImplTest extends AbstractOracleRepositoryTest {

    private static final String TARGET_ENO = "T4-CINFMM-ENO";
    private static final String OTHER_ENO = "T4-CINFMM-OTHER";

    @Autowired
    private CinfmmRepository cinfmmRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("수신자의 미읽음 알림만 읽음 처리하고 조회/감사 컬럼을 갱신한다")
    void markAllReadByRmsEno_updatesUnreadRowsAndAuditColumns() {
        Cinfmm unread1 = insertNotification("INF-T4-00000001", TARGET_ENO, "N", "N");
        Cinfmm unread2 = insertNotification("INF-T4-00000002", TARGET_ENO, "N", "N");
        Cinfmm alreadyRead = insertNotification("INF-T4-00000003", TARGET_ENO, "Y", "N");
        Cinfmm otherRecipient = insertNotification("INF-T4-00000004", OTHER_ENO, "N", "N");
        Cinfmm deleted = insertNotification("INF-T4-00000005", TARGET_ENO, "N", "Y");
        em.flush();
        em.clear();

        long updated = cinfmmRepository.markAllReadByRmsEno(TARGET_ENO);

        assertThat(updated).isEqualTo(2);

        em.clear();
        assertReadAndAudited(unread1.getInfmMsgNo());
        assertReadAndAudited(unread2.getInfmMsgNo());

        Cinfmm reloadedRead = em.find(Cinfmm.class, alreadyRead.getInfmMsgNo());
        assertThat(reloadedRead.getInqYn()).isEqualTo("Y");
        assertThat(reloadedRead.getLstChgUsid()).isEqualTo("FIXTURE");

        Cinfmm reloadedOther = em.find(Cinfmm.class, otherRecipient.getInfmMsgNo());
        assertThat(reloadedOther.getInqYn()).isEqualTo("N");
        assertThat(reloadedOther.getLstChgUsid()).isEqualTo("FIXTURE");

        Cinfmm reloadedDeleted = em.find(Cinfmm.class, deleted.getInfmMsgNo());
        assertThat(reloadedDeleted.getInqYn()).isEqualTo("N");
        assertThat(reloadedDeleted.getLstChgUsid()).isEqualTo("FIXTURE");
    }

    private Cinfmm insertNotification(String infmMsgNo, String rmsEno, String inqYn, String delYn) {
        LocalDateTime now = LocalDateTime.now().minusDays(1);
        return em.persist(Cinfmm.builder()
                .infmMsgNo(infmMsgNo)
                .infmSvcTc("01")
                .ttl("테스트 알림")
                .infmMsgCone("테스트 본문")
                .rmsEno(rmsEno)
                .inqYn(inqYn)
                .inqDtm("Y".equals(inqYn) ? now : null)
                .delYn(delYn)
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(now)
                .lstChgUsid("FIXTURE")
                .lstChgDtm(now)
                .build());
    }

    private void assertReadAndAudited(String infmMsgNo) {
        Cinfmm reloaded = em.find(Cinfmm.class, infmMsgNo);
        assertThat(reloaded.getInqYn()).isEqualTo("Y");
        assertThat(reloaded.getInqDtm()).isNotNull();
        assertThat(reloaded.getLstChgUsid()).isEqualTo(TARGET_ENO);
        assertThat(reloaded.getLstChgDtm()).isNotNull();
    }
}
