package com.kdb.it.common.approval.mail;

import com.kdb.it.common.approval.entity.Capplm;

/** 신청서 엔티티에서 메일 렌더링 입력을 만든다. */
public final class ApprovalMailContextFactory {

    /**
     * 메일 링크 목적지 — 결재 대기 목록.
     *
     * <p>신청서 상세({@code /approval/{apfMngNo}})는 그룹웨어 메일에서 곧바로 열리지 않는 URL이라, 결재자가 실제로 처리를 시작할 수 있는 결재
     * 대기 탭으로 보낸다.
     */
    private static final String APPROVAL_PENDING_PATH = "/approval/list?tab=pending";

    private ApprovalMailContextFactory() {}

    /**
     * 메일 렌더링 입력을 만듭니다.
     *
     * @param application 신청서 마스터
     * @param requesterName 기안자 성명. 조회 실패 시 null 허용
     * @param deptName 작성부서명. 조회 실패 시 null 허용
     * @param frontendUrl 프론트 기준 URL (끝 슬래시 유무 무관)
     * @return 렌더링 입력
     */
    public static ApprovalMailContext create(
            Capplm application, String requesterName, String deptName, String frontendUrl) {
        String base = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return new ApprovalMailContext(
                application.getApfMngNo(),
                application.getDcdReqTtl(),
                application.getDcdReqDtm(),
                requesterName,
                deptName,
                base + APPROVAL_PENDING_PATH,
                application.getDcdReqInf());
    }
}
