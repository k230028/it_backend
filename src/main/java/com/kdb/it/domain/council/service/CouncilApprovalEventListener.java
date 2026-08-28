package com.kdb.it.domain.council.service;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재 완료 이벤트 → 협의회 상태 자동 전이 리스너
 *
 * <p>{@link ApprovalCompletedEvent}를 구독하여, 해당 신청서가 협의회(BASCTM) 원본 데이터와 연결되어 있을 경우 협의회 상태를 자동으로
 * 업데이트합니다.
 *
 * <p>상태 전이:
 *
 * <pre>
 *   결재완료 → CouncilApprovalService.processApprovalCallback(approved=true)
 *              → APPROVAL_PENDING → APPROVED
 *
 *   반려     → CouncilApprovalService.processApprovalCallback(approved=false)
 *              → APPROVAL_PENDING → DRAFT
 * </pre>
 *
 * <p>{@code @EventListener}는 발행자({@code ApplicationService.approve()})와 동일한 트랜잭션 안에서 동기적으로 실행되므로,
 * APF 상태 변경과 협의회 상태 변경이 하나의 트랜잭션으로 처리됩니다. 어느 쪽이든 실패하면 전체가 롤백됩니다.
 */
@Component
@RequiredArgsConstructor
public class CouncilApprovalEventListener {

    private static final Logger log = LoggerFactory.getLogger(CouncilApprovalEventListener.class);

    /** 협의회 원본 데이터 테이블명 (CAPPLA.FNT_TB_NM) */
    private static final String COUNCIL_ORC_TB_CD = "BASCTM";

    /** 신청서-원본 연결 조회용 */
    private final ApplicationMapRepository applicationMapRepository;

    /** 협의회 상태 전이 처리 서비스 */
    private final CouncilApprovalService councilApprovalService;

    /**
     * 결재 완료/반려 이벤트 처리
     *
     * <p>신청서가 BASCTM(협의회)에 연결된 경우에만 협의회 상태를 업데이트합니다. 연결되지 않은 신청서(예: 정보화사업, 전산관리비)는 아무 동작도 하지 않습니다.
     *
     * @param event 결재 완료 이벤트 (신청관리번호, 새 상태 포함)
     */
    // 설계 주의: @EventListener + @Transactional 조합 사용.
    // @TransactionalEventListener 대신 @EventListener를 선택해 발행자 트랜잭션 안에서 동기 처리한다.
    // @TransactionalEventListener(AFTER_COMMIT)로 변경하면 결재 커밋 후 협의회 상태 전이가 별도 후속 작업으로 지연된다.
    @EventListener
    @Transactional
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        // BASCTM(협의회)에 연결된 Cappla 레코드 조회
        List<Cappla> links =
                applicationMapRepository.findByApfDcmNoAndFntTbNm(
                        event.apfMngNo(), COUNCIL_ORC_TB_CD);

        if (links.isEmpty()) {
            // 협의회와 무관한 신청서 — 처리 불필요
            return;
        }

        // 결재완료 여부 판별 (결재완료=승인, 반려=false)
        boolean approved = "결재완료".equals(event.newStatus());

        // 연결된 협의회 각각에 대해 상태 전이 처리
        for (Cappla link : links) {
            String asctId = link.getPkColNm();
            try {
                councilApprovalService.processApprovalCallback(
                        asctId, new CouncilDto.ApprovalCallbackRequest(approved));
                log.info("협의회 결재 상태 자동 전이 완료 - asctId: {}, approved: {}", asctId, approved);
            } catch (RuntimeException e) {
                // 상태 전이 실패 시 로그 기록 후 예외 재발생 → 트랜잭션 전체 롤백
                log.error(
                        "협의회 결재 상태 전이 실패 - asctId: {}, apfMngNo: {}", asctId, event.apfMngNo(), e);
                throw e;
            }
        }
    }
}
