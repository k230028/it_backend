package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
import org.mockito.Mockito;

/**
 * BudgetWorkService 단위 테스트
 *
 * <p>
 * BbugtmRepository, CodeRepository를 Mock 처리하여 Oracle DB 없이
 * 편성비목 조회, 편성률 적용, 편성 결과 조회 로직을 검증합니다.
 * </p>
 *
 * <p>
 * 핵심 검증 대상:
 * <ul>
 * <li>extractPrefix: "DUP-IOE-237" → "IOE-237" 변환</li>
 * <li>calculateDupBg: 요청금액 × (편성률/100) 계산 (HALF_UP 반올림)</li>
 * <li>getIoeCategories: IOE 코드별 편성률 + 요청금액 반환</li>
 * <li>getSummary: 비목별 요약 + 합계 반환</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetWorkServiceTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;

    @InjectMocks
    private BudgetWorkService budgetWorkService;

    // =========================================================================
    // getIoeCategories — 편성비목 목록 조회
    // =========================================================================

    @Test
    @DisplayName("getIoeCategories - DUP_IOE 코드가 없으면 빈 목록 반환")
    void getIoeCategories_코드없음_빈목록반환() {
        // given
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getIoeCategories - 코드 1개 반환 시 편성률은 null (기존 데이터 없음)")
    void getIoeCategories_기존데이터없음_편성률null() {
        // given: DUP-IOE-237 코드 1개
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("DUP-IOE-237").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(bbugtmRepository.sumApprovedAmountByPrefix("IOE-237", "2026")).willReturn(null);

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).cdId()).isEqualTo("DUP-IOE-237");
        assertThat(result.get(0).prefix()).isEqualTo("IOE-237"); // extractPrefix 검증
        assertThat(result.get(0).dupRt()).isNull();               // 기존 편성률 없음
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.ZERO); // null → ZERO
    }

    @Test
    @DisplayName("getIoeCategories - 기존 BBUGTM에 편성률이 있으면 기존 편성률을 반환한다")
    void getIoeCategories_기존편성률있음_편성률반환() {
        // given
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("DUP-IOE-237").build();
        Bbugtm existing = Bbugtm.builder()
                .ioeC("IOE-237-0700")
                .dupRt(80)
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(existing));
        given(bbugtmRepository.sumApprovedAmountByPrefix("IOE-237", "2026"))
                .willReturn(BigDecimal.valueOf(1000000));

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result.get(0).dupRt()).isEqualTo(80);
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.valueOf(1000000));
    }

    // =========================================================================
    // getSummary — 편성 결과 조회
    // =========================================================================

    /** 세부 비목 코드 타입 목록 (getSummary에서 조회하는 cttTp들) */
    private static final List<String> DETAIL_CTT_TPS = List.of(
            "IOE_CPIT", "IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

    /** 세부 코드 조회 mock 헬퍼: 모든 세부 cttTp에 대해 빈 목록 반환 */
    private void mockEmptyDetailCodes() {
        for (String cttTp : DETAIL_CTT_TPS) {
            given(codeRepository.findByCIdWithValidDate(cttTp, null)).willReturn(List.of());
        }
    }

    @Test
    @DisplayName("getSummary - 비목이 없으면 빈 목록과 합계 0을 반환한다")
    void getSummary_비목없음_빈결과반환() {
        // given
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // then
        assertThat(result.data()).isEmpty();
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getSummary: budgetWorkQueryRepository를 단 1회씩 호출한다 — DB-01 N+1 제거")
    void getSummary_집계쿼리_단일호출_N1없음() {
        // given: DUP_IOE 코드 2개 (N+1이면 각 prefix마다 2회씩 = 4회 호출)
        Ccodem code1 = Ccodem.builder().cNm("전산임차료").cdva("DUP-IOE-237").build();
        Ccodem code2 = Ccodem.builder().cNm("자산비").cdva("DUP-IOE-238").build();
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code1, code2));
        mockEmptyDetailCodes();
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC("2026")).willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt("2026")).willReturn(java.util.Map.of());

        // when
        budgetWorkService.getSummary("2026");

        // then: 각각 정확히 1회 호출 (N+1 없음)
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1)).findApprovedCostAmountByIoeC("2026");
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1)).findApprovedItemAmountByGclDtt("2026");
        Mockito.verify(bbugtmRepository, Mockito.never()).findApprovedCostsByPrefix(any(), any());
        Mockito.verify(bbugtmRepository, Mockito.never()).findApprovedItemsByPrefix(any(), any());
    }

    @Test
    @DisplayName("getSummary - 세부 비목 단위로 편성금액 합계를 올바르게 계산한다")
    void getSummary_세부비목_합계계산() {
        // given: 마이그레이션 후 CCODEM 구조
        // DUP_IOE: cdva="237"(접두어) / IOE: cdva="101", cNm="237-0700"(계층코드), cdvaDtl=표시명
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("237-0700").cdvaDtl("국외전산임차료").cTp("IOE_IDR")
                .build();
        Bbugtm bbugtm = Bbugtm.builder()
                .ioeC("101")
                .dupBg(BigDecimal.valueOf(800000))
                .dupRt(80)
                .build();

        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC("2026"))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt("2026"))
                .willReturn(java.util.Map.of());

        // when
        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // then: 세부 ioeC 단위로 1건 반환, 요청금액은 결재완료 원본 데이터 기반
        assertThat(result.data()).hasSize(1);
        BudgetWorkDto.SummaryItem item = result.data().get(0);
        assertThat(item.ioeCategory()).isEqualTo("국외전산임차료");
        assertThat(item.ioeC()).isEqualTo("101");
        assertThat(item.groupName()).isEqualTo("전산임차료");
        assertThat(item.capital()).isFalse(); // IOE_IDR = 일반관리비
        assertThat(item.dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800000));
        assertThat(item.requestAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000000));
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000000));
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800000));
    }

    // =========================================================================
    // applyRates — 편성률 일괄 적용 (경계값/계산 검증)
    // =========================================================================

    @Test
    @DisplayName("applyRates - rates 목록이 비어있으면 0건 처리 결과를 반환한다")
    void applyRates_빈rates목록_0건처리() {
        // given: rates 없는 요청
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of());
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then
        assertThat(result.message()).contains("편성률 적용 완료");
        assertThat(result.totalRecords()).isEqualTo(0);
    }

    // =========================================================================
    // applyRates — 기존 레코드 없음 → save 호출 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyRates: 기존 BBUGTM 레코드가 없으면 새 레코드를 save 한다")
    void applyRates_기존레코드없음_save호출() {
        // given: 비목 1개, BCOSTM 1건, BITEMM 없음
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("DUP-IOE-237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        // 결재완료 BCOSTM 1건 (mock으로 protected 생성자 우회)
        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost.getItMngcSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getItMngcBg()).willReturn(BigDecimal.valueOf(1_000_000));

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByPrefix("IOE-237", "2026"))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByPrefix("IOE-237", "2026"))
                .willReturn(List.of());
        // 기존 BBUGTM 레코드 없음 → INSERT 경로
        given(bbugtmRepository.findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                any(), any(), any(), any(), any(), any()))
                .willReturn(Optional.empty());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then: 1건 처리, bbugtmRepository.save() 호출 확인
        assertThat(result.totalRecords()).isEqualTo(1);
        assertThat(result.message()).contains("편성률 적용 완료");
        verify(bbugtmRepository).save(any(Bbugtm.class));
    }

    @Test
    @DisplayName("applyRates: 기존 BBUGTM 레코드가 있으면 update를 호출하고 save는 하지 않는다")
    void applyRates_기존레코드있음_update호출() {
        // given: 비목 1개, BCOSTM 1건
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("DUP-IOE-237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost.getItMngcSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getItMngcBg()).willReturn(BigDecimal.valueOf(1_000_000));

        // 기존 BBUGTM 레코드 존재 → UPDATE 경로
        Bbugtm existing = mock(Bbugtm.class);

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByPrefix("IOE-237", "2026"))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByPrefix("IOE-237", "2026"))
                .willReturn(List.of());
        given(bbugtmRepository.findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                any(), any(), any(), any(), any(), any()))
                .willReturn(Optional.of(existing));

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then: 1건 처리, existing.update() 호출 확인 (JPA Dirty Checking)
        assertThat(result.totalRecords()).isEqualTo(1);
        verify(existing).update(any(BigDecimal.class), any(Integer.class));
    }

    // =========================================================================
    // getProjectSummary — 사업별 편성 결과 조회 (신규)
    // =========================================================================

    @Test
    @DisplayName("getProjectSummary: BBUGTM 데이터가 없으면 빈 사업 목록과 합계 0을 반환한다")
    void getProjectSummary_데이터없음_빈목록반환() {
        // given: 빈 데이터
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());

        // when
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // then
        assertThat(result.data()).isEmpty();
        assertThat(result.categories()).isEmpty();
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // applyItemRates — 사업별 편성률 적용 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: BCOSTM 항목에 대해 편성금액 계산 후 save 한다")
    void applyItemRates_BCOSTM항목_save호출() {
        // given: BCOSTM 원본 1건
        BudgetWorkDto.ItemRate itemRate = new BudgetWorkDto.ItemRate(
                "BCOSTM", "COST_2026_0001", 100, 80);
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of(itemRate));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost.getItMngcSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getItMngcBg()).willReturn(BigDecimal.valueOf(500_000));

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // 기존 BBUGTM Soft Delete 대상 없음
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        // 자본예산 비목코드 없음 → 경상 처리
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());
        // BCOSTM LST_YN='Y' 최신 1건 반환
        given(costRepository.findByItMngcNoAndDelYnAndLstYn("COST_2026_0001", "N", "Y"))
                .willReturn(List.of(cost));

        // getSummary 내부 호출용 mock
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        // then: 1건 처리, save() 호출 확인
        assertThat(result.totalRecords()).isEqualTo(1);
        assertThat(result.message()).contains("사업별 편성률 적용 완료");
        verify(bbugtmRepository).save(any(Bbugtm.class));
    }

    // =========================================================================
    // applyRates — BITEMM 경로 검증
    // =========================================================================

    @Test
    @DisplayName("applyRates: BITEMM 원본 항목이 있으면 편성금액 계산 후 save 한다")
    void applyRates_BITEMM항목_save호출() {
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("DUP-IOE-237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        // BCOSTM 없음
        given(bbugtmRepository.findApprovedCostsByPrefix("IOE-237", "2026")).willReturn(List.of());

        // BITEMM 1건 (환율 없음 → xcr=null, 기본 1 적용)
        Bitemm item = mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getGclSno()).willReturn(1);
        given(item.getIoeC()).willReturn("IOE-237-0700");
        given(item.getGclAmt()).willReturn(BigDecimal.valueOf(500_000));
        given(item.getXcr()).willReturn(null);
        given(bbugtmRepository.findApprovedItemsByPrefix("IOE-237", "2026")).willReturn(List.of(item));

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                any(), any(), any(), any(), any(), any()))
                .willReturn(Optional.empty());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        assertThat(result.totalRecords()).isEqualTo(1);
        assertThat(result.message()).contains("편성률 적용 완료");
        verify(bbugtmRepository).save(any(Bbugtm.class));
    }

    // =========================================================================
    // applyItemRates — BPROJM 경로 검증
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: BPROJM 사업의 품목에 대해 편성금액 계산 후 save 한다")
    void applyItemRates_BPROJM사업_save호출() {
        BudgetWorkDto.ItemRate itemRate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 100, 80);
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of(itemRate));

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getGclMngNo()).willReturn("GCL-0001");
        given(bitemm.getGclSno()).willReturn(1);
        given(bitemm.getIoeC()).willReturn("IOE-351-0100");
        given(bitemm.getGclAmt()).willReturn(BigDecimal.valueOf(1_000_000));
        given(bitemm.getXcr()).willReturn(null);

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // 기존 BBUGTM Soft Delete 대상 없음
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        // 자본예산 비목코드 없음 → 경상 처리
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());
        // BPROJM → BITEMM 목록 반환
        given(projectItemRepository.findByPrjMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(bitemm));

        // getSummary 내부 호출용 mock
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        assertThat(result.totalRecords()).isEqualTo(1);
        assertThat(result.message()).contains("사업별 편성률 적용 완료");
        verify(bbugtmRepository).save(any(Bbugtm.class));
    }

    @Test
    @DisplayName("applyRates: 요청금액 또는 편성률이 null이면 편성금액을 0으로 저장한다")
    void applyRates_null금액또는편성률_0원저장() {
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("DUP-IOE-237", null);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost.getItMngcSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getItMngcBg()).willReturn(null);
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByPrefix("IOE-237", "2026")).willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByPrefix("IOE-237", "2026")).willReturn(List.of());
        given(bbugtmRepository.findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                any(), any(), any(), any(), any(), any())).willReturn(Optional.empty());
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        budgetWorkService.applyRates(request);

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("applyItemRates: 기존 편성 레코드는 먼저 논리삭제하고 자본/경상 편성률을 구분 적용한다")
    void applyItemRates_기존삭제와자본경상구분적용() {
        Bbugtm prior = Bbugtm.builder().bgMngNo("BG-OLD").bgSno(1).delYn("N").build();
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40),
                new BudgetWorkDto.ItemRate("BCOSTM", "COST_2026_0001", null, null)
        ));
        Ccodem capitalCode = Ccodem.builder().cdva("IOE-351-0100").build();
        Bitemm capitalItem = mock(Bitemm.class);
        given(capitalItem.getGclMngNo()).willReturn("GCL-0001");
        given(capitalItem.getGclSno()).willReturn(1);
        given(capitalItem.getIoeC()).willReturn("IOE-351-0100");
        given(capitalItem.getGclAmt()).willReturn(BigDecimal.valueOf(1000));
        given(capitalItem.getXcr()).willReturn(BigDecimal.valueOf(2));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getItMngcNo()).willReturn("COST_2026_0001");
        given(cost.getItMngcSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-999-0100");
        given(cost.getItMngcBg()).willReturn(BigDecimal.valueOf(500));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(prior), List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of(capitalCode));
        given(projectItemRepository.findByPrjMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(capitalItem));
        given(costRepository.findByItMngcNoAndDelYnAndLstYn("COST_2026_0001", "N", "Y"))
                .willReturn(List.of(cost));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        for (String cttTp : DETAIL_CTT_TPS) {
            if (!"IOE_CPIT".equals(cttTp)) {
                given(codeRepository.findByCIdWithValidDate(cttTp, null)).willReturn(List.of());
            }
        }

        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        assertThat(prior.getDelYn()).isEqualTo("Y");
        assertThat(result.totalRecords()).isEqualTo(2);
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Bbugtm::getDupRt).containsExactly(60, 100);
        assertThat(captor.getAllValues()).extracting(Bbugtm::getDupBg)
                .containsExactly(new BigDecimal("1200.00"), new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("getSummary: 그룹 접두어가 있는 세부명과 CCODEM 등록 코드는 BBUGTM 유무와 무관하게 표시된다")
    void getSummary_세부명접두어제거와미등록원본포함() {
        // 마이그레이션 후 CCODEM 구조:
        // DUP_IOE cdva="351" / IOE: cdva="101"(cNm="351-0100", 자본), cdva="102"(cNm="351-9999")
        Ccodem dupCode = Ccodem.builder().cNm("자본그룹").cDes("자본그룹명").cdva("351").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("351-0100").cdvaDtl("자본그룹 - 개발비").cTp("IOE_CPIT")
                .build();
        // "102"는 CCODEM에 등록된 코드 (cNm에 계층코드 포함하여 "351" 접두어에 매칭됨)
        Ccodem detailCode2 = Ccodem.builder()
                .cdva("102").cNm("351-9999")
                .build();
        // BBUGTM에는 "102"만 있음 (dupBg=300)
        Bbugtm budget = Bbugtm.builder()
                .ioeC("102")
                .dupBg(BigDecimal.valueOf(300))
                .dupRt(30)
                .build();

        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of(detailCode, detailCode2));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC("2026"))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt("2026"))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // cdvaDtl="자본그룹 - 개발비" → stripGroupPrefix → "개발비"
        // cNm="351-9999" (cdvaDtl 없음) → stripGroupPrefix → "351-9999"
        assertThat(result.data()).extracting(BudgetWorkDto.SummaryItem::ioeCategory)
                .contains("개발비", "351-9999");
        assertThat(result.data()).anySatisfy(item -> {
            if ("개발비".equals(item.ioeCategory())) {
                assertThat(item.capital()).isTrue();
                assertThat(item.requestAmount()).isEqualByComparingTo("1000");
            }
        });
    }

    @Test
    @DisplayName("getProjectSummary: BITEMM은 프로젝트로 통합하고 BCOSTM은 계약명으로 표시한다")
    void getProjectSummary_BITEMM프로젝트통합과BCOSTM계약명표시() {
        // 마이그레이션 후: DUP_IOE cdva="237", IOE cdva="101"(cNm="237-0100"), cdva="102"(cNm="237-0200")
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cDes("임차료").cdva("237").build();
        Bbugtm itemBudget = Bbugtm.builder()
                .orcTb("BITEMM")
                .orcPkVl("GCL-0001")
                .ioeC("101")
                .dupBg(BigDecimal.valueOf(800))
                .dupRt(80)
                .build();
        Bbugtm costBudget = Bbugtm.builder()
                .orcTb("BCOSTM")
                .orcPkVl("COST-2026-0001")
                .ioeC("102")
                .dupBg(BigDecimal.valueOf(500))
                .dupRt(50)
                .build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getPrjMngNo()).willReturn("PRJ-2026-0001");
        Bprojm project = mock(Bprojm.class);
        given(project.getPrjNm()).willReturn("정보화사업");
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCttNm()).willReturn("유지보수계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of(ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(itemBudget, costBudget));
        given(projectItemRepository.findByGclMngNoAndDelYn("GCL-0001", "N")).willReturn(List.of(item));
        given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N")).willReturn(Optional.of(project));
        given(costRepository.findByItMngcNoAndDelYn("COST-2026-0001", "N")).willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.data()).extracting(BudgetWorkDto.ProjectSummaryItem::name)
                .containsExactly("정보화사업", "유지보수계약");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("1300");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("applyItemRates: 알 수 없는 원본과 null 비목은 처리 건수 0으로 무시한다")
    void applyItemRates_알수없는원본과Null비목_무시() {
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(
                new BudgetWorkDto.ItemRate("UNKNOWN", "UNK-1", 10, 20),
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40)
        ));
        Ccodem capitalCodeWithoutDash = Ccodem.builder().cdva("IOE351").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getIoeC()).willReturn(null);
        given(item.getGclAmt()).willReturn(null);
        given(item.getGclMngNo()).willReturn("GCL-NULL");
        given(item.getGclSno()).willReturn(1);
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N")).willReturn(List.of(), List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of(capitalCodeWithoutDash));
        given(projectItemRepository.findByPrjMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(item));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        for (String cttTp : DETAIL_CTT_TPS) {
            if (!"IOE_CPIT".equals(cttTp)) {
                given(codeRepository.findByCIdWithValidDate(cttTp, null)).willReturn(List.of());
            }
        }

        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        assertThat(result.totalRecords()).isEqualTo(1);
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getDupRt()).isEqualTo(40);
        assertThat(captor.getValue().getDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getProjectSummary: 매핑이 없으면 원본 PK를 이름으로 사용하고 금액 역산은 건너뛴다")
    void getProjectSummary_이름폴백과금액역산건너뜀() {
        // 마이그레이션 후: DUP_IOE cdva="237", IOE cdva="100"/"101"/"102" (cNm으로 "237-" 접두어 매칭)
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdva("237").build();
        // orcPkVl=null → 처음부터 skip
        Bbugtm nullPk = Bbugtm.builder()
                .orcTb("BITEMM").orcPkVl(null)
                .ioeC("100").dupBg(BigDecimal.TEN).dupRt(10)
                .build();
        // dupRt=0 → requestAmt 역산 skip, dupBg=100은 합산
        Bbugtm itemNoProject = Bbugtm.builder()
                .orcTb("BITEMM").orcPkVl("GCL-MISSING")
                .ioeC("101").dupBg(BigDecimal.valueOf(100)).dupRt(0)
                .build();
        // dupBg=null → 금액 미합산
        Bbugtm costNoName = Bbugtm.builder()
                .orcTb("BCOSTM").orcPkVl("COST-MISSING")
                .ioeC("102").dupBg(null).dupRt(null)
                .build();
        // ioeC="NO-MATCH" → ioeCdvaToHierarchyCode에 없음 → matchedPrefix=null → 금액 skip, 이름은 표시
        Bbugtm unknown = Bbugtm.builder()
                .orcTb("UNKNOWN").orcPkVl("UNK-1")
                .ioeC("NO-MATCH").dupBg(BigDecimal.ONE).dupRt(50)
                .build();
        Ccodem ioeCode0 = Ccodem.builder().cdva("100").cNm("237-0000").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE", null)).willReturn(List.of(ioeCode0, ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBgYyAndDelYn("2026", "N"))
                .willReturn(List.of(nullPk, itemNoProject, costNoName, unknown));
        given(projectItemRepository.findByGclMngNoAndDelYn("GCL-MISSING", "N")).willReturn(List.of());
        given(projectRepository.findByPrjMngNoAndDelYn("GCL-MISSING", "N")).willReturn(Optional.empty());
        given(costRepository.findByItMngcNoAndDelYn("COST-MISSING", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).extracting(BudgetWorkDto.ProjectSummaryItem::name)
                .contains("GCL-MISSING", "COST-MISSING", "UNK-1");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("100");
    }
}
