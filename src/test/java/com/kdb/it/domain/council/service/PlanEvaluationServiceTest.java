package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.PlanEvaluationRepository;

import jakarta.persistence.EntityManager;

/**
 * PlanEvaluationService 단위 테스트 (dbrTc='02' 사업별 적정/유보)
 *
 * <p>사업별 판정 집계("1명이라도 유보(N)면 유보")와 저장 검증(위원 권한·사유 필수)을 확인합니다.
 * Basctm·Bplevm·Bcmmtm·CuserI는 Mockito.mock()으로 생성하며 Oracle DB 없이 실행됩니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanEvaluationServiceTest {

    @Mock
    private PlanEvaluationRepository planEvaluationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CouncilService councilService;
    @Mock
    private CommitteeRepository committeeRepository;
    @Mock
    private PlanService planService;
    @Mock
    private ProjectService projectService;
    @Mock
    private com.kdb.it.domain.council.repository.CouncilRepository councilRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PlanEvaluationService planEvaluationService;

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(planEvaluationService, "entityManager", entityManager);
    }

    private static final String ASCT_ID = "ASCT-2026-0001";

    private Bplevm mockEval(String eno, String abusMngNo, String adqYn) {
        Bplevm e = mock(Bplevm.class);
        given(e.getEno()).willReturn(eno);
        given(e.getAbusMngNo()).willReturn(abusMngNo);
        given(e.getAdqYn()).willReturn(adqYn);
        given(e.getEvalOpnn()).willReturn("의견");
        return e;
    }

    private CuserI mockUser(String eno, String nm) {
        CuserI u = mock(CuserI.class);
        given(u.getEno()).willReturn(eno);
        given(u.getUsrNm()).willReturn(nm);
        return u;
    }

    @Test
    @DisplayName("getAllEvaluations: 사업별 위원 중 1명이라도 유보(N)면 최종 판정은 유보, 전원 적정이면 적정")
    void aggregate_oneReserve_verdictReserve() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        // mockEval/mockUser 내부에도 given()이 있으므로 변수에 먼저 생성 후 willReturn에 전달(중첩 스터빙 회피)
        // PRJ-A: 위원 2명 모두 적정(Y) → 최종 Y / PRJ-B: 위원 1명 유보(N) → 최종 N
        Bplevm aE1 = mockEval("E1", "PRJ-A", "Y");
        Bplevm aE2 = mockEval("E2", "PRJ-A", "Y");
        Bplevm bE1 = mockEval("E1", "PRJ-B", "Y");
        Bplevm bE2 = mockEval("E2", "PRJ-B", "N");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(aE1, aE2, bE1, bE2));
        CuserI u1 = mockUser("E1", "홍길동");
        CuserI u2 = mockUser("E2", "김철수");
        given(userRepository.findByEnoIn(anyCollection())).willReturn(List.of(u1, u2));

        CouncilDto.PlanEvaluationSummaryResponse res = planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(res.evaluations()).hasSize(4);
        var a = res.verdicts().stream().filter(v -> "PRJ-A".equals(v.abusMngNo())).findFirst().orElseThrow();
        var b = res.verdicts().stream().filter(v -> "PRJ-B".equals(v.abusMngNo())).findFirst().orElseThrow();
        assertThat(a.finalAdqYn()).isEqualTo("Y");
        assertThat(a.reserveCount()).isEqualTo(0L);
        assertThat(b.finalAdqYn()).isEqualTo("N");
        assertThat(b.reserveCount()).isEqualTo(1L);
        assertThat(b.evaluatorCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("saveEvaluation: 해당 협의회 평가위원이 아니면 AccessDeniedException")
    void save_notMember_denied() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E9", "N")).willReturn(Optional.empty());
        CustomUserDetails user = new CustomUserDetails("E9", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req = new CouncilDto.PlanEvaluationRequest(
                List.of(new CouncilDto.PlanEvaluationItem("PRJ-A", "Y", "의견")));

        assertThatThrownBy(() -> planEvaluationService.saveEvaluation(ASCT_ID, req, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("saveEvaluation: 사유(evalOpnn) 미작성이면 IllegalArgumentException")
    void save_blankOpinion_rejected() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N")).willReturn(List.<Bplevm>of());
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req = new CouncilDto.PlanEvaluationRequest(
                List.of(new CouncilDto.PlanEvaluationItem("PRJ-A", "Y", "   ")));

        assertThatThrownBy(() -> planEvaluationService.saveEvaluation(ASCT_ID, req, user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("saveEvaluation: 적정여부가 Y/N이 아니면 IllegalArgumentException")
    void save_invalidAdqYn_rejected() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N")).willReturn(List.<Bplevm>of());
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req = new CouncilDto.PlanEvaluationRequest(
                List.of(new CouncilDto.PlanEvaluationItem("PRJ-A", "X", "의견")));

        assertThatThrownBy(() -> planEvaluationService.saveEvaluation(ASCT_ID, req, user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildResultSummary: 사업별 판정 표(HTML) 생성 — 유보 사업은 '유보', 사업명은 스냅샷에서 해석")
    void buildResultSummary_rendersTable() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn("PLN-2026-0001");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // PRJ-A: E1 적정 / E2 유보 → 최종 유보
        Bplevm e1 = mockEval("E1", "PRJ-A", "Y");
        Bplevm e2 = mockEval("E2", "PRJ-A", "N");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(e1, e2));

        PlanDto.DetailResponse plan = PlanDto.DetailResponse.builder()
                .redtConeInf("{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-A\",\"abusNm\":\"클라우드 전환\"}]}")
                .build();
        given(planService.getPlan("PLN-2026-0001")).willReturn(plan);

        CouncilDto.PlanResultSummaryResponse res = planEvaluationService.buildResultSummary(ASCT_ID);

        assertThat(res.verdicts()).hasSize(1);
        assertThat(res.verdicts().get(0).finalAdqYn()).isEqualTo("N");
        assertThat(res.summaryHtml())
                .contains("<table>")           // 표 구조
                .contains("클라우드 전환")      // 스냅샷에서 사업명 해석
                .contains("유보");             // 최종 판정
    }
}
