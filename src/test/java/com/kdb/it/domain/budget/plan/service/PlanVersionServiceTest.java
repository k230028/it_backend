package com.kdb.it.domain.budget.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 경상예산 재신청 버전 서비스의 상태 전이 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class PlanVersionServiceTest {

    @Mock private BplanmRepository bplanmRepository;
    @Mock private BplanaRepository bplanaRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;

    @InjectMocks private PlanVersionService planVersionService;

    @Test
    @DisplayName("결재완료 최종 계획을 다음 순번의 초안과 같은 관계 품목으로 복제한다")
    void createReapplication_완료최종본을다음순번초안으로복제한다() {
        Bplanm source =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(1)
                        .lstYn("Y")
                        .svnDpmC("900")
                        .itPtlPlnTpC("신규")
                        .bseYy("2026")
                        .redtConeInf("{\"snapshot\":true}")
                        .build();
        Bplana relation =
                Bplana.builder().prjMngNo("PRJ-2026-0001").reqDocNo("PLN-2026-0001").sno(1).build();
        CustomUserDetails owner =
                new CustomUserDetails("10000001", List.of(CustomUserDetails.ATH_USER), "900");
        given(bplanmRepository.findCurrentVersionForUpdate("PLN-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(bplanmRepository.getNextVersionSno("PLN-2026-0001")).willReturn(2);
        given(bplanaRepository.findAllByReqDocNoAndSnoAndDelYn("PLN-2026-0001", 1, "N"))
                .willReturn(List.of(relation));

        PlanVersionService.PlanVersion result =
                planVersionService.createReapplication("PLN-2026-0001", owner);

        assertThat(result.reqDocNo()).isEqualTo("PLN-2026-0001");
        assertThat(result.sno()).isEqualTo(2);
        assertThat(result.lstYn()).isEqualTo("N");
        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getReqDocNo()).isEqualTo("PLN-2026-0001");
        assertThat(planCaptor.getValue().getSno()).isEqualTo(2);
        assertThat(planCaptor.getValue().getLstYn()).isEqualTo("N");
        assertThat(planCaptor.getValue().getSvnDpmC()).isEqualTo("900");
        verify(bplanaRepository)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                copied ->
                                        copied.getPrjMngNo().equals("PRJ-2026-0001")
                                                && copied.getReqDocNo().equals("PLN-2026-0001")
                                                && copied.getSno().equals(2)));
    }

    @Test
    @DisplayName("결재완료가 아닌 최종 계획은 재신청을 거부한다")
    void createReapplication_미완료최종본을거부한다() {
        Bplanm source =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(1)
                        .lstYn("Y")
                        .svnDpmC("900")
                        .build();
        given(bplanmRepository.findCurrentVersionForUpdate("PLN-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.IN_PROGRESS.code()));

        assertThatThrownBy(() -> planVersionService.createReapplication("PLN-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재완료");

        verify(bplanmRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성부서는 부모 계획번호의 과거 최종본과 재신청 초안을 순번순으로 조회한다")
    void findHistory_부모계획의모든개정본과결재상태를반환한다() {
        Bplanm first =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(1)
                        .lstYn("N")
                        .svnDpmC("900")
                        .build();
        Bplanm second =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(2)
                        .lstYn("Y")
                        .svnDpmC("900")
                        .build();
        given(bplanmRepository.findByReqDocNoAndDelYnOrderBySnoAsc("PLN-2026-0001", "N"))
                .willReturn(List.of(first, second));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 2))
                .willReturn(Optional.of(ApprovalStatus.IN_PROGRESS.code()));

        List<com.kdb.it.domain.budget.plan.dto.PlanDto.VersionResponse> result =
                planVersionService.findHistory(
                        "PLN-2026-0001",
                        new CustomUserDetails(
                                "10000001", List.of(CustomUserDetails.ATH_USER), "900"));

        assertThat(result).extracting("sno").containsExactly(1, 2);
        assertThat(result).extracting("approvalStatus").containsExactly("02", "01");
    }

    @Test
    @DisplayName("결재완료된 정확한 순번만 이전 최종본을 내리고 최종본으로 승격한다")
    void promoteApprovedVersion_정확한순번만최종본으로승격한다() {
        Bplanm current =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(1)
                        .lstYn("Y")
                        .svnDpmC("900")
                        .build();
        Bplanm draft =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(2)
                        .lstYn("N")
                        .svnDpmC("900")
                        .build();
        given(bplanmRepository.findCurrentVersionForUpdate("PLN-2026-0001"))
                .willReturn(Optional.of(current));
        given(bplanmRepository.findVersionForUpdate("PLN-2026-0001", 2))
                .willReturn(Optional.of(draft));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 2))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(bplanmRepository.markVersionCurrent("PLN-2026-0001", 2)).willReturn(1);

        planVersionService.promoteApprovedVersion("PLN-2026-0001", 2);

        InOrder lockOrder = org.mockito.Mockito.inOrder(bplanmRepository);
        lockOrder.verify(bplanmRepository).findCurrentVersionForUpdate("PLN-2026-0001");
        lockOrder.verify(bplanmRepository).findVersionForUpdate("PLN-2026-0001", 2);
        verify(bplanmRepository).clearCurrentVersion("PLN-2026-0001", 2);
        verify(bplanmRepository).markVersionCurrent("PLN-2026-0001", 2);
    }

    @Test
    @DisplayName("이미 최종본인 순번의 결재완료 이벤트는 다른 버전을 다시 전환하지 않는다")
    void promoteApprovedVersion_이미최종본이면멱등처리한다() {
        Bplanm current =
                Bplanm.builder()
                        .reqDocNo("PLN-2026-0001")
                        .sno(2)
                        .lstYn("Y")
                        .svnDpmC("900")
                        .build();
        given(bplanmRepository.findCurrentVersionForUpdate("PLN-2026-0001"))
                .willReturn(Optional.of(current));
        given(bplanmRepository.findVersionForUpdate("PLN-2026-0001", 2))
                .willReturn(Optional.of(current));
        given(applicationMapRepository.findLatestApplicationStatus("BPLANM", "PLN-2026-0001", 2))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));

        planVersionService.promoteApprovedVersion("PLN-2026-0001", 2);

        verify(bplanmRepository, never()).clearCurrentVersion("PLN-2026-0001", 2);
        verify(bplanmRepository, never()).markVersionCurrent("PLN-2026-0001", 2);
    }
}
