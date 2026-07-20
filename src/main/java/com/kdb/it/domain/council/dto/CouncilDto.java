package com.kdb.it.domain.council.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 정보화실무협의회 DTO 중앙 관리
 *
 * <p>모든 협의회 관련 Request/Response DTO를 이 클래스에서 inner record로 관리합니다.</p>
 *
 * <p>모듈별 DTO 범위:</p>
 * <ul>
 *   <li>M3 (CouncilService): {@link ListResponse}, {@link CreateRequest}, {@link DetailResponse}</li>
 *   <li>M4 (FeasibilityService): {@link FeasibilityRequest}, {@link FeasibilityResponse},
 *       {@code CheckItemRequest}, {@link PerformanceRequest}</li>
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
     *
     * @param asctId 협의회ID
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno 프로젝트순번
     * @param abusNm 사업명
     * @param asctStsC 협의회상태코드
     * @param dbrTc 심의유형
     * @param cnrcDt 회의일자
     * @param cnrcTm 회의시간
     * @param applied 협의회 신청 여부
     * @param prjYy 사업연도
     * @param prjTp 프로젝트유형
     * @param svnDpm 주관부서
     * @param prjBg 사업예산
     * @param sttDt 사업시작일자
     * @param endDt 사업종료일자
     * @param itDpm IT담당부서
     * @param prjDes 사업설명
     * @param csfHeldYn 대면개최여부
     */
    public record ListResponse(
        /** 협의회ID (ASCT-{연도}-{4자리}). 협의회 신청 전이면 null */
        String asctId,
        /** 프로젝트관리번호 */
        String prjMngNo,
        /** 프로젝트순번 */
        Integer prjSno,
        /** 사업명 (BPROJM.ABUS_NM 또는 BPOVWM.PRJ_NM) */
        String abusNm,
        /** 협의회상태 코드 (DRAFT~COMPLETED). 협의회 신청 전이면 null */
        String asctStsC,
        /** 심의유형 (INFO_SYS/INFO_SEC/ETC). 협의회 신청 전이면 null */
        String dbrTc,
        /** 회의일자 (SCHEDULED 이후 설정). 협의회 신청 전이면 null */
        LocalDate cnrcDt,
        /** 회의시간 (예: '10:00', SCHEDULED 이후 설정). 미확정이면 null (PRD §25) */
        String cnrcTm,
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
        String prjDes,
        /** 대면개최여부 (Y=대면 / N=서면 / null=미확정) (PRD_c_20260620 #1) */
        String csfHeldYn
    ) {}

    /**
     * 협의회 신청 요청 (신규 생성)
     *
     * <p>소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 먼저 신청합니다.</p>
     *
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno 프로젝트순번
     * @param dbrTc 심의유형
     */
    public record CreateRequest(
        /** 프로젝트관리번호 (BPROJM FK) — dbrTc='02'(계획협의회)에서는 미사용(null) */
        String prjMngNo,
        /** 프로젝트순번 (BPROJM FK) — dbrTc='02'에서는 미사용(null) */
        Integer prjSno,
        /** 심의유형 ('02'=정보기술부문계획 / '03'=정보시스템 / '04'=정보보호 / '05'=기타) */
        @NotBlank String dbrTc,
        /** 계획관리번호 (BPLANM FK) — dbrTc='02'(계획협의회)에서만 필수 */
        String reqDocNo
    ) {
        /**
         * 심의 대상 검증: 계획협의회('02')는 reqDocNo 필수, 그 외 심의유형은 prjMngNo/prjSno 필수.
         *
         * <p>{@code @JsonIgnore}: Jackson이 이 boolean 게터를 'targetPresent' 속성으로 직렬화하지
         * 않도록 한다(직렬화 시 phantom 속성이 생기면 역직렬화에서 unknown property로 400 유발).</p>
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        @AssertTrue(message = "심의유형에 필요한 대상 정보(사업 또는 계획)가 누락되었습니다.")
        public boolean isTargetPresent() {
            if ("02".equals(dbrTc)) {
                return reqDocNo != null && !reqDocNo.isBlank();
            }
            return prjMngNo != null && !prjMngNo.isBlank() && prjSno != null;
        }
    }

    /**
     * 협의회 단건 상세 조회 응답
     *
     * <p>협의회 기본 정보를 반환합니다. 각 단계별 상세 정보는 별도 API로 조회합니다.</p>
     *
     * @param asctId 협의회ID
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno 프로젝트순번
     * @param asctStsC 협의회상태코드
     * @param dbrTc 심의유형
     * @param cnrcDt 회의일자
     * @param cnrcTm 회의시간
     * @param cnrcPlc 회의장소
     * @param abusNm 사업명
     * @param edrt 전결권자
     * @param sttDt 사업기간 시작일
     * @param endDt 사업기간 종료일
     * @param ncs 필요성
     * @param prjBg 소요예산
     * @param prjDes 사업내용
     * @param xptEff 기대효과
     * @param csfHeldYn 대면개최여부
     */
    public record DetailResponse(
        /** 협의회ID */
        String asctId,
        /** 프로젝트관리번호 */
        String prjMngNo,
        /** 프로젝트순번 */
        Integer prjSno,
        /** 협의회상태 코드 */
        String asctStsC,
        /** 심의유형 */
        String dbrTc,
        /** 회의일자 */
        LocalDate cnrcDt,
        /** 회의시간 */
        String cnrcTm,
        /** 회의장소 */
        String cnrcPlc,
        /** 사업명 (BPROJM.ABUS_NM) */
        String abusNm,
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
        String xptEff,
        /** 대면개최여부 (Y=대면 / N=서면 / null=미확정) (PRD_c_20260620 #1) */
        String csfHeldYn
    ) {}

    // =========================================================================
    // M4: 타당성검토표 관련
    // =========================================================================

    /**
     * 타당성검토표 전체 저장 요청 (사업개요 + 자체점검 + 성과지표)
     *
     * <p>임시저장(TEMP)과 작성완료(COMPLETE) 모두 이 요청을 사용합니다.
     * kpnTc 값에 따라 서비스 로직이 분기됩니다.</p>
     *
     * @param prjNm 사업명
     * @param prjTrm 사업기간
     * @param ncs 필요성
     * @param prjBg 소요예산금액
     * @param edrt 전결권자명
     * @param prjDes 사업내용
     * @param lglRglYn 법률규제대응여부
     * @param lglRglNm 관련법률규제명
     * @param xptEff 기대효과
     * @param kpnTc 저장구분코드
     * @param performances 성과지표 목록
     * @param flMngNo 첨부파일관리번호
     */
    public record FeasibilityRequest(
        /** 사업명 */
        String prjNm,
        /** 사업기간 */
        String prjTrm,
        /** 필요성 (최대 1000자) */
        String ncs,
        /** 소요예산금액 (NUMBER(18,3) → BigDecimal) */
        java.math.BigDecimal prjBg,
        /** 전결권자명 */
        String edrt,
        /** 사업내용 (최대 1000자) */
        String prjDes,
        /** 법률규제대응여부 (Y/N) */
        String lglRglYn,
        /** 관련법률규제명 */
        String lglRglNm,
        /** 기대효과 (최대 1000자) */
        String xptEff,
        /** 저장구분코드 (TEMP:임시저장 / COMPLETE:작성완료) */
        @NotBlank String kpnTc,
        /** 성과지표 목록 (1개 이상) */
        List<PerformanceRequest> performances,
        /** 첨부파일관리번호 (hwp/hwpx/pdf) */
        String flMngNo
    ) {}

    /**
     * 타당성검토표 조회 응답 (사업개요 + 자체점검 + 성과지표 통합)
     *
     * @param prjNm 사업명
     * @param prjTrm 사업기간
     * @param ncs 필요성
     * @param prjBg 소요예산금액
     * @param edrt 전결권자명
     * @param prjDes 사업내용
     * @param lglRglYn 법률규제대응여부
     * @param lglRglNm 관련법률규제명
     * @param xptEff 기대효과
     * @param kpnTc 저장유형
     * @param performances 성과지표 목록
     * @param flMngNo 첨부파일관리번호
     */
    public record FeasibilityResponse(
        /** 사업명 */
        String prjNm,
        /** 사업기간 */
        String prjTrm,
        /** 필요성 */
        String ncs,
        /** 소요예산금액 (BigDecimal) */
        java.math.BigDecimal prjBg,
        /** 전결권자명 */
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
        String kpnTc,
        /** 성과지표 목록 */
        List<PerformanceResponse> performances,
        /** 첨부파일관리번호 */
        String flMngNo
    ) {}



    /**
     * 성과지표 요청 (추가/수정 공통)
     *
     * @param dtpSno 지표순번
     * @param dtpNm 성과지표명
     * @param dtpCone 성과지표정의
     * @param clf 산식
     * @param msmTpm 측정시점
     * @param msmCle 측정주기
     */
    public record PerformanceRequest(
        /** 지표순번 (클라이언트 관리, 1부터 시작) */
        Integer dtpSno,
        /** 성과지표명 */
        String dtpNm,
        /** 성과지표정의 */
        String dtpCone,
        /** 산식 */
        String clf,
        /** 측정시점 */
        String msmTpm,
        /** 측정주기 */
        String msmCle
    ) {}

    /**
     * 성과지표 응답
     *
     * @param dtpSno 지표순번
     * @param dtpNm 성과지표명
     * @param dtpCone 성과지표정의
     * @param clf 산식
     * @param msmTpm 측정시점
     * @param msmCle 측정주기
     */
    public record PerformanceResponse(
        Integer dtpSno,
        String dtpNm,
        String dtpCone,
        String clf,
        String msmTpm,
        String msmCle
    ) {}

    // =========================================================================
    // M6: 평가위원/일정 관련
    // =========================================================================

    /**
     * 평가위원 선정 요청 (IT관리자)
     *
     * @param dbrTc 심의유형
     * @param members 위원 목록
     */
    public record CommitteeRequest(
        /** 심의유형 (당연위원 자동 배치 기준) */
        String dbrTc,
        /** 위원 목록 (당연+소집+간사 전체) */
        @NotEmpty List<CommitteeMemberRequest> members
    ) {}

    /**
     * 위원 항목 요청
     *
     * @param eno 사번
     * @param vlrTc 위원유형
     */
    public record CommitteeMemberRequest(
        /** 사번 */
        String eno,
        /** 위원유형 (MAND:당연/CALL:소집/SECR:간사) */
        String vlrTc
    ) {}

    /**
     * 일정 입력 요청 (평가위원)
     *
     * @param availableSlots 가능한 날짜와 시간대 목록
     * @param csfHopeYn 대면희망여부
     */
    public record ScheduleRequest(
        /** 가능한 날짜×시간대 목록 */
        @NotEmpty List<ScheduleItem> availableSlots,
        /** 대면희망여부 (Y/N) — 위원이 일정 응답 시 함께 선택 (PRD_c_20260620 #1) */
        String csfHopeYn
    ) {}

    /**
     * 일정 항목
     *
     * @param dsdDt 일정일자
     * @param dsdTm 일정시간
     * @param psbYn 가능여부
     */
    public record ScheduleItem(
        /** 일정일자 (DT 도메인 VARCHAR2(8) yyyyMMdd) */
        String dsdDt,
        /** 일정시간 (10:00/14:00/15:00/16:00) */
        String dsdTm,
        /** 가능여부 (Y/N) */
        String psbYn
    ) {}

    /**
     * 일정 확정 요청 (IT관리자)
     *
     * <p>확정 후 BASCTM.CNRC_DT/TM/PLC에 반영하고 상태를 SCHEDULED로 전이합니다.</p>
     *
     * @param cnrcDt 최종 확정 회의일자
     * @param cnrcTm 최종 확정 회의시간
     * @param cnrcPlc 회의장소
     */
    public record ScheduleConfirmRequest(
        /** 최종 확정 회의일자 */
        @NotNull LocalDate cnrcDt,
        /** 최종 확정 회의시간 (10:00/14:00/15:00/16:00) */
        @NotBlank String cnrcTm,
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
     *
     * @param items 점검항목별 점수와 의견 목록
     */
    public record EvaluationRequest(
        /** 6개 점검항목별 점수+의견 */
        @NotEmpty List<EvaluationItem> items
    ) {}

    /**
     * 평가의견 항목
     *
     * @param ckgItmC 점검항목코드
     * @param ckgRcrd 점검점수
     * @param ckgOpnn 점검의견
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
     *
     * @param synOpnn 종합의견
     * @param ckgOpnn 타당성검토의견
     * @param flMngNo 관련자료 첨부파일관리번호
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
     *
     * @param eno 사번
     * @param usrNm 성명
     * @param bbrNm 부서명
     * @param ptCNm 직위명
     * @param vlrTc 위원유형
     * @param cnfmYn 결과서 검토 확인 여부
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
        String vlrTc,
        /** 결과서 검토 확인 여부 (N: 미확인, Y: 확인완료) */
        String cnfmYn
    ) {}

    /**
     * 평가위원 목록 응답
     *
     * @param mandatory 당연위원 목록
     * @param call 소집위원 목록
     * @param secretary 간사 목록
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
     *
     * @param dsdDt 일정일자
     * @param dsdTm 일정시간
     * @param psbYn 가능여부
     */
    public record ScheduleSlotResponse(
        /** 일정일자 (DT 도메인 VARCHAR2(8) yyyyMMdd) */
        String dsdDt,
        /** 일정시간 (10:00/14:00/15:00/16:00) */
        String dsdTm,
        /** 가능여부 (Y/N) */
        String psbYn
    ) {}

    /**
     * 위원별 일정 응답 현황
     *
     * @param eno 사번
     * @param usrNm 성명
     * @param bbrNm 부서명
     * @param ptCNm 직책명
     * @param vlrTc 위원유형
     * @param responded 응답 완료 여부
     * @param csfHpYn 대면희망여부
     * @param slots 위원의 일정 응답 목록
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
        String vlrTc,
        /** 응답 완료 여부 */
        boolean responded,
        /** 대면희망여부 (Y/N/null) (PRD_c_20260620 #1) */
        String csfHpYn,
        /** 위원의 일정 응답 목록 */
        List<ScheduleSlotResponse> slots
    ) {}

    /**
     * 일정 입력 현황 응답 (IT관리자용)
     *
     * @param totalCount 전체 위원 수
     * @param respondedCount 응답 완료 위원 수
     * @param pendingCount 미응답 위원 수
     * @param memberStatuses 위원별 응답 현황
     * @param allRequiredResponded 필수 응답자 응답 완료 여부
     * @param anyFaceToFaceHope 대면희망 위원 존재 여부
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
        boolean allRequiredResponded,
        /**
         * 대면희망 위원 존재 여부 (PRD_c_20260620 #1).
         * 위원 중 한 명이라도 CSF_HP_YN='Y'이면 true → 대면개최.
         * 전원 미희망(false)이고 응답 완료 시 서면개최 확정 가능.
         */
        boolean anyFaceToFaceHope
    ) {}

    // =========================================================================
    // M7: 평가의견/결과서 응답 DTO
    // =========================================================================

    /**
     * 위원 개인 평가의견 항목 응답
     *
     * @param eno 사번
     * @param usrNm 성명
     * @param ckgItmC 점검항목코드
     * @param ckgItmNm 점검항목명
     * @param ckgRcrd 점검점수
     * @param ckgOpnn 점검의견
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
     *
     * @param ckgItmC 점검항목코드
     * @param ckgItmNm 점검항목명
     * @param avgScore 평균점수
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
     *
     * @param evaluations 위원별 평가의견 목록
     * @param avgScores   점검항목별 평균점수 목록
     */
    public record EvaluationSummaryResponse(
        /** 위원별 평가의견 목록 */
        List<EvaluationItemResponse> evaluations,
        /** 점검항목별 평균점수 */
        List<CheckItemAvgScore> avgScores
    ) {}

    // =========================================================================
    // 정보기술부문계획 협의회(dbrTc='02') — 사업별 적정/유보 평가
    // =========================================================================

    /** 계획협의회 사업별 평가 저장 요청 (위원이 여러 사업을 한 번에 제출) */
    public record PlanEvaluationRequest(
        /** 사업별 적정/유보 항목 */
        @NotEmpty List<PlanEvaluationItem> items
    ) {}

    /** 계획협의회 사업별 평가 항목 */
    public record PlanEvaluationItem(
        /** 사업관리번호 */
        @NotBlank String abusMngNo,
        /** 적정여부 (Y=적정 / N=유보) */
        @NotBlank String pprtYn,
        /** 평가의견(사유) — 적정/유보 모두 필수 */
        String evalOpnn
    ) {}

    /** 위원 개인 사업별 평가 응답 */
    public record PlanEvaluationItemResponse(
        /** 사번 */
        String eno,
        /** 성명 */
        String usrNm,
        /** 사업관리번호 */
        String abusMngNo,
        /** 적정여부 (Y=적정 / N=유보) */
        String pprtYn,
        /** 평가의견(사유) */
        String evalOpnn
    ) {}

    /** 사업별 최종 판정 (위원 1명이라도 유보면 유보) */
    public record PlanBusinessVerdict(
        /** 사업관리번호 */
        String abusMngNo,
        /** 최종 적정여부 (Y=적정 / N=유보) */
        String finalPprtYn,
        /** 유보(N) 선택 위원 수 */
        long reserveCount,
        /** 평가한 위원 수 */
        long evaluatorCount
    ) {}

    /** 계획협의회 평가 전체 현황 (IT관리자용): 위원별 평가 + 사업별 최종 판정 */
    public record PlanEvaluationSummaryResponse(
        /** 위원별 사업 평가 목록 */
        List<PlanEvaluationItemResponse> evaluations,
        /** 사업별 최종 판정 */
        List<PlanBusinessVerdict> verdicts
    ) {}

    /** 계획협의회 결과서 프리필 요약: 사업별 판정 표(HTML) + 구조화 판정 */
    public record PlanResultSummaryResponse(
        /** 사업별 판정 요약 표 (HTML, 결과서 본문 프리필용) */
        String summaryHtml,
        /** 사업별 최종 판정 */
        List<PlanBusinessVerdict> verdicts
    ) {}

    /** 계획협의회 심의 대상: 계획 요약 + 사업별 기본정보(스냅샷 예산 + 사업상세 개요/기간 병합) */
    public record PlanTargetsResponse(
        /** 계획관리번호 */
        String reqDocNo,
        /** 대상년도 */
        String bseYy,
        /** 계획구분 (신규/조정) */
        String itPtlPlnTpC,
        /** 심의 대상 정보화사업 목록 */
        List<PlanTargetBusiness> businesses,
        /** 전산업무비 참고 건수 (평가 대상 아님) */
        int costCount
    ) {}

    /** 심의 대상 사업 1건 (계획 스냅샷 예산 + BPROJM 사업개요/기간) */
    public record PlanTargetBusiness(
        /** 사업관리번호 */
        String abusMngNo,
        /** 사업명 */
        String abusNm,
        /** 진행구분 (신규/계속) */
        String pulDtt,
        /** 주관본부/부문 */
        String svnHdq,
        /** 주관부서명 */
        String svnDpmNm,
        /** 사업개요 (BPROJM 사업설명) */
        String prjDes,
        /** 시작일자 */
        java.time.LocalDate sttDt,
        /** 종료일자 */
        java.time.LocalDate endDt,
        /** 총예산 */
        java.math.BigDecimal prjBg,
        /** 자본예산 */
        java.math.BigDecimal assetBg,
        /** 일반관리비 */
        java.math.BigDecimal costBg,
        /** (조정 협의회) 직전 승인 수립계획의 총예산 — 최초. 비조정/미존재 시 null */
        java.math.BigDecimal basePrjBg,
        /** (조정 협의회) 최초 자본예산 */
        java.math.BigDecimal baseAssetBg,
        /** (조정 협의회) 최초 일반관리비 */
        java.math.BigDecimal baseCostBg
    ) {}

    /**
     * 결과서 조회 응답 (IT관리자용)
     *
     * @param synOpnn   종합의견
     * @param ckgOpnn   타당성검토의견
     * @param flMngNo   관련자료 첨부파일관리번호
     * @param avgScores 점검항목별 평균점수 목록
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
     *
     * @param approverEno 결재자 사번
     * @param rqsOpnn 신청의견
     */
    public record ApprovalRequest(
        /** 결재자(팀장) 사번 */
        @NotBlank String approverEno,
        /** 신청의견 (선택) */
        String rqsOpnn
    ) {}

    /**
     * 결재 콜백 요청 (전자결재 시스템 → 협의회 시스템)
     *
     * <p>팀장 결재 완료/반려 시 협의회 상태를 업데이트하는 콜백 요청입니다.</p>
     *
     * @param approved 결재 승인 여부. true이면 승인, false이면 반려
     */
    public record ApprovalCallbackRequest(
        /** 결재완료 여부 (true: 승인→APPROVED, false: 반려→DRAFT) */
        boolean approved
    ) {}

    /**
     * 결재 요청 응답
     *
     * @param apfMngNo 생성된 신청관리번호
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
     *
     * @param teamLeadEno 팀장 결재자 사번
     * @param deptHeadEno 부장 결재자 사번
     * @param rqsOpnn     신청의견
     */
    public record ResultApprovalRequest(
        /** 결재자 1순위 — 팀장 사번 */
        @NotBlank String teamLeadEno,
        /** 결재자 2순위 — 부장 사번 */
        @NotBlank String deptHeadEno,
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
     *
     * @param qtnCone 질의내용
     */
    public record QnaCreateRequest(
        /** 질의내용 (최대 4000자) */
        @NotBlank String qtnCone
    ) {}

    /**
     * 사전 질의 답변 요청 (추진부서 담당자)
     *
     * <p>추진부서 담당자(ITPZZ001)가 질의에 답변합니다.
     * 답변자 사번은 JWT에서 자동 주입됩니다.</p>
     *
     * @param repCone 답변내용
     */
    public record QnaReplyRequest(
        /** 답변내용 (최대 4000자) */
        @NotBlank String repCone
    ) {}

    /**
     * 사전질의 수정 요청 DTO
     *
     * <p>질의 등록자가 자신의 질의 내용을 수정합니다.</p>
     *
     * @param qtnCone 수정할 질의내용
     */
    public record QnaUpdateRequest(
        /** 수정할 질의내용 (최대 4000자) */
        @NotBlank String qtnCone
    ) {}

    /**
     * 사전질의응답 항목 응답
     *
     * @param qtnId   질의응답ID
     * @param qtnEno  질의자사번
     * @param qtnNm   질의자성명
     * @param qtnCone 질의내용
     * @param repEno  답변자사번
     * @param repNm   답변자성명
     * @param repCone 답변내용
     * @param repYn   답변여부
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
     *
     * @param eno   수신자 사번
     * @param usrNm 수신자 성명
     * @param bbrNm 수신자 부서명
     * @param temNm 수신자 팀명
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

    // =========================================================================
    // §26: 본회의 질의응답 (BMQNAM)
    // =========================================================================
    // 본회의 질의응답은 사전질의응답({@link QnaCreateRequest}/{@link QnaUpdateRequest}/
    // {@link QnaReplyRequest}/{@link QnaResponse})과 동일한 DTO 스키마를 재사용합니다.
    // 권한과 라이프사이클만 다르고 컬럼 구조는 동일하기 때문입니다.
    // =========================================================================

    // =========================================================================
    // PRD_c_20260620 #3: 타당성검토 생략 판정 요청 (BASKPM)
    // =========================================================================

    /**
     * 생략 판정 요청 생성 (정보보호기획 → IT기획).
     *
     * <p>타당성검토표 첨부는 Step1(Bpovwm)의 기존 첨부를 서버가 재사용하므로 클라이언트가 보내지 않습니다.
     * 사업계획서 첨부만 신규 업로드 후 그 파일관리번호를 전달합니다.</p>
     *
     * @param rsnTc    생략사유코드 (PRTY_IVG_OMT_RSN_TC, 01~04)
     * @param rsn      담당자의견내용 (요청 설명)
     * @param flMpnId  사업계획서 첨부 파일관리번호 (Cfilem.FL_MPN_ID, 신규 업로드)
     */
    public record SkipRequestCreate(
        @NotBlank String rsnTc,
        @NotBlank String rsn,
        @NotBlank String flMpnId
    ) {}

    /**
     * 생략 판정 + 결재 상신 (IT기획).
     *
     * @param omtYn        생략여부 (Y=생략 / N=개최)
     * @param cnfmCone     확인사유
     * @param approverEnos 결재선 사번 목록 (순서대로 IT기획팀장 → IT기획부장)
     */
    public record SkipDecisionRequest(
        @NotBlank String omtYn,
        @NotBlank String cnfmCone,
        @NotEmpty List<String> approverEnos
    ) {}

    /**
     * 생략 판정 요청 조회 응답 (정보보호기획 상태 확인 / IT기획 판정 화면).
     *
     * <p>{@code decided}=확인일시(cnfmDtm) 존재 여부. 회신 전이면 false(판정 대기).</p>
     *
     * @param asctId   협의회ID
     * @param rsnTc    생략사유코드
     * @param rsn      담당자의견내용 (요청 설명)
     * @param flMpnId  사업계획서 첨부 파일관리번호
     * @param rqsUsid  신청자 사번
     * @param rqsDtm   신청일시
     * @param decided  판정(확인) 완료 여부
     * @param omtYn    판정 결과 생략여부(Y/N, 미판정 시 null)
     * @param cnfmCone 담당자응답내용(미판정 시 null)
     * @param cnfmUsid 확인자 사번(미판정 시 null)
     * @param cnfmDtm  확인일시(미판정 시 null)
     * @param apfMngNo 전자결재 연동번호(미상신 시 null)
     */
    public record SkipRequestResponse(
        String asctId,
        String rsnTc,
        String rsn,
        String flMpnId,
        String rqsUsid,
        java.time.LocalDateTime rqsDtm,
        boolean decided,
        String omtYn,
        String cnfmCone,
        String cnfmUsid,
        java.time.LocalDateTime cnfmDtm,
        String apfMngNo
    ) {}
}
