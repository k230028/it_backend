package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;

/**
 * CostService 단위 테스트
 *
 * <p>
 * 전산관리비 서비스의 단건 조회·목록 조회·생성·수정·삭제(Soft Delete) 예외 경로와
 * 일괄 조회의 누락 항목 필터링 동작을 검증합니다.
 * 수정/삭제의 권한 검증(SecurityContextHolder)은 조회 실패 시 도달하지 않으므로
 * 이 테스트에서 별도 설정 없이 검증 가능합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostServiceTest {

    @Mock private CostRepository costRepository;
    @Mock private BtermmRepository btermmRepository;
    @Mock private ApplicationMapRepository capplaRepository;
    @Mock private ApplicationRepository capplmRepository;
    @Mock private OrganizationRepository corgnIRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private ApproverRepository cdecimRepository;
    @Mock private CodeRepository ccodemRepository;
    @Mock private CodeService codeService;
    @Mock private BbugtmRepository bbugtmRepository;
    /** 환율 표준 조회 헬퍼 (CONTEXT.md 결정 E / R3.7 — Wave 5 추가 의존성) */
    @Mock private XcrLookupService xcrLookupService;
    /** Phase 5 Task 5: CodeNameMapBuilder 추출 후 주입 */
    @Mock private com.kdb.it.common.util.CodeNameMapBuilder codeNameMapBuilder;
    /** 조직코드→조직명 해석기 (주관부서명/주관팀명 스냅샷 주입) */
    @Mock private com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;

    @InjectMocks
    private CostService costService;

    /** 테스트 공통 관리번호 */
    private static final String IT_MNGC_NO = "COST_2026_0001";

    @org.junit.jupiter.api.BeforeEach
    void setupCodeNameMapperDefaults() {
        // codeNameMapBuilder.build()의 기본값: 빈 Map 반환 (호출자가 필요시 override)
        given(codeNameMapBuilder.build(any(), any())).willReturn(java.util.Map.of());
        // 조직명 스냅샷 기본값: 미등록 코드로 간주해 null 반환 (기존 테스트 무영향)
        org.mockito.Mockito.lenient()
                .when(orgNameResolver.resolveName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
    }

    // ───────────────────────────────────────────────────────
    // getCost
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: 존재하지 않는 관리번호이면 IllegalArgumentException을 던진다")
    void getCost_존재하지않는관리번호_IllegalArgumentException발생() {
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

        assertThatThrownBy(() -> costService.getCost(IT_MNGC_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(IT_MNGC_NO);
    }

    // ───────────────────────────────────────────────────────
    // updateCost
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCost: 존재하지 않는 관리번호이면 IllegalArgumentException을 던진다")
    void updateCost_존재하지않는관리번호_IllegalArgumentException발생() {
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

        assertThatThrownBy(() -> costService.updateCost(IT_MNGC_NO, new CostDto.UpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(IT_MNGC_NO);
    }

    // ───────────────────────────────────────────────────────
    // deleteCost
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCost: 존재하지 않는 관리번호이면 IllegalArgumentException을 던진다")
    void deleteCost_존재하지않는관리번호_IllegalArgumentException발생() {
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

        assertThatThrownBy(() -> costService.deleteCost(IT_MNGC_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(IT_MNGC_NO);
    }

    // ───────────────────────────────────────────────────────
    // getCostsByIds
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCostsByIds: 존재하지 않는 관리번호는 결과에서 제외하고 빈 목록을 반환한다")
    void getCostsByIds_존재하지않는항목_필터링빈목록반환() {
        given(costRepository.findByCostBgNoAndDelYn(any(), eq("N"))).willReturn(List.of());

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(
                List.of("COST_NOTEXIST1", "COST_NOTEXIST2"), null);

        CostDto.BulkResponse result = costService.getCostsByIds(request);

        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("getCostsByIds: 전건 미존재면 items empty, failedIds 전부")
    void getCostsByIds_allMissing_collectsFailedIds() {
        given(costRepository.findByCostBgNoAndDelYn(any(), eq("N"))).willReturn(List.of());

        CostDto.BulkGetRequest req = new CostDto.BulkGetRequest();
        req.setCostBgNos(java.util.List.of("COST-X", "COST-Y"));
        req.setBseYy(null);

        CostDto.BulkResponse result = costService.getCostsByIds(req);

        assertThat(result.items()).isEmpty();
        assertThat(result.failedIds()).containsExactly("COST-X", "COST-Y");
    }

    // ───────────────────────────────────────────────────────
    // getCostList (신규)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCostList: 2건의 전산관리비가 있으면 2건 반환한다")
    void getCostList_2건존재_2건반환() {
        // mock 엔티티 생성 (protected 생성자 우회)
        Bcostm cost1 = mock(Bcostm.class);
        Bcostm cost2 = mock(Bcostm.class);
        given(cost1.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost1.getBgSno()).willReturn(1);
        given(cost2.getCostBgNo()).willReturn("COST_2026_0002");
        given(cost2.getBgSno()).willReturn(1);

        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost1, cost2));
        // 배치 조회용 Cappla 빈 목록 반환
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());

        // when
        List<CostDto.Response> result = costService.getCostList();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getCostList: 삭제된 항목만 있으면 빈 목록을 반환한다")
    void getCostList_빈목록_빈목록반환() {
        given(costRepository.findAllByDelYn("N")).willReturn(List.of());

        // when
        List<CostDto.Response> result = costService.getCostList();

        // then
        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // createCost (신규)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCost: 관리번호가 없으면 Oracle 시퀀스로 자동 채번하여 저장한다")
    void createCost_관리번호없음_시퀀스채번저장() {
        // given: 관리번호 미입력
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .cttNm("서버 유지보수 계약")
                .costTotXpAmt(BigDecimal.valueOf(10_000_000))
                .build();

        given(costRepository.getNextSequenceValue()).willReturn(1L);
        // 기존 데이터 없음 → SNO = 1 (null 반환 시 서비스에서 1로 처리)
        given(costRepository.getNextSnoValue(any())).willReturn(null);

        // when
        String result = costService.createCost(request);

        // then: 자동 채번된 관리번호 형식 검증 (COST-{year}-0001)
        assertThat(result).matches("COST-\\d{4}-0001");
        // repository.save() 호출 확인
        verify(costRepository).save(any(Bcostm.class));
    }

    @Test
    @DisplayName("createCost: 단말기(Btermm) 없이 생성하면 btermmRepository.save()를 호출하지 않는다")
    void createCost_단말기없이생성_btermmSave미호출() {
        // given: 단말기 목록을 포함하지 않는 요청
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("소프트웨어 라이선스")
                .build();

        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        // when
        costService.createCost(request);

        // then: btermmRepository.save() 미호출 검증
        verify(btermmRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCost: 상위조직명(PRLM_HRK_OGZ_C_CONE)을 담당자(CUSERI) 소속 상위조직명으로 채운다")
    void createCost_상위조직명_담당자기준설정() {
        // given: 담당자(cgprId) 소속 CUSERI의 상위조직명 스냅샷 (getPrlmHrkOgzCNm은 파생 게터라 mock 사용)
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("담당자 상위조직 계약")
                .cgprId("10003")
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);
        given(costRepository.save(any(Bcostm.class))).willAnswer(inv -> inv.getArgument(0));
        CuserI manager = mock(CuserI.class);
        given(manager.getPrlmHrkOgzCNm()).willReturn("경영지원본부");
        given(cuserIRepository.findByEno("10003")).willReturn(Optional.of(manager));

        // when
        costService.createCost(request);

        // then: 저장 엔티티에 담당자 소속 상위조직명이 반영된다 (담당자 기준)
        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        assertThat(captor.getValue().getPrlmHrkOgzCCone()).isEqualTo("경영지원본부");
    }

    @Test
    @DisplayName("전산업무비 생성 시 주관부서명은 CORGNI, 주관팀명은 담당자(CUSERI) 스냅샷으로 저장한다")
    void createCost_storesSvnOrgNameSnapshot() {
        // given: 담당부서코드→부서명은 CORGNI, 담당자(cgprId) 소속 팀명은 CUSERI에서 스냅샷
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("조직명 스냅샷 계약")
                .costSvnDpmC("BBR001")
                .svnTemC("18010")
                .cgprId("10003")
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);
        given(costRepository.save(any(Bcostm.class))).willAnswer(inv -> inv.getArgument(0));
        given(orgNameResolver.resolveName("BBR001")).willReturn("담당부서명A");
        given(cuserIRepository.findByEno("10003"))
                .willReturn(Optional.of(CuserI.builder().eno("10003").temC("18010").temNm("담당팀명A").build()));

        // when
        costService.createCost(request);

        // then: 저장 엔티티에 주관부서명(CORGNI)/주관팀명(CUSERI) 스냅샷이 함께 저장된다
        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        assertThat(captor.getValue().getSvnDpmNm()).isEqualTo("담당부서명A");
        assertThat(captor.getValue().getSvnTemNm()).isEqualTo("담당팀명A");
    }

    // ───────────────────────────────────────────────────────
    // updateCost (신규) — 정상 수정
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCost: 관리자가 정상 상태 항목 수정 시 관리번호를 반환한다")
    void updateCost_정상수정_관리번호반환() {
        // given: 관리자 인증 컨텍스트 설정
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            // mock 엔티티 (protected 생성자 우회)
            Bcostm cost = mock(Bcostm.class);
            given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost.getBgSno()).willReturn(1);
            given(cost.getLstYn()).willReturn("Y");
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getCostSvnDpmC()).willReturn("BBR001");

            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            // 기존 단말기 없음
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                    .willReturn(List.of());

            // when
            String result = costService.updateCost(IT_MNGC_NO, new CostDto.UpdateRequest());

            // then
            assertThat(result).isEqualTo(IT_MNGC_NO);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ───────────────────────────────────────────────────────
    // deleteCost (신규) — 정상 삭제
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCost: 관리자가 정상 삭제 시 cost.delete()를 호출하여 Soft Delete 처리한다")
    void deleteCost_정상삭제_SoftDelete처리() {
        // given: 관리자 인증 컨텍스트 설정
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            // mock 엔티티 (protected 생성자 우회)
            Bcostm cost = mock(Bcostm.class);
            given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost.getBgSno()).willReturn(1);
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getCostSvnDpmC()).willReturn("BBR001");

            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            // 연관 단말기 없음
            given(btermmRepository.findByTermBgNoAndTermBgSno(IT_MNGC_NO, 1))
                    .willReturn(List.of());

            // when
            costService.deleteCost(IT_MNGC_NO);

            // then: cost.delete() 호출 확인 (Soft Delete 검증)
            verify(cost).delete();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ───────────────────────────────────────────────────────
    // getCostsByIds (신규) — 존재 항목 포함
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCostsByIds: 존재하는 항목 2건을 조회하면 2건을 반환한다")
    void getCostsByIds_존재하는항목2건_2건반환() {
        // given: 두 관리번호 모두 존재
        Bcostm cost1 = mock(Bcostm.class);
        given(cost1.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost1.getBgSno()).willReturn(1);

        Bcostm cost2 = mock(Bcostm.class);
        given(cost2.getCostBgNo()).willReturn("COST_2026_0002");
        given(cost2.getBgSno()).willReturn(1);

        given(costRepository.findByCostBgNoAndDelYn("COST_2026_0001", "N"))
                .willReturn(List.of(cost1));
        given(costRepository.findByCostBgNoAndDelYn("COST_2026_0002", "N"))
                .willReturn(List.of(cost2));

        // 단건 조회 경로에서 호출되는 cappla/termm mock
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                eq("BCOSTM"), any(), any())).willReturn(List.of());
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(any(), any(), eq("N")))
                .willReturn(List.of());

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(
                List.of("COST_2026_0001", "COST_2026_0002"), null);

        // when
        CostDto.BulkResponse result = costService.getCostsByIds(request);

        // then: 2건 모두 반환
        assertThat(result.items()).hasSize(2);
    }

    // ───────────────────────────────────────────────────────
    // searchCostList — 검색 조건 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchCostList: 검색 조건으로 1건의 전산관리비를 반환한다")
    void searchCostList_검색조건_1건반환() {
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
        given(cost.getBgSno()).willReturn(1);
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        given(costRepository.searchByCondition(condition)).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(any(), any()))
                .willReturn(List.of());

        List<CostDto.Response> result = costService.searchCostList(condition);

        assertThat(result).hasSize(1);
    }

    // ───────────────────────────────────────────────────────
    // getCost — 정상 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: 존재하는 관리번호이면 응답 DTO를 반환한다")
    void getCost_존재하는관리번호_응답반환() {
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
        given(cost.getBgSno()).willReturn(1);
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                eq("BCOSTM"), eq(IT_MNGC_NO), eq(1))).willReturn(List.of());
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                .willReturn(List.of());

        CostDto.Response result = costService.getCost(IT_MNGC_NO);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getCost: 단말기 코드와 담당자가 비어 있으면 코드명 배치 조회를 건너뛴다")
    void getCost_단말기코드담당자없음_코드명조회건너뜀() {
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
        given(cost.getBgSno()).willReturn(1);
        Btermm terminal = Btermm.builder()
                .termBgNo(IT_MNGC_NO)
                .termBgSno(1)
                .tmnMngNo("TMN-EMPTY")
                .sno(1)
                .cgprId("")
                .tmnClsfC("")
                .tmnKdTc(null)
                .dfrCleC("")
                .build();
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                eq("BCOSTM"), eq(IT_MNGC_NO), eq(1))).willReturn(List.of());
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                .willReturn(List.of(terminal));

        CostDto.Response result = costService.getCost(IT_MNGC_NO);

        assertThat(result.getTerminals()).hasSize(1);
        assertThat(result.getTerminals().get(0).getCgprNm()).isNull();
        verify(cuserIRepository, never()).findByEnoIn(any());
        verify(ccodemRepository, never()).findByCIdWithValidDate(eq("IT_PTL_TMN_SVC_TC"), any());
        verify(ccodemRepository, never()).findByCIdWithValidDate(eq("IT_PTL_TMN_KD_TC"), any());
        verify(ccodemRepository, never()).findByCIdWithValidDate(eq("DFR_CLE_C"), any());
    }

    @Test
    @DisplayName("createCost: 단말기 식별자가 없으면 단말기 관리번호와 순번을 채번해 저장한다")
    void createCost_단말기식별자없음_채번후저장() {
        CostDto.TerminalDto terminal = CostDto.TerminalDto.builder()
                .spfTmnNm("금융단말")
                .curC("KRW")
                .termRqmBgAmt(BigDecimal.valueOf(1000))
                .build();
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("단말기 계약")
                .terminals(List.of(terminal))
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(2);
        given(btermmRepository.getNextSequenceValue()).willReturn(7L);

        String result = costService.createCost(request);

        assertThat(result).isEqualTo(IT_MNGC_NO);
        assertThat(terminal.getTmnMngNo()).matches("TER-\\d{4}-0007");
        assertThat(terminal.getSno()).isEqualTo(1);
        verify(btermmRepository).save(any(Btermm.class));
    }

    @Test
    @DisplayName("updateCost: 최신 이력이 없으면 첫 번째 항목을 수정하고, PK 없는 신규 단말기는 저장·미매칭 기존 단말기는 Soft Delete한다")
    void updateCost_최신이력없음_첫번째항목수정및단말기재등록() {
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            Bcostm first = mock(Bcostm.class);
            Bcostm second = mock(Bcostm.class);
            Btermm oldTerminal = mock(Btermm.class);
            CostDto.TerminalDto newTerminal = CostDto.TerminalDto.builder()
                    .spfTmnNm("교체단말")
                    .termRqmBgAmt(BigDecimal.valueOf(2000))
                    .build();
            CostDto.UpdateRequest request = CostDto.UpdateRequest.builder()
                    .cttNm("수정 계약")
                    .terminals(List.of(newTerminal))
                    .build();
            given(first.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(first.getBgSno()).willReturn(1);
            given(first.getLstYn()).willReturn("N");
            given(first.getFstEnrUsid()).willReturn("10001");
            given(first.getCostSvnDpmC()).willReturn("BBR001");
            given(second.getLstYn()).willReturn("N");
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(first, second));
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                    .willReturn(List.of(oldTerminal));
            given(btermmRepository.getNextSequenceValue()).willReturn(8L);

            String result = costService.updateCost(IT_MNGC_NO, request);

            assertThat(result).isEqualTo(IT_MNGC_NO);
            verify(first).update(any(), eq("수정 계약"), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(oldTerminal).delete();
            verify(btermmRepository).save(any(Btermm.class));
            assertThat(newTerminal.getTmnMngNo()).matches("TER-\\d{4}-0008");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("updateCost: 기존 PK와 일치하는 단말기는 제자리 수정하고 신규 저장·삭제·채번을 하지 않는다")
    void updateCost_기존PK일치_제자리수정() {
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            Bcostm cost = mock(Bcostm.class);
            given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost.getBgSno()).willReturn(1);
            given(cost.getLstYn()).willReturn("Y");
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getCostSvnDpmC()).willReturn("BBR001");

            Btermm existing = mock(Btermm.class);
            given(existing.getTmnMngNo()).willReturn("TER-2026-0001");
            given(existing.getSno()).willReturn(1);

            // 요청 단말기: 기존 PK(TER-2026-0001, SNO=1)와 일치 → 제자리 수정 대상
            CostDto.TerminalDto tDto = CostDto.TerminalDto.builder()
                    .tmnMngNo("TER-2026-0001")
                    .sno(1)
                    .spfTmnNm("수정단말")
                    .termRqmBgAmt(BigDecimal.valueOf(3000))
                    .build();
            CostDto.UpdateRequest request = CostDto.UpdateRequest.builder()
                    .cttNm("수정 계약")
                    .terminals(List.of(tDto))
                    .build();

            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                    .willReturn(List.of(existing));

            String result = costService.updateCost(IT_MNGC_NO, request);

            assertThat(result).isEqualTo(IT_MNGC_NO);
            // 제자리 수정: existing.update() 1회 호출 (Btermm.update 인자 15개)
            verify(existing).update(any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any());
            // 신규 저장·Soft Delete·시퀀스 채번은 발생하지 않음
            verify(existing, never()).delete();
            verify(btermmRepository, never()).save(any(Btermm.class));
            verify(btermmRepository, never()).getNextSequenceValue();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("createCost: 단말기 팀/부서 코드를 담당자(CGPR_ID) CUSERI 스냅샷으로 정정한다 (부서코드가 팀코드 컬럼에 유입되던 문제 교정)")
    void createCost_단말기팀부서코드_담당자기준정정() {
        // given: 담당자 CUSERI TEM_C=18001(팀), BBR_C=180(부서). 프론트가 팀코드 컬럼에 부서코드(180)를 넣어도 서버가 팀코드로 교정.
        CostDto.TerminalDto terminal = CostDto.TerminalDto.builder()
                .spfTmnNm("금융단말")
                .cgprId("K140024")
                .termSvnTemC("180") // 잘못된 값(부서코드) — 서버가 CUSERI.TEM_C로 교정해야 함
                .termSvnDpmC("180")
                .curC("KRW")
                .termRqmBgAmt(BigDecimal.valueOf(1000))
                .build();
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("단말기 팀코드 계약")
                .terminals(List.of(terminal))
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);
        given(btermmRepository.getNextSequenceValue()).willReturn(9L);
        given(cuserIRepository.findByEnoIn(java.util.Set.of("K140024")))
                .willReturn(List.of(CuserI.builder().eno("K140024").temC("18001").bbrC("180").build()));

        // when
        costService.createCost(request);

        // then: 저장된 Btermm의 팀코드=18001(CUSERI.TEM_C), 부서코드=180(CUSERI.BBR_C)
        ArgumentCaptor<Btermm> captor = ArgumentCaptor.forClass(Btermm.class);
        verify(btermmRepository).save(captor.capture());
        assertThat(captor.getValue().getTermSvnTemC()).isEqualTo("18001");
        assertThat(captor.getValue().getTermSvnDpmC()).isEqualTo("180");
    }

    @Test
    @DisplayName("deleteCost: 연결된 단말기도 함께 Soft Delete 처리한다")
    void deleteCost_연결단말기_SoftDelete처리() {
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            Bcostm cost = mock(Bcostm.class);
            Btermm terminal = mock(Btermm.class);
            given(cost.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost.getBgSno()).willReturn(1);
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getCostSvnDpmC()).willReturn("BBR001");
            given(terminal.getTermBgNo()).willReturn(IT_MNGC_NO);
            given(terminal.getTermBgSno()).willReturn(1);
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            given(btermmRepository.findByTermBgNoInAndDelYn(any(), eq("N")))
                    .willReturn(List.of(terminal));

            costService.deleteCost(IT_MNGC_NO);

            verify(cost).delete();
            verify(terminal).delete();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("getCostsByIds: 배경연도와 존재 항목이 있으면 편성예산을 자본/경상으로 분류한다")
    void getCostsByIds_배경연도있음_편성예산분류() {
        Bcostm assetCost = mock(Bcostm.class);
        Bcostm costCost = mock(Bcostm.class);
        given(assetCost.getCostBgNo()).willReturn("COST-ASSET");
        given(assetCost.getBgSno()).willReturn(1);
        given(assetCost.getIoeC()).willReturn("101");
        given(assetCost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1000));
        given(costCost.getCostBgNo()).willReturn("COST-COST");
        given(costCost.getBgSno()).willReturn(1);
        given(costCost.getIoeC()).willReturn("102");
        given(costCost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(2000));
        given(costRepository.findByCostBgNoAndDelYn("COST-ASSET", "N")).willReturn(List.of(assetCost));
        given(costRepository.findByCostBgNoAndDelYn("COST-COST", "N")).willReturn(List.of(costCost));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(eq("BCOSTM"), any(), any()))
                .willReturn(List.of());
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(any(), any(), eq("N")))
                .willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(
                        Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build(),
                        Ccodem.builder().cId("IOE_C").cdva("102").cTp("IOE_IDR").build()));
        given(bbugtmRepository.sumDupBgByItMngcNos(List.of("COST-ASSET", "COST-COST"), "2026"))
                .willReturn(java.util.Map.of(
                        "COST-ASSET", BigDecimal.valueOf(700),
                        "COST-COST", BigDecimal.valueOf(800)));

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(List.of("COST-ASSET", "COST-COST"), "2026");

        CostDto.BulkResponse result = costService.getCostsByIds(request);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).getAssetDupBg()).isEqualByComparingTo(BigDecimal.valueOf(700));
        assertThat(result.items().get(0).getCostDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.items().get(1).getAssetDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.items().get(1).getCostDupBg()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("getCost: 신청서, 코드명, 예산 구분, 단말기 담당자명을 함께 채운다")
    void getCost_상세보강정보_함께반환() {
        Bcostm cost = Bcostm.builder()
                .costBgNo(IT_MNGC_NO)
                .bgSno(1)
                .ioeC("101")
                .cttNm("계약")
                .costTotXpAmt(BigDecimal.valueOf(1000))
                .costSvnDpmC("101")
                .svnTemC("102")
                .cgprId("10001")
                .delYn("N")
                .build();
        Cappla cappla = Cappla.builder()
                .apfDcmNo("APF-001")
                .pkColNm(IT_MNGC_NO)
                .fntTbCrySno(1)
                .build();
        Capplm capplm = Capplm.builder()
                .apfMngNo("APF-001")
                .dcdReqTtl("결재")
                .apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code())
                .build();
        Cdecim decision = Cdecim.builder()
                .dcdMngNo("APF-001")
                .dcrSqnSno(1)
                .dcrEno("10002")
                .build();
        Btermm terminal = Btermm.builder()
                .tmnMngNo("TER-001")
                .sno(1)
                .termBgNo(IT_MNGC_NO)
                .termBgSno(1)
                .cgprId("10003")
                .tmnClsfC("SVC01")
                .tmnKdTc("KIND01")
                .dfrCleC("DFR01")
                .build();
        given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                "BCOSTM", IT_MNGC_NO, 1)).willReturn(List.of(cappla));
        given(capplmRepository.findById("APF-001")).willReturn(Optional.of(capplm));
        given(cdecimRepository.findByDcdMngNoOrderByDcrSqnSnoAsc("APF-001")).willReturn(List.of(decision));
        given(corgnIRepository.findById("101")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("101").bbrNm("부서").build()));
        given(corgnIRepository.findById("102")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("102").bbrNm("팀").build()));
        given(cuserIRepository.findById("10001")).willReturn(Optional.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(
                        Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build(),
                        Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("중복개발비").cTp("IOE_DVC").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                .willReturn(List.of(terminal));
        given(cuserIRepository.findByEnoIn(java.util.Set.of("10003")))
                .willReturn(List.of(CuserI.builder().eno("10003").usrNm("단말담당").build()));
        given(codeNameMapBuilder.build(eq("IT_PTL_TMN_SVC_TC"), eq(java.util.Set.of("SVC01"))))
                .willReturn(java.util.Map.of("SVC01", "업무용"));
        given(codeNameMapBuilder.build(eq("IT_PTL_TMN_KD_TC"), eq(java.util.Set.of("KIND01"))))
                .willReturn(java.util.Map.of("KIND01", "노트북"));
        given(codeNameMapBuilder.build(eq("DFR_CLE_C"), eq(java.util.Set.of("DFR01"))))
                .willReturn(java.util.Map.of("DFR01", "월납"));

        CostDto.Response result = costService.getCost(IT_MNGC_NO);

        assertThat(result.getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.getApfSts()).isEqualTo("결재완료");
        assertThat(result.getCostSvnDpmNm()).isEqualTo("부서");
        assertThat(result.getSvnTemNm()).isEqualTo("팀");
        assertThat(result.getCgprNm()).isEqualTo("담당자");
        assertThat(result.getAssetBg()).isEqualByComparingTo("1000");
        assertThat(result.getDvcBg()).isEqualByComparingTo("1000");
        assertThat(result.getIoeCNm()).isEqualTo("개발비");
        assertThat(result.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTerminals()).hasSize(1);
        assertThat(result.getTerminals().get(0).getCgprNm()).isEqualTo("단말담당");
        assertThat(result.getTerminals().get(0).getTmnClsfCNm()).isEqualTo("업무용");
        assertThat(result.getTerminals().get(0).getTmnKdTcNm()).isEqualTo("노트북");
        assertThat(result.getTerminals().get(0).getDfrCleCNm()).isEqualTo("월납");
    }

    @Test
    @DisplayName("getCost: 자본예산 코드타입별 세부 분류와 일반관리비를 계산한다")
    void getCost_예산구분세부분류계산() {
        Bcostm machCost = Bcostm.builder()
                .costBgNo("COST-MACH")
                .bgSno(1)
                .ioeC("101")
                .costTotXpAmt(BigDecimal.valueOf(200))
                .delYn("N")
                .build();
        Bcostm intanCost = Bcostm.builder()
                .costBgNo("COST-INTAN")
                .bgSno(1)
                .ioeC("102")
                .costTotXpAmt(BigDecimal.valueOf(300))
                .delYn("N")
                .build();
        Bcostm costBg = Bcostm.builder()
                .costBgNo("COST-GEN")
                .bgSno(1)
                .ioeC("103")
                .costTotXpAmt(null)
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-MACH", "N")).willReturn(List.of(machCost));
        given(costRepository.findByCostBgNoAndDelYn("COST-INTAN", "N")).willReturn(List.of(intanCost));
        given(costRepository.findByCostBgNoAndDelYn("COST-GEN", "N")).willReturn(List.of(costBg));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(
                        Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("기계장치").cTp("IOE_HW").build(),
                        Ccodem.builder().cId("IOE_C").cdva("102").cdvaNm("기타무형자산").cTp("IOE_SW").build(),
                        Ccodem.builder().cId("IOE_C").cdva("103").cTp("IOE_IDR").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(any(), eq(1), eq("N"))).willReturn(List.of());

        CostDto.Response mach = costService.getCost("COST-MACH");
        CostDto.Response intan = costService.getCost("COST-INTAN");
        CostDto.Response general = costService.getCost("COST-GEN");

        assertThat(mach.getHwBg()).isEqualByComparingTo("200");
        assertThat(intan.getSwBg()).isEqualByComparingTo("300");
        assertThat(general.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCost: 계속 항목 단건 조회 시 cncdRfrNo 기준 전년도 예산(prevBgAmt)을 설정한다")
    void getCost_계속항목_전년도예산설정() {
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-2026-0004")
                .bgSno(1)
                .abusTc("02")
                .bseYy("2026")
                .cncdRfrNo("COST-2026-0023")
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-2026-0004", "N")).willReturn(List.of(cost));
        given(costRepository.sumPrevBgByCostBgNos(List.of("COST-2026-0023"), "2025"))
                .willReturn(java.util.Map.of("COST-2026-0023", BigDecimal.valueOf(4_200_000)));

        CostDto.Response result = costService.getCost("COST-2026-0004");

        assertThat(result.getPrevBgAmt()).isEqualByComparingTo("4200000");
    }

    @Test
    @DisplayName("getCost: 신규 항목 단건 조회 시 전년도 예산(prevBgAmt)은 0이다")
    void getCost_신규항목_전년도예산0() {
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-2026-0005")
                .bgSno(1)
                .abusTc("01")
                .bseYy("2026")
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-2026-0005", "N")).willReturn(List.of(cost));

        CostDto.Response result = costService.getCost("COST-2026-0005");

        assertThat(result.getPrevBgAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCostList: 배치 보강으로 신청서, 부서명, 담당자명, 전년도 예산을 설정한다")
    void getCostList_배치보강정보설정() {
        Bcostm cost = Bcostm.builder()
                .costBgNo(IT_MNGC_NO)
                .bgSno(1)
                .ioeC("101")
                .costTotXpAmt(BigDecimal.valueOf(1000))
                .tmnYn("Y")
                .abusTc("02")
                .bseYy("2026")
                .cncdRfrNo("COST-2025-0001")
                .costSvnDpmC("101")
                .svnTemC("102")
                .cgprId("10001")
                .delYn("N")
                .build();
        Bcostm newCost = Bcostm.builder()
                .costBgNo("COST-NEW")
                .bgSno(1)
                .ioeC(null)
                .abusTc("01")
                .bseYy("2026")
                .delYn("N")
                .build();
        Cappla cappla = Cappla.builder()
                .apfDcmNo("APF-001")
                .pkColNm(IT_MNGC_NO)
                .fntTbCrySno(1)
                .build();
        Capplm capplm = Capplm.builder().apfMngNo("APF-001").apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code()).build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost, newCost));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc("BCOSTM", List.of(IT_MNGC_NO, "COST-NEW")))
                .willReturn(List.of(cappla));
        given(capplmRepository.findAllById(List.of("APF-001"))).willReturn(List.of(capplm));
        given(cdecimRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(List.of("APF-001")))
                .willReturn(List.of(Cdecim.builder().dcdMngNo("APF-001").dcrSqnSno(1).dcrEno("10002").build()));
        given(corgnIRepository.findAllById(any()))
                .willReturn(List.of(
                        CorgnI.builder().prlmOgzCCone("101").bbrNm("부서").build(),
                        CorgnI.builder().prlmOgzCCone("102").bbrNm("팀").build()));
        given(cuserIRepository.findAllById(any()))
                .willReturn(List.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE_C").cdva("101").cTp("IOE_IDR").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N")).willReturn(List.of());
        // 전년도 예산은 cncdRfrNo(전년도 항목 관리번호) 기준으로 조회한다
        given(costRepository.sumPrevBgByCostBgNos(List.of("COST-2025-0001"), "2025"))
                .willReturn(java.util.Map.of("COST-2025-0001", BigDecimal.valueOf(900)));
        given(bbugtmRepository.sumDupBgByItMngcNos(List.of("COST-2025-0001"), "2025"))
                .willReturn(java.util.Map.of("COST-2025-0001", BigDecimal.valueOf(800)));

        List<CostDto.Response> result = costService.getCostList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.get(0).getCostSvnDpmNm()).isEqualTo("부서");
        assertThat(result.get(0).getCgprNm()).isEqualTo("담당자");
        assertThat(result.get(0).getCostBg()).isEqualByComparingTo("1000");
        assertThat(result.get(0).getPrevBgAmt()).isEqualByComparingTo("900");
        assertThat(result.get(0).getPrevDupBg()).isEqualByComparingTo("800");
        assertThat(result.get(1).getPrevDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCostList: 혼합 연도 목록에서 첫 행이 전년도여도 계속 항목의 전년도 예산이 행별 연도 기준으로 설정된다")
    void getCostList_혼합연도목록_행별전년도계산() {
        // Arrange: 2025 행이 목록 앞에 오는 혼합 연도 목록 (과거 버그: 첫 행 연도로 전년도 일괄 계산 → 전부 0)
        Bcostm prev2025 = Bcostm.builder()
                .costBgNo("COST-2025-0001")
                .bgSno(1)
                .abusTc("01")
                .bseYy("2025")
                .delYn("N")
                .build();
        Bcostm cont2026 = Bcostm.builder()
                .costBgNo("COST-2026-0001")
                .bgSno(1)
                .abusTc("02")
                .bseYy("2026")
                .cncdRfrNo("COST-2025-0001")
                .delYn("N")
                .build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(prev2025, cont2026));
        given(costRepository.sumPrevBgByCostBgNos(List.of("COST-2025-0001"), "2025"))
                .willReturn(java.util.Map.of("COST-2025-0001", BigDecimal.valueOf(90_000_000)));
        given(bbugtmRepository.sumDupBgByItMngcNos(List.of("COST-2025-0001"), "2025"))
                .willReturn(java.util.Map.of("COST-2025-0001", BigDecimal.valueOf(90_000_000)));

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: 2026 계속 행은 cncdRfrNo 기준 전년도(2025) 예산이 채워지고, 2025 행은 0 유지
        assertThat(result).hasSize(2);
        CostDto.Response contRow = result.get(1);
        assertThat(contRow.getCostBgNo()).isEqualTo("COST-2026-0001");
        assertThat(contRow.getPrevBgAmt()).isEqualByComparingTo("90000000");
        assertThat(contRow.getPrevDupBg()).isEqualByComparingTo("90000000");
        assertThat(result.get(0).getPrevBgAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(0).getPrevDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("updateCost: 인증 주체가 비정상이면 거부된다")
    void updateCost_인증주체비정상_거부() {
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn("anonymous");
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
        try {
            Bcostm cost = Bcostm.builder()
                    .costBgNo(IT_MNGC_NO)
                    .bgSno(1)
                    .delYn("N")
                    .build();
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));

            assertThatThrownBy(() -> costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("인증 정보");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("updateCost: 부서관리자는 같은 부서 전산업무비를 수정할 수 있다")
    void updateCost_부서관리자_동일부서허용() {
        CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "101");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(manager);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
        try {
            Bcostm cost = Bcostm.builder()
                    .costBgNo(IT_MNGC_NO)
                    .bgSno(1)
                    .costSvnDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
            given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(2);
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N")).willReturn(List.of());

            String result = costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build());

            assertThat(result).isEqualTo(IT_MNGC_NO);
            verify(btermmRepository).findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("updateCost: 일반사용자는 본인 작성 전산업무비를 수정할 수 있다")
    void updateCost_일반사용자_본인작성허용() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "999");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(user);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
        try {
            Bcostm cost = Bcostm.builder()
                    .costBgNo(IT_MNGC_NO)
                    .bgSno(1)
                    .fstEnrUsid("10001")
                    .costSvnDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N")).willReturn(List.of());

            String result = costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build());

            assertThat(result).isEqualTo(IT_MNGC_NO);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("updateCost: 부서관리자가 다른 부서 전산업무비를 수정하면 거부된다")
    void updateCost_부서관리자_타부서거부() {
        CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(manager);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
        try {
            Bcostm cost = Bcostm.builder()
                    .costBgNo(IT_MNGC_NO)
                    .bgSno(1)
                    .costSvnDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));

            assertThatThrownBy(() -> costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("수정 권한");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("updateCost: 일반사용자는 같은 부서 전산업무비라도 타인 건을 수정할 수 없다")
    void updateCost_일반사용자_동일부서_타인수정거부() {
        CustomUserDetails user = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "101");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(user);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            Bcostm cost = Bcostm.builder()
                    .costBgNo(IT_MNGC_NO)
                    .bgSno(1)
                    .lstYn("Y")
                    .fstEnrUsid("10001")
                    .costSvnDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));

            assertThatThrownBy(() -> costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("수정 권한");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ───────────────────────────────────────────────────────
    // setCodeNames — abusC, dfrCleC, itMngcTp, pulDtt, ioeC 분기 (lambda 0% → 커버)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: abusC, dfrCleC, itMngcTp, pulDtt, ioeC 코드명을 모두 채운다")
    void getCost_모든코드명필드_조회() {
        // Arrange: 모든 코드명 관련 필드가 채워진 엔티티
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-ALL-CODE")
                .bgSno(1)
                .ioeC("101")
                .bgUntAbusC("ABUS01")
                .dfrCleC("DFR01")
                .tmnYn("Y")
                .abusTc("PD01")
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-ALL-CODE", "N")).willReturn(List.of(cost));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-ALL-CODE", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(any(), any(), any()))
                .willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cdva("101").cTp("IOE_IDR").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("BG_UNT_ABUS_C", "ABUS01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cdvaNm("남용코드명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("DFR_CLE_C", "DFR01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cdvaNm("납입주기명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("TMN_YN", "1", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cdvaNm("유형명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("ABUS_TC", "PD01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cdvaNm("지급구분명").build()));

        // Act
        CostDto.Response result = costService.getCost("COST-ALL-CODE");

        // Assert: 각 코드명 람다가 실행되어 이름이 설정됨
        assertThat(result.getBgUntAbusCNm()).isEqualTo("남용코드명");
        assertThat(result.getDfrCleCNm()).isEqualTo("납입주기명");
        assertThat(result.getTmnYnNm()).isEqualTo("유형명");
        assertThat(result.getAbusTcNm()).isEqualTo("지급구분명");
    }

    @Test
    @DisplayName("getCost: 비목코드가 비어 있으면 예산 분류를 0으로 유지한다")
    void getCost_비목코드없음_예산분류0유지() {
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-NO-IOE")
                .bgSno(1)
                .ioeC("")
                .costTotXpAmt(BigDecimal.valueOf(1000))
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-NO-IOE", "N")).willReturn(List.of(cost));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-NO-IOE", 1, "N")).willReturn(List.of());

        CostDto.Response result = costService.getCost("COST-NO-IOE");

        assertThat(result.getAssetBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ───────────────────────────────────────────────────────
    // setBudgetCategory — IOE_CPIT 구코드 cdvaDes 세부 분기 (Branch 69.9% → 개선)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: IOE_CPIT 구코드에서 cdvaDes=단말기이면 dvcBg에 금액이 설정된다")
    void getCost_IOECPIT개발비_dvcBg설정() {
        // Arrange: cTp=IOE_CPIT, cdvaDes=단말기 → 구버전 개발비 분기
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-CPIT-DVC")
                .bgSno(1)
                .ioeC("OLD_DVC")
                .costTotXpAmt(BigDecimal.valueOf(500))
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-CPIT-DVC", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE_C").cdva("OLD_DVC").cTp("IOE_CPIT").cdvaDes("단말기").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-CPIT-DVC", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(any(), any(), any()))
                .willReturn(List.of());

        // Act
        CostDto.Response result = costService.getCost("COST-CPIT-DVC");

        // Assert: 개발비 분기 → dvcBg=500, assetBg=500
        assertThat(result.getAssetBg()).isEqualByComparingTo("500");
        assertThat(result.getDvcBg()).isEqualByComparingTo("500");
        assertThat(result.getHwBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCost: IOE_CPIT 구코드에서 cdvaDes=기계장치이면 hwBg에 금액이 설정된다")
    void getCost_IOECPIT기계장치_hwBg설정() {
        // Arrange: cTp=IOE_CPIT, cdvaDes=기계장치
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-CPIT-HW")
                .bgSno(1)
                .ioeC("OLD_HW")
                .costTotXpAmt(BigDecimal.valueOf(300))
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-CPIT-HW", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE_C").cdva("OLD_HW").cTp("IOE_CPIT").cdvaDes("기계장치").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-CPIT-HW", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(any(), any(), any()))
                .willReturn(List.of());

        // Act
        CostDto.Response result = costService.getCost("COST-CPIT-HW");

        // Assert: 기계장치 분기 → hwBg=300
        assertThat(result.getHwBg()).isEqualByComparingTo("300");
        assertThat(result.getDvcBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCost: IOE_CPIT 구코드에서 cdvaDes=기타무형자산이면 swBg에 금액이 설정된다")
    void getCost_IOECPIT기타무형자산_swBg설정() {
        // Arrange: cTp=IOE_CPIT, cdvaDes=기타무형자산
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-CPIT-SW")
                .bgSno(1)
                .ioeC("OLD_SW")
                .costTotXpAmt(BigDecimal.valueOf(400))
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-CPIT-SW", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE_C").cdva("OLD_SW").cTp("IOE_CPIT").cdvaDes("기타무형자산").build()));
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-CPIT-SW", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(any(), any(), any()))
                .willReturn(List.of());

        // Act
        CostDto.Response result = costService.getCost("COST-CPIT-SW");

        // Assert: 기타무형자산 분기 → swBg=400
        assertThat(result.getSwBg()).isEqualByComparingTo("400");
        assertThat(result.getDvcBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCost: ioeC 코드가 IOE에 없으면 예산 분류 모두 0을 유지한다")
    void getCost_ioeC코드없음_예산분류0유지() {
        // Arrange: IOE 코드 목록에 일치하는 cdva 없음 → codeOpt.isEmpty() 분기
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-UNKNOWN-IOE")
                .bgSno(1)
                .ioeC("UNKNOWN")
                .costTotXpAmt(BigDecimal.valueOf(999))
                .delYn("N")
                .build();
        given(costRepository.findByCostBgNoAndDelYn("COST-UNKNOWN-IOE", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of()); // 빈 목록 → codeOpt = empty
        given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-UNKNOWN-IOE", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(any(), any(), any()))
                .willReturn(List.of());

        // Act
        CostDto.Response result = costService.getCost("COST-UNKNOWN-IOE");

        // Assert: 코드 미존재 → 모든 예산 분류 0
        assertThat(result.getAssetBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ───────────────────────────────────────────────────────
    // enrichCostListBatch — buildCodeNameMap lambda (0% 분기 커버)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCostList: abusC/dfrCleC/itMngcTp/pulDtt/ioeC 코드명을 배치 조회하여 설정한다")
    void getCostList_모든배치코드명설정() {
        // Arrange: 각 코드명 필드가 채워진 엔티티 (enrichCostListBatch 분기 모두 커버)
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-BATCH-CODE")
                .bgSno(1)
                .ioeC("101")
                .bgUntAbusC("ABUS01")
                .dfrCleC("DFR01")
                .tmnYn("Y")
                .abusTc("PD01")
                .bseYy("2026")
                .delYn("N")
                .build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(any())).willReturn(List.of());
        given(cuserIRepository.findAllById(any())).willReturn(List.of());
        // 배치 코드명 헬퍼 호출 결과: 각 코드타입 → 코드명 반환
        given(codeNameMapBuilder.build(eq("BG_UNT_ABUS_C"), eq(java.util.Set.of("ABUS01"))))
                .willReturn(java.util.Map.of("ABUS01", "남용유형"));
        given(codeNameMapBuilder.build(eq("DFR_CLE_C"), eq(java.util.Set.of("DFR01"))))
                .willReturn(java.util.Map.of("DFR01", "매월"));
        given(codeNameMapBuilder.build(eq("TMN_YN"), eq(java.util.Set.of("1"))))
                .willReturn(java.util.Map.of("1", "유형A"));
        given(codeNameMapBuilder.build(eq("ABUS_TC"), eq(java.util.Set.of("PD01"))))
                .willReturn(java.util.Map.of("PD01", "지급A"));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(Ccodem.builder().cdva("101").cdvaNm("전산임차료").cTp("IOE_IDR").build()));

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: 코드명 필드가 설정됨
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBgUntAbusCNm()).isEqualTo("남용유형");
        assertThat(result.get(0).getDfrCleCNm()).isEqualTo("매월");
        assertThat(result.get(0).getTmnYnNm()).isEqualTo("유형A");
        assertThat(result.get(0).getAbusTcNm()).isEqualTo("지급A");
        assertThat(result.get(0).getIoeCNm()).isEqualTo("전산임차료");
    }

    @Test
    @DisplayName("getCostList: cncdItMngcNo가 있고 bgYy가 없으면 prevDupBg를 0으로 설정한다")
    void getCostList_cncdItMngcNo있음bgYy없음_prevDupBg0() {
        // Arrange: cncdItMngcNo 있지만 bgYy 없음 → bgYy=null 분기
        Bcostm cost = Bcostm.builder()
                .costBgNo("COST-CNCD")
                .bgSno(1)
                .cncdRfrNo("COST-PREV-001")
                .bseYy(null) // bgYy 없음
                .delYn("N")
                .build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(any())).willReturn(List.of());
        given(cuserIRepository.findAllById(any())).willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: prevDupBg = 0 (bgYy null 분기)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrevDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ───────────────────────────────────────────────────────
    // plan 03-04: 외화 서버 재계산 (BudgetAmountCalculator) — 신규 4건
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCost: 외화 입력 시 itMngcBgAmt = fcAmt × xcr로 서버 재계산되어 저장된다 (클라 위조 무시)")
    void createCost_외화입력_itMngcBgAmt_서버재계산() {
        // given: 외화 USD, 클라가 itMngcBgAmt를 위조한 케이스
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("외화 라이선스 계약")
                .curC("USD")
                .fcAmt(new BigDecimal("1000.000"))
                .xcr(new BigDecimal("1300.5000"))
                .costTotXpAmt(new BigDecimal("999.999")) // 클라 위조 — 무시되어야 함
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);
        // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다 (CONTEXT.md 결정 E)
        given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                .willReturn(new BigDecimal("1300.5000"));

        // when
        costService.createCost(request);

        // then: 저장된 Bcostm 캡처 후 서버 재계산값 검증
        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        assertThat(captor.getValue().getCostTotXpAmt())
                .as("서버 재계산: 1000.000 × 1300.5000 = 1300500.0000")
                .isEqualByComparingTo(new BigDecimal("1300500.0000"));
        assertThat(captor.getValue().getFcAmt())
                .isEqualByComparingTo(new BigDecimal("1000.000"));
    }

    @Test
    @DisplayName("createCost: 원화(KRW) 입력 시 fcAmt=null로 강제되고 itMngcBgAmt는 클라값 그대로 저장된다")
    void createCost_원화입력_fcAmt_null_저장() {
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("원화 소프트웨어 라이선스")
                .curC("KRW")
                .costTotXpAmt(new BigDecimal("5000000"))
                .fcAmt(null)
                .xcr(null)
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        costService.createCost(request);

        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        assertThat(captor.getValue().getFcAmt()).isNull();
        assertThat(captor.getValue().getCostTotXpAmt())
                .isEqualByComparingTo(new BigDecimal("5000000"));
    }

    @Test
    @DisplayName("createCost: 단말기 외화 입력 시 tmlAmt = fcAmt × xcr로 서버 재계산되어 저장된다")
    void createCost_단말기외화입력_tmlAmt_서버재계산() {
        // given: 외화 단말기 1건 포함
        CostDto.TerminalDto terminal = CostDto.TerminalDto.builder()
                .spfTmnNm("외화 단말기")
                .curC("USD")
                .fcAmt(new BigDecimal("500.000"))
                .xcr(new BigDecimal("1300.0000"))
                .termRqmBgAmt(new BigDecimal("999")) // 클라 위조 — 무시되어야 함
                .build();
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("외화 단말기 계약")
                .curC("KRW") // Bcostm 본체는 원화
                .costTotXpAmt(new BigDecimal("1000000"))
                .terminals(List.of(terminal))
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);
        given(btermmRepository.getNextSequenceValue()).willReturn(1L);
        // Wave 5: Bcostm 본체 KRW → null, 단말기 USD → Ccodem 1300.0000
        given(xcrLookupService.resolveXcr(eq("KRW"), any(java.time.LocalDate.class))).willReturn(null);
        given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                .willReturn(new BigDecimal("1300.0000"));

        costService.createCost(request);

        ArgumentCaptor<Btermm> captor = ArgumentCaptor.forClass(Btermm.class);
        verify(btermmRepository).save(captor.capture());
        assertThat(captor.getValue().getTermRqmBgAmt())
                .as("단말기 서버 재계산: 500.000 × 1300.0000 = 650000.0000")
                .isEqualByComparingTo(new BigDecimal("650000.0000"));
        assertThat(captor.getValue().getFcAmt())
                .isEqualByComparingTo(new BigDecimal("500.000"));
    }

    @Test
    @DisplayName("updateCost: 외화 수정 시 itMngcBgAmt = fcAmt × xcr로 서버 재계산되어 target.update에 전달된다")
    void updateCost_외화수정_itMngcBgAmt_서버재계산() {
        // given: 관리자 인증
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            Bcostm target = mock(Bcostm.class);
            given(target.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(target.getBgSno()).willReturn(1);
            given(target.getLstYn()).willReturn("Y");
            given(target.getFstEnrUsid()).willReturn("10001");
            given(target.getCostSvnDpmC()).willReturn("BBR001");

            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(target));
            given(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(IT_MNGC_NO, 1, "N")).willReturn(List.of());

            CostDto.UpdateRequest request = CostDto.UpdateRequest.builder()
                    .curC("USD")
                    .fcAmt(new BigDecimal("1000.000"))
                    .xcr(new BigDecimal("1300.5000"))
                    .costTotXpAmt(new BigDecimal("999")) // 클라 위조
                    .build();
            // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다
            given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                    .willReturn(new BigDecimal("1300.5000"));

            // when
            costService.updateCost(IT_MNGC_NO, request);

            // then: target.update의 itMngcBgAmt 인자(4번째) 및 fcAmt 인자(마지막)를 캡처해 검증
            ArgumentCaptor<BigDecimal> itMngcBgCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            ArgumentCaptor<BigDecimal> fcAmtCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(target).update(
                    any(), any(), any(), itMngcBgCaptor.capture(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), fcAmtCaptor.capture());
            assertThat(itMngcBgCaptor.getValue())
                    .as("서버 재계산: 1000.000 × 1300.5000 = 1300500.0000")
                    .isEqualByComparingTo(new BigDecimal("1300500.0000"));
            assertThat(fcAmtCaptor.getValue()).isEqualByComparingTo(new BigDecimal("1000.000"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ───────────────────────────────────────────────────────
    // Batch 3a: 단말기 조회 N+1 제거 (IN 일괄 조회)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("목록 조회 시 단말기는 행별이 아닌 IN 일괄 조회로 1회만 조회한다")
    void enrichCostList_batchLoadsTerminals_once() {
        // Arrange: tmnYn='Y' 전산관리비 2건
        Bcostm cost1 = Bcostm.builder()
                .costBgNo("COST-T1").bgSno(1).tmnYn("Y").delYn("N").build();
        Bcostm cost2 = Bcostm.builder()
                .costBgNo("COST-T2").bgSno(1).tmnYn("Y").delYn("N").build();
        Btermm term1 = Btermm.builder()
                .tmnMngNo("TER-T1").sno(1).termBgNo("COST-T1").termBgSno(1).build();
        Btermm term2 = Btermm.builder()
                .tmnMngNo("TER-T2").sno(1).termBgNo("COST-T2").termBgSno(1).build();

        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost1, cost2));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(any())).willReturn(List.of());
        given(cuserIRepository.findAllById(any())).willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(btermmRepository.findByTermBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(term1, term2));

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: IN 일괄 조회 1회, 행별 조회 0회
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTerminals()).hasSize(1);
        assertThat(result.get(1).getTerminals()).hasSize(1);
        verify(btermmRepository).findByTermBgNoInAndDelYn(any(), eq("N"));
        verify(btermmRepository, never()).findByTermBgNoAndTermBgSnoAndDelYn(any(), any(), any());
    }

    @Test
    @DisplayName("deleteCost는 단말기를 IN 일괄 조회로 1회만 조회한다")
    void deleteCost_batchLoadsTerminals_once() {
        // Arrange: 관리자 인증 컨텍스트
        CustomUserDetails admin = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        given(auth.getPrincipal()).willReturn(admin);
        given(ctx.getAuthentication()).willReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        try {
            // 동일 관리번호의 이력 2건 (BG_SNO 상이)
            Bcostm cost1 = mock(Bcostm.class);
            Bcostm cost2 = mock(Bcostm.class);
            given(cost1.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost1.getBgSno()).willReturn(1);
            given(cost1.getFstEnrUsid()).willReturn("10001");
            given(cost1.getCostSvnDpmC()).willReturn("BBR001");
            given(cost2.getCostBgNo()).willReturn(IT_MNGC_NO);
            given(cost2.getBgSno()).willReturn(2);

            Btermm terminal = mock(Btermm.class);
            given(terminal.getTermBgNo()).willReturn(IT_MNGC_NO);
            given(terminal.getTermBgSno()).willReturn(1);

            given(costRepository.findByCostBgNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost1, cost2));
            given(btermmRepository.findByTermBgNoInAndDelYn(any(), eq("N")))
                    .willReturn(List.of(terminal));

            // Act
            costService.deleteCost(IT_MNGC_NO);

            // Assert: IN 일괄 조회 1회, 행별 조회 0회
            verify(btermmRepository).findByTermBgNoInAndDelYn(any(), eq("N"));
            verify(btermmRepository, never()).findByTermBgNoAndTermBgSno(any(), any());
            verify(cost1).delete();
            verify(cost2).delete();
            verify(terminal).delete();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
