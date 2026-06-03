package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@link CostService} 의 XCR 표준 조회 통합 테스트.
 *
 * <p>CONTEXT.md 결정 E / R3.7: 클라이언트가 보낸 {@code xcr} 을 {@link XcrLookupService}
 * 결과로 덮어쓴 뒤 {@code BudgetAmountCalculator.reconcileAmount} 가
 * 정확한 원화 금액을 산출하는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostServiceXcrLookupTest {

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
    @Mock private XcrLookupService xcrLookupService;

    @InjectMocks
    private CostService costService;

    private static final String IT_MNGC_NO = "COST_2026_0001";

    @Test
    @DisplayName("createCost 외화 USD: 클라 xcr=999 무시 후 Ccodem 환율 1400으로 itMngcBgAmt 재계산")
    void createCost_외화USD_Ccodem환율로재계산() {
        // given: Ccodem 조회 결과 1400 (클라가 보낸 999 무시 대상)
        given(xcrLookupService.resolveXcr(eq("USD"), any(LocalDate.class)))
                .willReturn(new BigDecimal("1400"));
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("외화 라이선스")
                .curC("USD")
                .fcAmt(new BigDecimal("1000.000"))
                .xcr(new BigDecimal("999"))         // 클라이언트 위조값
                .costTotXpAmt(new BigDecimal("0"))   // 클라이언트 위조값
                .build();

        // when
        costService.createCost(request);

        // then: 저장된 Bcostm 캡처 후 검증
        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        Bcostm saved = captor.getValue();
        assertThat(saved.getCostTotXpAmt()).isEqualByComparingTo(new BigDecimal("1400000.000"));
        assertThat(saved.getXcr()).isEqualByComparingTo(new BigDecimal("1400"));
        assertThat(saved.getFcAmt()).isEqualByComparingTo(new BigDecimal("1000.000"));
        assertThat(saved.getCurC()).isEqualTo("USD");
    }

    @Test
    @DisplayName("createCost 외화 미등록 XYZ: IllegalStateException으로 트랜잭션 롤백, costRepository.save 미호출")
    void createCost_외화미등록_IllegalStateException발생() {
        // given: XcrLookupService 가 미등록 통화 예외를 던짐
        given(xcrLookupService.resolveXcr(eq("XYZ"), any(LocalDate.class)))
                .willThrow(new IllegalStateException("환율 미등록: XYZ (기준일: 2026-05-24)"));
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("미등록 통화")
                .curC("XYZ")
                .fcAmt(new BigDecimal("1000"))
                .xcr(new BigDecimal("999"))
                .costTotXpAmt(new BigDecimal("0"))
                .build();

        // when / then
        assertThatThrownBy(() -> costService.createCost(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환율 미등록:");
        verify(costRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCost KRW: Ccodem 조회 미수행 + xcr=null 저장 + itMngcBgAmt 보존")
    void createCost_KRW_xcrNull저장() {
        // given: resolveXcr 가 null 반환 (KRW 우회)
        given(xcrLookupService.resolveXcr(eq("KRW"), any(LocalDate.class))).willReturn(null);
        given(costRepository.getNextSnoValue(IT_MNGC_NO)).willReturn(1);

        CostDto.CreateRequest request = CostDto.CreateRequest.builder()
                .costBgNo(IT_MNGC_NO)
                .cttNm("원화 계약")
                .curC("KRW")
                .costTotXpAmt(new BigDecimal("5000000"))
                .fcAmt(null)
                .build();

        // when
        costService.createCost(request);

        // then: KRW → xcr/fcAmt NULL, 원화 금액 보존
        ArgumentCaptor<Bcostm> captor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(captor.capture());
        Bcostm saved = captor.getValue();
        assertThat(saved.getXcr()).isNull();
        assertThat(saved.getFcAmt()).isNull();
        assertThat(saved.getCostTotXpAmt()).isEqualByComparingTo(new BigDecimal("5000000"));
    }
}
