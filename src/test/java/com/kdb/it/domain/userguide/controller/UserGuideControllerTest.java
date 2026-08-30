package com.kdb.it.domain.userguide.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.userguide.dto.UserGuideDto;
import com.kdb.it.domain.userguide.service.UserGuideService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UserGuideController @WebMvcTest
 *
 * <p>사용자가이드 API의 권한 경계와 응답 구조를 검증합니다.
 */
@WebMvcTest(UserGuideController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    UserGuideControllerTest.MethodSecurityTestConfig.class
})
class UserGuideControllerTest {

    /** TestSecurityConfig에는 @EnableMethodSecurity가 없어 @PreAuthorize가 꺼진다. 여기서 켠다. */
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserGuideService userGuideService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String FL_MPN_ID = "FL-00000001";

    private UserGuideDto.Response response(boolean active) {
        return UserGuideDto.Response.builder()
                .flMpnId(FL_MPN_ID)
                .flNm("guide.pdf")
                .apgFlSz(2048L)
                .active(active)
                .downloadUrl("/api/files/" + FL_MPN_ID + "/download")
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("현재 가이드가 없으면 204를 반환한다")
    void getActive_noContent() throws Exception {
        given(userGuideService.getActiveGuide()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/user-guides/active")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("현재 가이드가 있으면 200과 내려받기 URL을 반환한다")
    void getActive_ok() throws Exception {
        given(userGuideService.getActiveGuide()).willReturn(Optional.of(response(true)));

        mockMvc.perform(get("/api/user-guides/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flMpnId").value(FL_MPN_ID))
                .andExpect(
                        jsonPath("$.downloadUrl").value("/api/files/" + FL_MPN_ID + "/download"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 전체 목록을 조회한다")
    void getAll_admin() throws Exception {
        given(userGuideService.getAllGuides()).willReturn(List.of(response(true)));

        mockMvc.perform(get("/api/user-guides/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flMpnId").value(FL_MPN_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("일반 사용자는 전체 목록을 조회할 수 없다")
    void getAll_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/user-guides/admin")).andExpect(status().isForbidden());
        verifyNoInteractions(userGuideService);
    }

    @Test
    @WithMockUser
    @DisplayName("일반 사용자는 업로드할 수 없다")
    void upload_forbiddenForNonAdmin() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "guide.pdf", "application/pdf", "x".getBytes());

        // X-Requested-With를 붙여 SimpleRequestCsrfFilter가 아니라 @PreAuthorize가 403을 내는지 검증한다.
        mockMvc.perform(
                        multipart("/api/user-guides")
                                .file(file)
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userGuideService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자 업로드는 201을 반환한다")
    void upload_created() throws Exception {
        given(userGuideService.upload(any())).willReturn(response(true));
        MockMultipartFile file =
                new MockMultipartFile("file", "guide.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(
                        multipart("/api/user-guides")
                                .file(file)
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("active 누락은 400이다")
    void setActive_missingActive_badRequest() throws Exception {
        mockMvc.perform(
                        patch("/api/user-guides/{flMpnId}/active", FL_MPN_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 현재 가이드로 지정할 수 있다")
    void setActive_ok() throws Exception {
        given(userGuideService.setActive(anyString(), anyBoolean())).willReturn(response(true));

        mockMvc.perform(
                        patch("/api/user-guides/{flMpnId}/active", FL_MPN_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
