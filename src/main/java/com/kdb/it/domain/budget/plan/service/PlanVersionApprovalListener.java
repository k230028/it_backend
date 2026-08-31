package com.kdb.it.domain.budget.plan.service;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 경상예산 재신청본의 결재완료 이벤트를 최종본 전환에 연결합니다. */
@Component
@RequiredArgsConstructor
public class PlanVersionApprovalListener {

    private static final String PLAN_TABLE = "BPLANM";
    private static final String COMPLETED = "결재완료";

    private final ApplicationMapRepository applicationMapRepository;
    private final PlanVersionService planVersionService;

    /** 결재완료 신청서의 CAPPLA 부모 계획번호와 순번으로 정확한 개정본을 승격합니다. */
    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        if (!COMPLETED.equals(event.newStatus())) {
            return;
        }
        for (Cappla link :
                applicationMapRepository.findByApfDcmNoAndFntTbNm(event.apfMngNo(), PLAN_TABLE)) {
            if (link.getFntTbCrySno() == null) {
                throw new IllegalArgumentException("계획 결재 매핑에 개정 순번이 없습니다: " + event.apfMngNo());
            }
            planVersionService.promoteApprovedVersion(link.getPkColNm(), link.getFntTbCrySno());
        }
    }
}
