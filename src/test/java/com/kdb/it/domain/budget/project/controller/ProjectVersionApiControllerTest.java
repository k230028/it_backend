package com.kdb.it.domain.budget.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.service.ProjectQueryAssembler;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.project.service.ProjectVersionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class ProjectVersionApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProjectService projectService;
    @MockitoBean private ProjectVersionService projectVersionService;
    @MockitoBean private ProjectQueryAssembler projectQueryAssembler;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private final CustomUserDetails user =
            new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D100");

    @BeforeEach
    void setUp() {
        given(projectQueryAssembler.assembleDetail(any(Bprojm.class)))
                .willAnswer(
                        invocation -> {
                            Bprojm project = invocation.getArgument(0);
                            return ProjectDto.Response.builder()
                                    .abusMngNo(project.getAbusMngNo())
                                    .sno(project.getSno())
                                    .lstYn(project.getLstYn())
                                    .build();
                        });
    }

    @Test
    @DisplayName("GET /api/projects/{id}/history는 인증된 작성부서 사용자에게 순번 이력을 반환한다")
    void 프로젝트이력_조회() throws Exception {
        given(projectVersionService.findHistory(eq("PRJ-1"), any()))
                .willReturn(
                        List.of(
                                Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("N").build(),
                                Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("Y").build()));

        mockMvc.perform(get("/api/projects/PRJ-1/history").with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sno").value(1))
                .andExpect(jsonPath("$[0].lstYn").value("N"))
                .andExpect(jsonPath("$[1].sno").value(2))
                .andExpect(jsonPath("$[1].lstYn").value("Y"));
    }

    @Test
    @DisplayName("GET /api/projects/{id}/versions/{sno}는 명시 개정본 상세를 반환한다")
    void 프로젝트명시개정본_조회() throws Exception {
        given(projectVersionService.findVersion(eq("PRJ-1"), eq(2), any()))
                .willReturn(Optional.of(Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("N").build()));

        mockMvc.perform(get("/api/projects/PRJ-1/versions/2").with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abusMngNo").value("PRJ-1"))
                .andExpect(jsonPath("$.sno").value(2))
                .andExpect(jsonPath("$.lstYn").value("N"));
    }

    @Test
    @DisplayName("POST /api/projects/{id}/reapplications는 새 비최종 초안을 생성한다")
    void 프로젝트재신청_생성() throws Exception {
        given(projectVersionService.createReapplication(eq("PRJ-1"), any()))
                .willReturn(new ProjectVersionService.ProjectVersion("PRJ-1", 2, "N"));

        mockMvc.perform(post("/api/projects/PRJ-1/reapplications").with(authentication(userAuthentication())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/PRJ-1/versions/2"))
                .andExpect(jsonPath("$.abusMngNo").value("PRJ-1"))
                .andExpect(jsonPath("$.sno").value(2))
                .andExpect(jsonPath("$.lstYn").value("N"));
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
