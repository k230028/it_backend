package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalRecalledEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

/**
 * ApplicationService.recall() 단위 테스트
 *
 * <p>회수 6개 시나리오: 신청자 회수, 최종승인 후 회수 차단, 종결 상태 차단, 무관계 사용자 차단, 관리자 회수, 중간결재자 회수 시 이력 보존.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationServiceRecallTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApproverRepository approverRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CostRepository costRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApprovalLineDelegate approvalLineDelegate;
    @Mock private com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    @InjectMocks private ApplicationService service;

    private static final String APF = "APF-2026-00000001";

    /** 결재중/반려 등 상태의 Capplm 빌드 */
    private Capplm capplm(String stsC) {
        return Capplm.builder().apfMngNo(APF).itPtlApfPrgStsC(stsC).dcdReqUsid("E001").build();
    }

    /** 결재자 Cdecim 빌드 */
    private Cdecim approver(int sqn, String eno, String stsC, String last) {
        return Cdecim.builder()
                .dcdMngNo(APF)
                .dcrSqnSno(sqn)
                .dcrEno(eno)
                .itPtlDcdStsC(stsC)
                .lstDcdYn(last)
                .build();
    }

    /** 회수 요청 DTO */
    private ApplicationDto.RecallRequest req() {
        ApplicationDto.RecallRequest r = new ApplicationDto.RecallRequest();
        r.setRecallOpnn("사유");
        return r;
    }

    @Test
    @DisplayName("신청자 본인이 결재중 신청서를 회수하면 RECALLED로 전환되고 이벤트 발행")
    void recall_byRequester_setsStatusToRecalled() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("1")));
        when(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .thenReturn(List.of(approver(1, "E001", "2", "N"), approver(2, "E002", "1", "Y")));

        service.recall(APF, req(), "E001", false);

        verify(approvalLineDelegate).applyRecallInfo(any(), eq("E001"), eq("사유"));
        verify(eventPublisher).publishEvent(any(ApprovalRecalledEvent.class));
    }

    @Test
    @DisplayName("최종결재자가 이미 승인했으면 IllegalStateException")
    void recall_whenLastApproverApproved_throws() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("1")));
        when(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .thenReturn(List.of(approver(1, "E002", "2", "Y")));

        assertThatThrownBy(() -> service.recall(APF, req(), "E001", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최종 결재자");
    }

    @Test
    @DisplayName("종결 상태(반려) 신청서 회수 시 IllegalStateException")
    void recall_terminatedApplication_throws() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("3")));

        assertThatThrownBy(() -> service.recall(APF, req(), "E001", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("무관계 사용자 회수 시 AccessDeniedException")
    void recall_byUnrelatedUser_throwsAccessDenied() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("1")));
        when(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .thenReturn(List.of(approver(1, "E002", "1", "Y")));

        assertThatThrownBy(() -> service.recall(APF, req(), "E999", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("관리자라도 기안자가 아니면 회수할 수 없다")
    void recall_byAdmin_forbidden() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("1")));
        when(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .thenReturn(List.of(approver(1, "E002", "1", "Y")));

        assertThatThrownBy(() -> service.recall(APF, req(), "E999", true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("중간결재자라도 기안자가 아니면 회수할 수 없다")
    void recall_byMiddleApprover_forbidden() {
        when(applicationRepository.findById(APF)).thenReturn(Optional.of(capplm("1")));
        Cdecim a1 = approver(1, "E001", "2", "N");
        Cdecim a2 = approver(2, "E002", "1", "N");
        Cdecim a3 = approver(3, "E003", "1", "Y");
        when(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF))
                .thenReturn(List.of(a1, a2, a3));

        assertThatThrownBy(() -> service.recall(APF, req(), "E002", false))
                .isInstanceOf(AccessDeniedException.class);
    }
}
