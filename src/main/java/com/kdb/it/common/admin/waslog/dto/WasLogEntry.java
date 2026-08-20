package com.kdb.it.common.admin.waslog.dto;

/**
 * WAS 로그 한 줄.
 *
 * @param seq 인스턴스 내 단조 증가 일련번호. 조회 커서로 쓴다
 * @param timestamp 이벤트 발생 시각(epoch millis)
 * @param level ERROR/WARN/INFO/DEBUG/TRACE
 * @param thread 로그를 남긴 스레드명
 * @param logger 로거명(FQCN)
 * @param message 포맷이 적용된 메시지. 4000자에서 절단될 수 있다
 * @param throwable 스택트레이스 문자열. 예외가 없으면 null. 8000자에서 절단될 수 있다
 */
public record WasLogEntry(
        long seq,
        long timestamp,
        String level,
        String thread,
        String logger,
        String message,
        String throwable) {}
