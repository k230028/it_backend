package com.kdb.it.common.speeddial.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.notification.dispatcher.MailPayload;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcherRouter;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import com.kdb.it.common.notification.service.NotificationOutboxService;
import com.kdb.it.common.notification.util.NotificationMessageFormatter;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** FAQ 등록 후 활성 시스템관리자에게 GWE 메일 알림을 적재·발송합니다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqRegisteredEventListener {

    private static final String SYSTEM_ADMIN_AUTH_ID = "ITPAD001";

    private final RoleRepository roleRepository;
    private final NotificationOutboxService outboxService;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    /** FAQ 저장 트랜잭션이 커밋된 뒤에만 메일 아웃박스를 생성합니다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFaqRegistered(FaqRegisteredEvent event) {
        LinkedHashSet<String> recipients =
                new LinkedHashSet<>(roleRepository.findActiveUserEnosByAthId(SYSTEM_ADMIN_AUTH_ID));
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) continue;
            try {
                String outboxId = outboxService.enqueue(toNotification(event, recipient));
                if (outboxId != null) {
                    dispatchService.dispatch(outboxId);
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "FAQ 등록 메일 알림 처리가 실패했습니다: postId={}, recipient={}",
                        event.postId(),
                        recipient,
                        ex);
            }
        }
    }

    private NotificationEvent toNotification(FaqRegisteredEvent event, String recipient) {
        String title =
                NotificationMessageFormatter.abbreviate("FAQ 등록: " + safe(event.title()), 100);
        String body =
                "<p>새 FAQ가 등록되었습니다.</p><p>제목: "
                        + escape(event.title())
                        + "</p><p>등록자: "
                        + escape(event.authorName())
                        + " ("
                        + escape(event.authorEno())
                        + ")</p><p><a href=\""
                        + escape(event.faqUrl())
                        + "\">FAQ 확인</a></p>";
        return NotificationEvent.builder()
                .recipientEno(recipient)
                .itPtlInfmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl(title)
                .infmMsgCone(
                        NotificationMessageFormatter.abbreviate(
                                "새 FAQ가 등록되었습니다: " + safe(event.title()), 4000))
                .infmRcdUrl(event.faqUrl())
                .itPtlSdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                .sdPayload(writeMailPayload(title, body))
                .build();
    }

    private String writeMailPayload(String subject, String html) {
        try {
            return objectMapper.writeValueAsString(new MailPayload(subject, html));
        } catch (JsonProcessingException ex) {
            log.warn("FAQ 등록 메일 페이로드 직렬화에 실패해 기본 알림 본문을 사용합니다.", ex);
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
