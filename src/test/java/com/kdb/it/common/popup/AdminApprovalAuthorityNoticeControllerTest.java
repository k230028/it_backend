package com.kdb.it.common.popup;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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

@WebMvcTest(AdminApprovalAuthorityNoticeController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    AdminApprovalAuthorityNoticeControllerTest.MethodSecurityConfig.class
})
class AdminApprovalAuthorityNoticeControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CommonPopupService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("일반 사용자는 전결권 안내 관리 API를 사용할 수 없다")
    void getNotice_regularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/project-approval-authority-notice"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("관리자는 전결권 안내 등록 상태를 조회한다")
    void getNotice_admin_returnsRegisteredDocument() throws Exception {
        given(service.getAdminApprovalAuthorityNotice())
                .willReturn(
                        new CommonPopupDto.AdminResponse(
                                "PDOC-2026-0101",
                                "<p>전결권 안내</p>",
                                "PDOC-2026-0101:2026-09-04T10:20:30"));

        mockMvc.perform(get("/api/admin/project-approval-authority-notice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("PDOC-2026-0101"))
                .andExpect(jsonPath("$.contentHtml").value("<p>전결권 안내</p>"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("일반 사용자는 전결권 안내를 저장할 수 없다")
    void saveNotice_regularUser_returns403() throws Exception {
        mockMvc.perform(
                        put("/api/admin/project-approval-authority-notice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommonPopupDto.SaveRequest("<p>전결권 안내</p>"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10001", roles = "ADMIN")
    @DisplayName("관리자는 전결권 안내를 저장한다")
    void saveNotice_admin_returnsSavedDocument() throws Exception {
        given(service.saveApprovalAuthorityNotice(anyString()))
                .willReturn(
                        new CommonPopupDto.AdminResponse(
                                "PDOC-2026-0101",
                                "<p>전결권 안내</p>",
                                "PDOC-2026-0101:2026-09-04T10:20:30"));

        mockMvc.perform(
                        put("/api/admin/project-approval-authority-notice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommonPopupDto.SaveRequest("<p>전결권 안내</p>"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("PDOC-2026-0101"));
    }
}
