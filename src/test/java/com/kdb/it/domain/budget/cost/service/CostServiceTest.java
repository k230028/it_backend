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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;

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
                List.of("COST_NOTEXIST1", "COST_NOTEXIST2"));

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
                .itMngcBg(BigDecimal.valueOf(10_000_000))
                .build();

        given(costRepository.getNextSequenceValue()).willReturn(1L);
        // 기존 데이터 없음 → SNO = 1 (null 반환 시 서비스에서 1로 처리)
        given(costRepository.getNextSnoValue(any())).willReturn(null);

        // when
        String result = costService.createCost(request);

        // then: 자동 채번된 관리번호 형식 검증 (COST_{year}_0001)
        assertThat(result).matches("COST_\\d{4}_0001");
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
            given(cost.getBiceDpm()).willReturn("BBR001");

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
            given(cost.getBiceDpm()).willReturn("BBR001");

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
                List.of("COST_2026_0001", "COST_2026_0002"));

        // when
        List<CostDto.Response> result = costService.getCostsByIds(request);

        // then: 2건 모두 반환
        assertThat(result).hasSize(2);
    }
}
