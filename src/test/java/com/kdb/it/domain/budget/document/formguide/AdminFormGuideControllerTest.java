package com.kdb.it.domain.budget.document.formguide;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 사업 입력 길라잡이 API의 인가·오류 상태 계약을 검증합니다. */
@WebMvcTest(AdminFormGuideController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    AdminFormGuideControllerTest.MethodSecurityConfig.class
})
class AdminFormGuideControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FormGuideService formGuideService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("관리 카탈로그 조회는 관리자에게 전체 항목을 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getCatalog_관리자_200() throws Exception {
        given(formGuideService.getCatalog(FormGuideScope.INFO))
                .willReturn(
                        List.of(
                                new FormGuideDto.CatalogResponse(
                                        "info.basic.abusNm",
                                        "기본 정보",
                                        "사업명",
                                        "AutoComplete",
                                        "FDOC-2026-0001",
                                        "<p>안내</p>")));

        mockMvc.perform(get("/api/admin/form-guides/catalog").param("scope", "info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docMngNo").value("FDOC-2026-0001"));
    }

    @Test
    @DisplayName("관리 카탈로그 조회는 빈 사업 유형을 400으로 거부한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getCatalog_빈사업유형_400() throws Exception {
        mockMvc.perform(get("/api/admin/form-guides/catalog").param("scope", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리 카탈로그 조회는 지원하지 않는 사업 유형을 400으로 거부한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getCatalog_지원하지않는사업유형_400() throws Exception {
        mockMvc.perform(get("/api/admin/form-guides/catalog").param("scope", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리 길라잡이 저장은 일반 사용자 요청을 403으로 거부한다")
    @WithMockUser(username = "10001")
    void save_일반사용자_403() throws Exception {
        mockMvc.perform(
                        put("/api/admin/form-guides/info.basic.abusNm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new FormGuideDto.SaveRequest("<p>안내</p>"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 저장은 200과 현재 문서관리번호를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void save_관리자_200() throws Exception {
        given(formGuideService.save(any(), any())).willReturn("FDOC-2026-0001");

        mockMvc.perform(
                        put("/api/admin/form-guides/info.basic.abusNm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new FormGuideDto.SaveRequest("<p>안내</p>"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("FDOC-2026-0001"));
    }

    @Test
    @DisplayName("지원하지 않는 길라잡이 ID 저장은 400을 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void save_지원하지않는ID_400() throws Exception {
        given(formGuideService.save(any(), any()))
                .willThrow(new IllegalArgumentException("지원하지 않는 길라잡이 ID입니다"));

        mockMvc.perform(
                        put("/api/admin/form-guides/info.unknown")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new FormGuideDto.SaveRequest("<p>안내</p>"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("동시 중복 등록 충돌은 409를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void save_중복_409() throws Exception {
        given(formGuideService.save(any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 길라잡이입니다"));

        mockMvc.perform(
                        put("/api/admin/form-guides/info.basic.abusNm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new FormGuideDto.SaveRequest("<p>안내</p>"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("관리자 삭제는 204를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_관리자_204() throws Exception {
        mockMvc.perform(delete("/api/admin/form-guides/info.basic.abusNm"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("미등록 길라잡이 삭제는 404를 반환한다")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_미등록_404() throws Exception {
        doThrow(new NotFoundException("등록되지 않은 길라잡이입니다"))
                .when(formGuideService)
                .delete("info.basic.abusNm");

        mockMvc.perform(delete("/api/admin/form-guides/info.basic.abusNm"))
                .andExpect(status().isNotFound());
    }
}
