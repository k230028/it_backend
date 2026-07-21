package com.kdb.it.common.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 기능 DTO 모음
 *
 * <p>Static Nested Class 방식으로 요청/응답 DTO를 하나의 파일에 관리합니다.
 */
public class AdminDto {

    // =========================================================================
    // 공통코드 (TPRMPP_CCODEM)
    // =========================================================================

    /**
     * 공통코드 생성/수정 요청 DTO
     *
     * @param cId 코드 ID
     * @param cdva 코드값
     * @param cNm 코드명
     * @param cdvaNm 코드값명
     * @param cdvaDes 코드값 설명
     * @param cdvaDtl 코드값 상세
     * @param cdvaDtlC 코드값 상세 코드
     * @param cTp 코드 타입
     * @param cTpDes 코드 타입 설명
     * @param hrkC 상위 코드
     * @param sttDt 시작 일자
     * @param endDt 종료 일자
     * @param cSqn 코드 순서
     */
    @Schema(name = "AdminDto.CodeRequest", description = "공통코드 생성/수정 요청")
    public record CodeRequest(
            @NotBlank @Schema(description = "코드ID (prefix, 예: PRJ_TP)") String cId,
            @NotBlank @Schema(description = "코드값 (예: 001, STA)") String cdva,
            @Schema(description = "코드명 (구 CDVA, 예: 신규개발)") String cNm,
            @Schema(description = "코드값명 (예: 개발비, 기계장치, 기타무형자산)") String cdvaNm,
            @Schema(description = "코드값설명 (예: 사업유형)") String cdvaDes,
            @Schema(description = "코드값상세 (구 C_NM, 예: USD)") String cdvaDtl,
            @Schema(description = "코드값상세코드 (예: 237-0700)") String cdvaDtlC,
            @Schema(description = "코드타입 (구 CTT_TP)") String cTp,
            @Schema(description = "코드타입설명") String cTpDes,
            @Schema(description = "상위코드 {C_ID}_{CDVA}") String hrkC,
            @Schema(description = "시작일자 (YYYYMMDD)") String sttDt,
            @Schema(description = "종료일자 (YYYYMMDD)") String endDt,
            @Schema(description = "코드순서") Integer cSqn) {}

    /**
     * 공통코드 일괄 업로드(Upsert) 요청 DTO
     *
     * @param codes 업로드할 코드 목록
     */
    @Schema(name = "AdminDto.BulkCodeRequest", description = "공통코드 일괄 업로드 요청")
    public record BulkCodeRequest(
            @Schema(description = "업로드할 코드 목록") java.util.List<CodeRequest> codes) {}

    /**
     * 공통코드 조회 응답 DTO 최초생성자·마지막수정자 사원번호를 이름으로 변환하여 제공합니다.
     *
     * @param cId 코드 ID
     * @param cdva 코드값
     * @param cNm 코드명
     * @param cdvaNm 코드값명
     * @param cdvaDes 코드값 설명
     * @param cdvaDtl 코드값 상세
     * @param cdvaDtlC 코드값 상세 코드
     * @param cTp 코드 타입
     * @param cTpDes 코드 타입 설명
     * @param hrkC 상위 코드
     * @param sttDt 시작 일자
     * @param endDt 종료 일자
     * @param cSqn 코드 순서
     * @param fstEnrDtm 최초 등록 일시
     * @param fstEnrUsid 최초 등록자 사번
     * @param fstEnrUsNm 최초 등록자 이름
     * @param lstChgDtm 최종 변경 일시
     * @param lstChgUsid 최종 변경자 사번
     * @param lstChgUsNm 최종 변경자 이름
     */
    @Schema(name = "AdminDto.CodeResponse", description = "공통코드 조회 응답")
    public record CodeResponse(
            String cId,
            String cdva,
            String cNm,
            String cdvaNm,
            String cdvaDes,
            String cdvaDtl,
            String cdvaDtlC,
            String cTp,
            String cTpDes,
            String hrkC,
            String sttDt,
            String endDt,
            Integer cSqn,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm) {}

    // =========================================================================
    // 자격등급 (TPRMPP_CAUTHI)
    // =========================================================================

    /**
     * 자격등급 생성/수정 요청 DTO
     *
     * @param athId 권한 ID
     * @param qlfGrNm 자격등급명
     * @param qlfGrMat 자격등급 내용
     * @param useYn 사용 여부
     */
    @Schema(name = "AdminDto.AuthGradeRequest", description = "자격등급 생성/수정 요청")
    public record AuthGradeRequest(
            @NotBlank String athId, String qlfGrNm, String qlfGrMat, String useYn) {}

    /**
     * 자격등급 조회 응답 DTO
     *
     * @param athId 권한 ID
     * @param qlfGrNm 자격등급명
     * @param qlfGrMat 자격등급 내용
     * @param useYn 사용 여부
     * @param fstEnrDtm 최초 등록 일시
     * @param fstEnrUsid 최초 등록자 사번
     * @param fstEnrUsNm 최초 등록자 이름
     * @param lstChgDtm 최종 변경 일시
     * @param lstChgUsid 최종 변경자 사번
     * @param lstChgUsNm 최종 변경자 이름
     */
    @Schema(name = "AdminDto.AuthGradeResponse", description = "자격등급 조회 응답")
    public record AuthGradeResponse(
            String athId,
            String qlfGrNm,
            String qlfGrMat,
            String useYn,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm) {}

    // =========================================================================
    // 사용자 (TPRMPP_CUSERI)
    // =========================================================================

    /**
     * 사용자 생성/수정 요청 DTO
     *
     * @param eno 사번
     * @param usrNm 사용자명
     * @param ptCNm 직위명
     * @param temC 팀 코드
     * @param bbrC 부점 코드
     * @param etrMilAddrNm 이메일 주소
     * @param inleNo 내선 번호
     * @param cpnTpn 휴대전화 번호
     * @param password 비밀번호
     */
    @Schema(name = "AdminDto.UserRequest", description = "사용자 생성/수정 요청")
    public record UserRequest(
            @NotBlank String eno,
            String usrNm,
            String ptCNm,
            String temC,
            String bbrC,
            String etrMilAddrNm,
            String inleNo,
            String cpnTpn,
            String password) {}

    /**
     * 사용자 조회 응답 DTO
     *
     * @param eno 사번
     * @param usrNm 사용자명
     * @param ptCNm 직위명
     * @param temC 팀 코드
     * @param temNm 팀명
     * @param bbrC 부점 코드
     * @param bbrNm 부점명
     * @param etrMilAddrNm 이메일 주소
     * @param inleNo 내선 번호
     * @param cpnTpn 휴대전화 번호
     * @param fstEnrDtm 최초 등록 일시
     * @param lstChgDtm 최종 변경 일시
     */
    @Schema(name = "AdminDto.UserResponse", description = "사용자 조회 응답")
    public record UserResponse(
            String eno,
            String usrNm,
            String ptCNm,
            String temC,
            String temNm,
            String bbrC,
            String bbrNm,
            String etrMilAddrNm,
            String inleNo,
            String cpnTpn,
            LocalDateTime fstEnrDtm,
            LocalDateTime lstChgDtm) {}

    // =========================================================================
    // 조직 (TPRMPP_CORGNI)
    // =========================================================================

    /**
     * 조직 생성/수정 요청 DTO
     *
     * @param prlmOgzCCone 조직 코드
     * @param bbrNm 부점명
     * @param bbrWrenNm 부점 약칭명
     * @param itmSqnSno 항목 순서
     * @param prlmHrkOgzCCone 상위 조직 코드
     */
    @Schema(name = "AdminDto.OrgRequest", description = "조직 생성/수정 요청")
    public record OrgRequest(
            @NotBlank String prlmOgzCCone,
            String bbrNm,
            String bbrWrenNm,
            Integer itmSqnSno,
            String prlmHrkOgzCCone) {}

    /**
     * 조직 조회 응답 DTO
     *
     * @param prlmOgzCCone 조직 코드
     * @param bbrNm 부점명
     * @param bbrWrenNm 부점 약칭명
     * @param itmSqnSno 항목 순서
     * @param prlmHrkOgzCCone 상위 조직 코드
     * @param fstEnrDtm 최초 등록 일시
     * @param fstEnrUsid 최초 등록자 사번
     * @param fstEnrUsNm 최초 등록자 이름
     * @param lstChgDtm 최종 변경 일시
     * @param lstChgUsid 최종 변경자 사번
     * @param lstChgUsNm 최종 변경자 이름
     */
    @Schema(name = "AdminDto.OrgResponse", description = "조직 조회 응답")
    public record OrgResponse(
            String prlmOgzCCone,
            String bbrNm,
            String bbrWrenNm,
            Integer itmSqnSno,
            String prlmHrkOgzCCone,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm) {}

    // =========================================================================
    // 역할 (TPRMPP_CROLEI)
    // =========================================================================

    /**
     * 역할 생성/수정 요청 DTO
     *
     * @param athId 권한 ID
     * @param eno 사번
     * @param useYn 사용 여부
     */
    @Schema(name = "AdminDto.RoleRequest", description = "역할 생성/수정 요청")
    public record RoleRequest(@NotBlank String athId, @NotBlank String eno, String useYn) {}

    /**
     * 역할 조회 응답 DTO
     *
     * @param athId 권한 ID
     * @param eno 사번
     * @param usrNm 사용자명
     * @param useYn 사용 여부
     * @param fstEnrDtm 최초 등록 일시
     * @param fstEnrUsid 최초 등록자 사번
     * @param fstEnrUsNm 최초 등록자 이름
     * @param lstChgDtm 최종 변경 일시
     * @param lstChgUsid 최종 변경자 사번
     * @param lstChgUsNm 최종 변경자 이름
     */
    @Schema(name = "AdminDto.RoleResponse", description = "역할 조회 응답")
    public record RoleResponse(
            String athId,
            String eno,
            String usrNm, // ENO → 이름 변환
            String useYn,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm) {}

    // =========================================================================
    // 로그인 이력 (TPRMPP_CLOGNH)
    // =========================================================================

    /**
     * 로그인 이력 조회 응답 DTO
     *
     * @param eno 사번
     * @param usrNm 사용자명
     * @param lgnDtm 로그인 일시
     * @param itPtlLgnTc 로그인 유형 코드
     * @param ipAddr IP 주소
     * @param lgnErrRsn 로그인 오류 사유
     * @param agtVrsCone 에이전트 버전 내용
     * @param fstEnrDtm 최초 등록 일시
     */
    @Schema(name = "AdminDto.LoginHistoryResponse", description = "로그인 이력 조회 응답")
    public record LoginHistoryResponse(
            String eno,
            String usrNm, // ENO → 이름 변환
            LocalDateTime lgnDtm,
            String itPtlLgnTc, // 공통코드 C_ID='IT_PTL_LGN_TC' (1=성공, 2=실패, 3=로그아웃)
            String ipAddr,
            String lgnErrRsn, // 로그인오류사유
            String agtVrsCone, // 에이전트버전내용 (User-Agent)
            LocalDateTime fstEnrDtm) {}

    // =========================================================================
    // JWT 토큰 (TPRMPP_CRTOKM)
    // =========================================================================

    /**
     * Refresh Token 관리 조회 응답 DTO
     *
     * @param eno 사번
     * @param usrNm 사용자명
     * @param endDtm 만료 일시
     * @param tokMasked 마스킹된 토큰
     * @param fstEnrDtm 최초 등록 일시
     */
    @Schema(name = "AdminDto.TokenResponse", description = "JWT 토큰 조회 응답")
    public record TokenResponse(
            String eno,
            String usrNm, // ENO → 이름 변환
            LocalDateTime endDtm,
            String tokMasked, // 앞 20자 + "..." 마스킹
            LocalDateTime fstEnrDtm) {}

    // =========================================================================
    // 첨부파일 (TPRMPP_CFILEM)
    // =========================================================================

    /**
     * 첨부파일 관리 조회 응답 DTO
     *
     * @param flMpnId 파일 매핑 ID
     * @param flNm 파일명
     * @param flTpCone 파일 유형 내용
     * @param pkColNm 원본 PK 컬럼값
     * @param fstEnrDtm 최초 등록 일시
     * @param fstEnrUsid 최초 등록자 사번
     * @param fstEnrUsNm 최초 등록자 이름
     */
    @Schema(name = "AdminDto.FileResponse", description = "첨부파일 조회 응답")
    public record FileResponse(
            String flMpnId,
            String flNm,
            String flTpCone,
            String pkColNm,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm) {}

    // =========================================================================
    // 대시보드 통계
    // =========================================================================

    /**
     * 관리자 대시보드 로그인 통계 응답 DTO
     *
     * @param date 집계 일자
     * @param count 로그인 건수
     */
    @Schema(name = "AdminDto.LoginStatResponse", description = "일별 로그인 통계")
    public record LoginStatResponse(LocalDate date, Long count) {}
}
