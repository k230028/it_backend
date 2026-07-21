package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.config.CacheConfig;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * MenuAuthMapProvider 단위 테스트.
 *
 * <p>메뉴ID→권한ID 집합 매핑 빌드 로직과 {@code @Cacheable} 동작을 검증합니다. {@link CmenuaRepository}는
 * {@code @MockitoBean}으로 교체하며 Oracle DB 없이 실행됩니다.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, MenuAuthMapProvider.class})
class MenuAuthMapProviderTest {

    @Autowired private MenuAuthMapProvider provider;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private CmenuaRepository cmenuaRepository;

    // 캐시는 Spring 컨텍스트에 공유되므로 테스트 간 격리를 위해 매 테스트 전에 초기화한다.
    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
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

    // ─────────────────────────────────────────────────────────────────
    // 매핑 빌드 로직 분기 — 비어 있지 않은 Cmenua 목록
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMenuAuthMap: 단일 Cmenua 행에서 mnuId→athId 매핑이 올바르게 빌드된다")
    void getMenuAuthMap_단일행_매핑빌드() {
        // given: 메뉴ID=MNU001, 권한ID=ITPAD001 인 활성 행 1건
        Cmenua row = mock(Cmenua.class);
        given(row.getMnuId()).willReturn("MNU001");
        given(row.getAthId()).willReturn("ITPAD001");
        given(cmenuaRepository.findAllActive()).willReturn(List.of(row));

        // when
        Map<String, Set<String>> result = provider.getMenuAuthMap();

        // then: 메뉴ID에 대응하는 권한ID 집합이 1건 포함된다
        assertThat(result).containsKey("MNU001");
        assertThat(result.get("MNU001")).containsExactly("ITPAD001");
    }

    @Test
    @DisplayName("getMenuAuthMap: 동일 mnuId에 복수 권한ID가 있으면 하나의 Set에 모두 포함된다")
    void getMenuAuthMap_동일메뉴_복수권한_그룹화() {
        // given: 메뉴ID=MNU002에 권한 2건
        Cmenua rowA = mock(Cmenua.class);
        given(rowA.getMnuId()).willReturn("MNU002");
        given(rowA.getAthId()).willReturn("ITPAD001");

        Cmenua rowB = mock(Cmenua.class);
        given(rowB.getMnuId()).willReturn("MNU002");
        given(rowB.getAthId()).willReturn("ITPZZ002");

        given(cmenuaRepository.findAllActive()).willReturn(List.of(rowA, rowB));

        // when
        Map<String, Set<String>> result = provider.getMenuAuthMap();

        // then: 동일 메뉴ID가 하나의 키로 병합되고 권한 2건이 Set에 포함된다
        assertThat(result).hasSize(1);
        assertThat(result.get("MNU002")).containsExactlyInAnyOrder("ITPAD001", "ITPZZ002");
    }

    @Test
    @DisplayName("getMenuAuthMap: 서로 다른 mnuId는 개별 키로 분리된다")
    void getMenuAuthMap_다른메뉴_개별키분리() {
        // given: 서로 다른 메뉴ID=MNU003, MNU004 각 1건씩
        Cmenua rowX = mock(Cmenua.class);
        given(rowX.getMnuId()).willReturn("MNU003");
        given(rowX.getAthId()).willReturn("ITPAD001");

        Cmenua rowY = mock(Cmenua.class);
        given(rowY.getMnuId()).willReturn("MNU004");
        given(rowY.getAthId()).willReturn("ITPZZ001");

        given(cmenuaRepository.findAllActive()).willReturn(List.of(rowX, rowY));

        // when
        Map<String, Set<String>> result = provider.getMenuAuthMap();

        // then: 두 메뉴ID가 별도 키로 분리되어 각각 1개의 권한ID를 가진다
        assertThat(result).hasSize(2);
        assertThat(result.get("MNU003")).containsExactly("ITPAD001");
        assertThat(result.get("MNU004")).containsExactly("ITPZZ001");
    }

    @Test
    @DisplayName("getMenuAuthMap: 활성 Cmenua가 없으면 빈 맵을 반환한다")
    void getMenuAuthMap_활성행없음_빈맵() {
        // given: 활성 매핑 없음
        given(cmenuaRepository.findAllActive()).willReturn(List.of());

        // when
        Map<String, Set<String>> result = provider.getMenuAuthMap();

        // then: 빈 맵 반환
        assertThat(result).isEmpty();
    }
}
