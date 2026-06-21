package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * SecurityConfig CORS 설정 단위 테스트 (T9a) — 허용 헤더 명시화/오리진 검증.
 */
class SecurityConfigCorsTest {

    private CorsConfiguration corsFor(String origins) {
        SecurityConfig config = new SecurityConfig(Mockito.mock(JwtAuthenticationFilter.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        // "/**" 매핑을 직접 조회 — 요청 mock보다 결정적
        return source.getCorsConfigurations().get("/**");
    }

    @Test
    @DisplayName("allowedHeaders는 와일드카드(*)가 아니라 명시 헤더 목록이어야 한다")
    void allowedHeaders_명시목록() {
        CorsConfiguration cors = corsFor("https://it.kdb.co.kr");
        assertThat(cors.getAllowedHeaders())
                .containsExactlyInAnyOrder("Content-Type", "Authorization", "X-Requested-With");
        assertThat(cors.getAllowedHeaders()).doesNotContain("*");
    }

    @Test
    @DisplayName("allowCredentials=true 와 함께 명시 오리진을 허용한다")
    void allowedOrigins_명시오리진() {
        CorsConfiguration cors = corsFor("https://it.kdb.co.kr");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://it.kdb.co.kr");
    }
}
