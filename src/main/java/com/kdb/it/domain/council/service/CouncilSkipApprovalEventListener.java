package com.kdb.it.domain.council.service;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 협의회 타당성검토 생략 판정 결재 완료 이벤트 리스너. (PRD_c_20260620 #3)
 *
 * <p>{@link ApprovalCompletedEvent}를 구독하여, 신청서가 BASKPM(생략판정요청) 원본에 연결된 경우
 * {@link CouncilSkipService#handleApprovalCompleted}로 분기 처리(생략→skip / 개최→prepare / 반려→통보)합니다.</p>
 *
 * <p>{@code @EventListener} + {@code @Transactional} 조합으로 발행자(ApplicationService.approve)와
 * 동일 트랜잭션에서 동기 처리합니다(협의회 결재 리스너와 동일 패턴).</p>
 */
@Component
@RequiredArgsConstructor
public class CouncilSkipApprovalEventListener {

    private static final Logger log = LoggerFactory.getLogger(CouncilSkipApprovalEventListener.class);

    private final ApplicationMapRepository applicationMapRepository;
    private final CouncilSkipService councilSkipService;

    /**
     * 결재 완료 이벤트가 생략판정요청 원본에 연결된 경우 협의회 후속 상태를 동기 반영합니다.
     *
     * @param event 결재 완료 이벤트. 연결 원본이 BASKPM이 아니면 처리하지 않음
     * @throws RuntimeException 후속 상태 반영 실패 시 발행 트랜잭션을 롤백하기 위해 예외를 다시 전파
     */
    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        List<Cappla> links = applicationMapRepository
                .findByApfDcmNoAndFntTbNm(event.apfMngNo(), CouncilSkipService.ORC_TB_CD);
        if (links.isEmpty()) {
            // 생략판정요청과 무관한 신청서 — 처리 불필요
            return;
        }

        boolean approved = "결재완료".equals(event.newStatus());
        for (Cappla link : links) {
            String asctId = link.getPkColNm();
            try {
                councilSkipService.handleApprovalCompleted(asctId, approved);
                log.info("[생략판정요청] 결재 콜백 처리 완료 - asctId={}, approved={}", asctId, approved);
            } catch (Exception e) {
                log.error("[생략판정요청] 결재 콜백 처리 실패 - asctId={}, apfMngNo={}", asctId, event.apfMngNo(), e);
                throw e;
            }
        }
    }
}
