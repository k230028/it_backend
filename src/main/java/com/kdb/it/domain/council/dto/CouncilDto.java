package com.kdb.it.domain.council.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 정보화실무협의회 DTO 중앙 관리
 *
 * <p>모든 협의회 관련 Request/Response DTO를 이 클래스에서 inner record로 관리합니다.</p>
 *
 * <p>모듈별 DTO 범위:</p>
 * <ul>
 *   <li>M3 (CouncilService): {@link ListResponse}, {@link CreateRequest}, {@link DetailResponse}</li>
 *   <li>M4 (FeasibilityService): {@link FeasibilityRequest}, {@link FeasibilityResponse},
 *       {@link CheckItemRequest}, {@link PerformanceRequest}</li>
 *   <li>M6 (CommitteeService): {@link CommitteeRequest}, {@link CommitteeMemberRequest}</li>
 *   <li>M6 (ScheduleService): {@link ScheduleRequest}, {@link ScheduleItem}, {@link ScheduleConfirmRequest}</li>
 *   <li>M7 (EvaluationService): {@link EvaluationRequest}, {@link EvaluationItem}</li>
 *   <li>M7 (ResultService): {@link ResultRequest}</li>
 * </ul>
 */
public class CouncilDto {

    // =========================================================================
    // M3: 협의회 목록/기본 관련
    // =========================================================================

    /**
     * 협의회 목록 응답 (사업목록 화면용)
     *
     * <p>권한별 필터링 결과를 공통 포맷으로 반환합니다:</p>
     * <ul>
     *   <li>일반사용자(ITPZZ001): 내 부서 사업만</li>
     *   <li>관리자(ITPAD001): 전체 사업</li>
     *   <li>평가위원: 배정된 협의회만</li>
     * </ul>
     */
    public record ListResponse(
        /** 협의회ID (ASCT-{연도}-{4자리}). 협의회 신청 전이면 null */
        String asctId,
        /** 프로젝트관리번호 */
        String prjMngNo,
        /** 프로젝트순번 */
        Integer prjSno,
        /** 사업명 (BPROJM.PRJ_NM 또는 BPOVWM.PRJ_NM) */
        String prjNm,
        /** 협의회상태 코드 (DRAFT~COMPLETED). 협의회 신청 전이면 null */
        String asctSts,
        /** 심의유형 (INFO_SYS/INFO_SEC/ETC). 협의회 신청 전이면 null */
        String dbrTp,
        /** 회의일자 (SCHEDULED 이후 설정). 협의회 신청 전이면 null */
        LocalDate cnrcDt,
        /** 협의회 신청 여부 (false = 결재완료 사업이지만 아직 신청 전) */
        boolean applied,
        // ── 사업 상세 정보 (BPROJM) ──────────────────────────────────────
        /** 사업연도 */
        String prjYy,
        /** 프로젝트유형 (신규개발/고도화/유지보수 등) */
        String prjTp,
        /** 주관부서 */
        String svnDpm,
        /** 사업예산 (원) */
        java.math.BigDecimal prjBg,
        /** 사업시작일자 */
        LocalDate sttDt,
        /** 사업종료일자 */
        LocalDate endDt,
        /** IT담당부서 */
        String itDpm,
        /** 사업설명 (최대 1000자) */
        String prjDes
    ) {}

    /**
     * 협의회 신청 요청 (신규 생성)
     *
     * <p>소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 먼저 신청합니다.</p>
     */
    public record CreateRequest(
        /** 프로젝트관리번호 (BPROJM FK) */
        String prjMngNo,
        /** 프로젝트순번 (BPROJM FK) */
        Integer prjSno,
        /** 심의유형 (INFO_SYS/INFO_SEC/ETC) */
        String dbrTp
    ) {}

    /**
     * 협의회 단건 상세 조회 응답
     *
     * <p>협의회 기본 정보를 반환합니다. 각 단계별 상세 정보는 별도 API로 조회합니다.</p>
     */
    public record DetailResponse(
        /** 협의회ID */
        String asctId,
        /** 프로젝트관리번호 */
        String prjMngNo,
        /** 프로젝트순번 */
        Integer prjSno,
        /** 협의회상태 코드 */
        String asctSts,
        /** 심의유형 */
        String dbrTp,
        /** 회의일자 */
        LocalDate cnrcDt,
        /** 회의시간 */
        String cnrcTm,
        /** 회의장소 */
        String cnrcPlc,
        /** 사업명 (BPROJM.PRJ_NM) */
        String prjNm,
        /** 전결권자 (BPROJM.EDRT) */
        String edrt,
        /** 사업기간 시작일 (BPROJM.STT_DT) */
        java.time.LocalDate sttDt,
        /** 사업기간 종료일 (BPROJM.END_DT) */
        java.time.LocalDate endDt,
        /** 필요성 (BPROJM.NCS) */
        String ncs,
        /** 소요예산 (BPROJM.PRJ_BG) */
        java.math.BigDecimal prjBg,
        /** 사업내용 (BPROJM.PRJ_DES) */
        String prjDes,
        /** 기대효과 (BPROJM.XPT_EFF) */
        String xptEff
    ) {}

    // =========================================================================
    // M4: 타당성검토표 관련
    // =========================================================================

    /**
     * 타당성검토표 전체 저장 요청 (사업개요 + 자체점검 + 성과지표)
     *
     * <p>임시저장(TEMP)과 작성완료(COMPLETE) 모두 이 요청을 사용합니다.
     * kpnTp 값에 따라 서비스 로직이 분기됩니다.</p>
     */
    public record FeasibilityRequest(
        /** 사업명 */
        String prjNm,
        /** 사업기간 */
        String prjTrm,
        /** 필요성 (최대 1000자) */
        String ncs,
        /** 소요예산 (숫자형) */
        Long prjBg,
        /** 전결권자 */
        String edrt,
        /** 사업내용 (최대 1000자) */
        String prjDes,
        /** 법률규제대응여부 (Y/N) */
        String lglRglYn,
        /** 관련법률규제명 */
        String lglRglNm,
        /** 기대효과 (최대 1000자) */
        String xptEff,
        /** 저장유형 (TEMP:임시저장 / COMPLETE:작성완료) */
        String kpnTp,
        /** 타당성 자체점검 6개 항목 */
        List<CheckItemRequest> checkItems,
        /** 성과지표 목록 (1개 이상) */
        List<PerformanceRequest> performances,
        /** 첨부파일관리번호 (hwp/hwpx/pdf) */
        String flMngNo
    ) {}

    /**
     * 타당성검토표 조회 응답 (사업개요 + 자체점검 + 성과지표 통합)
     */
    public record FeasibilityResponse(
        /** 사업명 */
        String prjNm,
        /** 사업기간 */
        String prjTrm,
        /** 필요성 */
        String ncs,
        /** 소요예산 */
        Long prjBg,
        /** 전결권자 */
        String edrt,
        /** 사업내용 */
        String prjDes,
        /** 법률규제대응여부 */
        String lglRglYn,
        /** 관련법률규제명 */
        String lglRglNm,
        /** 기대효과 */
        String xptEff,
        /** 저장유형 */
        String kpnTp,
        /** 자체점검 항목 목록 */
        List<CheckItemResponse> checkItems,
        /** 성과지표 목록 */
        List<PerformanceResponse> performances,
        /** 첨부파일관리번호 */
        String flMngNo
    ) {}

    /**
     * 타당성 자체점검 항목 요청
     */
    public record CheckItemRequest(
        /** 점검항목코드 (MGMT_STR/FIN_EFC/RISK_IMP/REP_IMP/DUP_SYS/ETC) */
        String ckgItmC,
        /** 점검내용 */
        String ckgCone,
        /** 점검점수 (1~5) */
        Integer ckgRcrd
    ) {}

    /**
     * 타당성 자체점검 항목 응답 (화면 표출용 한글명 포함)
     */
    public record CheckItemResponse(
        /** 점검항목코드 */
        String ckgItmC,
        /** 화면 표출용 한글명 (예: 경영전략/계획 부합) */
        String ckgItmNm,
        /** 점검내용 */
        String ckgCone,
        /** 점검점수 (1~5) */
        Integer ckgRcrd
    ) {}

    /**
     * 성과지표 요청 (추가/수정 공통)
     */
    public record PerformanceRequest(
        /** 지표순번 (클라이언트 관리, 1부터 시작) */
        Integer dtpSno,
        /** 성과지표명 */
        String dtpNm,
        /** 성과지표정의 */
        String dtpCone,
        /** 측정방법 */
        String msmManr,
        /** 산식 */
        String clf,
        /** 목표치 */
        String glNv,
        /** 측정시작일 */
        LocalDate msmSttDt,
        /** 측정종료일 */
        LocalDate msmEndDt,
        /** 측정시점 */
        String msmTpm,
        /** 측정주기 */
        String msmCle
    ) {}

    /**
     * 성과지표 응답
     */
    public record PerformanceResponse(
        Integer dtpSno,
        String dtpNm,
        String dtpCone,
        String msmManr,
        String clf,
        String glNv,
        LocalDate msmSttDt,
        LocalDate msmEndDt,
        String msmTpm,
        String msmCle
    ) {}

    // =========================================================================
    // M6: 평가위원/일정 관련
    // =========================================================================

    /**
     * 평가위원 선정 요청 (IT관리자)
     */
    public record CommitteeRequest(
        /** 심의유형 (당연위원 자동 배치 기준) */
        String dbrTp,
        /** 위원 목록 (당연+소집+간사 전체) */
        List<CommitteeMemberRequest> members
    ) {}

    /**
     * 위원 항목 요청
     */
    public record CommitteeMemberRequest(
        /** 사번 */
        String eno,
        /** 위원유형 (MAND:당연/CALL:소집/SECR:간사) */
        String vlrTp
    ) {}

    /**
     * 일정 입력 요청 (평가위원)
     */
    public record ScheduleRequest(
        /** 가능한 날짜×시간대 목록 */
        List<ScheduleItem> availableSlots
    ) {}

    /**
     * 일정 항목
     */
    public record ScheduleItem(
        /** 일정일자 */
        LocalDate dsdDt,
        /** 일정시간 (10:00/14:00/15:00/16:00) */
        String dsdTm,
        /** 가능여부 (Y/N) */
        String psbYn
    ) {}

    /**
     * 일정 확정 요청 (IT관리자)
     *
     * <p>확정 후 BASCTM.CNRC_DT/TM/PLC에 반영하고 상태를 SCHEDULED로 전이합니다.</p>
     */
    public record ScheduleConfirmRequest(
        /** 최종 확정 회의일자 */
        LocalDate cnrcDt,
        /** 최종 확정 회의시간 (10:00/14:00/15:00/16:00) */
        String cnrcTm,
        /** 회의장소 */
        String cnrcPlc
    ) {}

    // =========================================================================
    // M7: 평가의견/결과서 관련
    // =========================================================================

    /**
     * 평가의견 작성 요청 (평가위원)
     *
     * <p>6개 항목 전체를 한 번에 저장합니다.</p>
     */
    public record EvaluationRequest(
        /** 6개 점검항목별 점수+의견 */
        List<EvaluationItem> items
    ) {}

    /**
     * 평가의견 항목
     */
    public record EvaluationItem(
        /** 점검항목코드 */
        String ckgItmC,
        /** 점검점수 (1~5) */
        Integer ckgRcrd,
        /** 점검의견 (1~2점 시 필수) */
        String ckgOpnn
    ) {}

    /**
     * 결과서 작성/수정 요청 (IT관리자)
     */
    public record ResultRequest(
        /** 종합의견 */
        String synOpnn,
        /** 타당성검토의견 */
        String ckgOpnn,
        /** 관련자료 첨부파일관리번호 */
        String flMngNo
    ) {}

    // =========================================================================
    // M6: 평가위원/일정 응답 DTO
    // =========================================================================

    /**
     * 평가위원 단건 응답 (사용자 정보 포함)
     */
    public record CommitteeMemberResponse(
        /** 사번 */
        String eno,
        /** 성명 */
        String usrNm,
        /** 부서명 */
        String bbrNm,
        /** 직위명 (팀장, 차장, 과장 등) */
        String ptCNm,
        /** 위원유형 (MAND:당연/CALL:소집/SECR:간사) */
        String vlrTp,
        /** 결과서 검토 확인 여부 (N: 미확인, Y: 확인완료) */
        String cnfmYn
    ) {}

    /**
     * 평가위원 목록 응답
     */
    public record CommitteeListResponse(
        /** 당연위원 목록 */
        List<CommitteeMemberResponse> mandatory,
        /** 소집위원 목록 */
        List<CommitteeMemberResponse> call,
        /** 간사 목록 */
        List<CommitteeMemberResponse> secretary
    ) {}

    /**
     * 일정 슬롯별 응답 현황 (위원별)
     */
    public record ScheduleSlotResponse(
        /** 일정일자 */
        LocalDate dsdDt,
        /** 일정시간 (10:00/14:00/15:00/16:00) */
        String dsdTm,
        /** 가능여부 (Y/N) */
        String psbYn
    ) {}

    /**
     * 위원별 일정 응답 현황
     */
    public record MemberScheduleStatus(
        /** 사번 */
        String eno,
        /** 성명 */
        String usrNm,
        /** 부서명 (화면 표출용) */
        String bbrNm,
        /** 직책명 (화면 표출용) */
        String ptCNm,
        /** 위원유형 */
        String vlrTp,
        /** 응답 완료 여부 */
        boolean responded,
        /** 위원의 일정 응답 목록 */
        List<ScheduleSlotResponse> slots
    ) {}

    /**
     * 일정 입력 현황 응답 (IT관리자용)
     */
    public record ScheduleStatusResponse(
        /** 전체 위원 수 */
        int totalCount,
        /** 응답 완료 위원 수 */
        int respondedCount,
        /** 미응답 위원 수 */
        long pendingCount,
        /** 위원별 응답 현황 */
        List<MemberScheduleStatus> memberStatuses,
        /**
         * 일정 확정 가능 여부 (필수 응답자 기준)
         * INFO_SYS: 예산팀장(12004) + IT기획팀장(18001) 응답 완료 시 true
         * 기타 타입: 전원 응답 완료 시 true
         */
        boolean allRequiredResponded
    ) {}

    // =========================================================================
    // M7: 평가의견/결과서 응답 DTO
    // =========================================================================

    /**
     * 위원 개인 평가의견 항목 응답
     */
    public record EvaluationItemResponse(
        /** 사번 */
        String eno,
        /** 성명 */
        String usrNm,
        /** 점검항목코드 */
        String ckgItmC,
        /** 점검항목명 */
        String ckgItmNm,
        /** 점검점수 (1~5) */
        Integer ckgRcrd,
        /** 점검의견 (1~2점 시 필수) */
        String ckgOpnn
    ) {}

    /**
     * 점검항목별 평균점수
     */
    public record CheckItemAvgScore(
        /** 점검항목코드 */
        String ckgItmC,
        /** 점검항목명 */
        String ckgItmNm,
        /** 평균점수 */
        Double avgScore
    ) {}

    /**
     * 평가의견 전체 현황 응답 (IT관리자용)
     *
     * <p>전체 위원별 평가의견 + 항목별 평균점수를 포함합니다.</p>
     */
    public record EvaluationSummaryResponse(
        /** 위원별 평가의견 목록 */
        List<EvaluationItemResponse> evaluations,
        /** 점검항목별 평균점수 */
        List<CheckItemAvgScore> avgScores
    ) {}

    /**
     * 결과서 조회 응답 (IT관리자용)
     */
    public record ResultResponse(
        /** 종합의견 */
        String synOpnn,
        /** 타당성검토의견 */
        String ckgOpnn,
        /** 관련자료 첨부파일관리번호 */
        String flMngNo,
        /** 점검항목별 평균점수 (결과서 작성 참고용) */
        List<CheckItemAvgScore> avgScores
    ) {}

    // =========================================================================
    // M5: 전자결재 연동
    // =========================================================================

    /**
     * 타당성검토표 결재 요청 (소관부서 담당자 → 팀장)
     *
     * <p>SUBMITTED 상태인 협의회의 타당성검토표를 팀장에게 결재 요청합니다.
     * 신청자 사번은 JWT에서, 신청의견은 선택사항입니다.</p>
     */
    public record ApprovalRequest(
        /** 결재자(팀장) 사번 */
        String approverEno,
        /** 신청의견 (선택) */
        String rqsOpnn
    ) {}

    /**
     * 결재 콜백 요청 (전자결재 시스템 → 협의회 시스템)
     *
     * <p>팀장 결재 완료/반려 시 협의회 상태를 업데이트하는 콜백 요청입니다.</p>
     */
    public record ApprovalCallbackRequest(
        /** 결재완료 여부 (true: 승인→APPROVED, false: 반려→DRAFT) */
        boolean approved
    ) {}

    /**
     * 결재 요청 응답
     */
    public record ApprovalResponse(
        /** 생성된 신청관리번호 (예: APF_202600000001) */
        String apfMngNo
    ) {}

    /**
     * 개최결과서 결재 요청 (IT관리자 → 부장)
     *
     * <p>FINAL_APPROVAL 상태에서 IT관리자가 결재자(부장)를 지정하여
     * 전자결재 시스템에 개최결과서 결재를 신청합니다.</p>
     */
    public record ResultApprovalRequest(
        /** 결재자 1순위 — 팀장 사번 */
        String teamLeadEno,
        /** 결재자 2순위 — 부장 사번 */
        String deptHeadEno,
        /** 신청의견 (선택) */
        String rqsOpnn
    ) {}

    // =========================================================================
    // M6: 사전질의응답 관련 (QnaService 사용)
    // =========================================================================

    /**
     * 사전 질의 등록 요청 (평가위원)
     *
     * <p>평가위원이 협의회 개최 전 사전 질의를 등록합니다.
     * 질의자 사번은 JWT에서 자동 주입됩니다.</p>
     */
    public record QnaCreateRequest(
        /** 질의내용 (최대 4000자) */
        String qtnCone
    ) {}

    /**
     * 사전 질의 답변 요청 (추진부서 담당자)
     *
     * <p>추진부서 담당자(ITPZZ001)가 질의에 답변합니다.
     * 답변자 사번은 JWT에서 자동 주입됩니다.</p>
     */
    public record QnaReplyRequest(
        /** 답변내용 (최대 4000자) */
        String repCone
    ) {}

    /**
     * 사전질의 수정 요청 DTO
     *
     * <p>질의 등록자가 자신의 질의 내용을 수정합니다.</p>
     */
    public record QnaUpdateRequest(
        /** 수정할 질의내용 (최대 4000자) */
        String qtnCone
    ) {}

    /**
     * 사전질의응답 항목 응답
     */
    public record QnaResponse(
        /** 질의응답ID (QTN-{asctId}-{순번}) */
        String qtnId,
        /** 질의자사번 */
        String qtnEno,
        /** 질의자성명 (null 가능, 성능 이슈 시 별도 조회) */
        String qtnNm,
        /** 질의내용 */
        String qtnCone,
        /** 답변자사번 (미답변 시 null) */
        String repEno,
        /** 답변자성명 (null 가능) */
        String repNm,
        /** 답변내용 (미답변 시 null) */
        String repCone,
        /** 답변여부 (Y/N) */
        String repYn
    ) {}

    /**
     * 추진부서 통보 응답
     *
     * <p>통보 완료 후 수신자(추진부서 담당자) 정보를 반환합니다.</p>
     */
    public record NotifyResponse(
        /** 수신자 사번 */
        String eno,
        /** 수신자 성명 */
        String usrNm,
        /** 수신자 부서명 */
        String bbrNm,
        /** 수신자 팀명 */
        String temNm
    ) {}
}
