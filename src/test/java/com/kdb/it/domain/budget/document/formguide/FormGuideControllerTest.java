package com.kdb.it.domain.budget.document.formguide;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 공개 사업 입력 길라잡이 조회 API의 인증·응답 계약을 검증합니다. */
@WebMvcTest(FormGuideController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class FormGuideControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FormGuideService formGuideService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("공개 길라잡이 조회는 비인증 요청을 401로 거부한다")
    void getPublished_비인증_401() throws Exception {
        mockMvc.perform(get("/api/form-guides").param("scope", "info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공개 길라잡이 조회는 인증 사용자에게 해당 범위의 목록을 반환한다")
    @WithMockUser(username = "10001")
    void getPublished_인증_200() throws Exception {
        given(formGuideService.getPublished(FormGuideScope.INFO))
                .willReturn(
                        List.of(
                                new FormGuideDto.PublicResponse(
                                        "info.basic.abusNm", "사업명", "<p>안내</p>")));

        mockMvc.perform(get("/api/form-guides").param("scope", "info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideId").value("info.basic.abusNm"))
                .andExpect(jsonPath("$[0].fieldLabel").value("사업명"))
                .andExpect(jsonPath("$[0].contentHtml").value("<p>안내</p>"));
    }
}
