package com.kdb.it.common.approval.event;

/**
 * 결재 완료/반려 이벤트
 *
 * <p>{@code ApplicationService.approve()}에서 신청서 상태가 "결재완료" 또는 "반려"로 바뀔 때 Spring {@link
 * org.springframework.context.ApplicationEventPublisher}로 발행됩니다. 이를 구독하는 도메인(예: 협의회)이 각자의 후처리를
 * 담당하므로, 공통 결재 모듈이 개별 도메인에 직접 의존하지 않아도 됩니다.
 *
 * @param apfMngNo 완료된 신청관리번호 (예: APF_202600000001)
 * @param newStatus 변경된 신청서 상태 ("결재완료" | "반려")
 */
public record ApprovalCompletedEvent(String apfMngNo, String newStatus) {}
