package com.kdb.it.common.approval.mail;

/**
 * 결재요청 메일 개요에 표기할 신청 관련 당사자 이름.
 *
 * @param requesterName 기안자 성명. 조회 실패·미매칭 시 null 허용
 * @param deptName 작성부서명. 조회 실패·미매칭 시 null 허용
 */
public record ApprovalMailParties(String requesterName, String deptName) {}
