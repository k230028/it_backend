package com.kdb.it.domain.banner.controller;

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
import com.kdb.it.domain.banner.dto.BannerDto;
import com.kdb.it.domain.banner.service.BannerService;
import java.util.List;
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
 * BannerController @WebMvcTest
 *
 * <p>배너 API의 권한 경계와 응답 구조를 검증합니다.
 */
@WebMvcTest(BannerController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    BannerControllerTest.MethodSecurityTestConfig.class
})
class BannerControllerTest {

    /** TestSecurityConfig에는 @EnableMethodSecurity가 없어 @PreAuthorize가 꺼진다. 여기서 켠다. */
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BannerService bannerService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String FL_MPN_ID = "FL-00000001";

    private BannerDto.Response response(boolean active) {
        return BannerDto.Response.builder()
                .flMpnId(FL_MPN_ID)
                .flNm("hero.png")
                .apgFlSz(2048L)
                .active(active)
                .previewUrl("/api/files/" + FL_MPN_ID + "/preview")
                .build();
    }

    @Test
    @DisplayName("GET /api/banners - 비인증 → 401")
    void getActiveBanners_비인증_401() throws Exception {
        mockMvc.perform(get("/api/banners")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/banners - 일반 사용자도 활성 배너를 조회한다")
    void getActiveBanners_일반사용자_200() throws Exception {
        given(bannerService.getActiveBanners()).willReturn(List.of(response(true)));

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flMpnId").value(FL_MPN_ID))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(
                        jsonPath("$[0].previewUrl").value("/api/files/" + FL_MPN_ID + "/preview"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/banners/admin - 일반 사용자 → 403")
    void getAllBanners_일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/banners/admin")).andExpect(status().isForbidden());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/banners/admin - 관리자는 비활성 배너까지 조회한다")
    void getAllBanners_관리자_200() throws Exception {
        given(bannerService.getAllBanners()).willReturn(List.of(response(false)));

        mockMvc.perform(get("/api/banners/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/banners - 일반 사용자 → 403")
    void upload_일반사용자_403() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "hero.png", "image/png", new byte[] {1});

        // X-Requested-With를 붙여 SimpleRequestCsrfFilter가 아니라 @PreAuthorize가 403을 내는지 검증한다.
        mockMvc.perform(
                        multipart("/api/banners")
                                .file(file)
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/banners - 관리자 업로드 → 201")
    void upload_관리자_201() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "hero.png", "image/png", new byte[] {1});
        given(bannerService.upload(any())).willReturn(response(true));

        mockMvc.perform(
                        multipart("/api/banners")
                                .file(file)
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flMpnId").value(FL_MPN_ID));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("PATCH /api/banners/{id}/active - 일반 사용자 → 403")
    void setActive_일반사용자_403() throws Exception {
        mockMvc.perform(
                        patch("/api/banners/" + FL_MPN_ID + "/active")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/banners/{id}/active - 관리자 비활성화 → 200")
    void setActive_관리자_200() throws Exception {
        given(bannerService.setActive(anyString(), anyBoolean())).willReturn(response(false));

        mockMvc.perform(
                        patch("/api/banners/" + FL_MPN_ID + "/active")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/banners/{id}/active - active 누락 → 400")
    void setActive_active누락_400() throws Exception {
        mockMvc.perform(
                        patch("/api/banners/" + FL_MPN_ID + "/active")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/banners/{id}/preview - 일반 사용자 → 403")
    void adminPreview_일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/banners/" + FL_MPN_ID + "/preview"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bannerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/banners/{id}/preview - 배너가 아닌 파일매핑ID → 403")
    void adminPreview_배너아닌파일_403() throws Exception {
        given(bannerService.getAdminPreviewImage(FL_MPN_ID))
                .willThrow(
                        new org.springframework.security.access.AccessDeniedException("배너가 아닙니다."));

        mockMvc.perform(get("/api/banners/" + FL_MPN_ID + "/preview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/banners/{id}/preview - 관리자는 비활성 배너 이미지를 서빙받는다")
    void adminPreview_관리자_200() throws Exception {
        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.ByteArrayResource(new byte[] {1, 2, 3});
        given(bannerService.getAdminPreviewImage(FL_MPN_ID))
                .willReturn(
                        new com.kdb.it.infra.file.service.FileService.FileDownloadResult(
                                resource, "hero.png", "image/png"));

        mockMvc.perform(get("/api/banners/" + FL_MPN_ID + "/preview"))
                .andExpect(status().isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentType(MediaType.IMAGE_PNG));
    }
}
