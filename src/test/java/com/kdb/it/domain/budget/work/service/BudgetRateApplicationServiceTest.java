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
    @Mock private ProjectRepository projectRepository;
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

    private BudgetSummaryService summaryService;
    private BudgetRateApplicationService budgetWorkService;

    @BeforeEach
    void setUp() {
        ioeCatalog = new BudgetIoeCatalog(codeRepository);
        summaryService =
                new BudgetSummaryService(
                        bbugtmRepository,
                        budgetWorkQueryRepository,
                        projectRepository,
                        projectItemRepository,
                        ioeCatalog);
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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));

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
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
        verify(existing).update(any(BigDecimal.class), any(Integer.class));
    }

    // =========================================================================
    // getProjectSummary — 사업별 편성 결과 조회 (신규)
    // =========================================================================

    @Test
    @DisplayName("applyItemRates: BCOSTM 항목에 대해 편성금액 계산 후 save 한다")
    void applyItemRates_BCOSTM항목_save호출() {
        // given: BCOSTM 원본 1건
        BudgetWorkDto.ItemRate itemRate =
                new BudgetWorkDto.ItemRate("BCOSTM", "COST_2026_0001", 100, 80);
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

        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 100, 80);
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
        BudgetWorkDto.ApplyRequest request =
                new BudgetWorkDto.ApplyRequest("2026", List.of(rateItem));
        Bcostm cost = mock(Bcostm.class);
        given(cost.getCostBgNo()).willReturn("COST_2026_0001");
        given(cost.getBgSno()).willReturn(1);
        given(cost.getIoeC()).willReturn("001");
        given(cost.getCostTotXpAmt()).willReturn(null);
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
                                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40),
                                new BudgetWorkDto.ItemRate(
                                        "BCOSTM", "COST_2026_0001", null, null)));
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
                .containsExactly(60, 100);
        assertThat(captor.getAllValues())
                .extracting(value -> value.getBgDupAmt())
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("applyItemRates: 알 수 없는 원본과 null 비목은 처리 건수 0으로 무시한다")
    void applyItemRates_알수없는원본과Null비목_무시() {
        BudgetWorkDto.ItemApplyRequest request =
                new BudgetWorkDto.ItemApplyRequest(
                        "2026",
                        List.of(
                                new BudgetWorkDto.ItemRate("UNKNOWN", "UNK-1", 10, 20),
                                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 60, 40)));
        Ccodem capitalCodeWithoutDash = Ccodem.builder().cdva("IOE351").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getIoeC()).willReturn(null);
        given(item.getAmt()).willReturn(null);
        given(item.getGclMngNo()).willReturn("GCL-NULL");
        given(item.getSno()).willReturn(1);
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
        assertThat(captor.getValue().getAsgRt()).isEqualTo(40);
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
                new BudgetWorkDto.ItemRate("BPROJM", "PRJ-2026-0001", 80, 60);
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
        given(bbugtmRepository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
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
        verify(existingBugtm).update(any(BigDecimal.class), any(Integer.class));
    }

    // =========================================================================
    // resolveProjectSummaryCategoryName — 세부코드 matchedGroupName=null/blank 분기
    // =========================================================================

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

    // =========================================================================
    // getProjectSummary — 대표행 결정론화 (BE-17)
    // =========================================================================

    @Test
    @DisplayName("applyRates - 저장 내용을 flush한 뒤 같은 쓰기 흐름에서 만든 요약을 응답한다")
    void applyRates_저장후Flush결과를요약에반영() {
        BbugtmRepository repository = mock(BbugtmRepository.class);
        BudgetSummaryService summaryService = mock(BudgetSummaryService.class);
        BudgetIoeCatalog ioeCatalog = mock(BudgetIoeCatalog.class);
        BudgetWorkDto.SummaryResponse expectedSummary =
                new BudgetWorkDto.SummaryResponse(
                        List.of(), new BudgetWorkDto.SummaryTotals(BigDecimal.TEN, BigDecimal.ONE));
        given(repository.generateBgMngNo("2026")).willReturn("BG-2026-0001");
        given(repository.findByBseYyAndFntTbNmAndDelYn("2026", "BCOSTM", "N"))
                .willReturn(List.of());
        given(repository.findByBseYyAndFntTbNmAndDelYn("2026", "BITEMM", "N"))
                .willReturn(List.of());
        given(ioeCatalog.findCodes("IOE_C")).willReturn(List.of());
        given(summaryService.getSummary("2026")).willReturn(expectedSummary);
        BudgetRateApplicationService service =
                new BudgetRateApplicationService(
                        repository,
                        mock(ProjectItemRepository.class),
                        mock(CostRepository.class),
                        mock(AuditorAware.class),
                        ioeCatalog,
                        summaryService);

        BudgetWorkDto.ApplyResponse response =
                service.applyRates(new BudgetWorkDto.ApplyRequest("2026", List.of()));

        InOrder order = inOrder(repository, summaryService);
        order.verify(repository).flush();
        order.verify(summaryService).getSummary("2026");
        assertThat(response.summary()).isSameAs(expectedSummary);
    }
}
