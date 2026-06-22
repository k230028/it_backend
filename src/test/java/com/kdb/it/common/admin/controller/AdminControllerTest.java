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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.admin.service.AdminLogService;
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
    private AdminLogService adminLogService;
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
        String sttDt = "20260101";
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt, null, 1);

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
                "CODE001", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, "20260101", null, 1);

        // when & then
        mockMvc.perform(post("/api/admin/codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/admin/codes/{cId}/{cdva} - 정상 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateCode_정상수정_200반환() throws Exception {
        // given
        String sttDt = "20260101";
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "001", "수정된코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt, null, 1);

        // when & then
        mockMvc.perform(put("/api/admin/codes/CODE001/001")
                .param("sttDt", "20260101")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/admin/codes/{cId}/{cdva} - 정상 삭제 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteCode_정상삭제_204반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/codes/CODE001/001")
                .param("sttDt", "20260101"))
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
        mockMvc.perform(delete("/api/admin/auth-grades/ITPZZ001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/admin/codes/bulk - 일괄 업로드 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void bulkUpsertCodes_정상요청_200반환() throws Exception {
        given(adminService.bulkUpsertCodes(any())).willReturn(Map.of("created", 0, "updated", 0));
        mockMvc.perform(post("/api/admin/codes/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codes\":[]}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 자격등급 추가/수정
    // =========================================================================

    @Test
    @DisplayName("POST /api/admin/auth-grades - 자격등급 추가 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createAuthGrade_정상요청_201반환() throws Exception {
        mockMvc.perform(post("/api/admin/auth-grades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"athId\":\"ITPZZ001\",\"qlfGrNm\":\"일반\",\"qlfGrMat\":null,\"useYn\":\"Y\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/admin/auth-grades/{athId} - 자격등급 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateAuthGrade_정상수정_200반환() throws Exception {
        mockMvc.perform(put("/api/admin/auth-grades/ITPZZ001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"athId\":\"ITPZZ001\",\"qlfGrNm\":\"일반수정\",\"qlfGrMat\":null,\"useYn\":\"Y\"}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 역할 관리
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/roles - 역할 목록 조회 → 200 + 배열 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getRoles_관리자인증_200반환() throws Exception {
        given(adminService.getRoles()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/admin/roles - 역할 추가 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createRole_정상요청_201반환() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"athId\":\"ITPZZ001\",\"eno\":\"10001\",\"useYn\":\"Y\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/admin/roles/{athId}/{eno} - 역할 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateRole_정상수정_200반환() throws Exception {
        mockMvc.perform(put("/api/admin/roles/ITPZZ001/10001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"athId\":\"ITPZZ001\",\"eno\":\"10001\",\"useYn\":\"N\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/admin/roles/{athId}/{eno} - 역할 삭제 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteRole_정상삭제_204반환() throws Exception {
        mockMvc.perform(delete("/api/admin/roles/ITPZZ001/10001"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // 사용자 생성/수정
    // =========================================================================

    @Test
    @DisplayName("POST /api/admin/users - 사용자 추가 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createUser_정상요청_201반환() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eno\":\"10002\",\"usrNm\":\"홍길동\",\"bbrC\":\"IT001\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/admin/users/{eno} - 사용자 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateUser_정상수정_200반환() throws Exception {
        mockMvc.perform(put("/api/admin/users/10001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eno\":\"10001\",\"usrNm\":\"수정된이름\",\"bbrC\":\"IT001\"}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 조직 관리
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/organizations - 조직 목록 조회 → 200 + 배열 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getOrganizations_관리자인증_200반환() throws Exception {
        given(adminService.getOrganizations()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/admin/organizations - 조직 추가 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createOrganization_정상요청_201반환() throws Exception {
        mockMvc.perform(post("/api/admin/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prlmOgzCCone\":\"IT001\",\"bbrNm\":\"IT부서\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/admin/organizations/{orgC} - 조직 수정 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateOrganization_정상수정_200반환() throws Exception {
        mockMvc.perform(put("/api/admin/organizations/IT001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prlmOgzCCone\":\"IT001\",\"bbrNm\":\"IT부서수정\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/admin/organizations/{orgC} - 조직 삭제 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteOrganization_정상삭제_204반환() throws Exception {
        mockMvc.perform(delete("/api/admin/organizations/IT001"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // 로그인 이력 / 토큰 / 파일 / 통계
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/login-history - 로그인 이력 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getLoginHistory_관리자인증_200반환() throws Exception {
        given(adminService.getLoginHistory(any())).willReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/admin/login-history"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/tokens - JWT 토큰 목록 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getTokens_관리자인증_200반환() throws Exception {
        given(adminService.getTokens()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/tokens"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/files - 첨부파일 목록 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getFiles_관리자인증_200반환() throws Exception {
        given(adminService.getFiles()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/files"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/dashboard/login-stats - 로그인 통계 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getLoginStats_관리자인증_200반환() throws Exception {
        given(adminService.getLoginStats()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/dashboard/login-stats"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 상세 로그
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/logs/tables - 로그 테이블 목록 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getLogTables_관리자인증_200반환() throws Exception {
        given(adminLogService.getTables()).willReturn(List.of());
        mockMvc.perform(get("/api/admin/logs/tables"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/logs/{logKey} - 로그 목록 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getLogs_관리자인증_200반환() throws Exception {
        given(adminLogService.getLogs(any(), any())).willReturn(null);
        mockMvc.perform(get("/api/admin/logs/BPROJTM"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/logs/{logKey}/{logSno} - 로그 단건 조회 → 200 OK")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getLogDetail_관리자인증_200반환() throws Exception {
        given(adminLogService.getLogDetail(any(), any())).willReturn(null);
        mockMvc.perform(get("/api/admin/logs/BPROJTM/LOG001"))
                .andExpect(status().isOk());
    }
}
