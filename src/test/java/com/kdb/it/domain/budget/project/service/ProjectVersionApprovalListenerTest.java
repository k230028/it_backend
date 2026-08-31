package com.kdb.it.domain.budget.project.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectVersionApprovalListenerTest {

    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private ProjectVersionService projectVersionService;

    @InjectMocks private ProjectVersionApprovalListener listener;

    @Test
    @DisplayName("사업 결재완료 이벤트는 연결된 정확한 순번만 최종본으로 승격한다")
    void 사업_결재완료_이벤트는_연결된_정확한_순번만_최종본으로_승격한다() {
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm("APF-2026-0001", "BPROJM"))
                .willReturn(
                        List.of(
                                Cappla.builder()
                                        .apfDcmNo("APF-2026-0001")
                                        .pkColNm("PRJ-2026-0001")
                                        .fntTbCrySno(2)
                                        .build()));

        listener.handleApprovalCompleted(new ApprovalCompletedEvent("APF-2026-0001", "결재완료"));

        verify(projectVersionService).promoteApprovedVersion("PRJ-2026-0001", 2);
    }

    @Test
    @DisplayName("반려 이벤트는 최종본을 전환하지 않는다")
    void 반려_이벤트는_최종본을_전환하지_않는다() {
        listener.handleApprovalCompleted(new ApprovalCompletedEvent("APF-2026-0001", "반려"));

        verifyNoInteractions(applicationMapRepository, projectVersionService);
    }
}
