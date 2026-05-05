package com.kdb.it.common.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.admin.service.AdminService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * AdminController @WebMvcTest
 *
 * <p>
 * ROLE_ADMIN 접근 제어와 HTTP 응답 구조를 검증합니다.
 * 인증 없는 접근은 401, ROLE 미보유 시 403을 반환해야 합니다.
 * </p>
 */
@WebMvcTest(AdminController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // =========================================================================
    // 인증/인가 기본 동작 검증
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/codes - 비인증 요청 → 401 반환")
    void getCodes_비인증_401반환() throws Exception {
        mockMvc.perform(get("/api/admin/codes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/codes - 인증된 사용자 (ADMIN 역할) → 200 + 목록 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getCodes_관리자인증_200반환() throws Exception {
        // given
        given(adminService.getCodes()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/codes"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 공통코드 CRUD
    // =========================================================================

    @Test
    @DisplayName("POST /api/admin/codes - 정상 요청 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createCode_정상요청_201반환() throws Exception {
        // given
        LocalDate sttDt = LocalDate.of(2026, 1, 1);
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "코드명", "값", "설명", "구분", "구분설명", sttDt, null, 1);

        // when & then
        mockMvc.perform(post("/api/admin/codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/admin/codes - 중복 코드ID → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createCode_중복코드ID_400반환() throws Exception {
        // given: 서비스에서 IllegalArgumentException 발생
        doThrow(new IllegalArgumentException("이미 존재하는 코드ID입니다: CODE001"))
                .when(adminService).createCode(any(AdminDto.CodeRequest.class));

        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "코드명", "값", "설명", "구분", "구분설명", LocalDate.of(2026, 1, 1), null, 1);

        // when & then
        mockMvc.perform(post("/api/admin/codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/admin/codes/{cdId} - 정상 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateCode_정상수정_200반환() throws Exception {
        // given
        LocalDate sttDt = LocalDate.of(2026, 1, 1);
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "수정된코드명", "값", "설명", "구분", "구분설명", sttDt, null, 1);

        // when & then
        mockMvc.perform(put("/api/admin/codes/CODE001")
                .param("sttDt", "2026-01-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/admin/codes/{cdId} - 정상 삭제 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteCode_정상삭제_204반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/codes/CODE001")
                .param("sttDt", "2026-01-01"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // 사용자 관리
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/users - 관리자 인증 → 200 + 빈 목록 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getUsers_관리자인증_200반환() throws Exception {
        // given
        given(adminService.getUsers()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{eno} - 미존재 사용자 삭제 → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteUser_미존재사용자_400반환() throws Exception {
        // given
        doThrow(new IllegalArgumentException("존재하지 않는 사원번호입니다: 99999"))
                .when(adminService).deleteUser("99999");

        // when & then
        mockMvc.perform(delete("/api/admin/users/99999"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 자격등급 관리
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/auth-grades - 관리자 인증 → 200 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getAuthGrades_관리자인증_200반환() throws Exception {
        // given
        given(adminService.getAuthGrades()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/auth-grades"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/admin/auth-grades/{athId} - 정상 삭제 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteAuthGrade_정상삭제_204반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/auth-grades/ITPZZ001"))
                .andExpect(status().isNoContent());
    }
}
