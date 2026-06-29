package com.kdb.it.common.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kdb.it.common.admin.controller.AdminController;
import com.kdb.it.common.admin.service.AdminLogService;
import com.kdb.it.common.admin.service.AdminService;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.config.SecurityConfig;

import jakarta.servlet.http.Cookie;

/**
 * 관리자 API JWT 경계 검증 (TASK 보안 #2)
 *
 * <p>
 * 실제 보안 체인({@link SecurityConfig} + {@link JwtAuthenticationFilter})을 그대로 적용해,
 * 백엔드 권한 판단이 httpOnly {@code accessToken} JWT에만 기반함을 증명합니다.
 * 프론트 가드용 {@code it-portal-user} 쿠키는 백엔드 인가에 사용되지 않으므로,
 * 위조된 {@code it-portal-user} 쿠키만으로(유효 JWT 없이) {@code /api/admin/**}에 접근하면
 * {@link JwtAuthenticationFilter}가 인증을 설정하지 않아 401을 반환해야 합니다.
 * </p>
 *
 * <p>
 * {@code TestSecurityConfig}(우회용) 대신 실제 {@code SecurityConfig}와 {@code JwtAuthenticationFilter}를
 * 가져와 운영과 동일한 인가 경계를 검증합니다. {@code JwtUtil}은 mock으로 대체하나, JWT 자체가 없으므로
 * 토큰 추출 단계에서 인증이 설정되지 않습니다.
 * </p>
 */
@WebMvcTest(AdminController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class AdminSecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig → JwtAuthenticationFilter 가 의존하는 JwtUtil (유효 JWT가 없으므로 호출되지 않음)
    @MockitoBean
    private JwtUtil jwtUtil;

    // AdminController 의존성 (보안 체인 검증이 목적이므로 동작은 mock)
    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private AdminLogService adminLogService;

    @Test
    @DisplayName("위조 it-portal-user 쿠키만으로(유효 JWT 없이) /api/admin/** 접근 시 401")
    void adminApi_forgedPortalUserCookie_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .cookie(new Cookie("it-portal-user",
                                URLEncoder.encode("{\"athIds\":[\"ITPAD001\"]}", StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
    }
}
