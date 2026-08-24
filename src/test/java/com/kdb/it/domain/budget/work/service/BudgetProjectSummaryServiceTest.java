package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

/** 사업·비용 네임스페이스, 대표행, 배치 조회와 비목 합계를 검증합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetProjectSummaryServiceTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;
    @Mock private AuditorAware<String> auditorAware;

    private BudgetIoeCatalog ioeCatalog;

    private BudgetProjectSummaryService budgetWorkService;

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
        given(
                        projectRepository.findKeyViewsByAbusMngNoInAndLstYnAndDelYn(
                                anyCollection(), anyString(), anyString()))
                .willAnswer(
                        invocation ->
                                projectRepository
                                        .findByAbusMngNoInAndDelYn(
                                                invocation.getArgument(0),
                                                invocation.getArgument(2))
                                        .stream()
                                        .filter(project -> !"N".equals(project.getLstYn()))
                                        .map(ReadProjectionStubs::project)
                                        .toList());
        ioeCatalog = new BudgetIoeCatalog(codeRepository);
        budgetWorkService =
                new BudgetProjectSummaryService(
                        bbugtmRepository,
                        budgetWorkQueryRepository,
                        projectRepository,
                        projectItemRepository,
                        costRepository,
                        ioeCatalog);
    }

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
    @DisplayName("getProjectSummary: BITEMM은 프로젝트로 통합하고 BCOSTM은 계약명으로 표시한다")
    void getProjectSummary_BITEMM프로젝트통합과BCOSTM계약명표시() {
        // 마이그레이션 후: DUP_IOE cdva="237", IOE cdva="101"(cNm="237-0100"), cdva="102"(cNm="237-0200")
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Bbugtm itemBudget =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-0001")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm costBudget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0001")
                        .ioeC("102")
                        .bgDupAmt(BigDecimal.valueOf(500))
                        .asgRt(new BigDecimal("50"))
                        .build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();
        Bitemm item = mock(Bitemm.class);
        given(item.getGclMngNo()).willReturn("GCL-0001");
        given(item.getAbusMngNo()).willReturn("PRJ-2026-0001");
        Bprojm project = mock(Bprojm.class);
        given(project.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(project.getAbusNm()).willReturn("정보화사업");
        given(project.getLstYn()).willReturn("Y");
        CostRepository.CostRepresentativeView cost =
                mock(CostRepository.CostRepresentativeView.class);
        given(cost.getCostBgNo()).willReturn("COST-2026-0001");
        given(cost.getCttNm()).willReturn("유지보수계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(itemBudget, costBudget));
        // Phase 4 T12: 배치 조회로 변경
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(project));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(cost));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.data())
                .extracting(value -> value.name())
                .containsExactly("정보화사업", "유지보수계약");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("1300");
        verify(bbugtmRepository, Mockito.times(1)).findByBseYyAndDelYn("2026", "N");
        verify(bbugtmRepository, Mockito.times(1)).findReadViewsByBseYyAndDelYn("2026", "N");
    }

    @Test
    @DisplayName("getProjectSummary: 비용 이력 순서와 무관하게 최신 활성 계약명을 선택한다")
    void getProjectSummary_비용이력순서무관_최신활성계약명선택() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Bbugtm costBudget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0001")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(500))
                        .asgRt(new BigDecimal("50"))
                        .build();
        CostRepository.CostRepresentativeView oldHistory =
                mock(CostRepository.CostRepresentativeView.class);
        given(oldHistory.getCostBgNo()).willReturn("COST-2026-0001");
        given(oldHistory.getBgSno()).willReturn(2);
        given(oldHistory.getLstYn()).willReturn("N");
        given(oldHistory.getCttNm()).willReturn("이전 계약");
        CostRepository.CostRepresentativeView latestHistory =
                mock(CostRepository.CostRepresentativeView.class);
        given(latestHistory.getCostBgNo()).willReturn("COST-2026-0001");
        given(latestHistory.getBgSno()).willReturn(1);
        given(latestHistory.getLstYn()).willReturn("Y");
        given(latestHistory.getCttNm()).willReturn("최신 계약");
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(costBudget));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldHistory, latestHistory))
                .willReturn(List.of(latestHistory, oldHistory));

        BudgetWorkDto.ProjectSummaryResponse first = budgetWorkService.getProjectSummary("2026");
        BudgetWorkDto.ProjectSummaryResponse second = budgetWorkService.getProjectSummary("2026");

        assertThat(first.data())
                .singleElement()
                .extracting(value -> value.name())
                .isEqualTo("최신 계약");
        assertThat(second.data())
                .singleElement()
                .extracting(value -> value.name())
                .isEqualTo("최신 계약");
    }

    @Test
    @DisplayName("getProjectSummary: BITEMM 당해 요청액과 편성액은 품목 예정금액을 제외하지 않는다")
    void getProjectSummary_BITEMM당해금액은_예정금액을제외하지않는다() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode =
                Ccodem.builder()
                        .cdva("101")
                        .cNm("237-0100")
                        .cdvaDtlC("237-0100")
                        .cTp("IOE_LEAFE")
                        .build();
        Bbugtm itemBudget =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-MPL-001")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(1600))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-MPL-001")
                        .abusMngNo("PRJ-MPL-001")
                        .amt(BigDecimal.valueOf(2000))
                        .mplAmt(BigDecimal.valueOf(800))
                        .build();
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-MPL-001").abusNm("예정금액 포함 사업").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(itemBudget));
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(project));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        BudgetWorkDto.ProjectSummaryItem summaryItem = result.data().get(0);
        BudgetWorkDto.CategoryAmount categoryAmount = summaryItem.categoryAmounts().get("237");
        assertThat(summaryItem.requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(summaryItem.dupAmount()).isEqualByComparingTo("1600.0");
        assertThat(categoryAmount.requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(categoryAmount.dupAmount()).isEqualByComparingTo("1600.0");
        assertThat(result.totals().requestAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.totals().dupAmount()).isEqualByComparingTo("1600.0");
    }

    @Test
    @DisplayName("getProjectSummary: 컬럼명은 편성률 값이 아닌 IOE C_TP_DES를 표시한다")
    void getProjectSummary_컬럼명은CtpDes표시() {
        Ccodem dupCode = Ccodem.builder().cNm("70").cdvaDes("전산임차료 편성 비율").cdva("237").build();
        Ccodem ioeCode =
                Ccodem.builder()
                        .cdva("001")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cTpDes("전산임차료")
                        .build();
        Bbugtm budget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0001")
                        .ioeC("001")
                        .bgDupAmt(BigDecimal.valueOf(70))
                        .asgRt(new BigDecimal("70"))
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
        Ccodem ioeCode =
                Ccodem.builder()
                        .cdva("001")
                        .cNm("237-0700")
                        .cdvaDtlC("237-0700")
                        .cdvaNm(null)
                        .cdvaDtl("전산임차료 - 국내")
                        .cTpDes(null)
                        .build();
        Bbugtm budget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-001")
                        .ioeC("001")
                        .bgDupAmt(BigDecimal.valueOf(100))
                        .asgRt(new BigDecimal("50"))
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
        Ccodem ioeCode =
                Ccodem.builder()
                        .cdva("005")
                        .cNm("304-0100")
                        .cdvaDtlC("304-0100")
                        .cdvaDtl(null)
                        .cTpDes(null)
                        .cdvaDes("전산제비용세목")
                        .build();
        Bbugtm budget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-002")
                        .ioeC("005")
                        .bgDupAmt(BigDecimal.valueOf(200))
                        .asgRt(new BigDecimal("80"))
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
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .willReturn(List.of(dupWithCdvaNm));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("cdvaNm폴백");
    }

    @Test
    @DisplayName("getProjectSummary: IOE 세부코드 groupName이 blank이면 dupCode cNm을 컬럼명으로 사용한다")
    void getProjectSummary_세부코드groupName_blank_cNm폴백() {
        // resolveIoeGroupName: cTpDes=null, cdvaDtl=null, cdvaDes=null → null 반환
        // resolveProjectSummaryCategoryName: groupName null/blank → dupCode.getCNm() 폴백
        Ccodem dupCode = Ccodem.builder().cNm("전산제비용폴백").cdva("304").build();
        Ccodem ioeCode =
                Ccodem.builder()
                        .cdva("007")
                        .cNm("304-0100")
                        .cdvaDtlC("304-0100")
                        .cTpDes(null)
                        .cdvaDtl(null)
                        .cdvaDes(null)
                        .cdvaNm(null)
                        .cdvaNm(null)
                        .build();
        Bbugtm budget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-003")
                        .ioeC("007")
                        .bgDupAmt(BigDecimal.valueOf(100))
                        .asgRt(new BigDecimal("50"))
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
        Bbugtm nullPk =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm(null)
                        .ioeC("100")
                        .bgDupAmt(BigDecimal.TEN)
                        .asgRt(new BigDecimal("10"))
                        .build();
        // asgRt=0 → requestAmt 역산 skip, bgDupAmt=100은 합산
        Bbugtm itemNoProject =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-MISSING")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(100))
                        .asgRt(new BigDecimal("0"))
                        .build();
        // bgDupAmt=null → 금액 미합산
        Bbugtm costNoName =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-MISSING")
                        .ioeC("102")
                        .bgDupAmt(null)
                        .asgRt(null)
                        .build();
        // ioeC="NO-MATCH" → ioeCdvaToHierarchyCode에 없음 → matchedPrefix=null → 금액 skip, 이름은 표시
        Bbugtm unknown =
                Bbugtm.builder()
                        .fntTbNm("UNKNOWN")
                        .pkColNm("UNK-1")
                        .ioeC("NO-MATCH")
                        .bgDupAmt(BigDecimal.ONE)
                        .asgRt(new BigDecimal("50"))
                        .build();
        Ccodem ioeCode0 = Ccodem.builder().cdva("100").cNm("237-0000").cdvaDtlC("237-0000").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();
        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(ioeCode0, ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(nullPk, itemNoProject, costNoName, unknown));
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());
        given(costRepository.findByCostBgNoInAndDelYn(any(), eq("N"))).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data())
                .extracting(value -> value.name())
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
        Ccodem dupCode = Ccodem.builder().cdva("888").cdvaNm("CDVA명폴백").cNm("CNM폴백").build();
        Ccodem unrelatedIoeCode =
                Ccodem.builder().cdva("001").cdvaDtlC("777-0100").cTpDes("다른그룹").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(unrelatedIoeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).cdNm()).isEqualTo("CDVA명폴백");
    }

    @Test
    @DisplayName("getProjectSummary: 세부코드 계층명이 없으면 세부코드 설명을 컬럼명으로 사용한다")
    void getProjectSummary_세부코드계층명없음_cdvaDes사용() {
        Ccodem dupCode = Ccodem.builder().cdva("555").cdvaNm("사용되지않는폴백").build();
        Ccodem ioeCode =
                Ccodem.builder()
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
    @DisplayName("getProjectSummary - 헤더 편성률은 최신 편성 실행(bgNo 최대) 행 기준")
    void getProjectSummary_헤더편성률_최신bgNo행기준() {
        // given: 같은 비목 접두어에 편성률이 다른 두 실행 행 — 앞에 구 실행(80), 뒤에 신 실행(50)
        Ccodem dupCode = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm olderRun =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0001")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("800"))
                        .build();
        Bbugtm newerRun =
                Bbugtm.builder()
                        .bgNo("BG-2026-0002")
                        .sno(1)
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0002")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("50"))
                        .bgDupAmt(new BigDecimal("500"))
                        .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(olderRun, newerRun));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of());
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of());

        // when
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // then: encounter order(80)가 아니라 최신 편성 실행(50) 기준
        assertThat(result.categories().get(0).dupRt()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("getProjectSummary - BITEMM 구버전 행이 앞에 와도 LST_YN='Y' 행의 사업번호로 그룹핑한다")
    void getProjectSummary_BITEMM대표행_lstYnY기준() {
        // given: 같은 gclMngNo의 구버전(N, PRJ-OLD)이 리스트 앞, 최신(Y, PRJ-NEW)이 뒤
        Ccodem dupCode = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm budget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("800"))
                        .build();
        Bitemm oldVersion =
                Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("N").abusMngNo("PRJ-OLD").build();
        Bitemm latest =
                Bitemm.builder().gclMngNo("GCL-1").sno(2).lstYn("Y").abusMngNo("PRJ-NEW").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of());
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldVersion, latest));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());

        // when
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // then: encounter order(PRJ-OLD)가 아니라 LST_YN='Y' 행(PRJ-NEW)의 사업번호로 그룹핑
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).orcPkVl()).isEqualTo("PRJ-NEW");
    }

    @Test
    @DisplayName("getProjectSummary - 사업명은 LST_YN='Y' 행 이름, 구버전 행이 앞에 와도 최신명 표시")
    void getProjectSummary_사업명_lstYnY행이름() {
        Ccodem dupCode = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm budget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("800"))
                        .build();
        Bitemm item =
                Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("Y").abusMngNo("PRJ-1").build();
        Bprojm oldVersion =
                Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("N").abusNm("구버전명").build();
        Bprojm latest = Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("Y").abusNm("최신명").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of());
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldVersion, latest));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).name()).isEqualTo("최신명");
    }

    @Test
    @DisplayName("getProjectSummary - LST_YN='Y' 행이 없으면 사업명 대신 관리번호로 폴백한다")
    void getProjectSummary_사업명_lstYnY없음_관리번호폴백() {
        Ccodem dupCode = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm budget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("800"))
                        .build();
        Bitemm item =
                Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("Y").abusMngNo("PRJ-1").build();
        Bprojm oldOnly =
                Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("N").abusNm("구버전명").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(budget));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of());
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(oldOnly));

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).name()).isEqualTo("PRJ-1");
    }

    @Test
    @DisplayName("getProjectSummary - 사업번호와 비용번호가 같은 문자열이어도 별도 행으로 분리 집계한다")
    void getProjectSummary_동일키충돌_orcTb별분리() {
        // given: BCOSTM 원본 pk "X-1"과, BITEMM→사업 변환 결과가 같은 "X-1"인 두 편성행
        Ccodem dupCode = Ccodem.builder().cNm("자산비").cdva("237").build();
        Ccodem ioeCode = Ccodem.builder().cdva("001").cNm("237-0700").cdvaDtlC("237-0700").build();
        Bbugtm costBudget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(1)
                        .fntTbNm("BCOSTM")
                        .pkColNm("X-1")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("800"))
                        .build();
        Bbugtm itemBudget =
                Bbugtm.builder()
                        .bgNo("BG-2026-0001")
                        .sno(2)
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-1")
                        .fntTbCrySno(1)
                        .ioeC("001")
                        .asgRt(new BigDecimal("80"))
                        .bgDupAmt(new BigDecimal("400"))
                        .build();
        Bitemm item = Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("Y").abusMngNo("X-1").build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(costBudget, itemBudget));
        given(budgetWorkQueryRepository.findApprovedSourcePks("2026"))
                .willReturn(java.util.Set.of());
        given(projectItemRepository.findByGclMngNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(any(), eq("N"))).willReturn(List.of());
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(any(), eq("N")))
                .willReturn(List.of());

        // when
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // then: 단일 문자열 키였다면 1행으로 병합되지만, 복합키 분리 후 BCOSTM/BPROJM 2행
        assertThat(result.data()).hasSize(2);
        assertThat(result.data())
                .extracting(summaryItem -> summaryItem.orcTb())
                .containsExactlyInAnyOrder("BCOSTM", "BPROJM");
    }

    @Test
    @DisplayName("getProjectSummary: 품목/사업/계약 조회를 In-쿼리 1회로 배치하고 단건 finder를 호출하지 않는다")
    void getProjectSummary_batchesLookups() {
        // Arrange: BITEMM 1건(프로젝트 통합) + BCOSTM 1건(계약명 표시) — 기존 단건 테스트와 동일 픽스처
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();

        Bbugtm itemBudget =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-0001")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();
        Bbugtm costBudget =
                Bbugtm.builder()
                        .fntTbNm("BCOSTM")
                        .pkColNm("COST-2026-0001")
                        .ioeC("102")
                        .bgDupAmt(BigDecimal.valueOf(500))
                        .asgRt(new BigDecimal("50"))
                        .build();

        // 배치 조회 결과: gclMngNo→abusMngNo, 사업명, 계약명
        Bitemm item = Bitemm.builder().gclMngNo("GCL-0001").abusMngNo("PRJ-2026-0001").build();
        Bprojm project = org.mockito.Mockito.mock(Bprojm.class);
        given(project.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(project.getAbusNm()).willReturn("정보화사업");
        given(project.getLstYn()).willReturn("Y");
        CostRepository.CostRepresentativeView cost =
                org.mockito.Mockito.mock(CostRepository.CostRepresentativeView.class);
        given(cost.getCostBgNo()).willReturn("COST-2026-0001");
        given(cost.getCttNm()).willReturn("유지보수계약");

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(List.of(itemBudget, costBudget));
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(cost));

        // Act
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // Assert: 동작 동치 — 사업명/계약명 매핑 결과가 리팩터 전과 동일
        assertThat(result.data())
                .extracting(value -> value.name())
                .containsExactly("정보화사업", "유지보수계약");

        // Assert: In-쿼리 1회 배치, 단건 finder 미호출
        verify(projectItemRepository, times(1)).findByGclMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectItemRepository, never())
                .findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
        verify(projectRepository, times(1)).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never())
                .findNameViewByAbusMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(anyString(), anyString());
        verify(costRepository, times(1))
                .findRepresentativeViewsByCostBgNoInAndDelYn(anyCollection(), eq("N"));
        verify(costRepository, never()).findByCostBgNoInAndDelYn(anyCollection(), eq("N"));
        verify(costRepository, never()).findByCostBgNoAndDelYn(anyString(), anyString());
        verify(bbugtmRepository, times(1)).findByBseYyAndDelYn("2026", "N");
    }

    @Test
    @DisplayName("getProjectSummary: BITEMM 매핑이 없으면 gclMngNo를 그룹키/이름으로 폴백한다 (동작 동치)")
    void getProjectSummary_fallsBackToGclWhenItemMissing() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Bbugtm itemBudget =
                Bbugtm.builder()
                        .fntTbNm("BITEMM")
                        .pkColNm("GCL-MISSING")
                        .ioeC("101")
                        .bgDupAmt(BigDecimal.valueOf(800))
                        .asgRt(new BigDecimal("80"))
                        .build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode1));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(itemBudget));
        // 품목 배치 결과 비어 있음 → gclMngNo 자체가 그룹키, BPROJM 매핑 없음 → orcPkVl 폴백
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).extracting(value -> value.name()).containsExactly("GCL-MISSING");
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
    }
}
