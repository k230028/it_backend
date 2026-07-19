package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
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
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
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
    private CouncilRepository councilRepository;
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
        return mockEval(eno, abusMngNo, adqYn, "의견");
    }

    private Bplevm mockEval(String eno, String abusMngNo, String adqYn, String opinion) {
        Bplevm e = mock(Bplevm.class);
        given(e.getEno()).willReturn(eno);
        given(e.getAbusMngNo()).willReturn(abusMngNo);
        given(e.getAdqYn()).willReturn(adqYn);
        given(e.getEvalOpnn()).willReturn(opinion);
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
        given(userRepository.findByEnoIn(anyCollection())).willReturn(List.of(u1, u1, u2));

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
    @DisplayName("getPlanTargets: 계획 연결이 없으면 예외를 반환한다")
    void getPlanTargets_missingPlan_rejected() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn(" ");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        assertThatThrownBy(() -> planEvaluationService.getPlanTargets(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ASCT_ID);

        verify(planService, never()).getPlan(any());
    }

    @Test
    @DisplayName("getPlanTargets: 정보화사업만 추려 상세와 전산업무비 건수를 병합한다")
    void getPlanTargets_mergesProjectDetailsAndExcludesOperatingBusiness() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn("PLN-2026-0001");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        PlanDto.DetailResponse plan = PlanDto.DetailResponse.builder()
                .bseYy("2026")
                .itPtlPlnTpC("신규")
                .redtConeInf("""
                        {"prjSnapshots":[
                          {"prjMngNo":"PRJ-1","abusNm":"통합사업","pulDtt":"신규","svnHdq":"본부","svnDpmNm":"부서","prjBg":300,"assetBg":200,"costBg":100},
                          {"prjMngNo":"ORN-1","ornYn":"Y"},
                          {"prjMngNo":" "}
                        ],"costDetails":[{},{}]}
                        """)
                .build();
        ProjectDto.Response detail = ProjectDto.Response.builder()
                .abusMngNo("PRJ-1")
                .abusCone("사업 개요")
                .sttDtm(LocalDate.of(2026, 1, 1))
                .endDtm(LocalDate.of(2026, 12, 31))
                .build();
        given(planService.getPlan("PLN-2026-0001")).willReturn(plan);
        ProjectDto.Response duplicate = ProjectDto.Response.builder()
                .abusMngNo("PRJ-1")
                .abusCone("중복 상세")
                .build();
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(detail, duplicate), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.reqDocNo()).isEqualTo("PLN-2026-0001");
        assertThat(result.costCount()).isEqualTo(2);
        assertThat(result.businesses()).singleElement().satisfies(business -> {
            assertThat(business.abusMngNo()).isEqualTo("PRJ-1");
            assertThat(business.prjDes()).isEqualTo("사업 개요");
            assertThat(business.prjBg()).isEqualByComparingTo("300");
            assertThat(business.basePrjBg()).isNull();
        });
    }

    @Test
    @DisplayName("getPlanTargets: 조정계획은 조회 실패 후보를 건너뛰고 직전 수립계획 예산을 사용한다")
    void getPlanTargets_adjustmentUsesLatestMatchingBaseline() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn("PLN-CURRENT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        PlanDto.DetailResponse current = PlanDto.DetailResponse.builder()
                .bseYy("2026")
                .itPtlPlnTpC("조정")
                .redtConeInf("{\"projects\":[{\"prjMngNo\":\"PRJ-1\",\"prjBg\":250,\"assetBg\":150,\"costBg\":100}]}")
                .build();
        given(planService.getPlan("PLN-CURRENT")).willReturn(current);
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of("PRJ-1")));

        Basctm blank = mock(Basctm.class);
        Basctm same = mock(Basctm.class);
        Basctm broken = mock(Basctm.class);
        Basctm wrongYear = mock(Basctm.class);
        Basctm baseline = mock(Basctm.class);
        given(blank.getReqDocNo()).willReturn(null);
        given(same.getReqDocNo()).willReturn("PLN-CURRENT");
        given(broken.getReqDocNo()).willReturn("PLN-BROKEN");
        given(wrongYear.getReqDocNo()).willReturn("PLN-OLD");
        given(baseline.getReqDocNo()).willReturn("PLN-BASE");
        given(councilRepository
                .findByItPtlAsctDbrTcAndItPtlAsctPrgStsTcAndDelYnOrderByFstEnrDtmDesc("02", "13", "N"))
                .willReturn(List.of(blank, same, broken, wrongYear, baseline));
        given(planService.getPlan("PLN-BROKEN")).willThrow(new IllegalStateException("조회 실패"));
        given(planService.getPlan("PLN-OLD")).willReturn(PlanDto.DetailResponse.builder()
                .bseYy("2025").itPtlPlnTpC("신규").build());
        given(planService.getPlan("PLN-BASE")).willReturn(PlanDto.DetailResponse.builder()
                .bseYy("2026")
                .itPtlPlnTpC("신규")
                .redtConeInf("{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"prjBg\":300,\"assetBg\":200,\"costBg\":100}]}")
                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).singleElement().satisfies(business -> {
            assertThat(business.prjDes()).isNull();
            assertThat(business.basePrjBg()).isEqualByComparingTo("300");
            assertThat(business.baseAssetBg()).isEqualByComparingTo("200");
            assertThat(business.baseCostBg()).isEqualByComparingTo("100");
        });
    }

    @Test
    @DisplayName("getPlanTargets: 손상된 스냅샷과 대상년도 없음은 빈 결과로 안전하게 처리한다")
    void getPlanTargets_invalidSnapshotReturnsEmptyTargets() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn("PLN-BROKEN");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-BROKEN")).willReturn(PlanDto.DetailResponse.builder()
                .itPtlPlnTpC("조정")
                .redtConeInf("{broken")
                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isZero();
        verify(projectService, never()).getProjectsByIds(any());
        verify(councilRepository, never())
                .findByItPtlAsctDbrTcAndItPtlAsctPrgStsTcAndDelYnOrderByFstEnrDtmDesc(any(), any(), any());
    }

    @Test
    @DisplayName("getMyEvaluation: 로그인 위원의 평가만 응답 DTO로 변환한다")
    void getMyEvaluation_returnsCurrentMemberRows() {
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        Bplevm evaluation = mockEval("E1", "PRJ-A", "Y", "적정 의견");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.of(evaluation));

        List<CouncilDto.PlanEvaluationItemResponse> result =
                planEvaluationService.getMyEvaluation(ASCT_ID, user);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.eno()).isEqualTo("E1");
            assertThat(item.usrNm()).isNull();
            assertThat(item.evalOpnn()).isEqualTo("적정 의견");
        });
    }

    @Test
    @DisplayName("getAllEvaluations: 사용자 정보가 없으면 이름을 null로 반환한다")
    void getAllEvaluations_missingUserReturnsNullName() {
        Bplevm evaluation = mockEval("UNKNOWN", "PRJ-A", "Y");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(evaluation));
        given(userRepository.findByEnoIn(List.of("UNKNOWN"))).willReturn(List.of());

        CouncilDto.PlanEvaluationSummaryResponse result =
                planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations()).singleElement().satisfies(item -> assertThat(item.usrNm()).isNull());
        assertThat(result.verdicts()).singleElement().satisfies(verdict -> {
            assertThat(verdict.finalAdqYn()).isEqualTo("Y");
            assertThat(verdict.evaluatorCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("getAllEvaluations: 평가가 없으면 사용자 조회 없이 빈 목록을 반환한다")
    void getAllEvaluations_emptyRowsSkipsUserLookup() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        CouncilDto.PlanEvaluationSummaryResponse result =
                planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations()).isEmpty();
        assertThat(result.verdicts()).isEmpty();
        verify(userRepository, never()).findByEnoIn(anyCollection());
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
    @DisplayName("saveEvaluation: 기존 평가는 갱신하고 신규 평가는 저장한 뒤 첫 제출 상태를 전이한다")
    void saveEvaluation_updatesAndPersistsThenChangesInitialStatus() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        Bplevm existing = mock(Bplevm.class);
        Bplevm duplicate = mock(Bplevm.class);
        given(existing.getAbusMngNo()).willReturn("PRJ-A");
        given(duplicate.getAbusMngNo()).willReturn("PRJ-A");
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.of(existing, duplicate));
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest request = new CouncilDto.PlanEvaluationRequest(List.of(
                new CouncilDto.PlanEvaluationItem("PRJ-A", "N", "보완 필요"),
                new CouncilDto.PlanEvaluationItem("PRJ-B", "Y", "적정")));

        planEvaluationService.saveEvaluation(ASCT_ID, request, user);

        verify(existing).update("N", "보완 필요");
        ArgumentCaptor<Bplevm> captor = ArgumentCaptor.forClass(Bplevm.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getItPtlAsctId()).isEqualTo(ASCT_ID);
        assertThat(captor.getValue().getEno()).isEqualTo("E1");
        assertThat(captor.getValue().getAbusMngNo()).isEqualTo("PRJ-B");
        assertThat(captor.getValue().getAdqYn()).isEqualTo("Y");
        verify(councilService).changeStatus(ASCT_ID, "08");
    }

    @Test
    @DisplayName("saveEvaluation: 최초 제출 상태가 아니면 상태를 변경하지 않는다")
    void saveEvaluation_nonInitialStatusKeepsStatus() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.of());
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");

        planEvaluationService.saveEvaluation(
                ASCT_ID, new CouncilDto.PlanEvaluationRequest(List.of()), user);

        verify(councilService, never()).changeStatus(any(), any());
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("saveEvaluation: null 의견은 저장하지 않는다")
    void saveEvaluation_nullOpinionRejected() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.of());
        CustomUserDetails user = new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest request = new CouncilDto.PlanEvaluationRequest(
                List.of(new CouncilDto.PlanEvaluationItem("PRJ-A", "N", null)));

        assertThatThrownBy(() -> planEvaluationService.saveEvaluation(ASCT_ID, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사유");

        verify(entityManager, never()).persist(any());
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

    @Test
    @DisplayName("buildResultSummary: 사업명과 의견을 이스케이프하고 적정 사업은 의견 없음으로 표시한다")
    void buildResultSummary_escapesHtmlAndFallsBackToBusinessId() {
        Basctm council = mock(Basctm.class);
        given(council.getReqDocNo()).willReturn("PLN-BROKEN");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bplevm reserve = mockEval("E1", "PRJ-<A>", "N", "보완 & 재검토");
        Bplevm blankOpinion = mockEval("E2", "PRJ-<A>", "N", " ");
        Bplevm adequate = mockEval("E1", "PRJ-B", "Y", "적정");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(reserve, blankOpinion, adequate));
        given(planService.getPlan("PLN-BROKEN")).willReturn(PlanDto.DetailResponse.builder()
                .redtConeInf("{broken")
                .build());

        CouncilDto.PlanResultSummaryResponse result =
                planEvaluationService.buildResultSummary(ASCT_ID);

        assertThat(result.summaryHtml())
                .contains("PRJ-&lt;A&gt;")
                .contains("보완 &amp; 재검토")
                .contains("적정 1, 유보 0")
                .contains("<td>-</td>");
    }
}
