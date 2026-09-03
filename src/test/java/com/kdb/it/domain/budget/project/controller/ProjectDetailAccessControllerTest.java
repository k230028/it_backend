package com.kdb.it.domain.budget.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.budget.project.service.ProjectBudgetSummaryService;
import com.kdb.it.domain.budget.project.service.ProjectQueryAssembler;
import com.kdb.it.domain.budget.project.service.ProjectQueryService;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.project.service.ProjectVersionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    ProjectService.class,
    ProjectQueryService.class
})
class ProjectDetailAccessControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private ApplicationMapRepository applicationMapRepository;
    @MockitoBean private ProjectItemRepository projectItemRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private OrgNameResolver orgNameResolver;
    @MockitoBean private CodeService codeService;
    @MockitoBean private XcrLookupService xcrLookupService;
    @MockitoBean private BprojaSyncService bprojaSyncService;
    @MockitoBean private ProjectBudgetSummaryService projectBudgetSummaryService;
    @MockitoBean private ProjectQueryAssembler projectQueryAssembler;
    @MockitoBean private ProjectVersionService projectVersionService;

    /** 작성완료 신청서 스탬프 (Task 6) — 이 테스트는 조회 경로만 검증하므로 스텁 없이 존재만 필요 */
    @MockitoBean private com.kdb.it.common.approval.service.ApprovalStamper approvalStamper;

    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-1", "N"))
                .willReturn(
                        Optional.of(
                                Bprojm.builder()
                                        .abusMngNo("PRJ-1")
                                        .sno(1)
                                        .svnDpmC("D100")
                                        .build()));
        given(projectQueryAssembler.assembleDetail(any(Bprojm.class)))
                .willReturn(ProjectDto.Response.builder().abusMngNo("PRJ-1").build());
    }

    @Test
    @DisplayName("GET /api/projects/{id}: 다른 부서 일반 사용자는 403을 받는다")
    void 다른부서_일반사용자는_프로젝트상세를_조회할수없다() throws Exception {
        mockMvc.perform(get("/api/projects/PRJ-1").with(authentication(actor("D200", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/projects/{id}: 같은 부서 사용자는 200을 받는다")
    void 같은부서_사용자는_프로젝트상세를_조회할수있다() throws Exception {
        mockMvc.perform(get("/api/projects/PRJ-1").with(authentication(actor("D100", false))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"013", "180", "181", "182", "183", "185"})
    @DisplayName("GET /api/projects/{id}: IT 조직 사용자는 다른 부서 사업을 조회할 수 있다")
    void IT조직_사용자는_프로젝트상세를_조회할수있다(String itOrganizationCode) throws Exception {
        mockMvc.perform(
                        get("/api/projects/PRJ-1")
                                .with(authentication(actor(itOrganizationCode, false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/projects/{id}: 시스템관리자는 다른 부서 사업을 조회할 수 있다")
    void 시스템관리자는_프로젝트상세를_조회할수있다() throws Exception {
        mockMvc.perform(get("/api/projects/PRJ-1").with(authentication(actor("D200", true))))
                .andExpect(status().isOk());
    }

    private static UsernamePasswordAuthenticationToken actor(String bbrC, boolean admin) {
        CustomUserDetails user =
                new CustomUserDetails(
                        "USER",
                        List.of(admin ? CustomUserDetails.ATH_ADMIN : CustomUserDetails.ATH_USER),
                        bbrC);
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
