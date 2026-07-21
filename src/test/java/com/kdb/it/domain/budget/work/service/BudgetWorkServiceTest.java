package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;

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
    @Mock private org.springframework.data.domain.AuditorAware<String> auditorAware;

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
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result).isEmpty();
        verify(bbugtmRepository, Mockito.times(1)).findByBseYyAndDelYn("2026", "N");
    }

    @Test
    @DisplayName("getIoeCategories - 코드 1개 반환 시 편성률은 null (기존 데이터 없음)")
    void getIoeCategories_기존데이터없음_편성률null() {
        // given: V003 이후 DUP_IOE cdva="237", IOE cdva="001" cNm="237-0700"
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026"))).willReturn(null);

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).cdId()).isEqualTo("237");
        assertThat(result.get(0).prefix()).isEqualTo("237"); // extractPrefix 검증
        assertThat(result.get(0).dupRt()).isNull();           // 기존 편성률 없음
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.ZERO); // null → ZERO
    }

    @Test
    @DisplayName("getIoeCategories - 기존 BBUGTM에 편성률이 있으면 기존 편성률을 반환한다")
    void getIoeCategories_기존편성률있음_편성률반환() {
        // given: V003 이후 DUP_IOE cdva="237", IOE cdva="001" / BBUGTM ioeC="001"
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm existing = Bbugtm.builder()
                .ioeC("001") // V003 이후 단축 cdva 저장
                .asgRt(80)
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(existing));
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.valueOf(1000000));

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result.get(0).dupRt()).isEqualTo(80);
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.valueOf(1000000));
    }

    @Test
    @DisplayName("getIoeCategories - 기존 편성행이 비목 집합과 매칭되지 않으면 편성률 null")
    void getIoeCategories_기존편성행비매칭_편성률null() {
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cdvaDtlC("237-0700").build();
        Bbugtm unmatched = Bbugtm.builder()
                .ioeC("999")
                .asgRt(80)
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(unmatched));
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.TEN);

        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dupRt()).isNull();
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
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // then
        assertThat(result.data()).isEmpty();
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bbugtmRepository, Mockito.times(1)).findByBseYyAndDelYn("2026", "N");
    }

    @Test
    @DisplayName("getSummary: budgetWorkQueryRepository를 단 1회씩 호출한다 — DB-01 N+1 제거")
    void getSummary_집계쿼리_단일호출_N1없음() {
        // given: DUP_IOE 코드 2개 (N+1이면 각 prefix마다 2회씩 = 4회 호출)
        Ccodem code1 = Ccodem.builder().cNm("전산임차료").cdva("DUP-IOE-237").build();
        Ccodem code2 = Ccodem.builder().cNm("자산비").cdva("DUP-IOE-238").build();
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code1, code2));
        mockEmptyDetailCodes();
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any())).willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any())).willReturn(java.util.Map.of());

        // when
        budgetWorkService.getSummary("2026");

        // then: 각각 정확히 1회 호출 (N+1 없음)
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1)).findApprovedCostAmountByIoeC(eq("2026"), any());
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1)).findApprovedItemAmountByGclDtt(eq("2026"), any());
        Mockito.verify(bbugtmRepository, Mockito.never()).findApprovedCostsByIoeCValues(any(), any());
        Mockito.verify(bbugtmRepository, Mockito.never()).findApprovedItemsByIoeCValues(any(), any());
    }

    @Test
    @DisplayName("getSummary - 세부 비목 단위로 편성금액 합계를 올바르게 계산한다")
    void getSummary_세부비목_합계계산() {
        // given: 마이그레이션 후 CCODEM 구조
        // DUP_IOE: cdva="237"(접두어) / IOE: cdva="101", cNm="237-0700"(계층코드), cdvaDtl=표시명
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("237-0700").cdvaDtlC("237-0700").cdvaNm("국외전산임차료").cTp("IOE_IDR")
                .build();
        Bbugtm bbugtm = Bbugtm.builder()
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800000))
                .asgRt(80)
                .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
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

    @Test
    @DisplayName("getSummary: 사업 예정금액을 품목 비율로 차감한다")
    void getSummary_예정금액_비율차감() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101")
                .cNm("237-0700")
                .cdvaDtlC("237-0700")
                .cdvaNm("국내전산임차료")
                .cTp("IOE_LEAFE")
                .cTpDes("전산임차료")
                .build();
        Bbugtm bbugtm = Bbugtm.builder()
                .fntTbNm("BITEMM")
                .pkColNm("GCL-1")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800))
                .asgRt(80)
                .build();
        Bitemm item = Bitemm.builder()
                .gclMngNo("GCL-1")
                .abusMngNo("PRJ-1")
                .amt(BigDecimal.valueOf(1000))
                .xcr(BigDecimal.ONE)
                .mplAmt(BigDecimal.valueOf(500)) // 예정금액: 품목 단위로 관리 (Bprojm.mplMngcAmt 제거 후)
                .build();
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-1")
                .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        // Phase 4 T12: 배치 조회로 변경 (findByGclMngNoInAndDelYn, findByAbusMngNoInAndDelYn)
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).requestAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    @DisplayName("getSummary: 결재완료 원본과 선택 원본만 집계한다")
    void getSummary_승인원본과선택원본필터() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101")
                .cNm("237-0700")
                .cdvaDtlC("237-0700")
                .cdvaNm("국내전산임차료")
                .cTp("IOE_LEAFE")
                .cTpDes("전산임차료")
                .build();
        Bbugtm selected = Bbugtm.builder()
                .pkColNm("SRC-1")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800))
                .asgRt(80)
                .build();
        Bbugtm notSelected = Bbugtm.builder()
                .pkColNm("SRC-2")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(1600))
                .asgRt(80)
                .build();
        Bbugtm notApproved = Bbugtm.builder()
                .pkColNm("SRC-3")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(2400))
                .asgRt(80)
                .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(selected, notSelected, notApproved));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of("SRC-1", "SRC-2"));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(3000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026", List.of("SRC-1"));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("buildPrefixToIoeCValuesMap: 계층코드가 없거나 cdva가 없으면 건너뛴다")
    void buildPrefixToIoeCValuesMap_누락값건너뜀() {
        List<Ccodem> codes = List.of(
                Ccodem.builder().cdva("001").cdvaDtlC("237-0700").build(),
                Ccodem.builder().cdva(null).cdvaDtlC("238-0100").build(),
                Ccodem.builder().cdva("003").cdvaDtlC(null).build(),
                Ccodem.builder().cdva("004").cdvaDtlC("240").build());

        java.util.Map<String, java.util.Set<String>> result = budgetWorkService.buildPrefixToIoeCValuesMap(codes);

        assertThat(result).containsEntry("237", java.util.Set.of("001"));
        assertThat(result).containsEntry("240", java.util.Set.of("004"));
        assertThat(result).doesNotContainKey("238");
    }

    @Test
    @DisplayName("getSummary: IOE C_TP_DES 기준으로 일반관리비 중분류 그룹을 반환한다")
    void getSummary_cTpDes기준_일반관리비그룹분류() {
        List<Ccodem> dupCodes = List.of(
                Ccodem.builder().cNm("237").cdvaDes("전산제비").cdva("237").build(),
                Ccodem.builder().cNm("238").cdvaDes("전산제비").cdva("238").build(),
                Ccodem.builder().cNm("239").cdvaDes("전산제비").cdva("239").build(),
                Ccodem.builder().cNm("240").cdvaDes("전산제비").cdva("240").build());
        List<Ccodem> ioeCodes = List.of(
                Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").cdvaNm("국내전산임차료").cTp("IOE_LEAFE").cTpDes("전산임차료").build(),
                Ccodem.builder().cdva("003").cNm("238-0100").cdvaDtlC("238-0100").cdvaNm("국내출장").cTp("IOE_XPN").cTpDes("전산여비").build(),
                Ccodem.builder().cdva("006").cNm("239-0300").cdvaDtlC("239-0300").cdvaNm("원고강사심사료").cTp("IOE_SEVS").cTpDes("전산용역비").build(),
                Ccodem.builder().cdva("010").cNm("240-0100").cdvaDtlC("240-0100").cdvaNm("회선사용료").cTp("IOE_IDR").cTpDes("전산제비").build());

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(dupCodes);
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(ioeCodes);
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of(
                        "001", BigDecimal.valueOf(100),
                        "003", BigDecimal.valueOf(200),
                        "006", BigDecimal.valueOf(300),
                        "010", BigDecimal.valueOf(400)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).extracting(value -> value.groupName())
                .contains("전산임차료", "전산여비", "전산용역비", "전산제비");
        assertThat(result.data()).filteredOn(item -> "전산임차료".equals(item.groupName()))
                .extracting(value -> value.ioeCategory())
                .containsExactly("국내전산임차료");
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
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then
        assertThat(result.message()).contains("편성률 적용 완료");
        assertThat(result.totalRecords()).isEqualTo(0);
    }

    @Test
    @DisplayName("applyRates는 존재확인을 레코드별이 아닌 테이블별 일괄 조회로 수행한다")
    void applyRates_batchExistenceCheck() {
        // given: rate 1건 + BCOSTM 1건 (기존 테스트 입력 stub 패턴 재사용)
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1_000_000));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        // 존재확인을 테이블별 일괄 조회로 수행: 빈 목록 반환 → INSERT 경로
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N"))).willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        budgetWorkService.applyRates(request);

        // then: 레코드별 존재확인 0회 (테이블별 일괄 조회로 대체)
        Mockito.verify(bbugtmRepository, Mockito.never())
                .findByBseYyAndFntTbNmAndPkColNmAndFntTbCrySnoAndIoeCAndDelYn(any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // applyRates — 기존 레코드 없음 → save 호출 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyRates: 기존 BBUGTM 레코드가 없으면 새 레코드를 save 한다")
    void applyRates_기존레코드없음_save호출() {
        // given: 비목 1개(V003 이후 cdva="237"), BCOSTM 1건, BITEMM 없음
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        // 결재완료 BCOSTM 1건 (V003 이후 ioeC="001")
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1_000_000));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        // 기존 BBUGTM 레코드 없음 → INSERT 경로 (테이블별 일괄 조회 빈 목록)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N"))).willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
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
        // given: 비목 1개(V003 이후 cdva="237"), BCOSTM 1건
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1_000_000));

        // 기존 BBUGTM 레코드 존재 → UPDATE 경로 (테이블별 일괄 조회 키맵에 동일 키로 포함)
        Bbugtm existing = mock(Bbugtm.class);
        given(existing.getPkColNm()).willReturn("COST_2026_0001");
        given(existing.getFntTbCrySno()).willReturn(1);
        given(existing.getIoeC()).willReturn("001");

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of(existing));
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N"))).willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
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
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
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
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(500_000));

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // getSummary 내부 호출용 (선정리는 softDeleteByBseYy 벌크 UPDATE로 수행, findByBseYyAndDelYn 미사용)
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        // 자본예산 비목코드 없음 → 경상 처리
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());
        // BCOSTM LST_YN='Y' 최신 1건 반환
        given(costRepository.findByCostBgNoAndDelYnAndLstYn("COST_2026_0001", "N", "Y"))
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
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));

        // BCOSTM 없음
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026"))).willReturn(List.of());

        // BITEMM 1건 (V003 이후 ioeC="001", 환율 없음 → xcr=null, 기본 1 적용)
        Bitemm item = mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getSno()).willReturn(1);
        given(item.getIoeC()).willReturn("001");
        given(item.getAmt()).willReturn(BigDecimal.valueOf(500_000));
        given(item.getXcr()).willReturn(null);
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026"))).willReturn(List.of(item));

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // 기존 BBUGTM 레코드 없음 → INSERT 경로 (테이블별 일괄 조회 빈 목록)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N"))).willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
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
        given(bitemm.getSno()).willReturn(1);
        given(bitemm.getIoeC()).willReturn("IOE-351-0100");
        given(bitemm.getAmt()).willReturn(BigDecimal.valueOf(1_000_000));
        given(bitemm.getXcr()).willReturn(null);

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // getSummary 내부 호출용 (선정리는 softDeleteByBseYy 벌크 UPDATE로 수행, findByBseYyAndDelYn 미사용)
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        // 자본예산 비목코드 없음 → 경상 처리
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());
        // BPROJM → BITEMM 목록 반환
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
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
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", null);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(null);
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026"))).willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N"))).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        budgetWorkService.applyRates(request);

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("applyItemRates: 기존 편성 레코드는 먼저 논리삭제하고 자본/경상 편성률을 구분 적용한다")
    void applyItemRates_기존삭제와자본경상구분적용() {
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40),
                new BudgetWorkDto.ItemRate("BCOSTM", "COST_2026_0001", null, null)
        ));
        Ccodem capitalCode = Ccodem.builder().cdva("IOE-351-0100").build();
        Bitemm capitalItem = mock(Bitemm.class);
        given(capitalItem.getGclMngNo()).willReturn("GCL-0001");
        given(capitalItem.getSno()).willReturn(1);
        given(capitalItem.getIoeC()).willReturn("IOE-351-0100");
        // BITEMM.amt는 이미 원화(KRW) 정규화 금액 → 편성 계산 시 환율을 다시 곱하지 않는다.
        given(capitalItem.getAmt()).willReturn(BigDecimal.valueOf(1000));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-999-0100");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(500));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        // 실제 인증 사번 경로 검증: AuditorAware가 사번을 제공하면 그 값이 LST_CHG_USID로 전달되어야 한다.
        given(auditorAware.getCurrentAuditor()).willReturn(java.util.Optional.of("ADMINUSER"));
        given(bbugtmRepository.softDeleteByBseYy(org.mockito.ArgumentMatchers.eq("2026"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).willReturn(1);
        // getSummary 내부에서만 조회 (선정리는 더 이상 findByBseYyAndDelYn 사용 안 함)
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of(capitalCode));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(capitalItem));
        given(costRepository.findByCostBgNoAndDelYnAndLstYn("COST_2026_0001", "N", "Y"))
                .willReturn(List.of(cost));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        for (String cttTp : DETAIL_CTT_TPS) {
            if (!"IOE_CPIT".equals(cttTp)) {
                given(codeRepository.findByCIdWithValidDate(cttTp, null)).willReturn(List.of());
            }
        }

        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        verify(bbugtmRepository).softDeleteByBseYy(org.mockito.ArgumentMatchers.eq("2026"),
                org.mockito.ArgumentMatchers.eq("ADMINUSER"), org.mockito.ArgumentMatchers.any());
        assertThat(result.totalRecords()).isEqualTo(2);
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(value -> value.getAsgRt()).containsExactly(60, 100);
        assertThat(captor.getAllValues()).extracting(value -> value.getBgDupAmt())
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("예산작업 - 편성요청액은 품목 예정금액(mplAmt)만큼 차감된다")
    void 예산작업_편성요청액은_품목_예정금액만큼_차감된다() {
        // 시나리오:
        //   - 품목 AMT = 2000, MPL_AMT(예정금액) = 800
        //   - BITEMM → 그룹 합산: groupReqSum = 2000, groupMplSum = 800
        //   - factor = 800/2000 = 0.4
        //   - 비목별 req 차감 = 2000 × 0.4 = 800
        //   - 최종 requestAmount = 원시집계(2000) − 차감(800) = 1200
        //   - 만약 MPL_AMT 를 제외하지 않았다면 requestAmount = 2000 (차이 800 이 명확)
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101")
                .cNm("237-0700")
                .cdvaDtlC("237-0700")
                .cdvaNm("국내전산임차료")
                .cTp("IOE_LEAFE")
                .cTpDes("전산임차료")
                .build();
        Bbugtm bbugtm = Bbugtm.builder()
                .fntTbNm("BITEMM")
                .pkColNm("GCL-MPL-001")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(1600))  // 편성액
                .asgRt(80)
                .build();
        // 품목: AMT=2000, MPL_AMT=800 (예정금액)
        Bitemm item = Bitemm.builder()
                .gclMngNo("GCL-MPL-001")
                .abusMngNo("PRJ-MPL-001")
                .amt(BigDecimal.valueOf(2000))
                .xcr(BigDecimal.ONE)
                .mplAmt(BigDecimal.valueOf(800))
                .build();
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-MPL-001")
                .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        // 결재완료 원본 집계: 비목 "101" → 2000 (raw, MPL_AMT 차감 전)
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(2000)));
        // Phase 4 T12 배치 조회
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        BudgetWorkDto.SummaryItem summaryItem = result.data().get(0);

        // 편성요청액 = 원시집계(2000) − 예정금액비례차감(800) = 1200
        // 만약 차감이 없었다면 2000이 반환되었을 것임 → 명시적 음성 검증
        assertThat(summaryItem.requestAmount())
                .as("예산작업 편성요청액은 품목 예정금액(800)만큼 차감되어 1200이어야 한다")
                .isEqualByComparingTo(BigDecimal.valueOf(1200));
        assertThat(summaryItem.requestAmount())
                .as("예정금액 차감이 적용되지 않은 원시 AMT 합계(2000)면 버그")
                .isNotEqualByComparingTo(BigDecimal.valueOf(2000));

        // 편성액 = 1600 − (1600 × 0.4) = 960
        assertThat(summaryItem.dupAmount())
                .as("편성액도 동일 비율(0.4)로 차감되어 960이어야 한다")
                .isEqualByComparingTo(BigDecimal.valueOf(960));
    }

    @Test
    @DisplayName("getSummary: 그룹 접두어가 있는 세부명과 CCODEM 등록 코드는 BBUGTM 유무와 무관하게 표시된다")
    void getSummary_세부명접두어제거와미등록원본포함() {
        // 마이그레이션 후 CCODEM 구조:
        // DUP_IOE cdva="351" / IOE: cdva="101"(cNm="351-0100", 자본), cdva="102"(cNm="351-9999")
        Ccodem dupCode = Ccodem.builder().cNm("자본그룹").cdvaDes("자본그룹명").cdva("351").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("351-0100").cdvaDtlC("351-0100").cdvaNm("자본그룹 - 개발비").cTp("IOE_CPIT")
                .build();
        // "102"는 CCODEM에 등록된 코드 (cNm에 계층코드 포함하여 "351" 접두어에 매칭됨)
        Ccodem detailCode2 = Ccodem.builder()
                .cdva("102").cNm("351-9999").cdvaDtlC("351-9999")
                .build();
        // BBUGTM에는 "102"만 있음 (dupBgAmt=300)
        Bbugtm budget = Bbugtm.builder()
                .ioeC("102")
                .bgDupAmt(BigDecimal.valueOf(300))
                .asgRt(30)
                .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode, detailCode2));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // cdvaDtl="자본그룹 - 개발비" → stripGroupPrefix → "개발비"
        // cNm="351-9999" (cdvaDtl 없음) → stripGroupPrefix → "351-9999"
        assertThat(result.data()).extracting(value -> value.ioeCategory())
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
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Bbugtm itemBudget = Bbugtm.builder()
                .fntTbNm("BITEMM")
                .pkColNm("GCL-0001")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800))
                .asgRt(80)
                .build();
        Bbugtm costBudget = Bbugtm.builder()
                .fntTbNm("BCOSTM")
                .pkColNm("COST-2026-0001")
                .ioeC("102")
                .bgDupAmt(BigDecimal.valueOf(500))
                .asgRt(50)
                .build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getAbusMngNo()).willReturn("PRJ-2026-0001");
        Bprojm project = mock(Bprojm.class);
        given(project.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(project.getAbusNm()).willReturn("정보화사업");
        CostRepository.CostRepresentativeView cost = mock(CostRepository.CostRepresentativeView.class);
        given(cost.getCostBgNo()).willReturn("COST-2026-0001");
        given(cost.getCttNm()).willReturn("유지보수계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(itemBudget, costBudget));
        // Phase 4 T12: 배치 조회로 변경
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(project));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.data()).extracting(value -> value.name())
                .containsExactly("정보화사업", "유지보수계약");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("1300");
        verify(bbugtmRepository, Mockito.times(1)).findByBseYyAndDelYn("2026", "N");
        assertThat(java.util.Arrays.stream(BbugtmRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.toLowerCase().contains("view"))).isTrue();
    }

    @Test
    @DisplayName("getProjectSummary: 비용 이력 순서와 무관하게 최신 활성 계약명을 선택한다")
    void getProjectSummary_비용이력순서무관_최신활성계약명선택() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Bbugtm costBudget = Bbugtm.builder()
                .fntTbNm("BCOSTM")
                .pkColNm("COST-2026-0001")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(500))
                .asgRt(50)
                .build();
        CostRepository.CostRepresentativeView oldHistory = mock(CostRepository.CostRepresentativeView.class);
        given(oldHistory.getCostBgNo()).willReturn("COST-2026-0001");
        given(oldHistory.getBgSno()).willReturn(2);
        given(oldHistory.getLstYn()).willReturn("N");
        given(oldHistory.getCttNm()).willReturn("이전 계약");
        CostRepository.CostRepresentativeView latestHistory = mock(CostRepository.CostRepresentativeView.class);
        given(latestHistory.getCostBgNo()).willReturn("COST-2026-0001");
        given(latestHistory.getBgSno()).willReturn(1);
        given(latestHistory.getLstYn()).willReturn("Y");
        given(latestHistory.getCttNm()).willReturn("최신 계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(costBudget));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldHistory, latestHistory), List.of(latestHistory, oldHistory));

        BudgetWorkDto.ProjectSummaryResponse first = budgetWorkService.getProjectSummary("2026");
        BudgetWorkDto.ProjectSummaryResponse second = budgetWorkService.getProjectSummary("2026");

        assertThat(first.data()).singleElement().extracting(value -> value.name()).isEqualTo("최신 계약");
        assertThat(second.data()).singleElement().extracting(value -> value.name()).isEqualTo("최신 계약");
    }

    @Test
    @DisplayName("getProjectSummary: BITEMM 요청금액은 품목 예정금액을 제외한다")
    void getProjectSummary_BITEMM요청금액은_예정금액제외() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("101")
                .cNm("237-0100")
                .cdvaDtlC("237-0100")
                .cTp("IOE_LEAFE")
                .build();
        Bbugtm itemBudget = Bbugtm.builder()
                .fntTbNm("BITEMM")
                .pkColNm("GCL-MPL-001")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(1600))
                .asgRt(80)
                .build();
        Bbugtm secondItemBudget = Bbugtm.builder()
                .fntTbNm("BITEMM")
                .pkColNm("GCL-MPL-002")
                .ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800))
                .asgRt(80)
                .build();
        Bitemm item = Bitemm.builder()
                .gclMngNo("GCL-MPL-001")
                .abusMngNo("PRJ-MPL-001")
                .amt(BigDecimal.valueOf(2000))
                .mplAmt(BigDecimal.valueOf(800))
                .build();
        Bitemm secondItem = Bitemm.builder()
                .gclMngNo("GCL-MPL-002")
                .abusMngNo("PRJ-MPL-001")
                .amt(BigDecimal.valueOf(1000))
                .mplAmt(BigDecimal.valueOf(400))
                .build();
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-MPL-001")
                .abusNm("예정금액 제외 사업")
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(itemBudget, secondItemBudget));
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item, secondItem));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of(project));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        BudgetWorkDto.ProjectSummaryItem summaryItem = result.data().get(0);
        BudgetWorkDto.CategoryAmount categoryAmount = summaryItem.categoryAmounts().get("237");
        assertThat(summaryItem.requestAmount()).isEqualByComparingTo("1800.00");
        assertThat(summaryItem.dupAmount()).isEqualByComparingTo("1440.0");
        assertThat(categoryAmount.requestAmount()).isEqualByComparingTo("1800.00");
        assertThat(categoryAmount.dupAmount()).isEqualByComparingTo("1440.0");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo("1800.00");
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("1440.0");
    }

    @Test
    @DisplayName("getProjectSummary: 컬럼명은 편성률 값이 아닌 IOE C_TP_DES를 표시한다")
    void getProjectSummary_컬럼명은CtpDes표시() {
        Ccodem dupCode = Ccodem.builder().cNm("70").cdvaDes("전산임차료 편성 비율").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("001")
                .cNm("237-0700")
                .cdvaDtlC("237-0700")
                .cTpDes("전산임차료")
                .build();
        Bbugtm budget = Bbugtm.builder()
                .fntTbNm("BCOSTM")
                .pkColNm("COST-2026-0001")
                .ioeC("001")
                .bgDupAmt(BigDecimal.valueOf(70))
                .asgRt(70)
                .build();
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST-2026-0001");
        given(cost.getCttNm()).willReturn("임차 계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("전산임차료");
        assertThat(result.categories().get(0).cdNm()).isNotEqualTo("70");
    }

    // =========================================================================
    // resolveIoeGroupName / resolveProjectSummaryCategoryName 분기 커버
    // =========================================================================

    @Test
    @DisplayName("getProjectSummary: cTpDes 없고 cdvaDtl 있으면 cdvaDtl 중분류를 컬럼명으로 사용한다")
    void getProjectSummary_cTpDes없고cdvaDtl중분류사용() {
        // resolveIoeGroupName: cTpDes=null, cdvaDtl="전산임차료 - 국내" → 두 번째 파트 "국내" 반환
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("001").cNm("237-0700").cdvaDtlC("237-0700")
                .cdvaNm(null)
                .cdvaDtl("전산임차료 - 국내")
                .cTpDes(null)
                .build();
        Bbugtm budget = Bbugtm.builder()
                .fntTbNm("BCOSTM").pkColNm("COST-001")
                .ioeC("001").bgDupAmt(BigDecimal.valueOf(100)).asgRt(50)
                .build();
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST-001");
        given(cost.getCttNm()).willReturn("계약A");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // resolveProjectSummaryCategoryName → resolveIoeGroupName → "국내"
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("국내");
    }

    @Test
    @DisplayName("getProjectSummary: cTpDes/cdvaDtl 없고 cdvaDes 있으면 cdvaDes를 컬럼명으로 사용한다")
    void getProjectSummary_cdvaDes폴백컬럼명() {
        // resolveIoeGroupName: cTpDes=null, cdvaDtl=null → cdvaDes 폴백
        Ccodem dupCode = Ccodem.builder().cNm("제비용").cdva("304").build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("005").cNm("304-0100").cdvaDtlC("304-0100")
                .cdvaDtl(null).cTpDes(null)
                .cdvaDes("전산제비용세목")
                .build();
        Bbugtm budget = Bbugtm.builder()
                .fntTbNm("BCOSTM").pkColNm("COST-002")
                .ioeC("005").bgDupAmt(BigDecimal.valueOf(200)).asgRt(80)
                .build();
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST-002");
        given(cost.getCttNm()).willReturn("계약B");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("전산제비용세목");
    }

    @Test
    @DisplayName("getProjectSummary: IOE 세부코드 매칭 없으면 dupCode cdvaNm을 컬럼명으로 사용한다")
    void getProjectSummary_컬럼명cdvaNm폴백() {
        // resolveProjectSummaryCategoryName: ioeDetailCodes가 prefix 매칭 안됨 → cdvaNm 폴백
        Ccodem dupWithCdvaNm = Ccodem.builder().cNm("999").cdva("999").cdvaNm("cdvaNm폴백").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupWithCdvaNm));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("cdvaNm폴백");
    }

    @Test
    @DisplayName("buildPrefixToIoeCValuesMap: cdvaDtlC에 대시가 없으면 전체를 접두어로 사용한다")
    void buildPrefixToIoeCValuesMap_대시없음_전체접두어() {
        // cdvaDtlC="237" (대시 없음) → dashIdx=-1 → prefix=hierarchyCode 전체
        Ccodem codeNoDash = Ccodem.builder().cdva("ABC").cdvaDtlC("237").build();
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdva("237").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(codeNoDash));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026"))).willReturn(BigDecimal.ZERO);

        // getIoeCategories 내부에서 buildPrefixToIoeCValuesMap 호출됨
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // "ABC"가 prefix "237"과 매핑 → ioeCValues에 포함 (cdvaDtlC="237" === prefix "237")
        assertThat(result).hasSize(1);
        assertThat(result.get(0).prefix()).isEqualTo("237");
    }

    @Test
    @DisplayName("applyItemRates: 알 수 없는 원본과 null 비목은 처리 건수 0으로 무시한다")
    void applyItemRates_알수없는원본과Null비목_무시() {
        BudgetWorkDto.ItemApplyRequest request = new BudgetWorkDto.ItemApplyRequest("2026", List.of(
                new BudgetWorkDto.ItemRate("UNKNOWN", "UNK-1", 10, 20),
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40)
        ));
        Ccodem capitalCodeWithoutDash = Ccodem.builder().cdva("IOE351").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getIoeC()).willReturn(null);
        given(item.getAmt()).willReturn(null);
        given(item.getGclMngNo()).willReturn("GCL-NULL");
        given(item.getSno()).willReturn(1);
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of(capitalCodeWithoutDash));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
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
        assertThat(captor.getValue().getAsgRt()).isEqualTo(40);
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // getSummary — dupBgAmt null 분기 커버 (filter(v -> v != null))
    // =========================================================================

    @Test
    @DisplayName("getSummary: dupBg가 null인 BBUGTM은 편성금액 합산에서 제외된다")
    void getSummary_dupBgNull_합산제외() {
        // given: ioeC="101" (cNm="237-0100")에 dupBgAmt=null인 레코드 → filter(v -> v != null) 분기 커버
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("237-0100").cdvaDtlC("237-0100").cdvaNm("국내전산임차료").cTp("IOE_IDR")
                .build();
        // dupBgAmt=null 레코드 (null 필터 분기)
        Bbugtm nullBudget = Bbugtm.builder().ioeC("101").bgDupAmt(null).asgRt(80).build();
        // bgDupAmt=200 정상 레코드
        Bbugtm normalBudget = Bbugtm.builder().ioeC("101").bgDupAmt(BigDecimal.valueOf(200)).asgRt(80).build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(nullBudget, normalBudget));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        // when
        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // then: null은 필터링되어 200만 합산
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // =========================================================================
    // applyItemRates — findCodes("IOE_C") 람다 커버 (lambda$applyItemRates$0)
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: IOE 코드에 자본예산 cTp가 있으면 해당 코드를 자본예산으로 분류한다")
    void applyItemRates_IOE코드에자본예산cTp있음_람다커버() {
        // given: codeRepository.findByCIdWithValidDate("IOE_C", null)이 IOE_CPIT cTp 코드 반환
        // → lambda$applyItemRates$0(isCapitalCTp 필터 람다) 실행
        BudgetWorkDto.ItemRate itemRate = new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 80, 60);
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of(itemRate));

        // IOE 코드에 자본예산 cTp(IOE_CPIT) 있음 → filter 람다 실행
        Ccodem capitalIoeCode = Ccodem.builder().cdva("IOE-351-0100").cTp("IOE_CPIT").build();
        Bitemm bitemm = org.mockito.Mockito.mock(Bitemm.class);
        given(bitemm.getGclMngNo()).willReturn("GCL-0001");
        given(bitemm.getSno()).willReturn(1);
        given(bitemm.getIoeC()).willReturn("IOE-351-0100");
        given(bitemm.getAmt()).willReturn(BigDecimal.valueOf(1_000_000));
        given(bitemm.getXcr()).willReturn(BigDecimal.ONE);

        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(capitalIoeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(bitemm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyItemRates(request);

        // then: 자본예산 편성률(80) 적용 → 1건 처리
        assertThat(result.totalRecords()).isEqualTo(1);
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        // IOE-351-0100 → 자본예산 → assetDupRt=80 적용
        assertThat(captor.getValue().getAsgRt()).isEqualTo(80);
    }

    // =========================================================================
    // applyRates — BITEMM existing.isPresent()=true 분기 커버
    // =========================================================================

    @Test
    @DisplayName("applyRates: BITEMM 원본에 기존 BBUGTM 레코드가 있으면 update를 호출한다")
    void applyRates_BITEMM기존레코드있음_update호출() {
        // given: BITEMM 원본 1건 + 기존 BBUGTM 레코드 존재 → existing.isPresent()=true 분기
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bitemm item = org.mockito.Mockito.mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getSno()).willReturn(1);
        given(item.getIoeC()).willReturn("001");
        given(item.getAmt()).willReturn(BigDecimal.valueOf(500_000));
        given(item.getXcr()).willReturn(BigDecimal.ONE);

        // 기존 BBUGTM 레코드 존재 (BITEMM 경로에서 UPDATE 분기)
        Bbugtm existingBugtm = org.mockito.Mockito.mock(Bbugtm.class);
        // 일괄 조회 키맵에서 "GCL-0001|1|001"로 매칭되도록 키 구성 게터 스텁
        given(existingBugtm.getPkColNm()).willReturn("GCL-0001");
        given(existingBugtm.getFntTbCrySno()).willReturn(1);
        given(existingBugtm.getIoeC()).willReturn("001");

        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026"))).willReturn(List.of());
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026"))).willReturn(List.of(item));
        // BITEMM 경로: existing 있음 (테이블별 일괄 조회로 키맵 구성)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of(existingBugtm));
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N"))).willReturn(List.of());

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then: 1건 처리, existingBugtm.update() 호출 확인
        assertThat(result.totalRecords()).isEqualTo(1);
        verify(existingBugtm).update(any(BigDecimal.class), any(Integer.class));
    }

    // =========================================================================
    // resolveProjectSummaryCategoryName — 세부코드 matchedGroupName=null/blank 분기
    // =========================================================================

    @Test
    @DisplayName("getProjectSummary: IOE 세부코드 groupName이 blank이면 dupCode cNm을 컬럼명으로 사용한다")
    void getProjectSummary_세부코드groupName_blank_cNm폴백() {
        // resolveIoeGroupName: cTpDes=null, cdvaDtl=null, cdvaDes=null → null 반환
        // resolveProjectSummaryCategoryName: groupName null/blank → dupCode.getCNm() 폴백
        Ccodem dupCode = Ccodem.builder().cNm("전산제비용폴백").cdva("304").build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("007").cNm("304-0100").cdvaDtlC("304-0100")
                .cTpDes(null).cdvaDtl(null).cdvaDes(null).cdvaNm(null).cdvaNm(null)
                .build();
        Bbugtm budget = Bbugtm.builder()
                .fntTbNm("BCOSTM").pkColNm("COST-003")
                .ioeC("007").bgDupAmt(BigDecimal.valueOf(100)).asgRt(50)
                .build();
        Bcostm cost = org.mockito.Mockito.mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST-003");
        given(cost.getCttNm()).willReturn("계약C");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of(cost));

        // when
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // then: resolveProjectSummaryCategoryName → groupName blank → dupCode.getCNm() 반환
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("전산제비용폴백");
    }

    @Test
    @DisplayName("getProjectSummary: 매핑이 없으면 원본 PK를 이름으로 사용하고 금액 역산은 건너뛴다")
    void getProjectSummary_이름폴백과금액역산건너뜀() {
        // 마이그레이션 후: DUP_IOE cdva="237", IOE cdva="100"/"101"/"102" (cNm으로 "237-" 접두어 매칭)
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdva("237").build();
        // orcPkVl=null → 처음부터 skip
        Bbugtm nullPk = Bbugtm.builder()
                .fntTbNm("BITEMM").pkColNm(null)
                .ioeC("100").bgDupAmt(BigDecimal.TEN).asgRt(10)
                .build();
        // asgRt=0 → requestAmt 역산 skip, bgDupAmt=100은 합산
        Bbugtm itemNoProject = Bbugtm.builder()
                .fntTbNm("BITEMM").pkColNm("GCL-MISSING")
                .ioeC("101").bgDupAmt(BigDecimal.valueOf(100)).asgRt(0)
                .build();
        // bgDupAmt=null → 금액 미합산
        Bbugtm costNoName = Bbugtm.builder()
                .fntTbNm("BCOSTM").pkColNm("COST-MISSING")
                .ioeC("102").bgDupAmt(null).asgRt(null)
                .build();
        // ioeC="NO-MATCH" → ioeCdvaToHierarchyCode에 없음 → matchedPrefix=null → 금액 skip, 이름은 표시
        Bbugtm unknown = Bbugtm.builder()
                .fntTbNm("UNKNOWN").pkColNm("UNK-1")
                .ioeC("NO-MATCH").bgDupAmt(BigDecimal.ONE).asgRt(50)
                .build();
        Ccodem ioeCode0 = Ccodem.builder().cdva("100").cNm("237-0000").cdvaDtlC("237-0000").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode0, ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(nullPk, itemNoProject, costNoName, unknown));
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).extracting(value -> value.name())
                .contains("GCL-MISSING", "COST-MISSING", "UNK-1");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("getProjectSummary: 컬럼명 후보가 모두 비어 있으면 접두어를 사용한다")
    void getProjectSummary_컬럼명최종prefix폴백() {
        Ccodem dupCode = Ccodem.builder().cdva("999").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("999");
    }

    @Test
    @DisplayName("getProjectSummary: 세부코드 매칭이 없으면 dupCode cdvaNm을 컬럼명으로 사용한다")
    void getProjectSummary_세부코드매칭없음_cdvaNm폴백() {
        Ccodem dupCode = Ccodem.builder()
                .cdva("888")
                .cdvaNm("CDVA명폴백")
                .cNm("CNM폴백")
                .build();
        Ccodem unrelatedIoeCode = Ccodem.builder()
                .cdva("001")
                .cdvaDtlC("777-0100")
                .cTpDes("다른그룹")
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(unrelatedIoeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("CDVA명폴백");
    }

    @Test
    @DisplayName("getProjectSummary: 세부코드 계층명이 없으면 세부코드 설명을 컬럼명으로 사용한다")
    void getProjectSummary_세부코드계층명없음_cdvaDes사용() {
        Ccodem dupCode = Ccodem.builder()
                .cdva("555")
                .cdvaNm("사용되지않는폴백")
                .build();
        Ccodem ioeCode = Ccodem.builder()
                .cdva("005")
                .cdvaDtlC("555-0100")
                .cdvaDtl("계층구분없는문자열")
                .cdvaDes("세부설명컬럼명")
                .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("세부설명컬럼명");
    }

    @Test
    @DisplayName("applyItemRates: 선정리를 루프 delete가 아닌 벌크 UPDATE 1회로 수행한다 (P1 #1)")
    void applyItemRates_선정리_벌크UPDATE단일호출() {
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of());
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(bbugtmRepository.softDeleteByBseYy(eq("2026"), any(), any())).willReturn(3);
        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        budgetWorkService.applyItemRates(request);

        // 선정리는 벌크 UPDATE 1회 — 루프 delete용 선정리 조회는 발생하지 않는다.
        verify(bbugtmRepository).softDeleteByBseYy(eq("2026"), any(), any());
        // getSummary가 부르는 findByBseYyAndDelYn는 정확히 1회 (선정리용 추가 호출 없음)
        Mockito.verify(bbugtmRepository, Mockito.times(1)).findByBseYyAndDelYn("2026", "N");
    }
}
