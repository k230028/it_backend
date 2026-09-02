package com.kdb.it.common.popup;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommonPopupController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CommonPopupControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CommonPopupService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("공통 안내 팝업 조회는 비인증 사용자를 거부한다")
    void getActivePopup_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/common-popup")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("활성 안내가 없으면 204를 반환한다")
    void getActivePopup_withoutDocument_returns204() throws Exception {
        given(service.getActivePopup()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/common-popup")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("일반 인증 사용자에게 활성 안내와 콘텐츠 버전을 반환한다")
    void getActivePopup_authenticated_returnsPopup() throws Exception {
        given(service.getActivePopup())
                .willReturn(
                        Optional.of(
                                new CommonPopupDto.Response(
                                        "GDOC-2026-0100",
                                        "<p>안내</p>",
                                        "GDOC-2026-0100:2026-09-02T10:20:30.123456")));

        mockMvc.perform(get("/api/common-popup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("GDOC-2026-0100"))
                .andExpect(jsonPath("$.contentHtml").value("<p>안내</p>"))
                .andExpect(
                        jsonPath("$.contentVersion")
                                .value("GDOC-2026-0100:2026-09-02T10:20:30.123456"));
    }
}
