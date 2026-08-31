package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 전산업무비 재상신본의 결재 완료를 최종본 전환으로 연결합니다. */
@Component
@RequiredArgsConstructor
public class CostVersionApprovalListener {
    private static final String COST_TABLE = "BCOSTM";
    private static final String COMPLETED = "결재완료";

    private final ApplicationMapRepository applicationMapRepository;
    private final CostVersionService costVersionService;

    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        if (!COMPLETED.equals(event.newStatus())) return;
        applicationMapRepository
                .findByApfDcmNoAndFntTbNm(event.apfMngNo(), COST_TABLE)
                .forEach(
                        link ->
                                costVersionService.promoteApprovedVersion(
                                        link.getPkColNm(), link.getFntTbCrySno()));
    }
}
