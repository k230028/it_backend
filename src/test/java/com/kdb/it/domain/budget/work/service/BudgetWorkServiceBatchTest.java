package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * BudgetWorkService N+1 일괄조회(T12-D) 회귀 테스트.
 *
 * <p>{@code getProjectSummary}/{@code computeMplAdjustment}가 품목/사업/계약을 그룹 수만큼
 * 단건 조회하지 않고 {@code findBy...In...} 메서드로 1회 배치 조회하는지 검증한다.
 * 동작 동치(사업명/계약명 매핑 결과)도 함께 확인한다. (foreign WIP인 BudgetWorkServiceTest를
 * 건드리지 않도록 별도 클래스로 분리.)</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetWorkServiceBatchTest {

    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private CostRepository costRepository;
    @Mock private BudgetWorkQueryRepository budgetWorkQueryRepository;

    @InjectMocks private BudgetWorkService budgetWorkService;

    @Test
    @DisplayName("getProjectSummary: 품목/사업/계약 조회를 In-쿼리 1회로 배치하고 단건 finder를 호출하지 않는다")
    void getProjectSummary_batchesLookups() {
        // Arrange: BITEMM 1건(프로젝트 통합) + BCOSTM 1건(계약명 표시) — 기존 단건 테스트와 동일 픽스처
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Ccodem ioeCode2 = Ccodem.builder().cdva("102").cNm("237-0200").cdvaDtlC("237-0200").build();

        Bbugtm itemBudget = Bbugtm.builder()
                .fntTbNm("BITEMM").pkColNm("GCL-0001").ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800)).asgRt(80).build();
        Bbugtm costBudget = Bbugtm.builder()
                .fntTbNm("BCOSTM").pkColNm("COST-2026-0001").ioeC("102")
                .bgDupAmt(BigDecimal.valueOf(500)).asgRt(50).build();

        // 배치 조회 결과: gclMngNo→abusMngNo, 사업명, 계약명
        Bitemm item = Bitemm.builder().gclMngNo("GCL-0001").abusMngNo("PRJ-2026-0001").build();
        Bprojm project = org.mockito.Mockito.mock(Bprojm.class);
        given(project.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(project.getAbusNm()).willReturn("정보화사업");
        CostRepository.CostRepresentativeView cost = org.mockito.Mockito.mock(CostRepository.CostRepresentativeView.class);
        given(cost.getCostBgNo()).willReturn("COST-2026-0001");
        given(cost.getCttNm()).willReturn("유지보수계약");

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode1, ioeCode2));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(itemBudget, costBudget));
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));
        given(costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(cost));

        // Act
        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        // Assert: 동작 동치 — 사업명/계약명 매핑 결과가 리팩터 전과 동일
        assertThat(result.data()).extracting(value -> value.name())
                .containsExactly("정보화사업", "유지보수계약");

        // Assert: In-쿼리 1회 배치, 단건 finder 미호출
        verify(projectItemRepository, times(1)).findByGclMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectItemRepository, never()).findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
        verify(projectRepository, times(1)).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never()).findNameViewByAbusMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(anyString(), anyString());
        verify(costRepository, times(1)).findRepresentativeViewsByCostBgNoInAndDelYn(anyCollection(), eq("N"));
        verify(costRepository, never()).findByCostBgNoInAndDelYn(anyCollection(), eq("N"));
        verify(costRepository, never()).findByCostBgNoAndDelYn(anyString(), anyString());
        verify(bbugtmRepository, times(1)).findByBseYyAndDelYn("2026", "N");
    }

    @Test
    @DisplayName("getProjectSummary: BITEMM 매핑이 없으면 gclMngNo를 그룹키/이름으로 폴백한다 (동작 동치)")
    void getProjectSummary_fallsBackToGclWhenItemMissing() {
        Ccodem dupCode = Ccodem.builder().cNm("임차료").cdvaDes("임차료").cdva("237").build();
        Ccodem ioeCode1 = Ccodem.builder().cdva("101").cNm("237-0100").cdvaDtlC("237-0100").build();
        Bbugtm itemBudget = Bbugtm.builder()
                .fntTbNm("BITEMM").pkColNm("GCL-MISSING").ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800)).asgRt(80).build();

        given(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).willReturn(List.of(dupCode));
        given(codeRepository.findByCIdWithValidDate("IOE_C", null)).willReturn(List.of(ioeCode1));
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of(itemBudget));
        // 품목 배치 결과 비어 있음 → gclMngNo 자체가 그룹키, BPROJM 매핑 없음 → orcPkVl 폴백
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());

        BudgetWorkDto.ProjectSummaryResponse result = budgetWorkService.getProjectSummary("2026");

        assertThat(result.data()).extracting(value -> value.name())
                .containsExactly("GCL-MISSING");
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
    }

    @Test
    @DisplayName("computeMplAdjustment(getSummary 경유): 품목/사업 조회를 In-쿼리로 배치하고 단건 finder를 호출하지 않는다")
    void computeMplAdjustment_batchesLookups() {
        // getSummary는 내부적으로 computeMplAdjustment를 호출한다.
        Ccodem dupCode = Ccodem.builder().cNm("전산임차료").cdvaDes("전산임차료").cdva("237").build();
        Ccodem detailCode = Ccodem.builder()
                .cdva("101").cNm("237-0700").cdvaDtlC("237-0700")
                .cdvaNm("국내전산임차료").cTp("IOE_LEAFE").cTpDes("전산임차료").build();
        Bbugtm bbugtm = Bbugtm.builder()
                .fntTbNm("BITEMM").pkColNm("GCL-1").ioeC("101")
                .bgDupAmt(BigDecimal.valueOf(800)).asgRt(80).build();
        Bitemm item = Bitemm.builder()
                .gclMngNo("GCL-1").abusMngNo("PRJ-1")
                .amt(BigDecimal.valueOf(1000)).xcr(BigDecimal.ONE)
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
        given(projectItemRepository.findByGclMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));

        BudgetWorkDto.SummaryResponse result = budgetWorkService.getSummary("2026");

        // 동작 동치: 예정금액 비율 차감 결과가 리팩터 전과 동일 (req 500, dup 400)
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).requestAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.data().get(0).dupAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));

        // computeMplAdjustment 경로의 단건 finder 미호출 검증
        verify(projectItemRepository, times(1)).findByGclMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, times(1)).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never()).findNameViewByAbusMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        verify(projectItemRepository, never()).findByGclMngNoAndDelYn(anyString(), anyString());
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(anyString(), anyString());
    }
}
