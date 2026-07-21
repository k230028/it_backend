package com.kdb.it.common.approval.event;

import java.util.List;

/**
 * 신청서 회수 이벤트.
 *
 * @param apfMngNo 회수된 신청서 관리번호
 * @param recallerEno 회수자 사번
 * @param approvedMiddleApproverEnos 회수 시점 기준 이미 승인한 중간결재자 사번 목록 (알림 대상)
 */
public record ApprovalRecalledEvent(
        String apfMngNo, String recallerEno, List<String> approvedMiddleApproverEnos) {}
