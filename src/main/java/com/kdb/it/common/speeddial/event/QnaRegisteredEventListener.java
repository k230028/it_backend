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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 문의 등록 후 활성 시스템관리자에게 인앱 알림과 GWE 메일을 적재·발송합니다. */
@Slf4j
@Component
public class QnaRegisteredEventListener {

    private static final String SYSTEM_ADMIN_AUTH_ID = "ITPAD001";
    private static final String MAIL_PRIMARY = "#1e3a8a";
    private static final String MAIL_HEADER_BG = "#f3f4f6";
    private static final String MAIL_BORDER = "#d1d5db";

    private final RoleRepository roleRepository;
    private final NotificationOutboxService outboxService;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final String frontendUrl;

    public QnaRegisteredEventListener(
            RoleRepository roleRepository,
            NotificationOutboxService outboxService,
            NotificationDispatchService dispatchService,
            ObjectMapper objectMapper,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.roleRepository = roleRepository;
        this.outboxService = outboxService;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.frontendUrl = frontendUrl;
    }

    /** 문의 저장 트랜잭션이 커밋된 뒤에만 관리자별 알림 아웃박스를 생성합니다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQnaRegistered(QnaRegisteredEvent event) {
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
                        "문의 등록 알림 처리가 실패했습니다: postId={}, recipient={}",
                        event.postId(),
                        recipient,
                        ex);
            }
        }
    }

    private NotificationEvent toNotification(QnaRegisteredEvent event, String recipient) {
        String title =
                NotificationMessageFormatter.abbreviate("문의 등록: " + safe(event.title()), 100);
        String subject =
                "[IT정보화포탈] (" + safe(event.categoryName()) + ") " + safe(event.questionTitle());
        return NotificationEvent.builder()
                .recipientEno(recipient)
                .itPtlInfmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl(title)
                .infmMsgCone(
                        NotificationMessageFormatter.abbreviate(
                                "새 문의가 등록되었습니다: " + safe(event.title()), 4000))
                .infmRcdUrl(event.qnaUrl())
                .itPtlSdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                .sdPayload(writeMailPayload(subject, mailBody(event)))
                .build();
    }

    private String mailBody(QnaRegisteredEvent event) {
        String rows =
                row("문의 제목", event.questionTitle())
                        + row("문의 구분", event.categoryName())
                        + row("등록자", event.authorEno())
                        + row("등록 화면", event.screenName())
                        + row("화면 URL", event.screenUrl());
        return "<div style=\"font-family:'Malgun Gothic',sans-serif;color:#111827;max-width:720px;\">"
                + "<div style=\"background:"
                + MAIL_PRIMARY
                + ";color:#fff;font-size:16px;font-weight:700;padding:10px 12px;margin:0 0 14px;\">문의 등록</div>"
                + "<div style=\"font-size:14px;font-weight:700;color:"
                + MAIL_PRIMARY
                + ";margin:0 0 6px;\">문의 개요</div>"
                + "<table border=\"1\" cellpadding=\"10\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;margin:0 0 12px;border-color:"
                + MAIL_BORDER
                + ";font-size:13px;line-height:1.9;\">"
                + rows
                + "</table><div style=\"margin:0 0 18px;text-align:right;\"><a href=\""
                + escape(toFrontendUrl(event.qnaUrl()))
                + "\" style=\"display:inline-block;background:"
                + MAIL_PRIMARY
                + ";color:#fff;text-decoration:none;font-size:13px;font-weight:600;padding:8px 14px;border-radius:4px;\">문의 확인 ↗</a></div></div>";
    }

    private String row(String label, String value) {
        return "<tr><th style=\"background:"
                + MAIL_HEADER_BG
                + ";padding:8px 10px;\">"
                + escape(label)
                + "</th><td style=\"padding:8px 10px;\">"
                + escape(value)
                + "</td></tr>";
    }

    private String toFrontendUrl(String path) {
        String base = safe(frontendUrl).replaceAll("/+$", "");
        return base + safe(path);
    }

    private String writeMailPayload(String subject, String html) {
        try {
            return objectMapper.writeValueAsString(new MailPayload(subject, html));
        } catch (JsonProcessingException ex) {
            log.warn("문의 등록 메일 페이로드 직렬화에 실패해 기본 알림 본문을 사용합니다.", ex);
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
