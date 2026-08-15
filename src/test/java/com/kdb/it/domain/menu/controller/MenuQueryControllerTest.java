package com.kdb.it.domain.menu.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.service.MenuQueryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MenuQueryController @WebMvcTest
 *
 * <p>사용자용 메뉴 트리 조회 API의 HTTP 응답을 검증한다.
 */
@WebMvcTest(MenuQueryController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class MenuQueryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MenuQueryService menuQueryService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    // =========================================================================
    // 인증 검증
    // =========================================================================

    @Test
    @DisplayName("GET /api/menus - 비인증 요청 → 401")
    void getMenus_비인증_401() throws Exception {
        mockMvc.perform(get("/api/menus")).andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/menus
    // =========================================================================

    @Test
    @DisplayName("GET /api/menus - 인증된 일반 사용자 → 200 + 메뉴 목록")
    @WithMockUser(username = "10001", roles = "USER")
    void getMenus_일반사용자_200반환() throws Exception {
        // given
        MenuDto.Node node =
                MenuDto.Node.builder()
                        .mnuId("MNU0000001")
                        .mnuNm("공지사항")
                        .mnuTpC("PGE")
                        .srePth("/board/notice")
                        .mnuDep(1)
                        .children(List.of())
                        .build();
        given(menuQueryService.getMenuTree(anyList(), any())).willReturn(List.of(node));

        // when & then
        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mnuId").value("MNU0000001"))
                .andExpect(jsonPath("$[0].mnuNm").value("공지사항"))
                .andExpect(jsonPath("$[0].srePth").value("/board/notice"));
    }

    @Test
    @DisplayName("GET /api/menus - 관리자 → 200 + 관리자용 메뉴 목록")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getMenus_관리자_200반환() throws Exception {
        // given
        MenuDto.Node adminMenu =
                MenuDto.Node.builder()
                        .mnuId("MNU_ADMIN")
                        .mnuNm("시스템관리")
                        .mnuTpC("GRP")
                        .mnuDep(1)
                        .children(List.of())
                        .build();
        MenuDto.Node userMenu =
                MenuDto.Node.builder()
                        .mnuId("MNU_USER")
                        .mnuNm("공지사항")
                        .mnuTpC("PGE")
                        .mnuDep(1)
                        .children(List.of())
                        .build();
        given(menuQueryService.getMenuTree(anyList(), any()))
                .willReturn(List.of(adminMenu, userMenu));

        // when & then
        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/menus - 권한 없는 경우 빈 목록 반환")
    @WithMockUser(username = "10001", roles = "USER")
    void getMenus_빈목록_200반환() throws Exception {
        // given: 권한 필터링으로 빈 목록
        given(menuQueryService.getMenuTree(anyList(), any())).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/menus - children이 있는 중첩 트리 구조 반환")
    @WithMockUser(username = "10001", roles = "USER")
    void getMenus_중첩트리_200반환() throws Exception {
        // given
        MenuDto.Node child =
                MenuDto.Node.builder()
                        .mnuId("CHILD_1")
                        .mnuNm("예산목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .mnuDep(2)
                        .children(List.of())
                        .build();
        MenuDto.Node parent =
                MenuDto.Node.builder()
                        .mnuId("PARENT_1")
                        .mnuNm("예산관리")
                        .mnuTpC("GRP")
                        .mnuDep(1)
                        .children(List.of(child))
                        .build();
        given(menuQueryService.getMenuTree(anyList(), any())).willReturn(List.of(parent));

        // when & then
        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mnuId").value("PARENT_1"))
                .andExpect(jsonPath("$[0].children[0].mnuId").value("CHILD_1"));
    }

    @Test
    @DisplayName("GET /api/menus - CustomUserDetails 주체로 인증 시 athIds를 서비스에 전달한다")
    void getMenus_CustomUserDetails주체_athIds전달() throws Exception {
        // given: CustomUserDetails를 principal로 사용하는 인증 토큰
        CustomUserDetails userDetails =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

        MenuDto.Node node =
                MenuDto.Node.builder()
                        .mnuId("MNU_ADMIN")
                        .mnuNm("시스템관리")
                        .mnuTpC("GRP")
                        .mnuDep(1)
                        .children(List.of())
                        .build();
        given(menuQueryService.getMenuTree(eq(List.of(CustomUserDetails.ATH_ADMIN)), any()))
                .willReturn(List.of(node));

        // when & then
        mockMvc.perform(
                        get("/api/menus")
                                .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mnuId").value("MNU_ADMIN"));
    }
}
