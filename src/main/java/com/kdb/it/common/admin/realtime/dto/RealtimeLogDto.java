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

    /**
     * 서비스 내부 — Repository 조건 묶음.
     *
     * @param since        이 시각 이후의 로그만 조회 (null이면 최신 limit건 스냅샷)
     * @param cursorLogTbl 복합 커서 — 마지막 조회 로그의 로그테이블명 (중복 조회 회피용)
     * @param cursorLogSno 복합 커서 — 마지막 조회 로그의 로그이력트리거일련번호
     * @param limit        최대 조회 건수 (컨트롤러에서 200으로 상한 조정)
     * @param tableKeys    허용 LOG_KEY 목록 (null/빈 목록이면 제약 없음)
     * @param chgTypes     변경유형 필터 — C/U/D 부분집합 (null/빈 목록이면 제약 없음)
     */
    public record QueryCondition(
            LocalDateTime since,
            String cursorLogTbl,
            Long cursorLogSno,
            int limit,
            List<String> tableKeys,
            List<String> chgTypes
    ) {}
}
