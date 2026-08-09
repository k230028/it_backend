package com.kdb.it.common.notification.repository;

import java.time.LocalDateTime;

/** 알림함 응답 직렬화에 필요한 최소 필드 프로젝션. */
public record NotificationInboxRow(
        String infmMsgNo,
        String itPtlInfmSvcTc,
        String ttl,
        String infmMsgCone,
        String infmRcdUrl,
        String inqYn,
        LocalDateTime inqDtm,
        LocalDateTime fstEnrDtm) {}
