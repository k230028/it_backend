package com.kdb.it.common.iam.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.service.OrganizationService;
import com.kdb.it.common.iam.service.UserService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * OrganizationController + UserController @WebMvcTest
 *
 * <p>조직/사용자 조회 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest({ OrganizationController.class, UserController.class })
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class IamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // -------------------------------------------------------------------------
    // OrganizationController
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/organizations - 비인증 → 401")
    void getOrganizations_비인증_401() throws Exception {
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/organizations - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getOrganizations_인증_200() throws Exception {
        given(organizationService.getOrganizations()).willReturn(List.of());
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // -------------------------------------------------------------------------
    // UserController
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/users - 비인증 → 401")
    void getUsers_비인증_401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getUsers_인증_200() throws Exception {
        given(userService.getUsersByOrganization(anyString())).willReturn(List.of());
        mockMvc.perform(get("/api/users").param("orgCode", "IT001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/users/{eno} - 인증된 사용자 → 200")
    void getUserDetail_인증_200() throws Exception {
        // 본인/관리자 권한이 서비스 계층(OwnershipVerifier)에서 검증되므로
        // CustomUserDetails 주체로 요청하고 서비스 스텁은 any()로 매칭한다.
        given(userService.getUser(anyString(), any(CustomUserDetails.class)))
                .willReturn(new UserDto.DetailResponse());
        mockMvc.perform(get("/api/users/E10001")
                        .with(user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "D001"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/search - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void searchUsers_인증_200() throws Exception {
        given(userService.searchUsersByName(anyString(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/users/search").param("keyword", "홍"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
