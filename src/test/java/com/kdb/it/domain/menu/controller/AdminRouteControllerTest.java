package com.kdb.it.domain.menu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.service.AdminRouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminRouteController @WebMvcTest
 *
 * <p>라우트 카탈로그 CRUD API의 HTTP 응답을 검증한다.</p>
 */
@WebMvcTest(AdminRouteController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class AdminRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminRouteService adminRouteService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    /** 테스트용 Cmenud 엔티티 생성 헬퍼. */
    private Cmenud route(String srePth, String sreMnuNm) {
        return Cmenud.builder()
                .srePth(srePth).sreMnuNm(sreMnuNm)
                .useYn("Y").delYn("N")
                .build();
    }

    // =========================================================================
    // 인증 검증
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/routes - 비인증 요청 → 401")
    void usable_비인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/routes"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/admin/routes
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/routes - 인증된 관리자 → 200 + 사용 가능 라우트 목록")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void usable_관리자인증_200반환() throws Exception {
        // given
        given(adminRouteService.listUsable()).willReturn(List.of(
                route("/budget/list", "예산목록"),
                route("/project/list", "사업목록")
        ));

        // when & then
        mockMvc.perform(get("/api/admin/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].srePth").value("/budget/list"))
                .andExpect(jsonPath("$[1].srePth").value("/project/list"));
    }

    @Test
    @DisplayName("GET /api/admin/routes - 빈 목록 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void usable_빈목록_200반환() throws Exception {
        // given
        given(adminRouteService.listUsable()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // GET /api/admin/routes/all
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/routes/all - 인증된 관리자 → 200 + 전체 라우트 목록")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void all_관리자인증_200반환() throws Exception {
        // given
        given(adminRouteService.listAll()).willReturn(List.of(route("/budget/list", "예산목록")));

        // when & then
        mockMvc.perform(get("/api/admin/routes/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].srePth").value("/budget/list"));
    }

    // =========================================================================
    // POST /api/admin/routes
    // =========================================================================

    @Test
    @DisplayName("POST /api/admin/routes - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_정상요청_204반환() throws Exception {
        // given
        MenuDto.Route req = MenuDto.Route.builder()
                .srePth("/new/route").sreMnuNm("새화면").useYn("Y")
                .build();

        // when & then
        mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/admin/routes - srePth 누락 → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_srePth누락_400반환() throws Exception {
        // given: @NotBlank 위반
        MenuDto.Route req = MenuDto.Route.builder()
                .srePth("").sreMnuNm("새화면").build();

        // when & then
        mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/routes - 중복 경로 → 409")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_중복경로_409반환() throws Exception {
        // given
        MenuDto.Route req = MenuDto.Route.builder()
                .srePth("/dup/route").sreMnuNm("중복화면").build();
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "중복된 화면경로"))
                .when(adminRouteService).create(any());

        // when & then
        mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // PUT /api/admin/routes
    // =========================================================================

    @Test
    @DisplayName("PUT /api/admin/routes - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void update_정상요청_204반환() throws Exception {
        // given
        MenuDto.Route req = MenuDto.Route.builder()
                .srePth("/budget/list").sreMnuNm("예산목록(수정)").useYn("Y")
                .build();

        // when & then
        mockMvc.perform(put("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /api/admin/routes - 존재하지 않는 경로 → 404")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void update_존재하지않는경로_404반환() throws Exception {
        // given
        MenuDto.Route req = MenuDto.Route.builder()
                .srePth("/no/route").sreMnuNm("없는화면").build();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 경로"))
                .when(adminRouteService).update(any());

        // when & then
        mockMvc.perform(put("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/admin/routes
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/admin/routes?srePth=xxx - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_정상요청_204반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/routes")
                        .param("srePth", "/budget/list"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/admin/routes?srePth=xxx - 메뉴 참조 중 → 409")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_메뉴참조중_409반환() throws Exception {
        // given
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "메뉴에서 참조 중인 경로"))
                .when(adminRouteService).delete("/in-use");

        // when & then
        mockMvc.perform(delete("/api/admin/routes")
                        .param("srePth", "/in-use"))
                .andExpect(status().isConflict());
    }
}
