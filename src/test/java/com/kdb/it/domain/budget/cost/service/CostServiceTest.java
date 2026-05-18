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

    @InjectMocks
    private CostService costService;

    /** 테스트 공통 관리번호 */
    private static final String IT_MNGC_NO = "COST_2026_0001";

    // ───────────────────────────────────────────────────────
    // getCost
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: 존재하지 않는 관리번호이면 IllegalArgumentException을 던진다")
    void getCost_존재하지않는관리번호_IllegalArgumentException발생() {
        given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

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
        given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

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
        given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of());

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
        given(costRepository.findByItMngcNoAndDelYn(any(), eq("N"))).willReturn(List.of());

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(
                List.of("COST_NOTEXIST1", "COST_NOTEXIST2"), null);

        List<CostDto.Response> result = costService.getCostsByIds(request);

        assertThat(result).isEmpty();
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
        given(cost1.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost1.getItMngcSno()).willReturn(1);
        given(cost2.getItMngcNo()).willReturn("COST_2026_0002");
        given(cost2.getItMngcSno()).willReturn(1);

        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost1, cost2));
        // 배치 조회용 Cappla 빈 목록 반환
        given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(eq("BCOSTM"), any()))
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
                .itMngcBgAmt(BigDecimal.valueOf(10_000_000))
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
                .itMngcNo(IT_MNGC_NO)
                .cttNm("소프트웨어 라이선스")
                .build();

        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        // when
        costService.createCost(request);

        // then: btermmRepository.save() 미호출 검증
        verify(btermmRepository, never()).save(any());
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
            given(cost.getItMngcNo()).willReturn(IT_MNGC_NO);
            given(cost.getItMngcSno()).willReturn(1);
            given(cost.getLstYn()).willReturn("Y");
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getBiceDpmC()).willReturn("BBR001");

            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            // 기존 단말기 없음
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1))
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
            given(cost.getItMngcNo()).willReturn(IT_MNGC_NO);
            given(cost.getItMngcSno()).willReturn(1);
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getBiceDpmC()).willReturn("BBR001");

            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            // 연관 단말기 없음
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1))
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
        given(cost1.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost1.getItMngcSno()).willReturn(1);

        Bcostm cost2 = mock(Bcostm.class);
        given(cost2.getItMngcNo()).willReturn("COST_2026_0002");
        given(cost2.getItMngcSno()).willReturn(1);

        given(costRepository.findByItMngcNoAndDelYn("COST_2026_0001", "N"))
                .willReturn(List.of(cost1));
        given(costRepository.findByItMngcNoAndDelYn("COST_2026_0002", "N"))
                .willReturn(List.of(cost2));

        // 단건 조회 경로에서 호출되는 cappla/termm mock
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                eq("BCOSTM"), any(), any())).willReturn(List.of());
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(any(), any(), eq("N")))
                .willReturn(List.of());

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(
                List.of("COST_2026_0001", "COST_2026_0002"), null);

        // when
        List<CostDto.Response> result = costService.getCostsByIds(request);

        // then: 2건 모두 반환
        assertThat(result).hasSize(2);
    }

    // ───────────────────────────────────────────────────────
    // searchCostList — 검색 조건 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchCostList: 검색 조건으로 1건의 전산관리비를 반환한다")
    void searchCostList_검색조건_1건반환() {
        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn(IT_MNGC_NO);
        given(cost.getItMngcSno()).willReturn(1);
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        given(costRepository.searchByCondition(condition)).willReturn(List.of(cost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(any(), any()))
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
        given(cost.getItMngcNo()).willReturn(IT_MNGC_NO);
        given(cost.getItMngcSno()).willReturn(1);
        given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                eq("BCOSTM"), eq(IT_MNGC_NO), eq(1))).willReturn(List.of());
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                .willReturn(List.of());

        CostDto.Response result = costService.getCost(IT_MNGC_NO);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("createCost: 단말기 식별자가 없으면 단말기 관리번호와 순번을 채번해 저장한다")
    void createCost_단말기식별자없음_채번후저장() {
        CostDto.TerminalDto terminal = CostDto.TerminalDto.builder()
                .tmnNm("금융단말")
                .curC("KRW")
                .tmlAmt(BigDecimal.valueOf(1000))
                .build();
        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .itMngcNo(IT_MNGC_NO)
                .cttNm("단말기 계약")
                .terminals(List.of(terminal))
                .build();
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(2);
        given(btermmRepository.getNextSequenceValue()).willReturn(7L);

        String result = costService.createCost(request);

        assertThat(result).isEqualTo(IT_MNGC_NO);
        assertThat(terminal.getTmnMngNo()).matches("TER-\\d{4}-0007");
        assertThat(terminal.getTmnSno()).isEqualTo("1");
        verify(btermmRepository).save(any(Btermm.class));
    }

    @Test
    @DisplayName("updateCost: 최신 이력이 없으면 첫 번째 항목을 수정하고 단말기를 재등록한다")
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
                    .tmnNm("교체단말")
                    .tmlAmt(BigDecimal.valueOf(2000))
                    .build();
            CostDto.UpdateRequest request = CostDto.UpdateRequest.builder()
                    .cttNm("수정 계약")
                    .terminals(List.of(newTerminal))
                    .build();
            given(first.getItMngcNo()).willReturn(IT_MNGC_NO);
            given(first.getItMngcSno()).willReturn(1);
            given(first.getLstYn()).willReturn("N");
            given(first.getFstEnrUsid()).willReturn("10001");
            given(first.getBiceDpmC()).willReturn("BBR001");
            given(second.getLstYn()).willReturn("N");
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(first, second));
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1))
                    .willReturn(List.of(oldTerminal));
            given(btermmRepository.getNextSequenceValue()).willReturn(8L);

            String result = costService.updateCost(IT_MNGC_NO, request);

            assertThat(result).isEqualTo(IT_MNGC_NO);
            verify(first).update(any(), eq("수정 계약"), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(oldTerminal).delete();
            verify(btermmRepository).save(any(Btermm.class));
            assertThat(newTerminal.getTmnMngNo()).matches("TER-\\d{4}-0008");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
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
            given(cost.getItMngcNo()).willReturn(IT_MNGC_NO);
            given(cost.getItMngcSno()).willReturn(1);
            given(cost.getFstEnrUsid()).willReturn("10001");
            given(cost.getBiceDpmC()).willReturn("BBR001");
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N"))
                    .willReturn(List.of(cost));
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1))
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
        given(assetCost.getItMngcNo()).willReturn("COST-ASSET");
        given(assetCost.getItMngcSno()).willReturn(1);
        given(assetCost.getIoeC()).willReturn("101");
        given(assetCost.getItMngcBgAmt()).willReturn(BigDecimal.valueOf(1000));
        given(costCost.getItMngcNo()).willReturn("COST-COST");
        given(costCost.getItMngcSno()).willReturn(1);
        given(costCost.getIoeC()).willReturn("102");
        given(costCost.getItMngcBgAmt()).willReturn(BigDecimal.valueOf(2000));
        given(costRepository.findByItMngcNoAndDelYn("COST-ASSET", "N")).willReturn(List.of(assetCost));
        given(costRepository.findByItMngcNoAndDelYn("COST-COST", "N")).willReturn(List.of(costCost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(eq("BCOSTM"), any(), any()))
                .willReturn(List.of());
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(any(), any(), eq("N")))
                .willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(
                        Ccodem.builder().cId("IOE").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build(),
                        Ccodem.builder().cId("IOE").cdva("102").cTp("IOE_IDR").build()));
        given(bbugtmRepository.sumDupBgByItMngcNos(List.of("COST-ASSET", "COST-COST"), "2026"))
                .willReturn(java.util.Map.of(
                        "COST-ASSET", BigDecimal.valueOf(700),
                        "COST-COST", BigDecimal.valueOf(800)));

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(List.of("COST-ASSET", "COST-COST"), "2026");

        List<CostDto.Response> result = costService.getCostsByIds(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAssetDupBg()).isEqualByComparingTo(BigDecimal.valueOf(700));
        assertThat(result.get(0).getCostDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(1).getAssetDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(1).getCostDupBg()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("getCost: 신청서, 코드명, 예산 구분, 단말기 담당자명을 함께 채운다")
    void getCost_상세보강정보_함께반환() {
        Bcostm cost = Bcostm.builder()
                .itMngcNo(IT_MNGC_NO)
                .itMngcSno(1)
                .ioeC("101")
                .cttNm("계약")
                .itMngcBgAmt(BigDecimal.valueOf(1000))
                .biceDpmC("101")
                .biceTemC("102")
                .cgprEno("10001")
                .delYn("N")
                .build();
        Cappla cappla = Cappla.builder()
                .apfMngNo("APF-001")
                .orcPkVl(IT_MNGC_NO)
                .orcSnoVl(1)
                .build();
        Capplm capplm = Capplm.builder()
                .apfMngNo("APF-001")
                .apfNm("결재")
                .apfSts("결재완료")
                .build();
        Cdecim decision = Cdecim.builder()
                .dcdMngNo("APF-001")
                .dcdSqn(1)
                .dcdEno("10002")
                .build();
        Btermm terminal = Btermm.builder()
                .tmnMngNo("TER-001")
                .tmnSno("1")
                .itMngcNo(IT_MNGC_NO)
                .itMngcSno(1)
                .cgprEno("10003")
                .build();
        given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                "BCOSTM", IT_MNGC_NO, 1)).willReturn(List.of(cappla));
        given(capplmRepository.findById("APF-001")).willReturn(Optional.of(capplm));
        given(cdecimRepository.findByDcdMngNoOrderByDcdSqnAsc("APF-001")).willReturn(List.of(decision));
        given(corgnIRepository.findById("101")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("101").bbrNm("부서").build()));
        given(corgnIRepository.findById("102")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("102").bbrNm("팀").build()));
        given(cuserIRepository.findById("10001")).willReturn(Optional.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(IT_MNGC_NO, 1, "N"))
                .willReturn(List.of(terminal));
        given(cuserIRepository.findByEnoIn(java.util.Set.of("10003")))
                .willReturn(List.of(CuserI.builder().eno("10003").usrNm("단말담당").build()));

        CostDto.Response result = costService.getCost(IT_MNGC_NO);

        assertThat(result.getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.getApfSts()).isEqualTo("결재완료");
        assertThat(result.getBiceDpmNm()).isEqualTo("부서");
        assertThat(result.getBiceTemNm()).isEqualTo("팀");
        assertThat(result.getCgprNm()).isEqualTo("담당자");
        assertThat(result.getAssetBg()).isEqualByComparingTo("1000");
        assertThat(result.getDvcBg()).isEqualByComparingTo("1000");
        assertThat(result.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTerminals()).hasSize(1);
        assertThat(result.getTerminals().get(0).getCgprNm()).isEqualTo("단말담당");
    }

    @Test
    @DisplayName("getCost: 자본예산 코드타입별 세부 분류와 일반관리비를 계산한다")
    void getCost_예산구분세부분류계산() {
        Bcostm machCost = Bcostm.builder()
                .itMngcNo("COST-MACH")
                .itMngcSno(1)
                .ioeC("101")
                .itMngcBgAmt(BigDecimal.valueOf(200))
                .delYn("N")
                .build();
        Bcostm intanCost = Bcostm.builder()
                .itMngcNo("COST-INTAN")
                .itMngcSno(1)
                .ioeC("102")
                .itMngcBgAmt(BigDecimal.valueOf(300))
                .delYn("N")
                .build();
        Bcostm costBg = Bcostm.builder()
                .itMngcNo("COST-GEN")
                .itMngcSno(1)
                .ioeC("103")
                .itMngcBgAmt(null)
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-MACH", "N")).willReturn(List.of(machCost));
        given(costRepository.findByItMngcNoAndDelYn("COST-INTAN", "N")).willReturn(List.of(intanCost));
        given(costRepository.findByItMngcNoAndDelYn("COST-GEN", "N")).willReturn(List.of(costBg));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(
                        Ccodem.builder().cId("IOE").cdva("101").cdvaNm("기계장치").cTp("IOE_HW").build(),
                        Ccodem.builder().cId("IOE").cdva("102").cdvaNm("기타무형자산").cTp("IOE_SW").build(),
                        Ccodem.builder().cId("IOE").cdva("103").cTp("IOE_IDR").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(any(), eq(1), eq("N"))).willReturn(List.of());

        CostDto.Response mach = costService.getCost("COST-MACH");
        CostDto.Response intan = costService.getCost("COST-INTAN");
        CostDto.Response general = costService.getCost("COST-GEN");

        assertThat(mach.getHwBg()).isEqualByComparingTo("200");
        assertThat(intan.getSwBg()).isEqualByComparingTo("300");
        assertThat(general.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCostList: 배치 보강으로 신청서, 부서명, 담당자명, 전년도 예산을 설정한다")
    void getCostList_배치보강정보설정() {
        Bcostm cost = Bcostm.builder()
                .itMngcNo(IT_MNGC_NO)
                .itMngcSno(1)
                .ioeC("101")
                .itMngcBgAmt(BigDecimal.valueOf(1000))
                .itMngcTp("002")
                .pulDtt("002")
                .bgYy("2026")
                .cncdItMngcNo("COST-2025-0001")
                .biceDpmC("101")
                .biceTemC("102")
                .cgprEno("10001")
                .delYn("N")
                .build();
        Bcostm newCost = Bcostm.builder()
                .itMngcNo("COST-NEW")
                .itMngcSno(1)
                .ioeC(null)
                .pulDtt("001")
                .bgYy("2026")
                .delYn("N")
                .build();
        Cappla cappla = Cappla.builder()
                .apfMngNo("APF-001")
                .orcPkVl(IT_MNGC_NO)
                .orcSnoVl(1)
                .build();
        Capplm capplm = Capplm.builder().apfMngNo("APF-001").apfSts("결재중").build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost, newCost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc("BCOSTM", List.of(IT_MNGC_NO, "COST-NEW")))
                .willReturn(List.of(cappla));
        given(capplmRepository.findAllById(List.of("APF-001"))).willReturn(List.of(capplm));
        given(cdecimRepository.findByDcdMngNoInOrderByDcdSqnAsc(List.of("APF-001")))
                .willReturn(List.of(Cdecim.builder().dcdMngNo("APF-001").dcdSqn(1).dcdEno("10002").build()));
        given(corgnIRepository.findAllById(any()))
                .willReturn(List.of(
                        CorgnI.builder().prlmOgzCCone("101").bbrNm("부서").build(),
                        CorgnI.builder().prlmOgzCCone("102").bbrNm("팀").build()));
        given(cuserIRepository.findAllById(any()))
                .willReturn(List.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE").cdva("101").cTp("IOE_IDR").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn(IT_MNGC_NO, 1, "N")).willReturn(List.of());
        given(costRepository.sumPrevBgByItMngcNos(List.of(IT_MNGC_NO), "2025"))
                .willReturn(java.util.Map.of(IT_MNGC_NO, BigDecimal.valueOf(900)));
        given(bbugtmRepository.sumDupBgByItMngcNos(List.of("COST-2025-0001"), "2025"))
                .willReturn(java.util.Map.of("COST-2025-0001", BigDecimal.valueOf(800)));

        List<CostDto.Response> result = costService.getCostList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.get(0).getBiceDpmNm()).isEqualTo("부서");
        assertThat(result.get(0).getCgprNm()).isEqualTo("담당자");
        assertThat(result.get(0).getCostBg()).isEqualByComparingTo("1000");
        assertThat(result.get(0).getPrevBgAmt()).isEqualByComparingTo("900");
        assertThat(result.get(0).getPrevDupBg()).isEqualByComparingTo("800");
        assertThat(result.get(1).getPrevDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
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
                    .itMngcNo(IT_MNGC_NO)
                    .itMngcSno(1)
                    .delYn("N")
                    .build();
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));

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
                    .itMngcNo(IT_MNGC_NO)
                    .itMngcSno(1)
                    .biceDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
            given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(2);
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1)).willReturn(List.of());

            String result = costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build());

            assertThat(result).isEqualTo(IT_MNGC_NO);
            verify(btermmRepository).findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1);
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
                    .itMngcNo(IT_MNGC_NO)
                    .itMngcSno(1)
                    .fstEnrUsid("10001")
                    .biceDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));
            given(btermmRepository.findByItMngcNoAndItMngcSno(IT_MNGC_NO, 1)).willReturn(List.of());

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
                    .itMngcNo(IT_MNGC_NO)
                    .itMngcSno(1)
                    .biceDpmC("101")
                    .delYn("N")
                    .build();
            given(costRepository.findByItMngcNoAndDelYn(IT_MNGC_NO, "N")).willReturn(List.of(cost));

            assertThatThrownBy(() -> costService.updateCost(IT_MNGC_NO, CostDto.UpdateRequest.builder().build()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("소속 부서");
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
                .itMngcNo("COST-ALL-CODE")
                .itMngcSno(1)
                .ioeC("101")
                .abusC("ABUS01")
                .dfrCleC("DFR01")
                .itMngcTp("TP01")
                .pulDtt("PD01")
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-ALL-CODE", "N")).willReturn(List.of(cost));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-ALL-CODE", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(any(), any(), any()))
                .willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cdva("101").cTp("IOE_IDR").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("ABUS_C", "ABUS01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cNm("남용코드명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("DFR_CLE", "DFR01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cNm("납입주기명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("IT_MNGC_TP", "TP01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cNm("유형명").build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("PUL_DTT", "PD01", null))
                .willReturn(java.util.Optional.of(Ccodem.builder().cNm("지급구분명").build()));

        // Act
        CostDto.Response result = costService.getCost("COST-ALL-CODE");

        // Assert: 각 코드명 람다가 실행되어 이름이 설정됨
        assertThat(result.getAbusCNm()).isEqualTo("남용코드명");
        assertThat(result.getDfrCleCNm()).isEqualTo("납입주기명");
        assertThat(result.getItMngcTpNm()).isEqualTo("유형명");
        assertThat(result.getPulDttNm()).isEqualTo("지급구분명");
    }

    @Test
    @DisplayName("getCost: 비목코드가 비어 있으면 예산 분류를 0으로 유지한다")
    void getCost_비목코드없음_예산분류0유지() {
        Bcostm cost = Bcostm.builder()
                .itMngcNo("COST-NO-IOE")
                .itMngcSno(1)
                .ioeC("")
                .itMngcBgAmt(BigDecimal.valueOf(1000))
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-NO-IOE", "N")).willReturn(List.of(cost));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-NO-IOE", 1, "N")).willReturn(List.of());

        CostDto.Response result = costService.getCost("COST-NO-IOE");

        assertThat(result.getAssetBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCostBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ───────────────────────────────────────────────────────
    // setBudgetCategory — IOE_CPIT 구코드 cdvaDes 세부 분기 (Branch 69.9% → 개선)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCost: IOE_CPIT 구코드에서 cdvaDes=개발비이면 dvcBg에 금액이 설정된다")
    void getCost_IOECPIT개발비_dvcBg설정() {
        // Arrange: cTp=IOE_CPIT, cdvaDes=개발비 → 구버전 개발비 분기
        Bcostm cost = Bcostm.builder()
                .itMngcNo("COST-CPIT-DVC")
                .itMngcSno(1)
                .ioeC("OLD_DVC")
                .itMngcBgAmt(BigDecimal.valueOf(500))
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-CPIT-DVC", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE").cdva("OLD_DVC").cTp("IOE_CPIT").cdvaDes("개발비").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-CPIT-DVC", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(any(), any(), any()))
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
                .itMngcNo("COST-CPIT-HW")
                .itMngcSno(1)
                .ioeC("OLD_HW")
                .itMngcBgAmt(BigDecimal.valueOf(300))
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-CPIT-HW", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE").cdva("OLD_HW").cTp("IOE_CPIT").cdvaDes("기계장치").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-CPIT-HW", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(any(), any(), any()))
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
                .itMngcNo("COST-CPIT-SW")
                .itMngcSno(1)
                .ioeC("OLD_SW")
                .itMngcBgAmt(BigDecimal.valueOf(400))
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-CPIT-SW", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cId("IOE").cdva("OLD_SW").cTp("IOE_CPIT").cdvaDes("기타무형자산").build()));
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-CPIT-SW", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(any(), any(), any()))
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
                .itMngcNo("COST-UNKNOWN-IOE")
                .itMngcSno(1)
                .ioeC("UNKNOWN")
                .itMngcBgAmt(BigDecimal.valueOf(999))
                .delYn("N")
                .build();
        given(costRepository.findByItMngcNoAndDelYn("COST-UNKNOWN-IOE", "N")).willReturn(List.of(cost));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of()); // 빈 목록 → codeOpt = empty
        given(btermmRepository.findByItMngcNoAndItMngcSnoAndDelYn("COST-UNKNOWN-IOE", 1, "N")).willReturn(List.of());
        given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(any(), any(), any()))
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
                .itMngcNo("COST-BATCH-CODE")
                .itMngcSno(1)
                .ioeC("101")
                .abusC("ABUS01")
                .dfrCleC("DFR01")
                .itMngcTp("TP01")
                .pulDtt("PD01")
                .bgYy("2026")
                .delYn("N")
                .build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(any())).willReturn(List.of());
        given(cuserIRepository.findAllById(any())).willReturn(List.of());
        // buildCodeNameMap 람다 커버: 각 코드타입 → 코드명 반환
        given(ccodemRepository.findByCIdWithValidDate("ABUS_C", null))
                .willReturn(List.of(Ccodem.builder().cId("ABUS_C").cdva("ABUS01").cNm("남용유형").build()));
        given(ccodemRepository.findByCIdWithValidDate("DFR_CLE", null))
                .willReturn(List.of(Ccodem.builder().cId("DFR_CLE").cdva("DFR01").cNm("매월").build()));
        given(ccodemRepository.findByCIdWithValidDate("IT_MNGC_TP", null))
                .willReturn(List.of(Ccodem.builder().cId("IT_MNGC_TP").cdva("TP01").cNm("유형A").build()));
        given(ccodemRepository.findByCIdWithValidDate("PUL_DTT", null))
                .willReturn(List.of(Ccodem.builder().cId("PUL_DTT").cdva("PD01").cNm("지급A").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE", null))
                .willReturn(List.of(Ccodem.builder().cdva("101").cdvaNm("전산임차료").cTp("IOE_IDR").build()));

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: 코드명 필드가 설정됨
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAbusCNm()).isEqualTo("남용유형");
        assertThat(result.get(0).getDfrCleCNm()).isEqualTo("매월");
        assertThat(result.get(0).getItMngcTpNm()).isEqualTo("유형A");
        assertThat(result.get(0).getPulDttNm()).isEqualTo("지급A");
        assertThat(result.get(0).getIoeCNm()).isEqualTo("전산임차료");
    }

    @Test
    @DisplayName("getCostList: cncdItMngcNo가 있고 bgYy가 없으면 prevDupBg를 0으로 설정한다")
    void getCostList_cncdItMngcNo있음bgYy없음_prevDupBg0() {
        // Arrange: cncdItMngcNo 있지만 bgYy 없음 → bgYy=null 분기
        Bcostm cost = Bcostm.builder()
                .itMngcNo("COST-CNCD")
                .itMngcSno(1)
                .cncdItMngcNo("COST-PREV-001")
                .bgYy(null) // bgYy 없음
                .delYn("N")
                .build();
        given(costRepository.findAllByDelYn("N")).willReturn(List.of(cost));
        given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(eq("BCOSTM"), any()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(any())).willReturn(List.of());
        given(cuserIRepository.findAllById(any())).willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of());

        // Act
        List<CostDto.Response> result = costService.getCostList();

        // Assert: prevDupBg = 0 (bgYy null 분기)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrevDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
