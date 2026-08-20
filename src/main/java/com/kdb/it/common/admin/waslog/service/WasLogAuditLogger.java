package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * WAS 로그 화면의 관리자 행위 감사 기록기.
 *
 * <p>전용 감사 테이블 대신 애플리케이션 로그로 남긴다 — 파일 appender가 12개월 보관하므로 추적 가능성이 확보되고, DDL과 DBA 절차가 필요 없다. DB 적재가
 * 필요해지면 별도 과제로 분리한다.
 */
@Component
@Slf4j
public class WasLogAuditLogger {

    /** 로그 조회 진입. */
    public void logSnapshotAccess(String instanceId) {
        log.warn("[WAS로그감사] 조회 actor={} instance={}", actor(), instanceId);
    }

    /** 런타임 레벨 변경. */
    public void logLevelChange(WasLogDto.LevelRequest request) {
        log.warn(
                "[WAS로그감사] 레벨변경 actor={} instance={} logger={} level={} ttl={}분",
                actor(),
                request.instanceId(),
                request.logger(),
                request.level(),
                request.ttlMinutes());
    }

    /** 로그 파일 다운로드. */
    public void logDownload(String instanceId, int lineCount) {
        log.warn("[WAS로그감사] 다운로드 actor={} instance={} lines={}", actor(), instanceId, lineCount);
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
