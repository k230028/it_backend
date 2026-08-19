package com.kdb.it.common.admin.waslog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 자신의 {@code @PreAuthorize}를 격리 검증한다.
 *
 * <p>URL 패턴 규칙이 없는 {@link TestSecurityConfig}에 메서드 보안만 켜므로, 애너테이션을 지우면 이 테스트가 깨진다. 엔드포인트가 나중에
 * {@code /api/admin/**} 밖으로 옮겨져도 권한이 유지되는지를 지키는 안전망이다.
 */
@WebMvcTest(WasLogController.class)
@Import({
    TestSecurityConfig.class,
    WasLogControllerAuthorizationTest.MethodSecurityTestConfig.class
})
class WasLogControllerAuthorizationTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mvc;

    @MockitoBean private WasLogService service;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자는 로그 조회에서 403")
    void 일반사용자_조회_403() throws Exception {
        mvc.perform(get("/api/admin/was-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자는 인스턴스 목록에서도 403")
    void 일반사용자_인스턴스목록_403() throws Exception {
        mvc.perform(get("/api/admin/was-logs/instances")).andExpect(status().isForbidden());
    }
}
