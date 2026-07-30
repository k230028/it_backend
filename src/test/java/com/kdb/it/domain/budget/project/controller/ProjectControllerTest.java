package com.kdb.it.domain.budget.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
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

/**
 * ProjectController @WebMvcTest
 *
 * <p>Spring Security 인증/인가 동작과 HTTP 응답 구조를 검증합니다. 인증 필요 엔드포인트는 @WithMockUser로 처리합니다.
 */
@WebMvcTest(ProjectController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProjectService projectService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/projects - 인증된 사용자 → 200 + 목록 반환")
    @WithMockUser(username = "10001")
    void getProjects_인증된사용자_200반환() throws Exception {
        // given
        ProjectDto.Response project =
                ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").abusNm("테스트 사업").build();
        given(projectService.searchProjectList(any(ProjectDto.SearchCondition.class)))
                .willReturn(List.of(project));

        // when & then
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abusMngNo").value("PRJ-2026-0001"))
                .andExpect(jsonPath("$[0].abusNm").value("테스트 사업"));
    }

    @Test
    @DisplayName("GET /api/projects - 비인증 요청 → 401 반환")
    void getProjects_비인증_401반환() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/projects/{prjMngNo} - 존재하는 프로젝트 → 200 + 상세 정보")
    @WithMockUser(username = "10001")
    void getProject_존재하는프로젝트_200반환() throws Exception {
        // given
        ProjectDto.Response detail =
                ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").abusNm("테스트 사업").build();
        given(projectService.getProject("PRJ-2026-0001")).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/projects/PRJ-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abusMngNo").value("PRJ-2026-0001"));
    }

    @Test
    @DisplayName("POST /api/projects - 인증된 사용자 → 201 + Location 헤더")
    @WithMockUser(username = "10001")
    void createProject_성공_201반환() throws Exception {
        // given
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("신규 사업").build();
        given(projectService.createProject(any(ProjectDto.CreateRequest.class)))
                .willReturn("PRJ-2026-0001");

        // when & then
        mockMvc.perform(
                        post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/PRJ-2026-0001"))
                .andExpect(content().string("PRJ-2026-0001"));
    }

    @Test
    @DisplayName("DELETE /api/projects/{prjMngNo} - 결재중 프로젝트 삭제 → 500 반환")
    @WithMockUser(username = "10001")
    void deleteProject_결재중_500반환() throws Exception {
        // given
        doThrow(new IllegalStateException("결재중이거나 결재완료된 프로젝트는 삭제할 수 없습니다."))
                .when(projectService)
                .deleteProject("PRJ-2026-0001");

        // when & then
        mockMvc.perform(delete("/api/projects/PRJ-2026-0001")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/projects/{prjMngNo} - 인증된 사용자 → 200 + 수정된 관리번호")
    @WithMockUser(username = "10001")
    void updateProject_성공_200반환() throws Exception {
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정 사업").build();
        given(projectService.updateProject(any(String.class), any(ProjectDto.UpdateRequest.class)))
                .willReturn("PRJ-2026-0001");

        mockMvc.perform(
                        put("/api/projects/PRJ-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("PRJ-2026-0001"));
    }

    @Test
    @DisplayName("DELETE /api/projects/{prjMngNo} - 삭제 가능 프로젝트 → 204 반환")
    @WithMockUser(username = "10001")
    void deleteProject_성공_204반환() throws Exception {
        mockMvc.perform(delete("/api/projects/PRJ-2026-0001")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/projects/bulk-get - 관리번호 목록 → 200 + 존재하는 프로젝트 목록")
    @WithMockUser(username = "10001")
    void bulkGetProjects_성공_200반환() throws Exception {
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("PRJ-2026-0001", "PRJ-2026-0002"));
        ProjectDto.Response project =
                ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").abusNm("테스트 사업").build();
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(
                        new ProjectDto.BulkResponse(List.of(project), List.of("PRJ-2026-0002")));

        mockMvc.perform(
                        post("/api/projects/bulk-get")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].abusMngNo").value("PRJ-2026-0001"))
                .andExpect(jsonPath("$.failedIds[0]").value("PRJ-2026-0002"));
    }

    @Test
    @DisplayName("POST /api/projects/bulk-get - 빈 목록은 400이고 서비스를 호출하지 않는다")
    @WithMockUser(username = "10001")
    void bulkGetProjects_빈목록_400반환() throws Exception {
        mockMvc.perform(
                        post("/api/projects/bulk-get")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prjMngNos\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("POST /api/projects/bulk-get - 공백 관리번호는 400을 반환한다")
    @WithMockUser(username = "10001")
    void bulkGetProjects_공백관리번호_400반환() throws Exception {
        mockMvc.perform(
                        post("/api/projects/bulk-get")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prjMngNos\":[\" \"]}"))
                .andExpect(status().isBadRequest());
    }
}
