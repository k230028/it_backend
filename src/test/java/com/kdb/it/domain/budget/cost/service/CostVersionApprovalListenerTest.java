package com.kdb.it.domain.budget.cost.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostVersionApprovalListenerTest {
    @Mock ApplicationMapRepository applicationMapRepository;
    @Mock CostVersionService costVersionService;
    @InjectMocks CostVersionApprovalListener listener;

    @Test
    void 전산업무비_결재완료는_정확한_BG_SNO를_최종본으로_승격한다() {
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm("APF-1", "BCOSTM"))
                .willReturn(
                        List.of(
                                Cappla.builder()
                                        .apfDcmNo("APF-1")
                                        .pkColNm("COST-1")
                                        .fntTbCrySno(3)
                                        .build()));

        listener.handleApprovalCompleted(new ApprovalCompletedEvent("APF-1", "결재완료"));

        verify(costVersionService).promoteApprovedVersion("COST-1", 3);
    }

    @Test
    void 결재완료가_아닌_상태는_무시한다() {
        ApprovalCompletedEvent event = new ApprovalCompletedEvent("APF-1", "반려");

        listener.handleApprovalCompleted(event);

        verifyNoInteractions(applicationMapRepository, costVersionService);
    }
}
