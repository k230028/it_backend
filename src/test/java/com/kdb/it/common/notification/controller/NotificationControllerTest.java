package com.kdb.it.common.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.notification.dto.NotificationDto;
import com.kdb.it.common.notification.service.NotificationService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * NotificationController Web 계층 테스트
 *
 * <p>인증 사용자의 알림 조회와 상태 변경 API 응답 및 서비스 위임을 검증합니다.
 */
@WebMvcTest(NotificationController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/notifications: 비인증 사용자는 접근할 수 없다")
    void list_비인증_401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications: 조회 결과를 알림 DTO 페이지로 반환한다")
    void list_인증사용자_페이지반환() throws Exception {
        NotificationDto.Item notification =
                NotificationDto.Item.builder()
                        .infmMsgNo("INF-1")
                        .itPtlInfmSvcTc("01")
                        .ttl("공지")
                        .infmMsgCone("내용")
                        .inqYn("N")
                        .build();
        given(notificationService.listForCurrentUser("10001", true, PageRequest.of(1, 5)))
                .willReturn(new PageImpl<>(List.of(notification), PageRequest.of(1, 5), 1));

        mockMvc.perform(
                        get("/api/notifications")
                                .with(currentUser())
                                .param("unreadOnly", "true")
                                .param("page", "1")
                                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].infmMsgNo").value("INF-1"))
                .andExpect(jsonPath("$.content[0].ttl").value("공지"));
    }

    @Test
    @DisplayName("GET /api/notifications: size가 200을 초과하면 400을 반환한다")
    void list_size상한초과_400() throws Exception {
        mockMvc.perform(get("/api/notifications").with(currentUser()).param("size", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count: 미읽음 건수를 반환한다")
    void unreadCount_인증사용자_건수반환() throws Exception {
        given(notificationService.unreadCount("10001")).willReturn(4L);

        mockMvc.perform(get("/api/notifications/unread-count").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read: 읽음 처리 후 본문 없는 응답을 반환한다")
    void markRead_인증사용자_204() throws Exception {
        mockMvc.perform(patch("/api/notifications/INF-1/read").with(currentUser()))
                .andExpect(status().isNoContent());

        verify(notificationService).markRead("INF-1", "10001");
    }

    @Test
    @DisplayName("PATCH /api/notifications/read-all: 처리된 건수를 반환한다")
    void markAllRead_인증사용자_건수반환() throws Exception {
        given(notificationService.markAllRead("10001")).willReturn(2L);

        mockMvc.perform(patch("/api/notifications/read-all").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id}: 논리 삭제 후 본문 없는 응답을 반환한다")
    void remove_인증사용자_204() throws Exception {
        mockMvc.perform(delete("/api/notifications/INF-1").with(currentUser()))
                .andExpect(status().isNoContent());

        verify(notificationService).softDelete(eq("INF-1"), eq("10001"));
    }

    private RequestPostProcessor currentUser() {
        return user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"));
    }
}
