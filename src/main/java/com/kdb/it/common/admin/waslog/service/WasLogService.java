package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.appender.WasLogBuffer;
import com.kdb.it.common.admin.waslog.client.WasLogPeerClient;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * WAS 로그 조회 서비스.
 *
 * <p>로컬 링버퍼를 필터링해 스냅샷을 만든다. 대상 인스턴스가 자신이 아니면 {@link WasLogPeerClient}로 위임한다(Task 4).
 */
@Service
public class WasLogService {

    /** 조회 상한. 기본값이자 최대값. */
    public static final int MAX_LIMIT = 200;

    /** 허용 레벨. */
    public static final Set<String> ALLOWED_LEVELS =
            Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    private final WasLogProperties properties;
    private final String selfInstanceId;
    private final WasLogPeerClient peerClient;
    private final LevelOverrideRegistry overrideRegistry;

    public WasLogService(
            WasLogProperties properties,
            @Value("${app.server.instance-id:SVR1}") String selfInstanceId,
            WasLogPeerClient peerClient,
            LevelOverrideRegistry overrideRegistry) {
        this.properties = properties;
        this.selfInstanceId = selfInstanceId;
        this.peerClient = peerClient;
        this.overrideRegistry = overrideRegistry;
    }

    /** 이 인스턴스의 ID. */
    public String selfInstanceId() {
        return selfInstanceId;
    }

    /**
     * 로컬 링버퍼 스냅샷을 조건에 맞춰 반환한다.
     *
     * @throws IllegalArgumentException 허용되지 않은 레벨이 포함된 경우
     */
    public WasLogDto.Snapshot localSnapshot(WasLogDto.Query query) {
        Set<String> levels = query.levels() == null ? Set.of() : query.levels();
        for (String level : levels) {
            if (!ALLOWED_LEVELS.contains(level)) {
                throw new IllegalArgumentException("허용되지 않은 로그 레벨: " + level);
            }
        }

        int limit = query.limit() <= 0 ? MAX_LIMIT : Math.min(query.limit(), MAX_LIMIT);
        WasLogBuffer.BufferSnapshot buffer = WasLogBuffer.shared().snapshot();

        List<WasLogEntry> filtered = new ArrayList<>();
        for (WasLogEntry entry : buffer.entries()) {
            if (entry.seq() <= query.afterSeq()) continue;
            if (!levels.isEmpty() && !levels.contains(entry.level())) continue;
            if (!matchesLogger(entry, query.logger())) continue;
            if (!matchesKeyword(entry, query.keyword())) continue;
            filtered.add(entry);
        }
        boolean truncated = filtered.size() > limit;
        if (truncated) {
            filtered = new ArrayList<>(filtered.subList(filtered.size() - limit, filtered.size()));
        }

        // 커서 이후 항목을 건너뛰는 경로는 둘이다 — ① 버퍼에서 밀려남 ② 조회 상한을 넘겨 최신분만 남김.
        // ②는 오래된 쪽을 버리므로 커서를 낮춰도 복구되지 않는다. 조용히 넘기면 클라이언트는 연속된
        // 로그를 본다고 착각하므로, 두 경로 모두 dropped로 알린다.
        boolean evicted = query.afterSeq() > 0 && buffer.oldestSeq() > query.afterSeq() + 1;
        boolean dropped = evicted || truncated;
        long lastSeq = Math.max(query.afterSeq(), buffer.lastSeq());

        return new WasLogDto.Snapshot(
                selfInstanceId,
                buffer.epoch(),
                filtered,
                lastSeq,
                dropped,
                overrideRegistry == null ? List.of() : overrideRegistry.list(),
                null);
    }

    /** 설정에 등록된 인스턴스 목록. */
    public List<WasLogDto.InstanceInfo> instances() {
        List<WasLogDto.InstanceInfo> result = new ArrayList<>();
        for (var entry : properties.peers().entrySet()) {
            String id = entry.getKey();
            boolean self = id.equals(selfInstanceId);
            boolean reachable = self || (entry.getValue() != null && !entry.getValue().isBlank());
            result.add(new WasLogDto.InstanceInfo(id, self, reachable));
        }
        if (result.stream().noneMatch(WasLogDto.InstanceInfo::self)) {
            result.add(new WasLogDto.InstanceInfo(selfInstanceId, true, true));
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return result;
    }

    private boolean matchesLogger(WasLogEntry entry, String prefix) {
        if (prefix == null || prefix.isBlank()) return true;
        return entry.logger() != null && entry.logger().startsWith(prefix);
    }

    private boolean matchesKeyword(WasLogEntry entry, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String needle = keyword.toLowerCase(Locale.ROOT);
        String message = entry.message() == null ? "" : entry.message().toLowerCase(Locale.ROOT);
        String logger = entry.logger() == null ? "" : entry.logger().toLowerCase(Locale.ROOT);
        return message.contains(needle) || logger.contains(needle);
    }
}
