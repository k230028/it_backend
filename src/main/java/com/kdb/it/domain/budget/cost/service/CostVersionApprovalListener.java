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

    /**
     * 결재 완료 신청서가 가리키는 전산업무비의 정확한 개정본만 최종본(LST_YN='Y')으로 승격합니다.
     *
     * <p>신청서 상태가 "결재완료"가 아니면(반려·회수 등) 아무것도 하지 않습니다. 원 업무와 함께 반영되어야 하므로 동기 {@link EventListener}로 같은
     * 트랜잭션에서 처리합니다.
     *
     * @param event 결재 상태 변경 이벤트
     */
    @org.springframework.core.annotation.Order(20)
    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        if (!COMPLETED.equals(event.newStatus())) return;
        applicationMapRepository.findByApfDcmNoAndFntTbNm(event.apfMngNo(), COST_TABLE).stream()
                .sorted(
                        java.util.Comparator.comparing(
                                        com.kdb.it.common.approval.entity.Cappla::getPkColNm)
                                .thenComparing(
                                        com.kdb.it.common.approval.entity.Cappla::getFntTbCrySno))
                .forEach(
                        link ->
                                costVersionService.promoteApprovedVersion(
                                        link.getPkColNm(), link.getFntTbCrySno()));
    }
}
