package com.kdb.it.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 비활성 OpenAPI 경로의 인증 및 리소스 경계를 검증한다. */
@SpringBootTest(
        classes = OpenApiDisabledSecurityTest.OpenApiDisabledSecurityTestApp.class,
        properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDisabledSecurityTest {

    private static final String[] OPEN_API_PATHS = {
        "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html", "/swagger-ui.html"
    };

    @Autowired private MockMvc mockMvc;

    @Autowired private JwtUtil jwtUtil;

    @Test
    @DisplayName("비활성 OpenAPI 경로의 익명 요청은 인증 경계에서 모두 401")
    void disabledOpenApiPaths_unauthenticated_return401() throws Exception {
        for (String path : OPEN_API_PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("비활성 OpenAPI 경로의 인증 요청은 리소스 경계에서 모두 404")
    void disabledOpenApiPaths_authenticated_return404() throws Exception {
        Cookie accessToken = accessTokenCookie();

        for (String path : OPEN_API_PATHS) {
            mockMvc.perform(get(path).cookie(accessToken)).andExpect(status().isNotFound());
        }
    }

    private Cookie accessTokenCookie() {
        String token =
                jwtUtil.generateAccessToken("TESTER", List.of(CustomUserDetails.ATH_USER), "D001");
        return new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token);
    }

    /** OpenAPI와 Security만 최소로 띄워 실제 HTTP 경계를 검증한다. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        SecurityConfig.class,
        SwaggerConfig.class,
        JwtAuthenticationFilter.class,
        SimpleRequestCsrfFilter.class,
        JwtUtil.class
    })
    static class OpenApiDisabledSecurityTestApp {}
}
