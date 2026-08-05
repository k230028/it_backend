package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

/**
 * BE-29 회귀 방지: 공통코드 쓰기 경로는 CodeService와 같은 CodeRepository를 쓰므로 같은 캐시(codesByCid·budgetPeriod)를 반드시
 * 무효화해야 합니다.
 */
class AdminCodeServiceCacheEvictTest {

    @ParameterizedTest
    @ValueSource(strings = {"createCode", "updateCode", "deleteCode", "bulkUpsertCodes"})
    @DisplayName("공통코드 쓰기 메서드는 codesByCid·budgetPeriod를 allEntries로 무효화한다")
    void 공통코드_쓰기메서드_캐시무효화_적용됨(String methodName) {
        Method method =
                Arrays.stream(AdminCodeService.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("메서드를 찾을 수 없습니다: " + methodName));

        Caching caching = method.getAnnotation(Caching.class);
        assertThat(caching).as("%s에 @Caching(evict=...)가 없습니다 (BE-29)", methodName).isNotNull();

        List<String> evictedCaches =
                Arrays.stream(caching.evict())
                        .map(CacheEvict::value)
                        .flatMap(Arrays::stream)
                        .toList();

        assertThat(evictedCaches).contains("codesByCid", "budgetPeriod");
        assertThat(caching.evict()).allMatch(CacheEvict::allEntries);
    }
}
