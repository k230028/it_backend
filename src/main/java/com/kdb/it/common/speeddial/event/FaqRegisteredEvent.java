package com.kdb.it.common.speeddial.event;

/** FAQ 게시글 저장 완료 후 시스템관리자 메일 알림으로 변환되는 이벤트입니다. */
public record FaqRegisteredEvent(
        String postId,
        String title,
        String contentHtml,
        String authorEno,
        String authorName,
        String faqUrl) {}
