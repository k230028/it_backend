package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 정보화사업 재신청본의 결재완료 이벤트를 최종본 전환으로 연결합니다. */
@Component
@RequiredArgsConstructor
public class ProjectVersionApprovalListener {

    private static final String PROJECT_TABLE = "BPROJM";
    private static final String COMPLETED = "결재완료";

    private final ApplicationMapRepository applicationMapRepository;
    private final ProjectVersionService projectVersionService;

    /**
     * 결재완료 신청서가 가리키는 정보화사업의 정확한 개정본을 최종본으로 올립니다.
     *
     * @param event 결재 완료 또는 반려 이벤트
     */
    @org.springframework.core.annotation.Order(10)
    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        if (!COMPLETED.equals(event.newStatus())) {
            return;
        }
        for (Cappla link :
                applicationMapRepository
                        .findByApfDcmNoAndFntTbNm(event.apfMngNo(), PROJECT_TABLE)
                        .stream()
                        .sorted(
                                java.util.Comparator.comparing(Cappla::getPkColNm)
                                        .thenComparing(Cappla::getFntTbCrySno))
                        .toList()) {
            projectVersionService.promoteApprovedVersion(link.getPkColNm(), link.getFntTbCrySno());
        }
    }
}
