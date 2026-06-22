package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 * {@link BudgetWorkService} 의 BITEMM 원화 정규화 금액 편성 테스트.
 *
 * <p>BITEMM 저장 단계(ProjectService)에서 외화 환율을 검증하고 {@code amt = fcAmt × xcr}로
 * 원화 금액을 정규화한다. 편성 단계에서는 이미 정규화된 {@code amt}를 그대로 사용해
 * 환율 이중 적용을 방지한다.</p>
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

    @InjectMocks
    private BudgetWorkService budgetWorkService;

    /**
     * applyItemRates(BPROJM 분기, L298 경로) 를 통해 외화 USD Bitemm 1건을 재집계.
     * item.xcr 가 0이어도 이미 원화 정규화된 amt만 사용되는지 확인.
     */
    @Test
    @DisplayName("L298 BITEMM 결재완료 외화 USD: item.xcr=0이어도 원화 정규화 amt로 편성금액 계산")
    void applyItemRates_외화품목_원화정규화Amt로DupBgAmt계산() {
        // given: applyItemRates 진입 mocks
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        // 외화 Bitemm 1건: ProjectService 저장 단계에서 amt는 이미 원화로 정규화되어 있다.
        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("USD");
        given(bitemm.getAmt()).willReturn(new BigDecimal("1400000"));
        given(bitemm.getXcr()).willReturn(BigDecimal.ZERO);
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

        // then: dupBgAmt = 1000 × 1400 × 100/100 = 1,400,000.00 (item.xcr=0 이 아닌 Ccodem 1400 사용 확인)
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        Bbugtm saved = captor.getValue();
        assertThat(saved.getBgDupAmt()).isEqualByComparingTo(new BigDecimal("1400000.00"));
        assertThat(saved.getFntTbNm()).isEqualTo("BITEMM");
        assertThat(saved.getPkColNm()).isEqualTo("GCL-2026-0001");
    }

    @Test
    @DisplayName("L298 BITEMM 외화 XYZ: 편성 단계에서는 환율 재조회 없이 정규화 amt로 저장")
    void applyItemRates_외화미등록_편성단계환율재조회없이저장() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("XYZ");
        given(bitemm.getAmt()).willReturn(new BigDecimal("1000"));
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
        verify(bbugtmRepository).save(any());
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

        // then: dupBgAmt = 5,000,000 × 1 × 100/100 = 5,000,000.00 (BigDecimal.ONE fallback)
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        Bbugtm saved = captor.getValue();
        assertThat(saved.getBgDupAmt()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }
}
