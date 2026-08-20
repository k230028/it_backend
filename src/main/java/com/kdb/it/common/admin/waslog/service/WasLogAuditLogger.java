package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** 같은 행위자·인스턴스 조합의 조회를 다시 기록하기까지의 최소 간격(분). */
    private static final long THROTTLE_MINUTES = 10;

    private final Map<String, LocalDateTime> lastAccessLog = new ConcurrentHashMap<>();
    private final Clock clock;

    public WasLogAuditLogger(Clock clock) {
        this.clock = clock;
    }

    /**
     * 로그 조회 진입.
     *
     * <p>조회는 3초마다 폴링되므로 매 호출을 남기면 감사 기록이 정작 보려던 로그를 뒤덮는다. 그렇다고 클라이언트가 보낸 커서({@code afterSeq==0})로
     * first-call을 판정하면, 항상 0이 아닌 값을 보내는 호출자는 흔적을 하나도 남기지 않고 로그를 다 읽어갈 수 있다. 그래서 **서버가** 행위자+인스턴스별로
     * {@value #THROTTLE_MINUTES}분에 한 번만 기록한다 — 클라이언트가 회피할 수 없다.
     */
    public void logSnapshotAccess(String instanceId) {
        String actor = actor();
        if (!shouldLogAccess(actor, instanceId)) return;
        log.warn("[WAS로그감사] 조회 actor={} instance={}", sanitize(actor), sanitize(instanceId));
    }

    /** 런타임 레벨 변경. 드물고 상태를 바꾸므로 스로틀 없이 매번 남긴다. */
    public void logLevelChange(WasLogDto.LevelRequest request) {
        log.warn(
                "[WAS로그감사] 레벨변경 actor={} instance={} logger={} level={} ttl={}분",
                sanitize(actor()),
                sanitize(request.instanceId()),
                sanitize(request.logger()),
                sanitize(request.level()),
                request.ttlMinutes());
    }

    /** 로그 파일 다운로드. 스로틀 없이 매번 남긴다. */
    public void logDownload(String instanceId, int lineCount) {
        log.warn(
                "[WAS로그감사] 다운로드 actor={} instance={} lines={}",
                sanitize(actor()),
                sanitize(instanceId),
                lineCount);
    }

    /** 행위자+인스턴스별 스로틀 판정. 창을 벗어났으면 기록 시각을 갱신하고 true. */
    private boolean shouldLogAccess(String actor, String instanceId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusMinutes(THROTTLE_MINUTES);
        String key = actor + "|" + instanceId;
        LocalDateTime previous = lastAccessLog.get(key);
        if (previous != null && previous.isAfter(cutoff)) return false;
        lastAccessLog.put(key, now);
        return true;
    }

    /**
     * 감사 값 정화.
     *
     * <p>감사 기록은 이 기능의 유일한 보상 통제다. 값이 검증 전에 기록되는 경로가 있어, 개행이 들어가면 로그 파일에 가짜 감사 줄을 심을 수 있다. 개행·캐리지리턴을
     * 눈에 보이는 기호로 바꾼다.
     */
    private String sanitize(String value) {
        if (value == null) return null;
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
