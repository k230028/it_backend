package com.kdb.it.common.approval.mail;

import java.time.LocalDate;

/**
 * 결재요청 메일 렌더링 입력.
 *
 * <p>렌더러가 리포지토리를 모르도록 필요한 값을 모두 호출자가 채워 넘긴다. 선택 항목이 null이어도 렌더링은 계속되며 해당 칸만 빈 값으로 남는다.
 *
 * @param apfMngNo 문서번호(신청서식별번호)
 * @param title 신청서 제목
 * @param requestedDate 신청일자. null 허용
 * @param requesterName 기안자 성명. null 허용
 * @param deptName 작성부서명. null 허용
 * @param detailUrl 신청서 상세 화면 절대 URL
 * @param detailJson 신청서 상세 스냅샷 JSON. null·공백·파싱 실패 시 총괄표를 생략한다
 */
public record ApprovalMailContext(
        String apfMngNo,
        String title,
        LocalDate requestedDate,
        String requesterName,
        String deptName,
        String detailUrl,
        String detailJson) {}
