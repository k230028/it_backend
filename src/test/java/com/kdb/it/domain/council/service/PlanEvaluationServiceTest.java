package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PlanEvaluationService 단위 테스트 (dbrTc='02' 사업별 적정/유보)
 *
 * <p>사업별 판정 집계("1명이라도 유보(N)면 유보")와 저장 검증(위원 권한·사유 필수)을 확인합니다. Basctm·Bplevm·Bcmmtm·CuserI는
 * Mockito.mock()으로 생성하며 Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanEvaluationServiceTest {

    @Mock private PlanEvaluationRepository planEvaluationRepository;
    @Mock private UserRepository userRepository;
    @Mock private CouncilService councilService;
    @Mock private CommitteeRepository committeeRepository;
    @Mock private PlanService planService;
    @Mock private ProjectService projectService;
    @Mock private CouncilRepository councilRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks private PlanEvaluationService planEvaluationService;

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(planEvaluationService, "entityManager", entityManager);
    }

    private static final String ASCT_ID = "ASCT-2026-0001";

    private Bplevm mockEval(String eno, String abusMngNo, String pprtYn) {
        return mockEval(eno, abusMngNo, pprtYn, "의견");
    }

    private Bplevm mockEval(String eno, String abusMngNo, String pprtYn, String opinion) {
        Bplevm e = mock(Bplevm.class);
        given(e.getEno()).willReturn(eno);
        given(e.getAbusMngNo()).willReturn(abusMngNo);
        given(e.getPprtYn()).willReturn(pprtYn);
        given(e.getEvalOpnn()).willReturn(opinion);
        return e;
    }

    private UserRepository.UserNameView mockUser(String eno, String nm) {
        UserRepository.UserNameView u = mock(UserRepository.UserNameView.class);
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
        UserRepository.UserNameView u1 = mockUser("E1", "홍길동");
        UserRepository.UserNameView u2 = mockUser("E2", "김철수");
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of(u1, u1, u2));

        CouncilDto.PlanEvaluationSummaryResponse res =
                planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(res.evaluations()).hasSize(4);
        var a =
                res.verdicts().stream()
                        .filter(v -> "PRJ-A".equals(v.abusMngNo()))
                        .findFirst()
                        .orElseThrow();
        var b =
                res.verdicts().stream()
                        .filter(v -> "PRJ-B".equals(v.abusMngNo()))
                        .findFirst()
                        .orElseThrow();
        assertThat(a.finalPprtYn()).isEqualTo("Y");
        assertThat(a.reserveCount()).isEqualTo(0L);
        assertThat(b.finalPprtYn()).isEqualTo("N");
        assertThat(b.reserveCount()).isEqualTo(1L);
        assertThat(b.evaluatorCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getPlanTargets: 계획 연결이 없으면 예외를 반환한다")
    void getPlanTargets_missingPlan_rejected() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn(" ");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        assertThatThrownBy(() -> planEvaluationService.getPlanTargets(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ASCT_ID);

        verify(planService, never()).getPlan(any());
    }

    @Test
    @DisplayName("getPlanTargets: 정보화사업만 추려 상세와 전산업무비 건수를 병합한다 (누락 prjMngNo 원소는 손상으로 표시)")
    void getPlanTargets_mergesProjectDetailsAndExcludesOperatingBusiness() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-2026-0001");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        PlanDto.DetailResponse plan =
                PlanDto.DetailResponse.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .redtConeInf(
                                """
                        {"prjSnapshots":[
                          {"prjMngNo":"PRJ-1","abusNm":"통합사업","pulDtt":"신규","svnHdq":"본부","svnDpmNm":"부서","prjBg":300,"assetBg":200,"costBg":100},
                          {"prjMngNo":"ORN-1","ornYn":"Y"},
                          {"prjMngNo":" "}
                        ],"costDetails":[{},{}]}
                        """)
                        .build();
        ProjectDto.Response detail =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-1")
                        .abusCone("사업 개요")
                        .sttDtm(LocalDate.of(2026, 1, 1))
                        .endDtm(LocalDate.of(2026, 12, 31))
                        .build();
        given(planService.getPlan("PLN-2026-0001")).willReturn(plan);
        ProjectDto.Response duplicate =
                ProjectDto.Response.builder().abusMngNo("PRJ-1").abusCone("중복 상세").build();
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(detail, duplicate), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.reqDocNo()).isEqualTo("PLN-2026-0001");
        assertThat(result.costCount()).isEqualTo(2);
        // 빈 prjMngNo("ORN-1" 다음 원소)는 구조 손상으로 제외되므로 불완전 플래그가 서야 한다
        assertThat(result.snapshotIncomplete()).isTrue();
        assertThat(result.businesses())
                .singleElement()
                .satisfies(
                        business -> {
                            assertThat(business.abusMngNo()).isEqualTo("PRJ-1");
                            assertThat(business.prjDes()).isEqualTo("사업 개요");
                            assertThat(business.prjBg()).isEqualByComparingTo("300");
                            assertThat(business.basePrjBg()).isNull();
                        });
    }

    @Test
    @DisplayName("getPlanTargets: 조정계획은 조인 단건 조회로 직전 수립계획 예산을 사용한다")
    void getPlanTargets_adjustmentUsesLatestMatchingBaseline() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-CURRENT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        PlanDto.DetailResponse current =
                PlanDto.DetailResponse.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("02")
                        .redtConeInf(
                                "{\"projects\":[{\"prjMngNo\":\"PRJ-1\",\"prjBg\":250,\"assetBg\":150,\"costBg\":100}]}")
                        .build();
        given(planService.getPlan("PLN-CURRENT")).willReturn(current);
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of("PRJ-1")));

        given(
                        councilRepository.findBaselineReqDocNos(
                                org.mockito.ArgumentMatchers.eq("02"),
                                org.mockito.ArgumentMatchers.eq("13"),
                                org.mockito.ArgumentMatchers.eq("2026"),
                                org.mockito.ArgumentMatchers.eq("01"),
                                org.mockito.ArgumentMatchers.eq("PLN-CURRENT"),
                                any(Pageable.class)))
                .willReturn(List.of("PLN-BASE"));
        given(planService.getPlan("PLN-BASE"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .bseYy("2026")
                                .itPtlPlnTpC("01")
                                .redtConeInf(
                                        "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"prjBg\":300,\"assetBg\":200,\"costBg\":100}]}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses())
                .singleElement()
                .satisfies(
                        business -> {
                            assertThat(business.prjDes()).isNull();
                            assertThat(business.basePrjBg()).isEqualByComparingTo("300");
                            assertThat(business.baseAssetBg()).isEqualByComparingTo("200");
                            assertThat(business.baseCostBg()).isEqualByComparingTo("100");
                        });
        verify(councilRepository)
                .findBaselineReqDocNos(
                        org.mockito.ArgumentMatchers.eq("02"),
                        org.mockito.ArgumentMatchers.eq("13"),
                        org.mockito.ArgumentMatchers.eq("2026"),
                        org.mockito.ArgumentMatchers.eq("01"),
                        org.mockito.ArgumentMatchers.eq("PLN-CURRENT"),
                        any(Pageable.class));
    }

    @Test
    @DisplayName(
            "getPlanTargets: 조정계획의 기준(baseline) 스냅샷이 구조 손상이면 현재 사업 목록은 유지하고 기준 예산은"
                    + " 비운 채 snapshotIncomplete=true를 반환한다")
    void getPlanTargets_corruptedBaselineSnapshotKeepsCurrentBusinessesButFlagsIncomplete() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-CURRENT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        // 현재 계획 스냅샷은 완전히 유효(abusNm 포함) — 불완전 플래그가 오직 기준 계획 손상에서만 기인함을 증명
        PlanDto.DetailResponse current =
                PlanDto.DetailResponse.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("02")
                        .redtConeInf(
                                "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\",\"prjBg\":250,\"assetBg\":150,\"costBg\":100}]}")
                        .build();
        given(planService.getPlan("PLN-CURRENT")).willReturn(current);
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of("PRJ-1")));

        given(
                        councilRepository.findBaselineReqDocNos(
                                any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of("PLN-BASE"));
        // 기준 계획 조회 자체는 성공하지만 스냅샷 구조가 손상(알려진 루트 키 없음)
        given(planService.getPlan("PLN-BASE"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .bseYy("2026")
                                .itPtlPlnTpC("01")
                                .redtConeInf("{}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses())
                .singleElement()
                .satisfies(
                        business -> {
                            assertThat(business.abusMngNo()).isEqualTo("PRJ-1");
                            assertThat(business.prjBg()).isEqualByComparingTo("250");
                            assertThat(business.basePrjBg()).isNull();
                            assertThat(business.baseAssetBg()).isNull();
                            assertThat(business.baseCostBg()).isNull();
                        });
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: 기준 계획 조회 예외를 삼키지 않고 전파한다")
    void getPlanTargets_baselineLookupFailurePropagates() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-CURRENT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-CURRENT"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .bseYy("2026")
                                .itPtlPlnTpC("02")
                                .redtConeInf("{\"prjSnapshots\":[]}")
                                .build());
        given(
                        councilRepository.findBaselineReqDocNos(
                                any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of("PLN-BROKEN"));
        given(planService.getPlan("PLN-BROKEN"))
                .willThrow(new IllegalStateException("기준 계획 DB 오류"));

        assertThatThrownBy(() -> planEvaluationService.getPlanTargets(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기준 계획 DB 오류");
    }

    @Test
    @DisplayName("getPlanTargets: 스냅샷 JSON 구문 오류는 예외 대신 빈 부분결과 + snapshotIncomplete=true를 반환한다")
    void getPlanTargets_syntaxErrorSnapshotReturnsIncompletePartial() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-BROKEN");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-BROKEN"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("조정")
                                .redtConeInf("{broken")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isZero();
        assertThat(result.snapshotIncomplete()).isTrue();
        verify(projectService, never()).getProjectsByIds(any());
        verify(councilRepository, never())
                .findBaselineReqDocNos(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("getPlanTargets: 현재 계획 조회(getPlan) 예외는 삼키지 않고 전파한다")
    void getPlanTargets_currentPlanLookupFailurePropagates() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-DBERROR");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-DBERROR")).willThrow(new IllegalStateException("DB 커넥션 오류"));

        assertThatThrownBy(() -> planEvaluationService.getPlanTargets(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 커넥션 오류");
    }

    @Test
    @DisplayName("getPlanTargets: null 스냅샷은 정상적인 빈 심의 대상(snapshotIncomplete=false)을 반환한다")
    void getPlanTargets_nullSnapshotIsNormalEmpty() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NULL");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NULL"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(null)
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isZero();
        assertThat(result.snapshotIncomplete()).isFalse();
    }

    @Test
    @DisplayName("getPlanTargets: 공백 스냅샷은 정상적인 빈 심의 대상(snapshotIncomplete=false)을 반환한다")
    void getPlanTargets_blankSnapshotIsNormalEmpty() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-BLANK");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-BLANK"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("   ")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.snapshotIncomplete()).isFalse();
    }

    @Test
    @DisplayName("getPlanTargets: 빈 객체({}) 스냅샷은 구조 손상으로 취급해 snapshotIncomplete=true를 반환한다")
    void getPlanTargets_emptyObjectSnapshotIsIncompletePartial() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-EMPTYOBJ");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-EMPTYOBJ"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("{}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isZero();
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: 알려진 루트 키가 전혀 없는 객체 스냅샷은 구조 손상으로 취급한다")
    void getPlanTargets_unknownRootKeysSnapshotIsIncompletePartial() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-UNKNOWNKEYS");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-UNKNOWNKEYS"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("{\"foo\":\"bar\"}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: 루트가 배열이면 구조 손상으로 취급한다")
    void getPlanTargets_arrayRootSnapshotIsIncompletePartial() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-ARRAYROOT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-ARRAYROOT"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("[1,2,3]")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: 루트가 스칼라 값이면 구조 손상으로 취급한다")
    void getPlanTargets_scalarRootSnapshotIsIncompletePartial() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-SCALARROOT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-SCALARROOT"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("\"just-a-string\"")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: prjSnapshots가 배열이 아니면 그 부분만 제외하고 costDetails는 유지한다")
    void getPlanTargets_nonArrayBusinessListExcludedButCostDetailsSurvive() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NONARRAY-BIZ");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NONARRAY-BIZ"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(
                                        "{\"prjSnapshots\":\"not-an-array\",\"costDetails\":[{},{}]}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isEqualTo(2);
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: costDetails가 배열이 아니면 그 부분만 제외하고 사업 목록은 유지한다")
    void getPlanTargets_nonArrayCostDetailsExcludedButBusinessesSurvive() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NONARRAY-COST");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NONARRAY-COST"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(
                                        "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\"}],\"costDetails\":\"broken\"}")
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).extracting("abusMngNo").containsExactly("PRJ-1");
        assertThat(result.costCount()).isZero();
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: 배열 원소가 객체가 아니면 그 원소만 제외하고 유효한 사업은 살린다")
    void getPlanTargets_nonObjectArrayElementExcludedButValidElementsSurvive() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NONOBJ-ELEM");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NONOBJ-ELEM"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(
                                        "{\"prjSnapshots\":[42,{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\"}]}")
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).extracting("abusMngNo").containsExactly("PRJ-1");
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: prjMngNo가 없는 사업 원소는 제외하되 다른 유효 사업은 그대로 반환한다(부분 성공)")
    void getPlanTargets_missingPrjMngNoElementExcludedButOthersSurvive() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-MIXED");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-MIXED"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(
                                        "{\"prjSnapshots\":[{\"abusNm\":\"관리번호없음\"},{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\"}]}")
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses())
                .singleElement()
                .satisfies(business -> assertThat(business.abusMngNo()).isEqualTo("PRJ-1"));
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: abusNm이 없는 사업은 제외하지 않고 유지하되 불완전 플래그를 세운다")
    void getPlanTargets_missingAbusNmKeepsBusinessButFlagsIncomplete() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NONAME");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NONAME"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf("{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\"}]}")
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses())
                .singleElement()
                .satisfies(
                        business -> {
                            assertThat(business.abusMngNo()).isEqualTo("PRJ-1");
                            assertThat(business.abusNm()).isNull();
                        });
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("getPlanTargets: ornYn 필드가 아예 없는 사업은 기존과 동일하게 포함하고 불완전 플래그를 세우지 않는다")
    void getPlanTargets_missingOrnYnFieldIncludedWithoutIncompleteFlag() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NOORN");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-NOORN"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(
                                        "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\"}]}")
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).extracting("abusMngNo").containsExactly("PRJ-1");
        assertThat(result.snapshotIncomplete()).isFalse();
    }

    @Test
    @DisplayName("getPlanTargets: 스냅샷 JSON은 요청당 정확히 1회만 파싱한다(단일 파싱 지점 검증)")
    void getPlanTargets_parsesSnapshotExactlyOnce()
            throws com.fasterxml.jackson.core.JsonProcessingException {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-ONCE");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        String snapshotJson = "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-1\",\"abusNm\":\"A사업\"}]}";
        given(planService.getPlan("PLN-ONCE"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .itPtlPlnTpC("신규")
                                .redtConeInf(snapshotJson)
                                .build());
        given(projectService.getProjectsByIds(any(ProjectDto.BulkGetRequest.class)))
                .willReturn(new ProjectDto.BulkResponse(List.of(), List.of()));
        ObjectMapper spyMapper = spy(new ObjectMapper());
        ReflectionTestUtils.setField(planEvaluationService, "snapshotMapper", spyMapper);

        planEvaluationService.getPlanTargets(ASCT_ID);

        verify(spyMapper, times(1)).readTree(snapshotJson);
    }

    @Test
    @DisplayName("getPlanTargets: 비어 있는 유효 스냅샷은 빈 심의 대상을 반환한다(snapshotIncomplete=false)")
    void getPlanTargets_emptySnapshotReturnsEmptyTargets() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-EMPTY");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planService.getPlan("PLN-EMPTY"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .bseYy("2026")
                                .itPtlPlnTpC("신규")
                                .redtConeInf("{\"prjSnapshots\":[],\"costDetails\":[]}")
                                .build());

        CouncilDto.PlanTargetsResponse result = planEvaluationService.getPlanTargets(ASCT_ID);

        assertThat(result.businesses()).isEmpty();
        assertThat(result.costCount()).isZero();
        assertThat(result.snapshotIncomplete()).isFalse();
    }

    @Test
    @DisplayName("getMyEvaluation: 로그인 위원의 평가만 응답 DTO로 변환한다")
    void getMyEvaluation_returnsCurrentMemberRows() {
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        Bplevm evaluation = mockEval("E1", "PRJ-A", "Y", "적정 의견");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.of(evaluation));

        List<CouncilDto.PlanEvaluationItemResponse> result =
                planEvaluationService.getMyEvaluation(ASCT_ID, user);

        assertThat(result)
                .singleElement()
                .satisfies(
                        item -> {
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
        given(userRepository.findNameViewsByEnoIn(List.of("UNKNOWN"))).willReturn(List.of());

        CouncilDto.PlanEvaluationSummaryResponse result =
                planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations())
                .singleElement()
                .satisfies(item -> assertThat(item.usrNm()).isNull());
        assertThat(result.verdicts())
                .singleElement()
                .satisfies(
                        verdict -> {
                            assertThat(verdict.finalPprtYn()).isEqualTo("Y");
                            assertThat(verdict.evaluatorCount()).isEqualTo(1);
                        });
    }

    @Test
    @DisplayName("getAllEvaluations: 평가가 없으면 사용자 조회 없이 빈 목록을 반환한다")
    void getAllEvaluations_emptyRowsSkipsUserLookup() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of());

        CouncilDto.PlanEvaluationSummaryResponse result =
                planEvaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations()).isEmpty();
        assertThat(result.verdicts()).isEmpty();
        verify(userRepository, never()).findNameViewsByEnoIn(anyCollection());
    }

    @Test
    @DisplayName("saveEvaluation: 해당 협의회 평가위원이 아니면 AccessDeniedException")
    void save_notMember_denied() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E9", "N"))
                .willReturn(Optional.empty());
        CustomUserDetails user =
                new CustomUserDetails("E9", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req =
                new CouncilDto.PlanEvaluationRequest(
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
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.<Bplevm>of());
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req =
                new CouncilDto.PlanEvaluationRequest(
                        List.of(new CouncilDto.PlanEvaluationItem("PRJ-A", "Y", "   ")));

        assertThatThrownBy(() -> planEvaluationService.saveEvaluation(ASCT_ID, req, user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("saveEvaluation: 적정여부가 Y/N이 아니면 IllegalArgumentException")
    void save_invalidPprtYn_rejected() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        given(planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "E1", "N"))
                .willReturn(List.<Bplevm>of());
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest req =
                new CouncilDto.PlanEvaluationRequest(
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
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest request =
                new CouncilDto.PlanEvaluationRequest(
                        List.of(
                                new CouncilDto.PlanEvaluationItem("PRJ-A", "N", "보완 필요"),
                                new CouncilDto.PlanEvaluationItem("PRJ-B", "Y", "적정")));

        planEvaluationService.saveEvaluation(ASCT_ID, request, user);

        verify(existing).update("N", "보완 필요");
        ArgumentCaptor<Bplevm> captor = ArgumentCaptor.forClass(Bplevm.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getItPtlAsctId()).isEqualTo(ASCT_ID);
        assertThat(captor.getValue().getEno()).isEqualTo("E1");
        assertThat(captor.getValue().getAbusMngNo()).isEqualTo("PRJ-B");
        assertThat(captor.getValue().getPprtYn()).isEqualTo("Y");
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
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");

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
        CustomUserDetails user =
                new CustomUserDetails("E1", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.PlanEvaluationRequest request =
                new CouncilDto.PlanEvaluationRequest(
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
        given(council.getAbusMngNo()).willReturn("PLN-2026-0001");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // PRJ-A: E1 적정 / E2 유보 → 최종 유보
        Bplevm e1 = mockEval("E1", "PRJ-A", "Y");
        Bplevm e2 = mockEval("E2", "PRJ-A", "N");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(e1, e2));

        PlanDto.DetailResponse plan =
                PlanDto.DetailResponse.builder()
                        .redtConeInf(
                                "{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-A\",\"abusNm\":\"클라우드 전환\"}]}")
                        .build();
        given(planService.getPlan("PLN-2026-0001")).willReturn(plan);

        CouncilDto.PlanResultSummaryResponse res =
                planEvaluationService.buildResultSummary(ASCT_ID);

        assertThat(res.verdicts()).hasSize(1);
        assertThat(res.verdicts().get(0).finalPprtYn()).isEqualTo("N");
        assertThat(res.summaryHtml())
                .contains("<table>") // 표 구조
                .contains("클라우드 전환") // 스냅샷에서 사업명 해석
                .contains("유보"); // 최종 판정
        assertThat(res.snapshotIncomplete()).isFalse();
    }

    @Test
    @DisplayName("buildResultSummary: 계획 조회(getPlan) 예외는 삼키지 않고 전파한다")
    void buildResultSummary_planLookupFailurePropagates() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-DBERROR");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of());
        given(planService.getPlan("PLN-DBERROR")).willThrow(new IllegalStateException("DB 커넥션 오류"));

        assertThatThrownBy(() -> planEvaluationService.buildResultSummary(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 커넥션 오류");
    }

    @Test
    @DisplayName("buildResultSummary: abusNm이 없는 사업은 관리번호로 대체 표시하고 snapshotIncomplete=true를 반환한다")
    void buildResultSummary_missingAbusNmFallsBackToMngNoAndFlagsIncomplete() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-NONAME");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bplevm e1 = mockEval("E1", "PRJ-NONAME", "Y");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(e1));
        given(planService.getPlan("PLN-NONAME"))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .redtConeInf("{\"prjSnapshots\":[{\"prjMngNo\":\"PRJ-NONAME\"}]}")
                                .build());

        CouncilDto.PlanResultSummaryResponse result =
                planEvaluationService.buildResultSummary(ASCT_ID);

        assertThat(result.summaryHtml()).contains("PRJ-NONAME"); // 이름 없음 → 관리번호로 대체
        assertThat(result.snapshotIncomplete()).isTrue();
    }

    @Test
    @DisplayName("buildResultSummary: 사업명과 의견을 이스케이프하고 적정 사업은 의견 없음으로 표시한다")
    void buildResultSummary_escapesHtmlAndFallsBackToBusinessId() {
        Basctm council = mock(Basctm.class);
        given(council.getAbusMngNo()).willReturn("PLN-BROKEN");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bplevm reserve = mockEval("E1", "PRJ-<A>", "N", "보완 & 재검토");
        Bplevm blankOpinion = mockEval("E2", "PRJ-<A>", "N", " ");
        Bplevm adequate = mockEval("E1", "PRJ-B", "Y", "적정");
        given(planEvaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(reserve, blankOpinion, adequate));
        given(planService.getPlan("PLN-BROKEN"))
                .willReturn(PlanDto.DetailResponse.builder().redtConeInf("{broken").build());

        CouncilDto.PlanResultSummaryResponse result =
                planEvaluationService.buildResultSummary(ASCT_ID);

        assertThat(result.summaryHtml())
                .contains("PRJ-&lt;A&gt;")
                .contains("보완 &amp; 재검토")
                .contains("적정 1, 유보 0")
                .contains("<td>-</td>");
        assertThat(result.snapshotIncomplete()).isTrue();
    }
}
