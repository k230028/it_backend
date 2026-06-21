package com.kdb.it.domain.menu.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.config.CacheConfig;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 메뉴 권한 매핑 캐시 검증 — 연속 호출 시 Cmenua 전체 조회는 1회만.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, MenuAuthMapProvider.class})
class MenuAuthMapProviderTest {

    @Autowired private MenuAuthMapProvider provider;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private CmenuaRepository cmenuaRepository;

    // 캐시는 Spring 컨텍스트에 공유되므로 테스트 간 격리를 위해 매 테스트 전에 초기화한다.
    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames()
                .forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("getMenuAuthMap 캐시: 연속 호출 시 Cmenua 전체 조회는 1회만 발생한다")
    void menuAuthMap_cached() {
        given(cmenuaRepository.findAllActive()).willReturn(List.of());

        provider.getMenuAuthMap();
        provider.getMenuAuthMap();

        verify(cmenuaRepository, times(1)).findAllActive();
    }

    @Test
    @DisplayName("getMenuAuthMap 캐시 evict: menuAuthMap 캐시 초기화 후 재조회 시 Cmenua 전체 조회가 다시 발생한다")
    void menuAuthMap_evictedAfterClear() {
        given(cmenuaRepository.findAllActive()).willReturn(List.of());

        // 1차 호출 → 캐시 적재
        provider.getMenuAuthMap();

        // menuAuthMap 캐시 무효화 (AdminMenuService의 @CacheEvict와 동일 효과)
        Objects.requireNonNull(cacheManager.getCache("menuAuthMap")).clear();

        // 2차 호출 → 캐시 miss → repository 재호출
        provider.getMenuAuthMap();

        // evict가 사이에 끼었으므로 전체 조회가 총 2회 발생해야 한다
        verify(cmenuaRepository, times(2)).findAllActive();
    }
}
