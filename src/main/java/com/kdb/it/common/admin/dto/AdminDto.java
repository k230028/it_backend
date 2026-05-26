package com.kdb.it.common.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 기능 DTO 모음
 *
 * <p>Static Nested Class 방식으로 요청/응답 DTO를 하나의 파일에 관리합니다.</p>
 */
public class AdminDto {

    // =========================================================================
    // 공통코드 (TPRMPP_CCODEM)
    // =========================================================================

    /**
     * 공통코드 생성/수정 요청 DTO
     */
    @Schema(name = "AdminDto.CodeRequest", description = "공통코드 생성/수정 요청")
    public record CodeRequest(
            @NotBlank @Schema(description = "코드ID (prefix, 예: PRJ_TP)") String cId,
            @NotBlank @Schema(description = "코드값 (예: 001, STA)") String cdva,
            @Schema(description = "코드명 (구 CDVA, 예: 신규개발)") String cNm,
            @Schema(description = "코드값설명 (예: 사업유형)") String cdvaDes,
            @Schema(description = "코드값상세 (구 C_NM, 예: USD)") String cdvaDtl,
            @Schema(description = "코드값상세코드 (예: 237-0700)") String cdvaDtlC,
            @Schema(description = "코드타입 (구 CTT_TP)") String cTp,
            @Schema(description = "코드타입설명") String cTpDes,
            @Schema(description = "상위코드 {C_ID}_{CDVA}") String hrkC,
            @Schema(description = "시작일자") LocalDate sttDt,
            @Schema(description = "종료일자") LocalDate endDt,
            @Schema(description = "코드순서") Integer cSqn
    ) {}

    /**
     * 공통코드 일괄 업로드(Upsert) 요청 DTO
     */
    @Schema(name = "AdminDto.BulkCodeRequest", description = "공통코드 일괄 업로드 요청")
    public record BulkCodeRequest(
            @Schema(description = "업로드할 코드 목록") java.util.List<CodeRequest> codes
    ) {}

    /**
     * 공통코드 조회 응답 DTO
     * 최초생성자·마지막수정자 사원번호를 이름으로 변환하여 제공합니다.
     */
    @Schema(name = "AdminDto.CodeResponse", description = "공통코드 조회 응답")
    public record CodeResponse(
            String cId,
            String cdva,
            String cNm,
            String cdvaDes,
            String cdvaDtl,
            String cdvaDtlC,
            String cTp,
            String cTpDes,
            String hrkC,
            LocalDate sttDt,
            LocalDate endDt,
            Integer cSqn,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm
    ) {}

    // =========================================================================
    // 자격등급 (TPRMPP_CAUTHI)
    // =========================================================================

    /** 자격등급 생성/수정 요청 DTO */
    @Schema(name = "AdminDto.AuthGradeRequest", description = "자격등급 생성/수정 요청")
    public record AuthGradeRequest(
            @NotBlank String athId,
            String qlfGrNm,
            String qlfGrMat,
            String useYn
    ) {}

    /** 자격등급 조회 응답 DTO */
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
            String lstChgUsNm
    ) {}

    // =========================================================================
    // 사용자 (TPRMPP_CUSERI)
    // =========================================================================

    /** 사용자 생성/수정 요청 DTO */
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
            String password
    ) {}

    /** 사용자 조회 응답 DTO */
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
            LocalDateTime lstChgDtm
    ) {}

    // =========================================================================
    // 조직 (TPRMPP_CORGNI)
    // =========================================================================

    /** 조직 생성/수정 요청 DTO */
    @Schema(name = "AdminDto.OrgRequest", description = "조직 생성/수정 요청")
    public record OrgRequest(
            @NotBlank String prlmOgzCCone,
            String bbrNm,
            String bbrWrenNm,
            Integer itmSqnSno,
            String prlmHrkOgzCCone
    ) {}

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
            String lstChgUsNm
    ) {}

    // =========================================================================
    // 역할 (TPRMPP_CROLEI)
    // =========================================================================

    /** 역할 생성/수정 요청 DTO */
    @Schema(name = "AdminDto.RoleRequest", description = "역할 생성/수정 요청")
    public record RoleRequest(
            @NotBlank String athId,
            @NotBlank String eno,
            String useYn
    ) {}

    /** 역할 조회 응답 DTO */
    @Schema(name = "AdminDto.RoleResponse", description = "역할 조회 응답")
    public record RoleResponse(
            String athId,
            String eno,
            String usrNm,    // ENO → 이름 변환
            String useYn,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm,
            LocalDateTime lstChgDtm,
            String lstChgUsid,
            String lstChgUsNm
    ) {}

    // =========================================================================
    // 로그인 이력 (TPRMPP_CLOGNH)
    // =========================================================================

    /** 로그인 이력 조회 응답 DTO */
    @Schema(name = "AdminDto.LoginHistoryResponse", description = "로그인 이력 조회 응답")
    public record LoginHistoryResponse(
            String eno,
            String usrNm,    // ENO → 이름 변환
            LocalDateTime lgnDtm,
            String lgnTc,        // 공통코드 C_ID='LGN_TC' (1=성공, 2=실패, 3=로그아웃)
            String ipAddr,
            String lgnErrRsn,    // 로그인오류사유
            String agtVrsCone,   // 에이전트버전내용 (User-Agent)
            LocalDateTime fstEnrDtm
    ) {}

    // =========================================================================
    // JWT 토큰 (TPRMPP_CRTOKM)
    // =========================================================================

    /** Refresh Token 관리 조회 응답 DTO */
    @Schema(name = "AdminDto.TokenResponse", description = "JWT 토큰 조회 응답")
    public record TokenResponse(
            String eno,
            String usrNm,    // ENO → 이름 변환
            LocalDateTime endDtm,
            String tokMasked,    // 앞 20자 + "..." 마스킹
            LocalDateTime fstEnrDtm
    ) {}

    // =========================================================================
    // 첨부파일 (TPRMPP_CFILEM)
    // =========================================================================

    /** 첨부파일 관리 조회 응답 DTO */
    @Schema(name = "AdminDto.FileResponse", description = "첨부파일 조회 응답")
    public record FileResponse(
            String flMpnId,
            String flNm,
            String flTpCone,
            String pkColNm,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String fstEnrUsNm
    ) {}

    // =========================================================================
    // 대시보드 통계
    // =========================================================================

    /** 관리자 대시보드 로그인 통계 응답 DTO */
    @Schema(name = "AdminDto.LoginStatResponse", description = "일별 로그인 통계")
    public record LoginStatResponse(
            LocalDate date,
            Long count
    ) {}
}
