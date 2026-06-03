package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
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
import java.time.LocalDate;
import java.util.List;

/**
 * {@link BudgetWorkService} 의 XCR 표준 조회 통합 테스트.
 *
 * <p>CONTEXT.md 결정 E / R3.7: L204(applyRates BITEMM) 와 L298(applyItemRates BPROJM)
 * 의 {@code BigDecimal.ONE} fallback 안티패턴이 {@link XcrLookupService} 호출로 교체되어,
 * 외화는 Ccodem 단일 원천 환율을 사용하고 KRW/null 만 1 fallback 을 적용함을 검증한다.</p>
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
    @Mock private XcrLookupService xcrLookupService;

    @InjectMocks
    private BudgetWorkService budgetWorkService;

    /**
     * applyItemRates(BPROJM 분기, L298 경로) 를 통해 외화 USD Bitemm 1건을 재집계.
     * item.xcr 가 0(위조)이어도 Ccodem 1400 환율로 amountKrw 계산되는지 확인.
     */
    @Test
    @DisplayName("L298 BITEMM 결재완료 외화 USD: item.xcr=0 위조 무시, Ccodem 1400으로 amountKrw 계산")
    void applyItemRates_외화품목_Ccodem환율로amountKrw계산() {
        // given: applyItemRates 진입 mocks
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        // 외화 Bitemm 1건 (item.xcr=0 위조)
        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("USD");
        given(bitemm.getAmt()).willReturn(new BigDecimal("1000"));
        given(bitemm.getXcr()).willReturn(BigDecimal.ZERO);  // 클라가 0으로 위조해도 무시
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0001");
        given(bitemm.getSno()).willReturn(1);
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0001"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        // XcrLookupService → Ccodem 1400 환율 반환
        given(xcrLookupService.resolveXcr(eq("USD"), any(LocalDate.class)))
                .willReturn(new BigDecimal("1400"));

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
        assertThat(saved.getBugRqmBgAmt()).isEqualByComparingTo(new BigDecimal("1400000.00"));
        assertThat(saved.getFntTbNm()).isEqualTo("BITEMM");
        assertThat(saved.getPkColNm()).isEqualTo("GCL-2026-0001");
    }

    @Test
    @DisplayName("L298 ItemRate 외화 미등록 XYZ: IllegalStateException으로 작업 중단, bbugtmRepository.save 미호출")
    void applyItemRates_외화미등록_IllegalStateException발생() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("XYZ");
        given(bitemm.getAmt()).willReturn(new BigDecimal("1000"));
        given(bitemm.getIoeC()).willReturn("001");
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0001"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        given(xcrLookupService.resolveXcr(eq("XYZ"), any(LocalDate.class)))
                .willThrow(new IllegalStateException("환율 미등록: XYZ (기준일: 2026-05-24)"));

        BudgetWorkDto.ItemRate rate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 100, 100);
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate));

        // when / then
        assertThatThrownBy(() -> budgetWorkService.applyItemRates(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환율 미등록:");
        verify(bbugtmRepository, never()).save(any());
    }

    @Test
    @DisplayName("KRW 항목: resolveXcr null 반환, BigDecimal.ONE으로 amountKrw 계산")
    void applyItemRates_KRW항목_BigDecimalONE으로계산() {
        // given
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getCurC()).willReturn("KRW");
        given(bitemm.getAmt()).willReturn(new BigDecimal("5000000"));
        given(bitemm.getIoeC()).willReturn("001");
        given(bitemm.getGclMngNo()).willReturn("GCL-2026-0002");
        given(bitemm.getSno()).willReturn(1);
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(eq("PRJ-2026-0002"), eq("N"), eq("Y")))
                .willReturn(List.of(bitemm));

        // KRW → null 반환 (Ccodem 조회 우회)
        given(xcrLookupService.resolveXcr(eq("KRW"), any(LocalDate.class))).willReturn(null);

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        BudgetWorkDto.ItemRate rate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0002", 100, 100);
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate));

        // when
        budgetWorkService.applyItemRates(request);

        // then: dupBgAmt = 5,000,000 × 1 × 100/100 = 5,000,000.00 (BigDecimal.ONE fallback)
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        Bbugtm saved = captor.getValue();
        assertThat(saved.getBugRqmBgAmt()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }
}
