package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 이 인스턴스에 적용 중인 런타임 로그레벨 변경 보관소.
 *
 * <p>프로세스 메모리에만 둔다. 재기동하면 설정 파일 레벨로 자연 복원되므로 영속화하지 않는다.
 */
@Component
public class LevelOverrideRegistry {

    private final Map<String, WasLogDto.LevelOverride> overrides = new ConcurrentHashMap<>();

    /**
     * 이 프로세스에서 한 번이라도 레벨을 바꾼 로거명. 만료돼도 줄지 않는다.
     *
     * <p>logback {@code LoggerContext}는 {@code setLogLevel}로 만들어진 {@code Logger}를 프로세스가 살아 있는 동안
     * 보관하고 해제 경로를 주지 않는다. 따라서 "지금 적용 중인 개수"만 제한해서는 프로세스 수명 전체의 상한이 서지 않는다. 만료 후에도 남는 이 집합이 그 상한의
     * 근거다(BE-62).
     */
    private final Set<String> touchedLoggers = ConcurrentHashMap.newKeySet();

    /** 로거별 오버라이드를 등록하거나 갱신한다. */
    public void put(WasLogDto.LevelOverride override) {
        overrides.put(override.logger(), override);
        touchedLoggers.add(override.logger());
    }

    /** 지금 적용 중인 오버라이드 수. */
    public int activeCount() {
        return overrides.size();
    }

    /** 이 프로세스에서 한 번이라도 레벨을 바꾼 서로 다른 로거 수. 만료돼도 줄지 않는다. */
    public int touchedCount() {
        return touchedLoggers.size();
    }

    /** 이 프로세스에서 한 번이라도 레벨을 바꾼 로거인지 여부. */
    public boolean isTouched(String logger) {
        return touchedLoggers.contains(logger);
    }

    /** 로거의 현재 오버라이드. 없으면 null. */
    public WasLogDto.LevelOverride find(String logger) {
        return overrides.get(logger);
    }

    /** 로거명 오름차순 목록. */
    public List<WasLogDto.LevelOverride> list() {
        List<WasLogDto.LevelOverride> result = new ArrayList<>(overrides.values());
        result.sort((a, b) -> a.logger().compareTo(b.logger()));
        return result;
    }

    /**
     * 만료된 항목을 꺼내며 제거한다.
     *
     * <p>스캔 시점에 읽은 값과 같을 때만 제거한다({@code remove(key, value)}). 무조건 {@code remove(key)}를 쓰면, 스캔과 제거
     * 사이에 사용자가 같은 로거를 재적용해 새 오버라이드가 들어온 경우 그 새 오버라이드까지 지워버린다.
     */
    public List<WasLogDto.LevelOverride> removeExpired(LocalDateTime now) {
        List<WasLogDto.LevelOverride> expired = new ArrayList<>();
        for (WasLogDto.LevelOverride override : list()) {
            if (!override.expiresAt().isAfter(now)
                    && overrides.remove(override.logger(), override)) {
                expired.add(override);
            }
        }
        return expired;
    }
}
