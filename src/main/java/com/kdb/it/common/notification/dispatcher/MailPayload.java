package com.kdb.it.common.notification.dispatcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 외부 메일 발송 페이로드 — {@code Cinfmm.SD_DOC_CONE}에 JSON으로 저장된다.
 *
 * <p>업무 도메인이 제목과 본문을 완성해 실어 보내고 발송 계층은 그대로 전달만 한다. 업무 문구가 발송 계층에 하드코딩되지 않게 하려는 계약이므로, 계약 자체는 소비자인
 * 알림 패키지에 둔다.
 *
 * @param subject 메일 제목
 * @param html 메일 본문 HTML (인라인 스타일)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MailPayload(String subject, String html) {}
