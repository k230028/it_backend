package com.kdb.it.domain.budget.project.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.service.ProjectDirectoryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectDirectoryController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class ProjectDirectoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProjectDirectoryService projectDirectoryService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("인증된 직원은 전체 사업 검색용 안전한 요약 목록을 조회한다")
    void directory_인증직원_전체요약반환() throws Exception {
        given(projectDirectoryService.findAll())
                .willReturn(
                        List.of(
                                new ProjectDirectoryDto.Response(
                                        "PRJ-OTHER",
                                        "타 부서 디지털 사업",
                                        "Y",
                                        "79",
                                        "사업 추진",
                                        "결재중",
                                        "1",
                                        "리스크관리부",
                                        "10002",
                                        "김팀장",
                                        "10003",
                                        "이담당")));

        mockMvc.perform(get("/api/project-directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abusMngNo").value("PRJ-OTHER"))
                .andExpect(jsonPath("$[0].odnYn").value("Y"))
                .andExpect(jsonPath("$[0].apfSts").value("결재중"))
                .andExpect(jsonPath("$[0].apfStsC").value("1"))
                .andExpect(jsonPath("$[0].stsTcNm").value("사업 추진"))
                .andExpect(jsonPath("$[0].svnDpmCNm").value("리스크관리부"))
                .andExpect(jsonPath("$[0].tyyBgAmt").doesNotExist())
                .andExpect(jsonPath("$[0].abusCone").doesNotExist());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("인증된 직원은 타 부서 사업도 담당자 안내용 안전한 요약으로 조회한다")
    void directoryDetail_인증직원_타부서요약반환() throws Exception {
        given(projectDirectoryService.findOne("PRJ-OTHER"))
                .willReturn(
                        new ProjectDirectoryDto.Response(
                                "PRJ-OTHER",
                                "타 부서 디지털 사업",
                                "Y",
                                "79",
                                "사업 추진",
                                "결재중",
                                "1",
                                "리스크관리부",
                                "10002",
                                "김팀장",
                                "10003",
                                "이담당"));

        mockMvc.perform(get("/api/project-directory/PRJ-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tlrUsid").value("10002"))
                .andExpect(jsonPath("$.usid").value("10003"));
    }

    @Test
    @DisplayName("비인증 사용자는 사업 검색 디렉터리를 조회할 수 없다")
    void directory_비인증_401() throws Exception {
        mockMvc.perform(get("/api/project-directory")).andExpect(status().isUnauthorized());
    }
}
