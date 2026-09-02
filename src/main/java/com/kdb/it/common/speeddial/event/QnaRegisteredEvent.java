package com.kdb.it.common.speeddial.event;

/** 스피드다이얼 문의 저장 완료 후 관리자 알림으로 변환되는 이벤트입니다. */
public record QnaRegisteredEvent(
        String postId,
        String title,
        String categoryName,
        String questionTitle,
        String authorEno,
        String screenName,
        String screenUrl,
        String qnaUrl) {}
