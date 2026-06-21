package com.kdb.it.domain.menu.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.config.CacheConfig;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 메뉴 권한 매핑 캐시 검증 — 연속 호출 시 Cmenua 전체 조회는 1회만.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, MenuAuthMapProvider.class})
class MenuAuthMapProviderTest {

    @Autowired private MenuAuthMapProvider provider;
    @MockitoBean private CmenuaRepository cmenuaRepository;

    @Test
    @DisplayName("getMenuAuthMap 캐시: 연속 호출 시 Cmenua 전체 조회는 1회만 발생한다")
    void menuAuthMap_cached() {
        given(cmenuaRepository.findAllActive()).willReturn(List.of());

        provider.getMenuAuthMap();
        provider.getMenuAuthMap();

        verify(cmenuaRepository, times(1)).findAllActive();
    }
}
