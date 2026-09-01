package com.kdb.it.common.speeddial.contact;

import static org.mockito.ArgumentMatchers.any;
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

/** 담당자 정보 관리 API의 관리자 전용 저장 계약을 검증합니다. */
@WebMvcTest(AdminContactInfoController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    AdminContactInfoControllerTest.MethodSecurityConfig.class
})
class AdminContactInfoControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ContactInfoService contactInfoService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("담당자 정보 조회는 관리자에게 현재 문서를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getContactInfo_관리자_200() throws Exception {
        given(contactInfoService.getContactInfo())
                .willReturn(new ContactInfoDto.Response("GDOC-2026-0042", "<p>담당자</p>"));

        mockMvc.perform(get("/api/admin/contact-information"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("GDOC-2026-0042"));
    }

    @Test
    @DisplayName("담당자 정보 저장은 일반 사용자를 403으로 거부한다")
    @WithMockUser(username = "10001")
    void saveContactInfo_일반사용자_403() throws Exception {
        mockMvc.perform(
                        put("/api/admin/contact-information")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ContactInfoDto.SaveRequest("<p>담당자</p>"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("담당자 정보 저장은 관리자에게 GDOC 문서관리번호를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void saveContactInfo_관리자_200() throws Exception {
        given(contactInfoService.saveContactInfo(any()))
                .willReturn(new ContactInfoDto.Response("GDOC-2026-0042", "<p>담당자</p>"));

        mockMvc.perform(
                        put("/api/admin/contact-information")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ContactInfoDto.SaveRequest("<p>담당자</p>"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("GDOC-2026-0042"));
    }
}
