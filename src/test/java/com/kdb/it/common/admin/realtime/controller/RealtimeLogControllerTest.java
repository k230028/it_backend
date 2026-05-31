package com.kdb.it.common.admin.realtime.controller;

import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import com.kdb.it.common.admin.realtime.service.RealtimeLogService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 로그 컨트롤러 권한 테스트.
 *
 * <p>WebMvcTest 슬라이스에서 ROLE_ADMIN/USER/Anonymous별 접근 제어를 검증한다.</p>
 */
@WebMvcTest(RealtimeLogController.class)
@Import({ TestSecurityConfig.class, RealtimeLogControllerTest.MethodSecurityTestConfig.class })
class RealtimeLogControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RealtimeLogService service;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 200 응답")
    void admin_returns200() throws Exception {
        when(service.snapshot(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(new RealtimeLogDto.Snapshot(
                        List.of(),
                        LocalDateTime.of(2026, 5, 31, 23, 14, 7),
                        Map.of(),
                        List.of()));

        mvc.perform(get("/api/admin/realtime-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN — since/커서/limit/tables/chgTypes 전체 쿼리 파라미터 전달 (split CSV 분기 커버)")
    void admin_withAllQueryParams_passesSplitValues() throws Exception {
        // Arrange: 서비스는 mock — 컨트롤러의 split()·파라미터 바인딩 경로만 검증한다.
        when(service.snapshot(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(new RealtimeLogDto.Snapshot(
                        List.of(),
                        LocalDateTime.of(2026, 5, 31, 23, 14, 7),
                        Map.of(),
                        List.of()));

        // Act: tables에 빈 세그먼트(", ,")를 포함시켜 split()의 trim + !isEmpty 필터 분기를 커버한다.
        mvc.perform(get("/api/admin/realtime-logs")
                        .param("since", "2026-05-31T23:00:00")
                        .param("cursorLogTbl", "BPROJM")
                        .param("cursorLogSno", "42")
                        .param("limit", "50")
                        .param("tables", "BPROJM, ,CCODEM")
                        .param("chgTypes", "C,U,D"))
                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").exists());

        // split()이 CSV를 trim·필터링한 결과(빈 세그먼트 제거)를 서비스에 전달했는지 검증한다.
        org.mockito.Mockito.verify(service).snapshot(
                org.mockito.ArgumentMatchers.eq(LocalDateTime.of(2026, 5, 31, 23, 0, 0)),
                org.mockito.ArgumentMatchers.eq("BPROJM"),
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(50),
                org.mockito.ArgumentMatchers.eq(List.of("BPROJM", "CCODEM")),
                org.mockito.ArgumentMatchers.eq(List.of("C", "U", "D")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("비-ADMIN 차단 (GlobalExceptionHandler가 AccessDeniedException → 400 매핑)")
    void user_forbidden() throws Exception {
        // 프로젝트 GlobalExceptionHandler가 RuntimeException(AccessDeniedException 포함)을
        // 일괄 400으로 매핑하므로 본 프로젝트 동작값은 400임. 권한 차단 자체는 정상 동작.
        mvc.perform(get("/api/admin/realtime-logs"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("미인증 401")
    void anonymous_unauthorized() throws Exception {
        mvc.perform(get("/api/admin/realtime-logs"))
                .andExpect(status().isUnauthorized());
    }
}
