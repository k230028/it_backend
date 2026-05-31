package com.kdb.it.common.admin.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 실시간 로그 모니터링 DTO 묶음.
 *
 * <p>응답 본문은 표준 로그 컬럼만 노출하며 변경 본문(BEFORE/AFTER)은 포함하지 않는다.</p>
 */
public final class RealtimeLogDto {

    private RealtimeLogDto() {}

    @Schema(name = "RealtimeLogFeedRow", description = "통합 로그 한 행")
    public record FeedRow(
            String logTbl,
            String logKey,
            Long logSno,
            String chgTp,
            LocalDateTime chgDtm,
            String chgUsid,
            String guid,
            String delYn
    ) {}

    @Schema(name = "RealtimeLogSnapshot", description = "실시간 로그 응답 스냅샷")
    public record Snapshot(
            List<FeedRow> rows,
            LocalDateTime serverTime,
            Map<String, Long> tableCounts,
            List<Long> perMinute
    ) {}

    /** 서비스 내부 — Repository 조건 묶음. */
    public record QueryCondition(
            LocalDateTime since,
            String cursorLogTbl,
            Long cursorLogSno,
            int limit,
            List<String> tableKeys,
            List<String> chgTypes
    ) {}
}
