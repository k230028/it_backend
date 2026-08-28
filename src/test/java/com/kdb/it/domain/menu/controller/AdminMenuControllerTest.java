package com.kdb.it.domain.menu.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.service.AdminMenuService;
import com.kdb.it.domain.menu.service.MenuQueryService;
import java.util.List;
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

@WebMvcTest(AdminMenuController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class AdminMenuControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AdminMenuService adminMenuService;

    @MockitoBean private MenuQueryService menuQueryService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    // =========================================================================
    // 인증 검증
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/menus - 비인증 요청 → 401")
    void getAll_비인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/menus")).andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/admin/menus
    // =========================================================================

    @Test
    @DisplayName("GET /api/admin/menus - 인증된 관리자 → 200 + 메뉴 트리 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getAll_관리자인증_200반환() throws Exception {
        // given
        MenuDto.Node node =
                MenuDto.Node.builder()
                        .mnuId("MNU0000001")
                        .mnuNm("예산관리")
                        .mnuTpC("GRP")
                        .mnuDep(1)
                        .children(List.of())
                        .build();
        given(menuQueryService.getAdminMenuTree()).willReturn(List.of(node));

        // when & then
        mockMvc.perform(get("/api/admin/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mnuId").value("MNU0000001"))
                .andExpect(jsonPath("$[0].mnuNm").value("예산관리"));
    }

    @Test
    @DisplayName("GET /api/admin/menus - 빈 트리 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getAll_빈트리_200반환() throws Exception {
        // given
        given(menuQueryService.getAdminMenuTree()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // POST /api/admin/menus
    // =========================================================================

    @Test
    @DisplayName("POST /api/admin/menus - 정상 요청 → 201 Created + Location 헤더")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_정상요청_201반환() throws Exception {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("새메뉴").mnuTpC("GRP").build();
        given(adminMenuService.create(any())).willReturn("MNU0000001");

        // when & then
        mockMvc.perform(
                        post("/api/admin/menus")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("MNU0000001")));
    }

    @Test
    @DisplayName("POST /api/admin/menus - mnuNm 누락 → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_mnuNm누락_400반환() throws Exception {
        // given: @NotBlank 위반 (빈 문자열)
        MenuDto.UpsertRequest req = MenuDto.UpsertRequest.builder().mnuNm("").mnuTpC("GRP").build();

        // when & then
        mockMvc.perform(
                        post("/api/admin/menus")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/admin/menus/{mnuId}
    // =========================================================================

    @Test
    @DisplayName("PUT /api/admin/menus/{mnuId} - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void update_정상요청_204반환() throws Exception {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("수정메뉴").mnuTpC("GRP").build();

        // when & then
        mockMvc.perform(
                        put("/api/admin/menus/MNU0000001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /api/admin/menus/{mnuId} - 존재하지 않는 메뉴 → 404")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void update_존재하지않는메뉴_404반환() throws Exception {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("수정메뉴").mnuTpC("GRP").build();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 메뉴"))
                .when(adminMenuService)
                .update(eq("NONE"), any());

        // when & then
        mockMvc.perform(
                        put("/api/admin/menus/NONE")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/admin/menus/{mnuId}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/admin/menus/{mnuId} - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_정상요청_204반환() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/menus/MNU0000001")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/admin/menus/{mnuId} - 하위 메뉴 존재 → 409")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void delete_하위메뉴존재_409반환() throws Exception {
        // given
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "하위 메뉴가 있어 삭제할 수 없습니다."))
                .when(adminMenuService)
                .delete("MNU_PARENT");

        // when & then
        mockMvc.perform(delete("/api/admin/menus/MNU_PARENT")).andExpect(status().isConflict());
    }

    // =========================================================================
    // PATCH /api/admin/menus/reorder
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/admin/menus/reorder - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void reorder_정상요청_204반환() throws Exception {
        // given
        MenuDto.ReorderRequest req =
                MenuDto.ReorderRequest.builder()
                        .orderedMnuIds(List.of("MNU0000001", "MNU0000002"))
                        .build();

        // when & then
        mockMvc.perform(
                        patch("/api/admin/menus/reorder")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // PATCH /api/admin/menus/{mnuId}/move
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/admin/menus/{mnuId}/move - 정상 요청 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void move_정상요청_204반환() throws Exception {
        // given
        MenuDto.MoveRequest req = MenuDto.MoveRequest.builder().newHrkMnuId("MNU_PARENT").build();

        // when & then
        mockMvc.perform(
                        patch("/api/admin/menus/MNU0000001/move")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/admin/menus/{mnuId}/move - 순환 참조 → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void move_순환참조_400반환() throws Exception {
        // given
        MenuDto.MoveRequest req = MenuDto.MoveRequest.builder().newHrkMnuId("MNU_CHILD").build();
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "순환 참조"))
                .when(adminMenuService)
                .move(eq("MNU0000001"), eq("MNU_CHILD"));

        // when & then
        mockMvc.perform(
                        patch("/api/admin/menus/MNU0000001/move")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── 메뉴 관리 서비스 부재 방어 가드 ──────────────────────────────
    // 스프링이 주입하는 경로에서는 서비스가 비어 있을 수 없어 MockMvc로는 이 분기에 닿지 않는다.
    // 컨트롤러를 직접 생성해 가드가 살아 있는지만 고정한다.

    @Test
    @DisplayName("update - 메뉴 관리 서비스가 없으면 IllegalStateException")
    void update_서비스없음_예외() {
        // given
        AdminMenuController controller = new AdminMenuController(null, menuQueryService);
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("새메뉴").mnuTpC("GRP").build();

        // when & then
        assertThatThrownBy(() -> controller.update("MNU0000001", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("메뉴 관리 서비스");
    }

    @Test
    @DisplayName("delete - 메뉴 관리 서비스가 없으면 IllegalStateException")
    void delete_서비스없음_예외() {
        // given
        AdminMenuController controller = new AdminMenuController(null, menuQueryService);

        // when & then
        assertThatThrownBy(() -> controller.delete("MNU0000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("메뉴 관리 서비스");
    }
}
