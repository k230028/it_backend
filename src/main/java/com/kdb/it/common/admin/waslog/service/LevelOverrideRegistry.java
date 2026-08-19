package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** 로거별 오버라이드를 등록하거나 갱신한다. */
    public void put(WasLogDto.LevelOverride override) {
        overrides.put(override.logger(), override);
    }

    /** 로거명 오름차순 목록. */
    public List<WasLogDto.LevelOverride> list() {
        List<WasLogDto.LevelOverride> result = new ArrayList<>(overrides.values());
        result.sort((a, b) -> a.logger().compareTo(b.logger()));
        return result;
    }

    /** 만료된 항목을 꺼내며 제거한다. */
    public List<WasLogDto.LevelOverride> removeExpired(LocalDateTime now) {
        List<WasLogDto.LevelOverride> expired = new ArrayList<>();
        for (WasLogDto.LevelOverride override : list()) {
            if (!override.expiresAt().isAfter(now)) {
                overrides.remove(override.logger());
                expired.add(override);
            }
        }
        return expired;
    }
}
