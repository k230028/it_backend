package com.kdb.it.common.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class NotificationInboxProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired CinfmmRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 알림함프로젝션은기존조회와필드순서페이지합계가동일하다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String recipient = "BE03-N-" + suffix;
        LocalDateTime base = LocalDateTime.of(2097, 12, 31, 23, 42);
        entityManager.persist(
                notification(
                        "BE03-NO-" + suffix + "-1",
                        recipient,
                        "01",
                        "이전 제목",
                        "이전 내용",
                        "/older",
                        "N",
                        null,
                        base));
        entityManager.persist(
                notification(
                        "BE03-NO-" + suffix + "-2",
                        recipient,
                        "02",
                        "최신 제목",
                        "최신 내용",
                        "/newer",
                        "Y",
                        base.plusMinutes(2),
                        base.plusMinutes(1)));
        entityManager.flush();
        entityManager.clear();

        Page<Cinfmm> entities = repository.findInbox(recipient, false, PageRequest.of(0, 2));
        Page<NotificationInboxRow> rows =
                repository.findInboxRows(recipient, false, PageRequest.of(0, 2));

        assertThat(rows.getTotalElements()).isEqualTo(entities.getTotalElements()).isEqualTo(2);
        assertThat(rows.getContent())
                .extracting(
                        NotificationInboxRow::infmMsgNo,
                        NotificationInboxRow::itPtlInfmSvcTc,
                        NotificationInboxRow::ttl,
                        NotificationInboxRow::infmMsgCone,
                        NotificationInboxRow::infmRcdUrl,
                        NotificationInboxRow::inqYn,
                        NotificationInboxRow::inqDtm,
                        NotificationInboxRow::fstEnrDtm)
                .containsExactly(
                        tuple(
                                "BE03-NO-" + suffix + "-2",
                                "02",
                                "최신 제목",
                                "최신 내용",
                                "/newer",
                                "Y",
                                base.plusMinutes(2),
                                base.plusMinutes(1)),
                        tuple(
                                "BE03-NO-" + suffix + "-1",
                                "01",
                                "이전 제목",
                                "이전 내용",
                                "/older",
                                "N",
                                null,
                                base));
    }

    private Cinfmm notification(
            String id,
            String recipient,
            String serviceType,
            String title,
            String content,
            String url,
            String readYn,
            LocalDateTime readAt,
            LocalDateTime createdAt) {
        return Cinfmm.builder()
                .infmMsgNo(id)
                .itPtlInfmSvcTc(serviceType)
                .ttl(title)
                .infmMsgCone(content)
                .infmRcdUrl(url)
                .rmsEno(recipient)
                .inqYn(readYn)
                .inqDtm(readAt)
                .itPtlSdTc("01")
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
