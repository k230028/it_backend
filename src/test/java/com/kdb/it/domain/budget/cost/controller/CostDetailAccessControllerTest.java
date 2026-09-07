package com.kdb.it.domain.budget.cost.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostQueryAssembler;
import com.kdb.it.domain.budget.cost.service.CostQueryService;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.cost.service.CostVersionService;
import com.kdb.it.domain.budget.cost.service.CostWriteTargetLoader;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.util.List;
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

@WebMvcTest(CostController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    CostService.class,
    CostWriteTargetLoader.class
})
class CostDetailAccessControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CostRepository costRepository;
    @MockitoBean private BtermmRepository btermmRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private OrgNameResolver orgNameResolver;
    @MockitoBean private CodeService codeService;
    @MockitoBean private XcrLookupService xcrLookupService;
    @MockitoBean private CostQueryService costQueryService;
    @MockitoBean private CostQueryAssembler costQueryAssembler;

    @MockitoBean
    private com.kdb.it.domain.budget.common.security.ApprovalWriteGuard approvalWriteGuard;

    /** 작성완료 신청서 스탬프 (Task 6) — 이 테스트는 조회 경로만 검증하므로 스텁 없이 존재만 필요 */
    @MockitoBean private com.kdb.it.common.approval.service.ApprovalStamper approvalStamper;

    /** CostService의 저장 동시성 방어 의존성 (이 슬라이스는 조회만 다룬다) */
    @MockitoBean
    private com.kdb.it.domain.budget.cost.service.CostConcurrencyGuard concurrencyGuard;

    @MockitoBean private CostVersionService costVersionService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        given(costQueryService.getCost("COST-1"))
                .willReturn(
                        CostDto.Response.builder().costBgNo("COST-1").costSvnDpmC("D100").build());
    }

    @Test
    @DisplayName("GET /api/cost/{id}: 다른 부서 일반 사용자는 403을 받는다")
    void 다른부서_일반사용자는_비용상세를_조회할수없다() throws Exception {
        mockMvc.perform(get("/api/cost/COST-1").with(authentication(actor("D200", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/cost/{id}: 같은 부서 사용자는 200을 받는다")
    void 같은부서_사용자는_비용상세를_조회할수있다() throws Exception {
        mockMvc.perform(get("/api/cost/COST-1").with(authentication(actor("D100", false))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"013", "180", "181", "182", "183", "185"})
    @DisplayName("GET /api/cost/{id}: IT 조직 사용자는 다른 부서 비용을 조회할 수 있다")
    void IT조직_사용자는_비용상세를_조회할수있다(String itOrganizationCode) throws Exception {
        mockMvc.perform(
                        get("/api/cost/COST-1")
                                .with(authentication(actor(itOrganizationCode, false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/cost/{id}: 시스템관리자는 다른 부서 비용을 조회할 수 있다")
    void 시스템관리자는_비용상세를_조회할수있다() throws Exception {
        mockMvc.perform(get("/api/cost/COST-1").with(authentication(actor("D200", true))))
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
