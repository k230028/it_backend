package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 메뉴ID→권한ID 집합 매핑 제공자.
 *
 * <p>별도 빈으로 분리한 이유: {@code @Cacheable}은 동일 빈 내부 self-invocation 시 프록시를 우회해
 * 캐시가 적용되지 않는다. {@link MenuQueryService}가 본 빈을 주입받아 호출해야 캐시가 동작한다.
 * 메뉴/권한 변경 시 {@code menuAuthMap} 캐시를 evict 해야 정합이 유지된다(AdminMenuService).</p>
 */
@Component
@RequiredArgsConstructor
public class MenuAuthMapProvider {

    private final CmenuaRepository cmenuaRepository;

    /** 활성 Cmenua를 mnuId→권한ID 집합으로 빌드. 캐시명: menuAuthMap. */
    @Cacheable("menuAuthMap")
    public Map<String, Set<String>> getMenuAuthMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Cmenua a : cmenuaRepository.findAllActive()) {
            map.computeIfAbsent(a.getMnuId(), k -> new HashSet<>()).add(a.getAthId());
        }
        return map;
    }
}
