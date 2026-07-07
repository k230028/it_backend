package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
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
import java.util.List;

/**
 * {@link BudgetWorkService} 의 BITEMM 저장 원화 금액 편성 테스트.
 *
 * <p>BITEMM의 {@code amt}는 저장 시 원화로 환산된 금액이고, {@code fcAmt}가 원천 통화 금액입니다.
 * 편성/조회 경계에서는 {@code amt}를 그대로 사용해 환율 이중 적용을 방지합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetWorkServiceXcrLookupTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;
    @Mock private org.springframework.data.domain.AuditorAware<String> auditorAware;

    @InjectMocks
    private BudgetWorkService budgetWorkService;

    @Test
    @DisplayName("BPROJM 편성: 저장된 KRW amt를 그대로 사용해 편성금액을 계산")
    void applyItemRates_외화품목_저장KrwAmt사용() {
        // given: applyItemRates 진입 mocks
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        // 외화 Bitemm 1건: fcAmt=100, xcr=1300, 저장 amt=130000.
        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("USD");
        given(bitemm.getFcAmt()).willReturn(new BigDecimal("100"));
        given(bitemm.getAmt()).willReturn(new BigDecimal("130000"));
        given(bitemm.getXcr()).willReturn(new BigDecimal("1300"));
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0001");
        given(bitemm.getSno()).willReturn(1);
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0001"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        // getSummary mocks
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        BudgetWorkDto.ItemRate rate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 100, 100);
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate));

        // when
        budgetWorkService.applyItemRates(request);

        // then: dupBgAmt = 130000 × 100/100 = 130,000.00
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        Bbugtm saved = captor.getValue();
        assertThat(saved.getBgDupAmt()).isEqualByComparingTo(new BigDecimal("130000.00"));
        assertThat(saved.getFntTbNm()).isEqualTo("BITEMM");
        assertThat(saved.getPkColNm()).isEqualTo("GCL-2026-0001");
    }

    @Test
    @DisplayName("BPROJM 편성: xcr이 있어도 저장된 KRW amt를 다시 환산하지 않는다")
    void applyItemRates_환율있어도_저장KrwAmt유지() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("XYZ");
        given(bitemm.getAmt()).willReturn(new BigDecimal("1000"));
        given(bitemm.getXcr()).willReturn(new BigDecimal("1300"));
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0003");
        given(bitemm.getSno()).willReturn(1);
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0001"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        BudgetWorkDto.ItemRate rate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 100, 100);
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate));

        // when
        budgetWorkService.applyItemRates(request);

        // then
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("KRW 항목: resolveXcr null 반환, BigDecimal.ONE으로 amountKrw 계산")
    void applyItemRates_KRW항목_BigDecimalONE으로계산() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("KRW");
        given(bitemm.getAmt()).willReturn(new BigDecimal("5000000"));
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0002");
        given(bitemm.getSno()).willReturn(1);
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0002"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        BudgetWorkDto.ItemRate rate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0002", 100, 100);
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate));

        // when
        budgetWorkService.applyItemRates(request);

        // then: dupBgAmt = 5,000,000 × 100/100 = 5,000,000.00
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        Bbugtm saved = captor.getValue();
        assertThat(saved.getBgDupAmt()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }

    @Test
    @DisplayName("DUP_IOE 편성: 결재완료 BITEMM 집계도 저장된 KRW amt를 그대로 사용")
    void applyRates_외화품목_저장KrwAmt사용() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn("2026", "BCOSTM", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn("2026", "BITEMM", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(
                com.kdb.it.common.code.entity.Ccodem.builder()
                        .cId("IOE_C")
                        .cdva("001")
                        .cdvaDtlC("237-0100")
                        .sttDt("20260101")
                        .build()));
        given(bbugtmRepository.findApprovedCostsByIoeCValues(eq(java.util.Set.of("001")), eq("2026")))
                .willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getFcAmt()).willReturn(new BigDecimal("100"));
        given(bitemm.getAmt()).willReturn(new BigDecimal("130000"));
        given(bitemm.getXcr()).willReturn(new BigDecimal("1300"));
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0004");
        given(bitemm.getSno()).willReturn(1);
        given(bbugtmRepository.findApprovedItemsByIoeCValues(eq(java.util.Set.of("001")), eq("2026")))
                .willReturn(List.of(bitemm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        BudgetWorkDto.RateItem rate = new BudgetWorkDto.RateItem("237", 100);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rate));

        // when
        budgetWorkService.applyRates(request);

        // then
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo(new BigDecimal("130000.00"));
    }
}
