package com.kdb.it.common.admin.realtime.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import com.kdb.it.common.admin.realtime.repository.RealtimeLogRepository;
import com.kdb.it.common.admin.service.AdminLogService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 로그 모니터링 화면 서비스.
 *
 * <p>
 * {@link AdminLogService#getTables()}의 허용 LOG_KEY 집합과 변경구분 {@code C/U/D}만 허용한다.
 * 검증 실패 시 {@link IllegalArgumentException}을 던진다.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class RealtimeLogService {

    private static final Set<String> ALLOWED_CHG_TYPES = Set.of("C", "U", "D");
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 200;
    private static final int TABLE_COUNT_WINDOW_MIN = 5;
    private static final int PER_MINUTE_WINDOW_MIN = 30;

    private final RealtimeLogRepository repository;
    private final AdminLogService adminLogService;
    private final Clock clock;

    public RealtimeLogService(RealtimeLogRepository repository,
            AdminLogService adminLogService,
            Clock clock) {
        this.repository = repository;
        this.adminLogService = adminLogService;
        this.clock = clock;
    }

    /**
     * 실시간 로그 스냅샷을 반환한다.
     *
     * @param since        이 시각 이후 로그만 조회. null이면 현재 필터 기준 최신 로그를 조회.
     * @param cursorLogTbl 복합 커서의 LOG_TBL. {@code since}와 함께 사용.
     * @param cursorLogSno 복합 커서의 LOG_HIS_TGR_SNO. {@code since}와 함께 사용.
     * @param limit        조회 상한. 0 이하면 기본값 200을 적용하고 최대 200으로 제한.
     * @param tableKeys    허용된 LOG_KEY 부분집합. null/빈 리스트는 필터 없음.
     * @param chgTypes     C/U/D 부분집합. null/빈 리스트는 필터 없음.
     * @throws IllegalArgumentException 허용되지 않은 LOG_KEY 또는 chgType 포함 시.
     */
    public RealtimeLogDto.Snapshot snapshot(
            LocalDateTime since, String cursorLogTbl, Long cursorLogSno,
            int limit, List<String> tableKeys, List<String> chgTypes) {

        Set<String> allowedKeys = allowedLogKeys();
        List<String> normalizedTables = normalize(tableKeys);
        for (String key : normalizedTables) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("허용되지 않은 LOG_KEY: " + key);
            }
        }
        List<String> normalizedChgTypes = normalize(chgTypes);
        for (String t : normalizedChgTypes) {
            if (!ALLOWED_CHG_TYPES.contains(t)) {
                throw new IllegalArgumentException("허용되지 않은 변경구분: " + t);
            }
        }

        int safeLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));

        var cond = new RealtimeLogDto.QueryCondition(
                since, cursorLogTbl, cursorLogSno, safeLimit, normalizedTables, normalizedChgTypes);

        var rows = repository.findFeed(cond);
        LocalDateTime serverTime = LocalDateTime.now(clock);
        var tableCounts = repository.countByTableSince(serverTime.minusMinutes(TABLE_COUNT_WINDOW_MIN));
        var perMinute = repository.perMinuteSince(serverTime.minusMinutes(PER_MINUTE_WINDOW_MIN), serverTime);
        return new RealtimeLogDto.Snapshot(rows, serverTime, tableCounts, perMinute);
    }

    private Set<String> allowedLogKeys() {
        return adminLogService.getTables().stream()
                .map(value -> value.key())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<String> normalize(List<String> input) {
        if (input == null)
            return List.of();
        return input.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(value -> value.trim())
                .toList();
    }
}
