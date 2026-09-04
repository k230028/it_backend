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

@WebMvcTest(ApprovalAuthorityNoticeController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class ApprovalAuthorityNoticeControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CommonPopupService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("인증 사용자는 전결권 안내 콘텐츠를 조회한다")
    void getNotice_authenticated_returnsContent() throws Exception {
        given(service.getApprovalAuthorityNotice())
                .willReturn(
                        Optional.of(
                                new CommonPopupDto.Response(
                                        "PDOC-2026-0101",
                                        "<p>전결권 안내</p>",
                                        "PDOC-2026-0101:2026-09-04T10:20:30")));

        mockMvc.perform(get("/api/project-approval-authority-notice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("PDOC-2026-0101"))
                .andExpect(jsonPath("$.contentHtml").value("<p>전결권 안내</p>"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("등록된 전결권 안내가 없으면 204를 반환한다")
    void getNotice_withoutDocument_returns204() throws Exception {
        given(service.getApprovalAuthorityNotice()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/project-approval-authority-notice"))
                .andExpect(status().isNoContent());
    }
}
