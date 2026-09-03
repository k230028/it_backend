package com.kdb.it.domain.budget.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectQueryAssembler;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.project.service.ProjectVersionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    @MockitoBean private ProjectVersionService projectVersionService;
    @MockitoBean private ProjectQueryAssembler projectQueryAssembler;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private UsernamePasswordAuthenticationToken adminAuthentication() {
        CustomUserDetails admin =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        return new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/projects - 인증된 사용자 → 200 + 목록 반환")
    @WithMockUser(username = "10001")
    void getProjects_인증된사용자_200반환() throws Exception {
        // given
        ProjectDto.Response project =
                ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").abusNm("테스트 사업").build();
        given(projectService.searchProjectList(any(ProjectDto.SearchCondition.class), any()))
                .willReturn(List.of(project));

        // when & then
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abusMngNo").value("PRJ-2026-0001"))
                .andExpect(jsonPath("$[0].abusNm").value("테스트 사업"))
                // 페이지를 지정하지 않으면 총건수 헤더도, COUNT 쿼리도 없다
                .andExpect(header().doesNotExist("X-Total-Count"));
        org.mockito.Mockito.verify(projectService, org.mockito.Mockito.never())
                .countProjectList(any());
    }

    @Test
    @DisplayName("GET /api/projects?page=1&size=100 - 페이지 조회는 X-Total-Count로 전체 건수를 알린다")
    @WithMockUser(username = "10001")
    void getProjects_페이지지정_총건수헤더() throws Exception {
        given(projectService.searchProjectList(any(ProjectDto.SearchCondition.class), any()))
                .willReturn(List.of());
        given(projectService.countProjectList(any(ProjectDto.SearchCondition.class)))
                .willReturn(1234L);

        mockMvc.perform(get("/api/projects").param("page", "1").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1234"));
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
        given(projectService.getProject(org.mockito.ArgumentMatchers.eq("PRJ-2026-0001"), any()))
                .willReturn(detail);

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
                ProjectDto.CreateRequest.builder()
                        .abusNm("신규 사업")
                        .abusTc("10")
                        .complete(true)
                        .build();
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
    @DisplayName("POST /api/projects - 사업구분 누락 → 400 (BE-19)")
    @WithMockUser(username = "10001")
    void createProject_사업구분누락_400반환() throws Exception {
        // 검증이 없던 시절에는 누락값이 서비스에서 '0'(해당없음)으로 조용히 보정돼
        // "입력하지 않음"과 "해당없음 선택"이 구분되지 않았다.
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("검증 대상").build();

        mockMvc.perform(
                        post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/projects - 사업구분 '0'(해당없음) → 400 (BE-19)")
    @WithMockUser(username = "10001")
    void createProject_사업구분해당없음_400반환() throws Exception {
        // 공통코드 ABUS_TC에는 '0'이 있지만 사업 도메인의 유효값은 신규('10')·계속('20')뿐이다.
        // @NotBlank만으로는 '0'이 통과하므로 값 집합도 함께 강제한다.
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("검증 대상").abusTc("0").build();

        mockMvc.perform(
                        post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/projects - 필요성 300Byte 초과 → 400, 서비스 미호출")
    @WithMockUser(username = "10001")
    void createProject_필요성300Byte초과_400반환() throws Exception {
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusTc("10")
                        .abusNcsCone("가".repeat(101))
                        .build();

        mockMvc.perform(
                        post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
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
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정 사업")
                        .abusTc("20")
                        .complete(true)
                        .build();
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
    @DisplayName("PUT /api/projects/{prjMngNo}?sno={sno} - 재신청 초안 순번으로 수정 요청을 전달한다")
    @WithMockUser(username = "10001")
    void updateProject_재신청초안순번_200반환() throws Exception {
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("재신청 수정 사업")
                        .abusTc("20")
                        .complete(true)
                        .build();
        given(
                        projectService.updateProject(
                                eq("PRJ-2026-0001"), eq(2), any(ProjectDto.UpdateRequest.class)))
                .willReturn("PRJ-2026-0001");

        mockMvc.perform(
                        put("/api/projects/PRJ-2026-0001")
                                .queryParam("sno", "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("PRJ-2026-0001"));
    }

    @Test
    @DisplayName("PUT /api/projects/{prjMngNo} - 시스템관리자는 사업구분이 없어도 수정 요청을 전달한다")
    void updateProject_관리자_사업구분누락_200반환() throws Exception {
        given(projectService.updateProject(any(String.class), any(ProjectDto.UpdateRequest.class)))
                .willReturn("PRJ-2026-0001");
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("관리자 정정").complete(true).build();

        mockMvc.perform(
                        put("/api/projects/PRJ-2026-0001")
                                .with(authentication(adminAuthentication()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("PRJ-2026-0001"));
    }

    @Test
    @DisplayName("PUT /api/projects/{prjMngNo} - 일반 사용자의 사업구분 0은 400 반환")
    @WithMockUser(username = "10001")
    void updateProject_일반사용자_사업구분해당없음_400반환() throws Exception {
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("검증 대상").abusTc("0").build();

        mockMvc.perform(
                        put("/api/projects/PRJ-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/projects/{prjMngNo} - 시스템관리자는 사업구분 0도 수정 요청을 전달한다")
    void updateProject_관리자_사업구분해당없음_200반환() throws Exception {
        given(projectService.updateProject(any(String.class), any(ProjectDto.UpdateRequest.class)))
                .willReturn("PRJ-2026-0001");
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("관리자 정정")
                        .abusTc("0")
                        .complete(true)
                        .build();

        mockMvc.perform(
                        put("/api/projects/PRJ-2026-0001")
                                .with(authentication(adminAuthentication()))
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
