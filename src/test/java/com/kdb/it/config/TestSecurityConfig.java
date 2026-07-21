package com.kdb.it.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 테스트용 Spring Security 설정
 *
 * <p>@WebMvcTest 환경에서 실제 SecurityConfig 및 JWT 필터를 우회합니다. 공개 엔드포인트(/api/auth/**)를 permitAll 처리하고,
 * 나머지 경로는 @WithMockUser 어노테이션으로 인증을 시뮬레이션합니다.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(value -> value.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/auth/**", "/sso/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        (request, response, authException) ->
                                                response.sendError(
                                                        HttpServletResponse.SC_UNAUTHORIZED,
                                                        "Unauthorized")));
        return http.build();
    }
}
