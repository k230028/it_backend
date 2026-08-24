package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.AuditorAware;

/** 비목 목록·편성 요약과 승인 원본·대표행 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetSummaryServiceTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;
    @Mock private AuditorAware<String> auditorAware;

    private BudgetIoeCatalog ioeCatalog;

    private BudgetSummaryService budgetWorkService;

    @BeforeEach
    void setUp() {
        given(bbugtmRepository.findReadViewsByBseYyAndDelYn(anyString(), anyString()))
                .willAnswer(
                        invocation ->
                                bbugtmRepository
                                        .findByBseYyAndDelYn(
                                                invocation.getArgument(0),
                                                invocation.getArgument(1))
                                        .stream()
                                        .map(ReadProjectionStubs::budget)
                                        .toList());
        ioeCatalog = new BudgetIoeCatalog(codeRepository);
        budgetWorkService =
                new BudgetSummaryService(bbugtmRepository, budgetWorkQueryRepository, ioeCatalog);
    }

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
        assertThat(result.get(0).dupRt()).isNull(); // 기존 편성률 없음
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.ZERO); // null → ZERO
    }

    @Test
    @DisplayName("getIoeCategories - 기존 BBUGTM에 편성률이 있으면 기존 편성률을 반환한다")
    void getIoeCategories_기존편성률있음_편성률반환() {
        // given: V003 이후 DUP_IOE cdva="237", IOE cdva="001" / BBUGTM ioeC="001"
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm existing =
                Bbugtm.builder()
                        .ioeC("001") // V003 이후 단축 cdva 저장
                        .asgRt(new BigDecimal("80"))
                        .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(existing));
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.valueOf(1000000));

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then
        assertThat(result.get(0).dupRt()).isEqualByComparingTo("80");
        assertThat(result.get(0).requestAmount()).isEqualTo(BigDecimal.valueOf(1000000));
    }

    @Test
    @DisplayName("getIoeCategories - 기존 편성행이 비목 집합과 매칭되지 않으면 편성률 null")
    void getIoeCategories_기존편성행비매칭_편성률null() {
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cdvaDtlC("237-0700").build();
        Bbugtm unmatched = Bbugtm.builder().ioeC("999").asgRt(new BigDecimal("80")).build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(unmatched));
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.TEN);

        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dupRt()).isNull();
    }

    @Test
    @DisplayName("getIoeCategories - 혼합 편성률이면 최신 편성 실행(bgNo 최대) 행의 편성률을 반환한다")
    void getIoeCategories_혼합편성률_최신bgNo행기준() {
        // given: 같은 비목 집합에 편성률이 다른 두 행 — 리스트 앞에 구 실행(80), 뒤에 신 실행(50)
        Ccodem code = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm olderRun =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm newerRun =
                Bbugtm.builder()
                        .bgNo("BG-2026-0002")
                        .sno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("50"))
                        .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(code));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(olderRun, newerRun));
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.TEN);

        // when
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // then: encounter order(80)가 아니라 최신 편성 실행(50) 기준
        assertThat(result.get(0).dupRt()).isEqualByComparingTo("50");
    }

    // =========================================================================
    // getSummary — 편성 결과 조회
    // =========================================================================

    /** 세부 비목 코드 타입 목록 (getSummary에서 조회하는 cttTp들) */
    private static final List<String> DETAIL_CTT_TPS =
            List.of("IOE_CPIT", "IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

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
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .willReturn(List.of(code1, code2));
        mockEmptyDetailCodes();
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        // when
        budgetWorkService.getSummary("2026");

        // then: 각각 정확히 1회 호출 (N+1 없음)
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1))
                .findApprovedCostAmountByIoeC(eq("2026"), any());
        Mockito.verify(budgetWorkQueryRepository, Mockito.times(1))
                .findApprovedItemAmountByGclDtt(eq("2026"), any());
        Mockito.verify(bbugtmRepository, Mockito.never())
                .findApprovedCostsByIoeCValues(any(), any());
        Mockito.verify(bbugtmRepository, Mockito.never())
                .findApprovedItemsByIoeCValues(any(), any());
    }

    @Test
    @DisplayName("getSummary - 세부 비목 단위로 편성금액 합계를 올바르게 계산한다")
    void getSummary_세부비목_합계계산() {
        // given: 마이그레이션 후 CCODEM 구조
        // DUP_IOE: cdva="237"(접두어) / IOE: cdva="101", cNm="237-0700"(계층코드), cdvaDtl=표시명
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국외전산임차료")
                        .cTp("IOE_IDR")
                        .build();
        Bbugtm bbugtm =
                Bbugtm.builder()
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800000))
                        .asgRt(new BigDecimal("80"))
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
        assertThat(result.totals().requestAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(1000000));
        assertThat(result.totals().dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800000));
    }

    @Test
    @DisplayName("getSummary - 같은 표시명은 sno가 작아도 최신 bgNo 대표행의 비목코드와 편성률을 함께 사용한다")
    void getSummary_같은표시명_최신bgNo대표행의비목코드와편성률사용() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem olderCode =
                Ccodem.builder()
                        .cdva("101")
                        .cdvaDtlC("237-0100")
                        .cdvaNm("공통 표시명")
                        .cTp("IOE_IDR")
                        .build();
        Ccodem representativeCode =
                Ccodem.builder()
                        .cdva("102")
                        .cdvaDtlC("237-0200")
                        .cdvaNm("공통 표시명")
                        .cTp("IOE_IDR")
                        .build();
        Bbugtm older =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(99)
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm representative =
                Bbugtm.builder()
                        .bgNo("BG-2026-0002")
                        .sno(1)
                        .ioeC("102")
                        .bgDupAmt(BigDecimal.valueOf(500))
                        .asgRt(new BigDecimal("50"))
                        .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(representative, older));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(olderCode, representativeCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(
                        java.util.Map.of(
                                "101", BigDecimal.valueOf(1000),
                                "102", BigDecimal.valueOf(1000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryItem result = budgetWorkService.getSummary("2026").data().get(0);

        assertThat(result.ioeC()).isEqualTo("102");
        assertThat(result.dupRt()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("getSummary: 사업 예정금액과 무관하게 승인 원본과 편성액을 그대로 합산한다")
    void getSummary_예정금액이있어도_원본금액그대로합산() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm bbugtm =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-1")
                        .abusMngNo("PRJ-1")
                        .amt(BigDecimal.valueOf(1000))
                        .xcr(BigDecimal.ONE)
                        .mplAmt(BigDecimal.valueOf(500)) // 예정금액: 품목 단위로 관리 (Bprojm.mplMngcAmt 제거 후)
                        .build();
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-1").build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        // Phase 4 T12: 배치 조회로 변경 (findByGclMngNoInAndDelYn, findByAbusMngNoInAndDelYn)
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).requestAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("getSummary: 결재완료 원본과 선택 원본만 집계한다")
    void getSummary_승인원본과선택원본필터() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm selected =
                Bbugtm.builder()
                        .pkColNm("SRC-1")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm notSelected =
                Bbugtm.builder()
                        .pkColNm("SRC-2")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(1600))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm notApproved =
                Bbugtm.builder()
                        .pkColNm("SRC-3")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(2400))
                        .asgRt(new BigDecimal("80"))
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

        BudgetWorkDto.SummaryResponse result =
                budgetWorkService.getSummary("2026", List.of("SRC-1"));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("buildPrefixToIoeCValuesMap: 계층코드가 없거나 cdva가 없으면 건너뛴다")
    void buildPrefixToIoeCValuesMap_누락값건너뜀() {
        List<Ccodem> codes =
                List.of(
                        Ccodem.builder().cdva("001").cdvaDtlC("237-0700").build(),
                        Ccodem.builder().cdva(null).cdvaDtlC("238-0100").build(),
                        Ccodem.builder().cdva("003").cdvaDtlC(null).build(),
                        Ccodem.builder().cdva("004").cdvaDtlC("240").build());

        java.util.Map<String, java.util.Set<String>> result =
                ioeCatalog.buildPrefixToIoeCValuesMap(codes);

        assertThat(result).containsEntry("237", java.util.Set.of("001"));
        assertThat(result).containsEntry("240", java.util.Set.of("004"));
        assertThat(result).doesNotContainKey("238");
    }

    @Test
    @DisplayName("getSummary: IOE C_TP_DES 기준으로 일반관리비 중분류 그룹을 반환한다")
    void getSummary_cTpDes기준_일반관리비그룹분류() {
        List<Ccodem> dupCodes =
                List.of(
                        Ccodem.builder().cNm("237").cdvaDes("전산제비").cdva("237").build(),
                        Ccodem.builder().cNm("238").cdvaDes("전산제비").cdva("238").build(),
                        Ccodem.builder().cNm("239").cdvaDes("전산제비").cdva("239").build(),
                        Ccodem.builder().cNm("240").cdvaDes("전산제비").cdva("240").build());
        List<Ccodem> ioeCodes =
                List.of(
                        Ccodem.builder()
                                .cdva("001")
                                .cNm("237-0700")
                                .cdvaDtlC("237-0700")
                                .cdvaNm("국내전산임차료")
                                .cTp("IOE_LEAFE")
                                .cTpDes("전산임차료")
                                .build(),
                        Ccodem.builder()
                                .cdva("003")
                                .cNm("238-0100")
                                .cdvaDtlC("238-0100")
                                .cdvaNm("국내출장")
                                .cTp("IOE_XPN")
                                .cTpDes("전산여비")
                                .build(),
                        Ccodem.builder()
                                .cdva("006")
                                .cNm("239-0300")
                                .cdvaDtlC("239-0300")
                                .cdvaNm("원고강사심사료")
                                .cTp("IOE_SEVS")
                                .cTpDes("전산용역비")
                                .build(),
                        Ccodem.builder()
                                .cdva("010")
                                .cNm("240-0100")
                                .cdvaDtlC("240-0100")
                                .cdvaNm("회선사용료")
                                .cTp("IOE_IDR")
                                .cTpDes("전산제비")
                                .build());

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(dupCodes);
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(ioeCodes);
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(
                        java.util.Map.of(
                                "001", BigDecimal.valueOf(100),
                                "003", BigDecimal.valueOf(200),
                                "006", BigDecimal.valueOf(300),
                                "010", BigDecimal.valueOf(400)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data())
                .extracting(value -> value.groupName())
                .contains("전산임차료", "전산여비", "전산용역비", "전산제비");
        assertThat(result.data())
                .filteredOn(item -> "전산임차료".equals(item.groupName()))
                .extracting(value -> value.ioeCategory())
                .containsExactly("국내전산임차료");
    }

    // =========================================================================
    // applyRates — 편성률 일괄 적용 (경계값/계산 검증)
    // =========================================================================

    @Test
    @DisplayName("예산작업 - 품목 예정금액이 있어도 당해 요청액과 편성액을 차감하지 않는다")
    void 예산작업_품목예정금액이있어도_당해금액을차감하지않는다() {
        // 시나리오:
        //   - 품목 AMT = 2000, MPL_AMT(예정금액) = 800
        //   - AMT는 이미 당해 원화이므로 MPL_AMT를 다시 차감하지 않는다.
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm bbugtm =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-MPL-001")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(1600)) // 편성액
                        .asgRt(new BigDecimal("80"))
                        .build();
        // 품목: AMT=2000, MPL_AMT=800 (예정금액)
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-MPL-001")
                        .abusMngNo("PRJ-MPL-001")
                        .amt(BigDecimal.valueOf(2000))
                        .xcr(BigDecimal.ONE)
                        .mplAmt(BigDecimal.valueOf(800))
                        .build();
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-MPL-001").build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        // 결재완료 원본 집계: 비목 "101" → 당해 요청액 2000
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(2000)));
        // Phase 4 T12 배치 조회
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        BudgetWorkDto.SummaryItem summaryItem = result.data().get(0);

        // MPL_AMT=800을 재차 빼면 1200이 되는 이중 차감을 막는다.
        assertThat(summaryItem.requestAmount())
                .as("예산작업 편성요청액은 AMT 원본 2000이어야 한다")
                .isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(summaryItem.requestAmount())
                .as("MPL 800을 다시 차감한 1200이면 이중 차감 버그")
                .isNotEqualByComparingTo(BigDecimal.valueOf(1200));

        assertThat(summaryItem.dupAmount())
                .as("저장 당해 편성액도 1600 원본을 유지해야 한다")
                .isEqualByComparingTo(BigDecimal.valueOf(1600));
    }

    @Test
    @DisplayName("getSummary - BITEMM 구버전 행이 앞에 와도 당해 원본 금액을 유지한다")
    void getSummary_BITEMM구버전행이앞이어도_당해원본금액유지() {
        // 시나리오: 같은 gclMngNo(GCL-MPL-002)의 구버전(N, PRJ-OLD)이 리스트 앞, 최신(Y, PRJ-MPL-002)이 뒤.
        // 어느 이력 행이 대표가 되더라도 AMT 원본을 MPL로 다시 차감하지 않는다.
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm bbugtm =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-MPL-002")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(1600)) // 편성액
                        .asgRt(new BigDecimal("80"))
                        .build();
        // 구버전(N): 리스트 앞, 다른 사업번호(PRJ-OLD)·다른 금액 — putIfAbsent였다면 이 행이 채택된다.
        Bitemm oldVersion =
                Bitemm.builder()
                        .gclMngNo("GCL-MPL-002")
                        .sno(1)
                        .lstYn("N")
                        .abusMngNo("PRJ-OLD")
                        .amt(BigDecimal.valueOf(500))
                        .xcr(BigDecimal.ONE)
                        .mplAmt(BigDecimal.valueOf(100))
                        .build();
        // 최신(Y): 리스트 뒤, 실제 대표로 채택되어야 하는 행
        Bitemm latest =
                Bitemm.builder()
                        .gclMngNo("GCL-MPL-002")
                        .sno(2)
                        .lstYn("Y")
                        .abusMngNo("PRJ-MPL-002")
                        .amt(BigDecimal.valueOf(2000))
                        .xcr(BigDecimal.ONE)
                        .mplAmt(BigDecimal.valueOf(800))
                        .build();
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-MPL-002").build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        // 결재완료 원본 집계: 비목 "101" → 당해 요청액 2000
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(2000)));
        // 배치 조회: 구버전이 앞, 최신이 뒤 순서로 반환 (encounter order 함정 재현)
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldVersion, latest));
        // 사업 마스터는 최신 행의 사업번호(PRJ-MPL-002)만 존재한다.
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        BudgetWorkDto.SummaryItem summaryItem = result.data().get(0);

        assertThat(summaryItem.requestAmount())
                .as("AMT 2000은 MPL 800과 무관하게 그대로여야 한다")
                .isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(summaryItem.requestAmount())
                .as("MPL을 차감한 1200이면 이중 차감 버그")
                .isNotEqualByComparingTo(BigDecimal.valueOf(1200));
    }

    @Test
    @DisplayName("getSummary: 그룹 접두어가 있는 세부명과 CCODEM 등록 코드는 BBUGTM 유무와 무관하게 표시된다")
    void getSummary_세부명접두어제거와미등록원본포함() {
        // 마이그레이션 후 CCODEM 구조:
        // DUP_IOE cdva="351" / IOE: cdva="101"(cNm="351-0100", 자본), cdva="102"(cNm="351-9999")
        Ccodem dupCode = Ccodem.builder().cNm("자본그룹").cdvaDes("자본그룹명").cdva("351").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("351-0100")
                        .cdvaDtlC("351-0100")
                        .cdvaNm("자본그룹 - 개발비")
                        .cTp("IOE_CPIT")
                        .build();
        // "102"는 CCODEM에 등록된 코드 (cNm에 계층코드 포함하여 "351" 접두어에 매칭됨)
        Ccodem detailCode2 =
                Ccodem.builder().cdva("102").cNm("351-9999").cdvaDtlC("351-9999").build();
        // BBUGTM에는 "102"만 있음 (dupBgAmt=300)
        Bbugtm budget =
                Bbugtm.builder()
                        .ioeC("102")
                        .bgDupAmt(BigDecimal.valueOf(300))
                        .asgRt(new BigDecimal("30"))
                        .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(detailCode, detailCode2));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of());

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // cdvaDtl="자본그룹 - 개발비" → stripGroupPrefix → "개발비"
        // cNm="351-9999" (cdvaDtl 없음) → stripGroupPrefix → "351-9999"
        assertThat(result.data())
                .extracting(value -> value.ioeCategory())
                .contains("개발비", "351-9999");
        assertThat(result.data())
                .anySatisfy(
                        item -> {
                            if ("개발비".equals(item.ioeCategory())) {
                                assertThat(item.capital()).isTrue();
                                assertThat(item.requestAmount()).isEqualByComparingTo("1000");
                            }
                        });
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
        given(bbugtmRepository.sumApprovedAmountByIoeCValues(any(), eq("2026")))
                .willReturn(BigDecimal.ZERO);

        // getIoeCategories 내부에서 buildPrefixToIoeCValuesMap 호출됨
        List<BudgetWorkDto.IoeCategoryResponse> result = budgetWorkService.getIoeCategories("2026");

        // "ABC"가 prefix "237"과 매핑 → ioeCValues에 포함 (cdvaDtlC="237" === prefix "237")
        assertThat(result).hasSize(1);
        assertThat(result.get(0).prefix()).isEqualTo("237");
    }

    @Test
    @DisplayName("getSummary: dupBg가 null인 BBUGTM은 편성금액 합산에서 제외된다")
    void getSummary_dupBgNull_합산제외() {
        // given: ioeC="101" (cNm="237-0100")에 dupBgAmt=null인 레코드 → filter(v -> v != null) 분기 커버
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0100")
                        .cdvaDtlC("237-0100")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_IDR")
                        .build();
        // dupBgAmt=null 레코드 (null 필터 분기)
        Bbugtm nullBudget =
                Bbugtm.builder().ioeC("101").bgDupAmt(null).asgRt(new BigDecimal("80")).build();
        // bgDupAmt=200 정상 레코드
        Bbugtm normalBudget =
                Bbugtm.builder()
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(200))
                        .asgRt(new BigDecimal("80"))
                        .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(nullBudget, normalBudget));
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
    // getSummary — 품목 예정금액과 무관한 승인 원본·편성액 집계
    // =========================================================================

    @Test
    @DisplayName("getSummary: 예정금액이 있어도 품목·사업 조회 없이 승인 원본과 편성액을 합산한다")
    void getSummary_예정금액이있어도_원본금액을합산한다() {
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm bbugtm =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(bbugtm));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).requestAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800));

        verify(projectItemRepository, never()).findByGclMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never()).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never())
                .findNameViewByAbusMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(anyString(), anyString());
    }

    @Test
    @DisplayName("getSummary - 품목 예정금액이 요청금액 이상이어도 당해 원본 금액을 유지한다")
    void getSummary_예정금액이요청금액이상이어도_원본금액유지() {
        Ccodem duplicateCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode =
                Ccodem.builder()
                        .cdva("101")
                        .cdvaDtlC("237-0700")
                        .cdvaNm("국내전산임차료")
                        .cTp("IOE_LEAFE")
                        .build();
        Bbugtm budget =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-1")
                        .abusMngNo("PRJ-1")
                        .amt(BigDecimal.valueOf(1000))
                        .mplAmt(BigDecimal.valueOf(1500))
                        .build();

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .willReturn(List.of(duplicateCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(detailCode));
        given(budgetWorkQueryRepository.findApprovedCostAmountByIoeC(eq("2026"), any()))
                .willReturn(java.util.Map.of());
        given(budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(eq("2026"), any()))
                .willReturn(java.util.Map.of("101", BigDecimal.valueOf(1000)));
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(Bprojm.builder().abusMngNo("PRJ-1").build()));

        BudgetWorkDto.SummaryItem result = budgetWorkService.getSummary("2026").data().get(0);

        assertThat(result.requestAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }
}
