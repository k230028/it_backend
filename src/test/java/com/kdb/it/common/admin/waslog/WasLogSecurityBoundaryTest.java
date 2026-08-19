package com.kdb.it.common.admin.waslog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.controller.WasLogController;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** WAS 로그 API의 인증·인가 경계 검증. 실제 {@link SecurityConfig}를 그대로 적용한다. */
@WebMvcTest(WasLogController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    CookieUtil.class,
    JacksonConfig.class
})
class WasLogSecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private WasLogService service;

    @Test
    @DisplayName("미인증 요청은 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/was-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자 요청은 403")
    @WithMockUser(roles = "USER")
    void 일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/admin/was-logs")).andExpect(status().isForbidden());
    }
}
