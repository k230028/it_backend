package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ApprovalLineManagementServiceTest {

    private static final String APF = "APF-2026-00000001";

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApproverRepository approverRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApprovalLineDelegate approvalLineDelegate;

    @InjectMocks private ApprovalLineManagementService service;

    @Test
    @DisplayName("결재중 결재선 참여자는 현재 결재선 뒤에 추가할 수 있다")
    void addApprover_appendsAfterCurrentLine() {
        Capplm application = application("1", "E001");
        Cdecim first = approver(1, "E002", "1");
        Cdecim second = approver(2, "E003", "1");
        given(applicationRepository.findById(APF)).willReturn(Optional.of(application));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(first, second));
        given(userRepository.findById("E004"))
                .willReturn(
                        Optional.of(
                                CuserI.builder().eno("E004").usrNm("추가결재자").ptCNm("과장").build()));

        service.addApprover(APF, "E004", "E002", false);

        assertThat(second.getLstDcdYn()).isEqualTo("N");
        verify(approverRepository)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                value ->
                                        value.getDcrSqnSno() == 3
                                                && "E004".equals(value.getDcrEno())
                                                && "Y".equals(value.getLstDcdYn())
                                                && DecisionStatus.isPendingCode(
                                                        value.getItPtlDcdStsC())));
    }

    @Test
    @DisplayName("결재완료 신청서에는 결재선을 추가할 수 없다")
    void addApprover_completedApplication_rejected() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("2", "E001")));

        assertThatThrownBy(() -> service.addApprover(APF, "E004", "E001", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("미결재 결재자는 기본 결재자 여부와 관계없이 삭제할 수 있다")
    void deleteApprover_pendingLine_deletesAnyApprover() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("1", "E001")));
        Cdecim target = approver(1, "E002", "1");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(target));

        service.deleteApprover(APF, 1, "E002", false);

        verify(approverRepository).delete(target);
        verify(approvalLineDelegate).removeApproverFromDetail(any(), eq(0));
        verify(approvalLineDelegate).updateApprovalOrder(any(), eq(List.of()));
    }

    @Test
    @DisplayName("승인된 추가 결재선은 삭제할 수 없다")
    void deleteApprover_approvedAdditionalLine_rejected() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("1", "E001")));
        Cdecim added = approver(3, "E004", "1");
        added.approve("승인", DecisionStatus.APPROVED);
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(approver(1, "E002", "1"), approver(2, "E003", "1"), added));

        assertThatThrownBy(() -> service.deleteApprover(APF, 3, "E002", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결재선 밖 사용자는 결재선을 추가하거나 삭제할 수 없다")
    void manageApprover_unrelatedUser_forbidden() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("1", "E001")));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(approver(1, "E002", "1")));

        assertThatThrownBy(() -> service.addApprover(APF, "E004", "E999", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("승인 완료 행은 고정하고 미결재 행만 요청한 순서로 변경한다")
    void reorderPendingApprovers_keepsApprovedRowsAndReordersPendingRows() {
        Capplm application = application("1", "E001");
        Cdecim approved = approver(1, "E002", "2");
        Cdecim pending1 = approver(2, "E003", "1");
        Cdecim pending2 = approver(3, "E004", "1");
        given(applicationRepository.findById(APF)).willReturn(Optional.of(application));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(approved, pending1, pending2));

        service.reorderPendingApprovers(APF, List.of(3, 2), "E002", false);

        verify(approverRepository)
                .shiftPendingSequences(
                        eq(APF), eq(List.of(2, 3)), org.mockito.ArgumentMatchers.anyInt());
        verify(approverRepository)
                .updateSequence(eq(APF), org.mockito.ArgumentMatchers.anyInt(), eq(3));
        verify(approverRepository)
                .updateSequence(eq(APF), org.mockito.ArgumentMatchers.anyInt(), eq(2));
        verify(approvalLineDelegate)
                .updateApprovalOrder(eq(application), eq(List.of(approved, pending2, pending1)));
    }

    @Test
    @DisplayName("승인 완료 순번을 포함한 순서 변경 요청은 거부한다")
    void reorderPendingApprovers_approvedSequenceRejected() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("1", "E001")));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(List.of(approver(1, "E002", "2"), approver(2, "E003", "1")));

        assertThatThrownBy(() -> service.reorderPendingApprovers(APF, List.of(1, 2), "E002", false))
                .isInstanceOf(IllegalArgumentException.class);

        verify(approverRepository, never())
                .shiftPendingSequences(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("결재선에 없는 순번이나 중복 순번은 거부한다")
    void reorderPendingApprovers_invalidPermutationRejected() {
        given(applicationRepository.findById(APF))
                .willReturn(Optional.of(application("1", "E001")));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .willReturn(
                        List.of(
                                approver(1, "E002", "2"),
                                approver(2, "E003", "1"),
                                approver(3, "E004", "1")));

        assertThatThrownBy(() -> service.reorderPendingApprovers(APF, List.of(2, 2), "E002", false))
                .isInstanceOf(IllegalArgumentException.class);

        verify(approverRepository, never())
                .shiftPendingSequences(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    private Capplm application(String status, String requester) {
        return Capplm.builder().apfMngNo(APF).itPtlApfPrgStsC(status).dcdReqUsid(requester).build();
    }

    private Cdecim approver(int sequence, String eno, String status) {
        return Cdecim.builder()
                .dcdMngNo(APF)
                .dcrSqnSno(sequence)
                .dcrEno(eno)
                .lstDcdYn("Y")
                .itPtlDcdStsC(status)
                .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                .build();
    }
}
