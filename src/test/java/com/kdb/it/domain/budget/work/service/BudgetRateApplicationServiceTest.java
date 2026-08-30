package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.AuditorAware;

/** 두 편성 적용 경로의 계산·upsert·soft-delete·감사 사용자와 저장 직후 요약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetRateApplicationServiceTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;
    @Mock private AuditorAware<String> auditorAware;

    private BudgetIoeCatalog ioeCatalog;

    private static final List<String> DETAIL_CTT_TPS =
            List.of("IOE_CPIT", "IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

    private void mockEmptyDetailCodes() {
        for (String cttTp : DETAIL_CTT_TPS) {
            given(codeRepository.findByCIdWithValidDate(cttTp, null)).willReturn(List.of());
        }
    }

    private Bitemm itemOf(String gclMngNo, int sno, String ioeC, BigDecimal amt) {
        return Bitemm.builder()
                .gclMngNo(gclMngNo)
                .sno(sno)
                .abusMngNo("PRJ-2026-0001")
                .ioeC(ioeC)
                .amt(amt)
                .build();
    }

    private BudgetSummaryService summaryService;
    private BudgetRateApplicationService budgetWorkService;

    @BeforeEach
    void setUp() {
        ioeCatalog = new BudgetIoeCatalog(codeRepository);
        summaryService =
                new BudgetSummaryService(bbugtmRepository, budgetWorkQueryRepository, ioeCatalog);
        budgetWorkService =
                new BudgetRateApplicationService(
                        bbugtmRepository,
                        projectItemRepository,
                        costRepository,
                        auditorAware,
                        ioeCatalog,
                        summaryService);
    }

    @Test
    @DisplayName("applyRates - rates 목록이 비어있으면 0건 처리 결과를 반환한다")
    void applyRates_빈rates목록_0건처리() {
        // given: rates 없는 요청
        BudgetWorkDto.ApplyRequest request = new BudgetWorkDto.ApplyRequest("2026", List.of());
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1_000_000));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        // 존재확인을 테이블별 일괄 조회로 수행: 빈 목록 반환 → INSERT 경로
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        budgetWorkService.applyRates(request);

        // then: 레코드별 존재확인 0회 (테이블별 일괄 조회로 대체)
        Mockito.verify(bbugtmRepository, Mockito.never())
                .findByBseYyAndFntTbNmAndPkColNmAndFntTbCrySnoAndIoeCAndDelYn(
                        any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // applyRates — 기존 레코드 없음 → save 호출 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyRates: 기존 BBUGTM 레코드가 없으면 새 레코드를 save 한다")
    void applyRates_기존레코드없음_save호출() {
        // given: 비목 1개(V003 이후 cdva="237"), BCOSTM 1건, BITEMM 없음
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        // 결재완료 BCOSTM 1건 (V003 이후 ioeC="001")
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(1_000_000));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        // 기존 BBUGTM 레코드 없음 → INSERT 경로 (테이블별 일괄 조회 빈 목록)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of());

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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

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
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of(existing));
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of());

        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then: 1건 처리, existing.update() 호출 확인 (JPA Dirty Checking)
        assertThat(result.totalRecords()).isEqualTo(1);
        verify(existing).update(any(BigDecimal.class), any(BigDecimal.class));
    }

    // =========================================================================
    // getProjectSummary — 사업별 편성 결과 조회 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: BCOSTM 항목에 대해 편성금액 계산 후 save 한다")
    void applyItemRates_BCOSTM항목_save호출() {
        // given: BCOSTM 원본 1건
        BudgetWorkDto.ItemRate itemRate =
                new BudgetWorkDto.ItemRate(
                        "BCOSTM",
                        "COST_2026_0001",
                        new BigDecimal("100"),
                        new BigDecimal("80"),
                        null);
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of(itemRate));

        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("IOE-237-0700");
        given(cost.getCostTotXpAmt()).willReturn(BigDecimal.valueOf(500_000));

        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));

        // BCOSTM 없음
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());

        // BITEMM 1건 (V003 이후 ioeC="001", 환율 없음 → xcr=null, 기본 1 적용)
        Bitemm item = mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getSno()).willReturn(1);
        given(item.getIoeC()).willReturn("001");
        given(item.getAmt()).willReturn(BigDecimal.valueOf(500_000));
        given(item.getXcr()).willReturn(null);
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(item));

        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        // 기존 BBUGTM 레코드 없음 → INSERT 경로 (테이블별 일괄 조회 빈 목록)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of());

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
        BudgetWorkDto.ItemRate itemRate =
                new BudgetWorkDto.ItemRate(
                        "BPROJM",
                        "PRJ-2026-0001",
                        new BigDecimal("100"),
                        new BigDecimal("80"),
                        null);
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of(itemRate));

        Bitemm bitemm = mock(Bitemm.class);
        given(bitemm.getGclMngNo()).willReturn("GCL-0001");
        given(bitemm.getSno()).willReturn(1);
        given(bitemm.getIoeC()).willReturn("IOE-351-0100");
        given(bitemm.getAmt()).willReturn(BigDecimal.valueOf(1_000_000));
        given(bitemm.getXcr()).willReturn(null);

        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(null);
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(cost));
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of());
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
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        new BigDecimal("60"),
                                        new BigDecimal("40"),
                                        null),
                                new BudgetWorkDto.ItemRate(
                                        "BCOSTM", "COST_2026_0001", null, null, null)));
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
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        // 실제 인증 사번 경로 검증: AuditorAware가 사번을 제공하면 그 값이 LST_CHG_USID로 전달되어야 한다.
        given(auditorAware.getCurrentAuditor()).willReturn(java.util.Optional.of("ADMINUSER"));
        given(
                        bbugtmRepository.softDeleteByBseYy(
                                org.mockito.ArgumentMatchers.eq("2026"),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(1);
        // getSummary 내부에서만 조회 (선정리는 더 이상 findByBseYyAndDelYn 사용 안 함)
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(capitalCode));
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

        verify(bbugtmRepository)
                .softDeleteByBseYy(
                        org.mockito.ArgumentMatchers.eq("2026"),
                        org.mockito.ArgumentMatchers.eq("ADMINUSER"),
                        org.mockito.ArgumentMatchers.any());
        assertThat(result.totalRecords()).isEqualTo(2);
        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(value -> value.getAsgRt())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("60"), new BigDecimal("100"));
        assertThat(captor.getAllValues())
                .extracting(value -> value.getBgDupAmt())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("600.000"), new BigDecimal("500.000"));
    }

    @Test
    @DisplayName("applyItemRates: 알 수 없는 원본과 null 비목은 처리 건수 0으로 무시한다")
    void applyItemRates_알수없는원본과Null비목_무시() {
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate(
                                        "UNKNOWN",
                                        "UNK-1",
                                        new BigDecimal("10"),
                                        new BigDecimal("20"),
                                        null),
                                new BudgetWorkDto.ItemRate(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        new BigDecimal("60"),
                                        new BigDecimal("40"),
                                        null)));
        Ccodem capitalCodeWithoutDash = Ccodem.builder().cdva("IOE351").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getIoeC()).willReturn(null);
        given(item.getAmt()).willReturn(null);
        given(item.getGclMngNo()).willReturn("GCL-NULL");
        given(item.getSno()).willReturn(1);
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(capitalCodeWithoutDash));
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
        assertThat(captor.getValue().getAsgRt()).isEqualByComparingTo("40");
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // getSummary — dupBgAmt null 분기 커버 (filter(v -> v != null))
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: IOE 코드에 자본예산 cTp가 있으면 해당 코드를 자본예산으로 분류한다")
    void applyItemRates_IOE코드에자본예산cTp있음_람다커버() {
        // given: codeRepository.findByCIdWithValidDate("IOE_C", null)이 IOE_CPIT cTp 코드 반환
        // → lambda$applyItemRates$0(isCapitalCTp 필터 람다) 실행
        BudgetWorkDto.ItemRate itemRate =
                new BudgetWorkDto.ItemRate(
                        "BPROJM",
                        "PRJ-2026-0001",
                        new BigDecimal("80"),
                        new BigDecimal("60"),
                        null);
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

        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(capitalIoeCode));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
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
        assertThat(captor.getValue().getAsgRt()).isEqualByComparingTo("80");
    }

    // =========================================================================
    // applyRates — BITEMM existing.isPresent()=true 분기 커버
    // =========================================================================

    @Test
    @DisplayName("applyRates: BITEMM 원본에 기존 BBUGTM 레코드가 있으면 update를 호출한다")
    void applyRates_BITEMM기존레코드있음_update호출() {
        // given: BITEMM 원본 1건 + 기존 BBUGTM 레코드 존재 → existing.isPresent()=true 분기
        BudgetWorkDto.RateItem rateItem = new BudgetWorkDto.RateItem("237", 80);
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

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
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findApprovedCostsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of());
        given(bbugtmRepository.findApprovedItemsByIoeCValues(any(), eq("2026")))
                .willReturn(List.of(item));
        // BITEMM 경로: existing 있음 (테이블별 일괄 조회로 키맵 구성)
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BITEMM"), eq("N")))
                .willReturn(List.of(existingBugtm));
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(any(), eq("BCOSTM"), eq("N")))
                .willReturn(List.of());

        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        // when
        BudgetWorkDto.ApplyResponse result = budgetWorkService.applyRates(request);

        // then: 1건 처리, existingBugtm.update() 호출 확인
        assertThat(result.totalRecords()).isEqualTo(1);
        verify(existingBugtm).update(any(BigDecimal.class), any(BigDecimal.class));
    }

    // =========================================================================
    // resolveProjectSummaryCategoryName — 세부코드 matchedGroupName=null/blank 분기
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: 선정리를 루프 delete가 아닌 벌크 UPDATE 1회로 수행한다 (P1 #1)")
    void applyItemRates_선정리_벌크UPDATE단일호출() {
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest("2026", List.of());
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.softDeleteByBseYy(eq("2026"), any(), any())).willReturn(3);
        // getSummary 내부 호출용 mock
        given(bbugtmRepository.findReadViewsByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of());
        mockEmptyDetailCodes();

        budgetWorkService.applyItemRates(request);

        // 선정리는 벌크 UPDATE 1회 — 루프 delete용 선정리 조회는 발생하지 않는다.
        verify(bbugtmRepository).softDeleteByBseYy(eq("2026"), any(), any());
        // getSummary가 부르는 projection 조회는 정확히 1회 (선정리용 엔티티 조회 없음)
        Mockito.verify(bbugtmRepository, Mockito.times(1))
                .findReadViewsByBseYyAndDelYn("2026", "N");
    }

    // =========================================================================
    // getProjectSummary — 대표행 결정론화 (BE-17)
    // =========================================================================

    @Test
    @DisplayName("applyRates - 비용 한 행을 저장하고 flush한 뒤 같은 쓰기 흐름의 요약을 응답한다")
    void applyRates_한행저장후Flush결과를요약에반영() {
        BudgetSummaryService summaryMock = mock(BudgetSummaryService.class);
        BudgetWorkDto.SummaryResponse expectedSummary =
                new BudgetWorkDto.SummaryResponse(
                        List.of(), new BudgetWorkDto.SummaryTotals(BigDecimal.TEN, BigDecimal.ONE));
        Ccodem ioeCode = Ccodem.builder().cdva("001").cdvaDtlC("237-0700").build();
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-2026-0001")
                        .bgSno(7)
                        .ioeC("001")
                        .costTotXpAmt(BigDecimal.valueOf(1_000_000))
                        .build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn("2026", "BCOSTM", "N"))
                .willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn("2026", "BITEMM", "N"))
                .willReturn(List.of());
        given(
                        bbugtmRepository.findApprovedCostsByIoeCValues(
                                eq(java.util.Set.of("001")), eq("2026")))
                .willReturn(List.of(cost));
        given(
                        bbugtmRepository.findApprovedItemsByIoeCValues(
                                eq(java.util.Set.of("001")), eq("2026")))
                .willReturn(List.of());
        given(summaryMock.getSummary("2026")).willReturn(expectedSummary);
        BudgetRateApplicationService service =
                new BudgetRateApplicationService(
                        bbugtmRepository,
                        projectItemRepository,
                        costRepository,
                        auditorAware,
                        ioeCatalog,
                        summaryMock);

        BudgetWorkDto.ApplyResponse response =
                service.applyRates(
                        new BudgetWorkDto.ApplyRequest(
                                "2026", List.of(new BudgetWorkDto.RateItem("237", 80))));

        ArgumentCaptor<Bbugtm> saved = ArgumentCaptor.forClass(Bbugtm.class);
        InOrder order = inOrder(bbugtmRepository, summaryMock);
        order.verify(bbugtmRepository).save(saved.capture());
        order.verify(bbugtmRepository).flush();
        order.verify(summaryMock).getSummary("2026");
        assertThat(saved.getValue().getBgNo()).isEqualTo("BG-2026-0001");
        assertThat(saved.getValue().getSno()).isEqualTo(1);
        assertThat(saved.getValue().getBseYy()).isEqualTo("2026");
        assertThat(saved.getValue().getFntTbNm()).isEqualTo("BCOSTM");
        assertThat(saved.getValue().getPkColNm()).isEqualTo("COST-2026-0001");
        assertThat(saved.getValue().getFntTbCrySno()).isEqualTo(7);
        assertThat(saved.getValue().getIoeC()).isEqualTo("001");
        assertThat(saved.getValue().getBgDupAmt())
                .isEqualByComparingTo(BigDecimal.valueOf(800_000));
        assertThat(saved.getValue().getAsgRt()).isEqualByComparingTo("80");
        assertThat(response.totalRecords()).isEqualTo(1);
        assertThat(response.summary()).isSameAs(expectedSummary);
    }

    @Test
    @DisplayName("applyItemRates - 사업별 비용 한 행을 저장하고 flush한 뒤 같은 쓰기 흐름의 요약을 응답한다")
    void applyItemRates_한행저장후Flush결과를요약에반영() {
        BudgetSummaryService summaryMock = mock(BudgetSummaryService.class);
        BudgetWorkDto.SummaryResponse expectedSummary =
                new BudgetWorkDto.SummaryResponse(
                        List.of(), new BudgetWorkDto.SummaryTotals(BigDecimal.ONE, BigDecimal.TEN));
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-2026-0002")
                        .bgSno(3)
                        .ioeC("IOE-237-0700")
                        .costTotXpAmt(BigDecimal.valueOf(500_000))
                        .build();
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(2L);
        given(auditorAware.getCurrentAuditor()).willReturn(java.util.Optional.of("TESTER"));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());
        given(costRepository.findByCostBgNoAndDelYnAndLstYn("COST-2026-0002", "N", "Y"))
                .willReturn(List.of(cost));
        given(summaryMock.getSummary("2026")).willReturn(expectedSummary);
        BudgetRateApplicationService service =
                new BudgetRateApplicationService(
                        bbugtmRepository,
                        projectItemRepository,
                        costRepository,
                        auditorAware,
                        ioeCatalog,
                        summaryMock);

        BudgetWorkDto.ApplyResponse response =
                service.applyItemRates(
                        new BudgetWorkDto.ItemApplyRequest(
                                "2026",
                                List.of(
                                        new BudgetWorkDto.ItemRate(
                                                "BCOSTM",
                                                "COST-2026-0002",
                                                new BigDecimal("100"),
                                                new BigDecimal("80"),
                                                null))));

        ArgumentCaptor<Bbugtm> saved = ArgumentCaptor.forClass(Bbugtm.class);
        InOrder order = inOrder(bbugtmRepository, summaryMock);
        order.verify(bbugtmRepository).save(saved.capture());
        order.verify(bbugtmRepository).flush();
        order.verify(summaryMock).getSummary("2026");
        assertThat(saved.getValue().getBgNo()).isEqualTo("BG-2026-0002");
        assertThat(saved.getValue().getSno()).isEqualTo(1);
        assertThat(saved.getValue().getBseYy()).isEqualTo("2026");
        assertThat(saved.getValue().getFntTbNm()).isEqualTo("BCOSTM");
        assertThat(saved.getValue().getPkColNm()).isEqualTo("COST-2026-0002");
        assertThat(saved.getValue().getFntTbCrySno()).isEqualTo(3);
        assertThat(saved.getValue().getIoeC()).isEqualTo("IOE-237-0700");
        assertThat(saved.getValue().getBgDupAmt())
                .isEqualByComparingTo(BigDecimal.valueOf(400_000));
        assertThat(saved.getValue().getAsgRt()).isEqualByComparingTo("80");
        assertThat(response.totalRecords()).isEqualTo(1);
        assertThat(response.summary()).isSameAs(expectedSummary);
    }

    @Test
    @DisplayName("applyItemRates_소수편성률_저장값이_소수로_보존된다")
    void applyItemRates_소수편성률_저장값이_소수로_보존된다() {
        // 이 테스트는 Task 2에서 ioeRates 경로로 옮겨간다. 여기서는 엔티티 계약만 고정한다.
        Bbugtm budget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .bseYy("2026")
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-2026-0001")
                        .fntTbCrySno(1)
                        .ioeC("106")
                        .bgDupAmt(new BigDecimal("416000000.000"))
                        .asgRt(new BigDecimal("29.58748"))
                        .build();

        assertThat(budget.getAsgRt()).isEqualByComparingTo("29.58748");

        budget.update(new BigDecimal("984000000.000"), new BigDecimal("70.00000"));
        assertThat(budget.getAsgRt()).isEqualByComparingTo("70.00000");
        assertThat(budget.getBgDupAmt()).isEqualByComparingTo("984000000.000");
    }

    // =========================================================================
    // applyItemRates — 비목별 편성률(ioeRates) 경로 (Task 2)
    // =========================================================================

    @Test
    @DisplayName("applyItemRates_비목별편성률_비목마다_다른_편성률이_적용된다")
    void applyItemRates_비목별편성률_비목마다_다른_편성률이_적용된다() {
        Bitemm dev = itemOf("GCL-2026-0001", 1, "103", new BigDecimal("1000"));
        Bitemm hw = itemOf("GCL-2026-0002", 1, "101", new BigDecimal("2000"));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(dev, hw));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null)).willReturn(List.of());

        BudgetWorkDto.ItemRate rate =
                new BudgetWorkDto.ItemRate(
                        "BPROJM",
                        "PRJ-2026-0001",
                        new BigDecimal("100"),
                        new BigDecimal("100"),
                        Map.of(
                                "103", new BigDecimal("70.00000"),
                                "101", new BigDecimal("29.58748")));

        budgetWorkService.applyItemRates(new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate)));

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository, Mockito.times(2)).save(captor.capture());
        Map<String, Bbugtm> saved =
                captor.getAllValues().stream()
                        .collect(Collectors.toMap(Bbugtm::getIoeC, Function.identity()));

        assertThat(saved.get("103").getAsgRt()).isEqualByComparingTo("70.00000");
        assertThat(saved.get("103").getBgDupAmt()).isEqualByComparingTo("700.000");
        assertThat(saved.get("101").getAsgRt()).isEqualByComparingTo("29.58748");
        assertThat(saved.get("101").getBgDupAmt()).isEqualByComparingTo("591.750");
    }

    @Test
    @DisplayName("applyItemRates_ioeRates가_비어있으면_종전_2버킷이_적용된다")
    void applyItemRates_ioeRates가_비어있으면_종전_2버킷이_적용된다() {
        Bitemm dev = itemOf("GCL-2026-0001", 1, "103", new BigDecimal("1000"));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(dev));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        // "103"을 자본예산 비목으로 분류해 2버킷 중 assetDupRt(70)가 적용되도록 한다.
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(Ccodem.builder().cdva("103").build()));

        budgetWorkService.applyItemRates(
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        new BigDecimal("70"),
                                        new BigDecimal("100"),
                                        null))));

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getAsgRt()).isEqualByComparingTo("70");
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo("700.000");
    }

    /**
     * MIG-15: 2버킷 편성률도 소수를 그대로 저장해야 한다.
     *
     * <p>{@code ASG_RT}는 소수 5자리를 담을 수 있고 비목별 경로({@code ioeRates})는 이미 {@code BigDecimal}이지만, 2버킷
     * ({@code assetDupRt}·{@code costDupRt})은 {@code Integer}라 Jackson이 {@code 70.5}를 조용히 {@code
     * 70}으로 잘랐다. `/budget/work` 화면이 이 경로를 쓴다.
     */
    @Test
    @DisplayName("applyItemRates_2버킷_편성률의_소수를_그대로_저장한다")
    void applyItemRates_2버킷_편성률의_소수를_그대로_저장한다() {
        Bitemm dev = itemOf("GCL-2026-0001", 1, "103", new BigDecimal("1000"));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(dev));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(Ccodem.builder().cdva("103").build()));

        budgetWorkService.applyItemRates(
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        new BigDecimal("70.5"),
                                        new BigDecimal("100"),
                                        null))));

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getAsgRt()).isEqualByComparingTo("70.5");
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo("705.000");
    }

    @Test
    @DisplayName("applyItemRates_ioeRates가_일부_비목만_지정되면_나머지는_2버킷으로_떨어진다")
    void applyItemRates_ioeRates가_일부_비목만_지정되면_나머지는_2버킷으로_떨어진다() {
        Bitemm dev = itemOf("GCL-2026-0001", 1, "103", new BigDecimal("1000"));
        Bitemm hw = itemOf("GCL-2026-0002", 1, "101", new BigDecimal("2000"));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(dev, hw));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        // "101"만 자본예산으로 분류해, 맵에 없는 비목의 2버킷 폴백이 assetDupRt를 고르는지 함께 확인한다.
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(Ccodem.builder().cdva("101").build()));

        BudgetWorkDto.ItemRate rate =
                new BudgetWorkDto.ItemRate(
                        "BPROJM",
                        "PRJ-2026-0001",
                        new BigDecimal("55"),
                        new BigDecimal("20"),
                        Map.of("103", new BigDecimal("45.50000")));

        budgetWorkService.applyItemRates(new BudgetWorkDto.ItemApplyRequest("2026", List.of(rate)));

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository, Mockito.times(2)).save(captor.capture());
        Map<String, Bbugtm> saved =
                captor.getAllValues().stream()
                        .collect(Collectors.toMap(Bbugtm::getIoeC, Function.identity()));

        // "103"은 ioeRates에 명시돼 있으므로 자본/일반 분류와 무관하게 그 값을 그대로 쓴다.
        assertThat(saved.get("103").getAsgRt()).isEqualByComparingTo("45.50000");
        assertThat(saved.get("103").getBgDupAmt()).isEqualByComparingTo("455.000");
        // "101"은 ioeRates에 없어 2버킷으로 떨어지고, 자본예산으로 분류돼 assetDupRt(55)를 받는다.
        assertThat(saved.get("101").getAsgRt()).isEqualByComparingTo("55");
        assertThat(saved.get("101").getBgDupAmt()).isEqualByComparingTo("1100.000");
    }

    @Test
    @DisplayName("applyItemRates_ioeRates가_빈맵이면_종전_2버킷이_적용된다")
    void applyItemRates_ioeRates가_빈맵이면_종전_2버킷이_적용된다() {
        Bitemm dev = itemOf("GCL-2026-0001", 1, "103", new BigDecimal("1000"));
        given(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn("PRJ-2026-0001", "N", "Y"))
                .willReturn(List.of(dev));
        given(bbugtmRepository.nextBgMngNoSeq()).willReturn(1L);
        given(codeRepository.findByCIdWithValidDate("IOE_CPIT", null))
                .willReturn(List.of(Ccodem.builder().cdva("103").build()));

        budgetWorkService.applyItemRates(
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        new BigDecimal("70"),
                                        new BigDecimal("100"),
                                        Map.of()))));

        ArgumentCaptor<Bbugtm> captor = ArgumentCaptor.forClass(Bbugtm.class);
        verify(bbugtmRepository).save(captor.capture());
        assertThat(captor.getValue().getAsgRt()).isEqualByComparingTo("70");
        assertThat(captor.getValue().getBgDupAmt()).isEqualByComparingTo("700.000");
    }
}
