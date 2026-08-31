package com.kdb.it.domain.budget.plan.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

/** 계획 결재 완료 이벤트가 CAPPLA의 정확한 개정 순번을 승격하는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class PlanVersionApprovalListenerTest {

    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private PlanVersionService planVersionService;

    @InjectMocks private PlanVersionApprovalListener listener;

    @Test
    @DisplayName("결재완료 이벤트는 BPLANM CAPPLA의 부모 계획번호와 순번만 승격한다")
    void handleApprovalCompleted_계획매핑의정확한순번을승격한다() {
        Cappla planMapping =
                Cappla.builder()
                        .apfDcmNo("APF-2026-00000001")
                        .fntTbNm("BPLANM")
                        .pkColNm("PLN-2026-0001")
                        .fntTbCrySno(2)
                        .build();
        given(
                        applicationMapRepository.findByApfDcmNoAndFntTbNm(
                                "APF-2026-00000001", "BPLANM"))
                .willReturn(List.of(planMapping));

        listener.handleApprovalCompleted(new ApprovalCompletedEvent("APF-2026-00000001", "결재완료"));

        verify(planVersionService).promoteApprovedVersion("PLN-2026-0001", 2);
    }

    @Test
    @DisplayName("결재완료 매핑에 순번이 없으면 다른 계획을 추정 승격하지 않고 실패한다")
    void handleApprovalCompleted_순번없는계획매핑을거부한다() {
        Cappla incompleteMapping =
                Cappla.builder()
                        .apfDcmNo("APF-2026-00000001")
                        .fntTbNm("BPLANM")
                        .pkColNm("PLN-2026-0001")
                        .build();
        given(
                        applicationMapRepository.findByApfDcmNoAndFntTbNm(
                                "APF-2026-00000001", "BPLANM"))
                .willReturn(List.of(incompleteMapping));

        assertThatThrownBy(
                        () ->
                                listener.handleApprovalCompleted(
                                        new ApprovalCompletedEvent(
                                                "APF-2026-00000001", "결재완료")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("개정 순번");

        verifyNoInteractions(planVersionService);
    }
}
