package com.kdb.it.common.popup;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCommonPopupController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    AdminCommonPopupControllerTest.MethodSecurityConfig.class
})
class AdminCommonPopupControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CommonPopupService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("일반 사용자는 안내 팝업 관리 조회를 할 수 없다")
    void getAdminPopup_regularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/common-popup")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("관리자는 현재 안내 팝업 등록 상태를 조회한다")
    void getAdminPopup_admin_returnsCurrentDocument() throws Exception {
        given(service.getAdminPopup())
                .willReturn(
                        new CommonPopupDto.AdminResponse(
                                "GDOC-2026-0100",
                                "<p>안내</p>",
                                "GDOC-2026-0100:2026-09-02T10:20:30.123456"));

        mockMvc.perform(get("/api/admin/common-popup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("GDOC-2026-0100"));
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("관리자는 안내 팝업 본문을 저장한다")
    void save_admin_returnsSavedDocument() throws Exception {
        given(service.save(anyString()))
                .willReturn(
                        new CommonPopupDto.AdminResponse(
                                "GDOC-2026-0100",
                                "<p>안내</p>",
                                "GDOC-2026-0100:2026-09-02T10:20:30.123456"));

        mockMvc.perform(
                        put("/api/admin/common-popup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommonPopupDto.SaveRequest("<p>안내</p>"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHtml").value("<p>안내</p>"));
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("공백 본문은 관리자 서비스에 전달하지 않고 400으로 거부한다")
    void save_blankContent_returns400() throws Exception {
        mockMvc.perform(
                        put("/api/admin/common-popup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommonPopupDto.SaveRequest(" "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("관리자는 현재 안내 팝업 게시를 중지한다")
    void stopPublishing_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/common-popup")).andExpect(status().isNoContent());

        verify(service).stopPublishing();
    }
}
