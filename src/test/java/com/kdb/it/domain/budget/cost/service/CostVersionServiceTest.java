package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostVersionServiceTest {

    @Mock CostRepository costRepository;
    @Mock BtermmRepository terminalRepository;
    @Mock ApplicationMapRepository applicationMapRepository;

    @Test
    void 결재완료_전산업무비와_단말기를_다음순번_초안으로_복제한다() {
        Bcostm source =
                Bcostm.builder()
                        .costBgNo("COST-2027-0001")
                        .bgSno(1)
                        .lstYn("Y")
                        .cttNm("원본 계약")
                        .costTotXpAmt(new BigDecimal("1000"))
                        .dfrCleC("0")
                        .abusTc("10")
                        .delYn("N")
                        .build();
        Btermm terminal =
                Btermm.builder()
                        .tmnMngNo("TMN-1")
                        .sno(1)
                        .termBgNo(source.getCostBgNo())
                        .termBgSno(1)
                        .spfTmnNm("단말 원본")
                        .dfrCleC("0")
                        .delYn("N")
                        .build();
        given(costRepository.findCurrentVersionForUpdate(source.getCostBgNo()))
                .willReturn(Optional.of(source));
        given(costRepository.getNextSnoValue(source.getCostBgNo())).willReturn(2);
        given(
                        applicationMapRepository.findLatestApplicationStatus(
                                "BCOSTM", source.getCostBgNo(), 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn(source.getCostBgNo(), 1, "N"))
                .willReturn(List.of(terminal));
        given(terminalRepository.getNextSnoValue("TMN-1")).willReturn(2);

        CostVersionService service =
                new CostVersionService(
                        costRepository, terminalRepository, applicationMapRepository);
        CostVersionService.CostVersion result =
                service.createReapplication(source.getCostBgNo(), administrator());

        assertThat(result.bgSno()).isEqualTo(2);
        ArgumentCaptor<Bcostm> costCaptor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(costCaptor.capture());
        assertThat(costCaptor.getValue().getLstYn()).isEqualTo("N");
        assertThat(costCaptor.getValue().getCostTotXpAmt()).isEqualByComparingTo("1000");
        ArgumentCaptor<Btermm> terminalCaptor = ArgumentCaptor.forClass(Btermm.class);
        verify(terminalRepository).save(terminalCaptor.capture());
        assertThat(terminalCaptor.getValue().getTermBgSno()).isEqualTo(2);
        assertThat(terminalCaptor.getValue().getSpfTmnNm()).isEqualTo("단말 원본");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("이미 미결 재상신 초안이 있으면 재상신을 거부한다")
    void 활성_초안이_있으면_재상신을_거부한다() {
        Bcostm source =
                Bcostm.builder().costBgNo("COST-2027-0001").bgSno(1).lstYn("Y").delYn("N").build();
        given(costRepository.findCurrentVersionForUpdate("COST-2027-0001"))
                .willReturn(Optional.of(source));
        given(costRepository.existsByCostBgNoAndBgSnoGreaterThanAndDelYn("COST-2027-0001", 1, "N"))
                .willReturn(true);
        CostVersionService service =
                new CostVersionService(
                        costRepository, terminalRepository, applicationMapRepository);

        assertThatThrownBy(() -> service.createReapplication("COST-2027-0001", administrator()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재상신 초안");

        verify(costRepository, never()).getNextSnoValue("COST-2027-0001");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("현재 최종본보다 낮은 순번으로는 승격하지 않는다")
    void 이전_순번으로의_승격을_거부한다() {
        Bcostm current =
                Bcostm.builder().costBgNo("COST-2027-0001").bgSno(2).lstYn("Y").delYn("N").build();
        Bcostm stale =
                Bcostm.builder().costBgNo("COST-2027-0001").bgSno(1).lstYn("N").delYn("N").build();
        given(costRepository.findVersionForUpdate("COST-2027-0001", 1))
                .willReturn(Optional.of(stale));
        given(costRepository.findByCostBgNoAndLstYnAndDelYn("COST-2027-0001", "Y", "N"))
                .willReturn(Optional.of(current));
        CostVersionService service =
                new CostVersionService(
                        costRepository, terminalRepository, applicationMapRepository);

        assertThatThrownBy(() -> service.promoteApprovedVersion("COST-2027-0001", 1))
                .isInstanceOf(IllegalStateException.class);

        verify(costRepository, never()).clearCurrentVersion("COST-2027-0001", 1);
    }

    @Test
    void 개정이력을_순번순으로_조회한다() {
        Bcostm first = Bcostm.builder().costBgNo("COST-1").bgSno(1).costSvnDpmC("D001").build();
        Bcostm second = Bcostm.builder().costBgNo("COST-1").bgSno(2).costSvnDpmC("D001").build();
        given(costRepository.findByCostBgNoAndDelYnOrderByBgSnoAsc("COST-1", "N"))
                .willReturn(List.of(first, second));
        CostVersionService service = service();

        List<Bcostm> history = service.findHistory("COST-1", administrator());

        assertThat(history).containsExactly(first, second);
    }

    @Test
    void 관리번호와_순번이_일치하는_개정본을_조회한다() {
        Bcostm version = Bcostm.builder().costBgNo("COST-1").bgSno(2).build();
        given(costRepository.findByCostBgNoAndBgSnoAndDelYn("COST-1", 2, "N"))
                .willReturn(Optional.of(version));
        CostVersionService service = service();

        Optional<Bcostm> result = service.findVersion("COST-1", 2);

        assertThat(result).containsSame(version);
    }

    @Test
    void 재상신할_최종본이_없으면_거부한다() {
        given(costRepository.findCurrentVersionForUpdate("COST-404")).willReturn(Optional.empty());
        CostVersionService service = service();

        assertThatThrownBy(() -> service.createReapplication("COST-404", administrator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COST-404");

        verify(applicationMapRepository, never())
                .findLatestApplicationStatus("BCOSTM", "COST-404", null);
    }

    @Test
    void 결재완료_상태가_아니면_재상신을_거부한다() {
        Bcostm source =
                Bcostm.builder().costBgNo("COST-1").bgSno(1).lstYn("Y").delYn("N").build();
        given(costRepository.findCurrentVersionForUpdate("COST-1")).willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BCOSTM", "COST-1", 1))
                .willReturn(Optional.of("DRAFT"));
        CostVersionService service = service();

        assertThatThrownBy(() -> service.createReapplication("COST-1", administrator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재 완료");

        verify(costRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 승격할_개정본이_없으면_거부한다() {
        given(costRepository.findVersionForUpdate("COST-404", 3)).willReturn(Optional.empty());
        CostVersionService service = service();

        assertThatThrownBy(() -> service.promoteApprovedVersion("COST-404", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COST-404");

        verify(costRepository, never()).clearCurrentVersion("COST-404", 3);
    }

    @Test
    void 승격_갱신결과가_한건이_아니면_거부한다() {
        Bcostm version = Bcostm.builder().costBgNo("COST-1").bgSno(2).build();
        given(costRepository.findVersionForUpdate("COST-1", 2)).willReturn(Optional.of(version));
        given(costRepository.findByCostBgNoAndLstYnAndDelYn("COST-1", "Y", "N"))
                .willReturn(Optional.empty());
        given(costRepository.markVersionCurrent("COST-1", 2)).willReturn(0);
        CostVersionService service = service();

        assertThatThrownBy(() -> service.promoteApprovedVersion("COST-1", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COST-1");

        verify(costRepository).clearCurrentVersion("COST-1", 2);
    }

    @Test
    void 개정이력은_정렬된_첫_개정본의_부서_권한만_판정한다() {
        Bcostm first = Bcostm.builder().costBgNo("COST-1").bgSno(1).costSvnDpmC("D001").build();
        Bcostm later = Bcostm.builder().costBgNo("COST-1").bgSno(2).costSvnDpmC("D002").build();
        given(costRepository.findByCostBgNoAndDelYnOrderByBgSnoAsc("COST-1", "N"))
                .willReturn(List.of(first, later));

        List<Bcostm> history = service().findHistory("COST-1", departmentUser("D001"));

        assertThat(history).containsExactly(first, later);
    }

    @Test
    void 타부서_사용자는_개정이력을_조회할_수_없다() {
        Bcostm first = Bcostm.builder().costBgNo("COST-1").bgSno(1).costSvnDpmC("D001").build();
        given(costRepository.findByCostBgNoAndDelYnOrderByBgSnoAsc("COST-1", "N"))
                .willReturn(List.of(first));

        assertThatThrownBy(() -> service().findHistory("COST-1", departmentUser("D002")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void 타부서_사용자는_재상신_초안을_생성할_수_없다() {
        Bcostm source =
                Bcostm.builder()
                        .costBgNo("COST-1")
                        .bgSno(1)
                        .costSvnDpmC("D001")
                        .build();
        given(costRepository.findCurrentVersionForUpdate("COST-1")).willReturn(Optional.of(source));

        assertThatThrownBy(() -> service().createReapplication("COST-1", departmentUser("D002")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(applicationMapRepository, never()).findLatestApplicationStatus("BCOSTM", "COST-1", 1);
    }

    @Test
    void actor_없는_공개_이력과_재상신_오버로드를_노출하지_않는다() {
        assertThatThrownBy(() -> CostVersionService.class.getMethod("findHistory", String.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(
                        () -> CostVersionService.class.getMethod("createReapplication", String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private CostVersionService service() {
        return new CostVersionService(costRepository, terminalRepository, applicationMapRepository);
    }

    private static CustomUserDetails administrator() {
        return new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
    }

    private static CustomUserDetails departmentUser(String departmentCode) {
        return new CustomUserDetails(
                "20001", List.of(CustomUserDetails.ATH_USER), departmentCode);
    }
}
