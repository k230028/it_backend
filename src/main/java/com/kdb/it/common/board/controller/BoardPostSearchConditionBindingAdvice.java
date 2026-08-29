package com.kdb.it.common.board.controller;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 게시물 검색 조건 바인딩 방어선.
 *
 * <p>서버 전용 {@code ignorePublicationPeriod} 플래그는 서비스가 내부 판단으로만 켜야 하므로 쿼리 파라미터 대입을 명시적으로 차단합니다.
 * 다른 검색 필드는 기존처럼 바인딩합니다.
 */
@ControllerAdvice(assignableTypes = BoardPostController.class)
public class BoardPostSearchConditionBindingAdvice {

    /** 서버 전용 검색 플래그를 모델 바인딩에서 제외합니다. */
    @InitBinder("cond")
    void initSearchConditionBinder(WebDataBinder binder) {
        binder.setDisallowedFields("ignorePublicationPeriod");
    }
}
