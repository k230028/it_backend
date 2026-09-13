package com.kdb.it.domain.budget.cost.dto;

import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.system.validation.NotBlankUnlessAdmin;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전산관리비 생성·수정·조회에 사용하는 DTO 모음입니다.
 *
 * <p>목록 프로젝션·검색 조건·일괄 조회 요청은 {@link CostQueryDto}, 금융정보단말기 계약은 {@link CostTerminalDto}에 있으며 상속으로
 * {@code CostDto.SearchCondition}처럼 같은 이름으로 접근합니다.
 */
public class CostDto extends CostQueryDto {

    /**
     * 전산관리비 생성 요청 DTO
     *
     * <p>신규 전산관리비 항목을 등록할 때 사용합니다.
     *
     * <p>{@code costBgNo}가 null 또는 빈 문자열이면 서비스에서 Oracle 시퀀스로 자동 채번합니다.
     *
     * <p>{@link #toEntity(Integer)} 메서드로 엔티티로 변환할 수 있습니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CostDto.CreateRequest", description = "전산업무비 생성 요청")
    public static class CreateRequest {
        /**
         * 전산관리비관리번호 (BG_NO)
         *
         * <p>null 또는 빈 문자열이면 서비스에서 자동 채번됩니다. 형식: {@code COST_{yyyy}_{seq:04d}} (예:
         * "COST_2026_0001")
         */
        @Schema(description = "전산업무비코드 (IT관리비관리번호)", example = "COST_2026_0001")
        private String costBgNo;

        /** 비목코드 */
        @Schema(description = "비목코드", example = "IOE001")
        private String ioeC;

        /** 계약명 (계약서 명칭) */
        @Schema(description = "계약명", example = "2026년 서버 유지보수 계약")
        private String cttNm;

        /** 계약상대처 (계약 업체명) */
        @Schema(description = "계약상대처", example = "(주)IT솔루션")
        private String cttOppNm;

        /** 전산관리비예산 (금액, 소수점 포함 가능) */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal costTotXpAmt;

        /** 지급주기 (예: "매월", "분기", "연") */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCleC;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private String fstDfrDt;

        /** 통화 코드 (예: "KRW", "USD") */
        @NotBlank
        @Schema(description = "통화", example = "KRW")
        private String curC;

        /** 환율 (외화인 경우 원화 환산 기준) */
        @Schema(description = "환율", example = "1300")
        private BigDecimal xcr;

        /** 외화금액(외화 통화 원금 — 원화(KRW) 행은 null. 외화 행은 서버에서 costTotXpAmt = fcAmt × xcr 재계산) */
        @Schema(description = "외화금액 (외화 원금. 원화 행은 null)", example = "1000")
        private BigDecimal fcAmt;

        /** 환율기준일자 (환율 적용 기준 날짜) */
        @Schema(description = "환율기준일자", example = "2026-01-01")
        private String xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N", 기본값 "N") */
        @Schema(description = "정보보호여부", example = "N")
        private String sectSysUtzYn;

        /** 증감사유 (예산 증감 이유) */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 (담당자 사번) */
        @Schema(description = "담당자", example = "홍길동")
        private String cgprId;

        /** 담당자명 스냅샷 */
        @Schema(description = "담당자명 스냅샷", nullable = true)
        private String cgprNm;

        /**
         * 담당부서 (부서코드)
         *
         * <p>목록·건수 조회가 담당부서 범위로 거르므로, 부서가 빈 행은 등록에 성공해도 어느 부서 목록에도 나타나지 않는다. 등록 시점에 막는다 (BE-106).
         */
        @NotBlank(message = "담당부서는 필수입니다.")
        @Schema(description = "담당부서", example = "001")
        private String costSvnDpmC;

        /** 담당팀 (팀코드) */
        @Schema(description = "담당팀", example = "00101")
        private String svnTemC;

        /** 담당부서명 스냅샷 */
        @Schema(description = "담당부서명 스냅샷", nullable = true)
        private String costSvnDpmNm;

        /** 담당팀명 스냅샷 */
        @Schema(description = "담당팀명 스냅샷", nullable = true)
        private String svnTemNm;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String bgUntAbusC;

        /** 전산업무비유형 */
        @Schema(description = "전산업무비유형", example = "TP01")
        private String tmnYn;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String abusTc;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026")
        private String bseYy;

        /** 관련전산업무비번호 (계속항목인 경우 전년도 항목의 관리번호) */
        @Schema(description = "관련전산업무비번호")
        private String cncdRfrNo;

        /** 금융정보단말기 목록 (1:N) */
        @Schema(description = "금융정보단말기 목록 (1:N)")
        private List<TerminalDto> terminals;

        /** 작성완료 저장 여부. 화면 요청에는 필수이고 내부 반입 경로는 null을 허용합니다. */
        @NotNull(message = "저장 종류(complete)는 필수입니다.")
        @Schema(
                description = "작성완료 여부 (true=저장, false=임시저장)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean complete;

        /**
         * 요청 DTO를 {@link Bcostm} 엔티티로 변환합니다.
         *
         * @param nextSno 설정할 전산관리비일련번호 (서비스에서 계산된 다음 SNO)
         * @return 변환된 Bcostm 엔티티 (LST_YN='Y' 기본값 설정)
         */
        public Bcostm toEntity(Integer nextSno) {
            return CostDtoSupport.toEntity(this, nextSno);
        }
    }

    /**
     * 전산관리비 수정 요청 DTO
     *
     * <p>기존 전산관리비 항목의 내용을 수정할 때 사용합니다. {@code BG_NO}는 URL PathVariable로 받으므로 이 DTO에는 포함하지 않습니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CostDto.UpdateRequest", description = "전산업무비 수정 요청")
    public static class UpdateRequest {
        /** 비목코드 */
        @Schema(description = "비목코드", example = "IOE001")
        private String ioeC;

        /** 계약명 */
        @Schema(description = "계약명", example = "2026년 서버 유지보수 계약")
        private String cttNm;

        /** 계약상대처 */
        @Schema(description = "계약상대처", example = "(주)IT솔루션")
        private String cttOppNm;

        /** 전산관리비예산 */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal costTotXpAmt;

        /** 지급주기 */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCleC;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private String fstDfrDt;

        /** 통화 코드 */
        @NotBlankUnlessAdmin
        @Schema(description = "통화", example = "KRW")
        private String curC;

        /** 환율 */
        @Schema(description = "환율", example = "1300")
        private BigDecimal xcr;

        /** 외화금액(외화 통화 원금 — 원화(KRW) 행은 null. 외화 행은 서버에서 costTotXpAmt = fcAmt × xcr 재계산) */
        @Schema(description = "외화금액 (외화 원금. 원화 행은 null)", example = "1000")
        private BigDecimal fcAmt;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자", example = "2026-01-01")
        private String xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N") */
        @Schema(description = "정보보호여부", example = "N")
        private String sectSysUtzYn;

        /** 증감사유 */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 */
        @Schema(description = "담당자", example = "홍길동")
        private String cgprId;

        /** 담당자명 스냅샷 */
        @Schema(description = "담당자명 스냅샷", nullable = true)
        private String cgprNm;

        /** 담당부서 */
        @Schema(description = "담당부서", example = "001")
        private String costSvnDpmC;

        /** 담당팀 */
        @Schema(description = "담당팀", example = "00101")
        private String svnTemC;

        /** 담당부서명 스냅샷 */
        @Schema(description = "담당부서명 스냅샷", nullable = true)
        private String costSvnDpmNm;

        /** 담당팀명 스냅샷 */
        @Schema(description = "담당팀명 스냅샷", nullable = true)
        private String svnTemNm;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String bgUntAbusC;

        /** 전산업무비유형 */
        @Schema(description = "전산업무비유형", example = "TP01")
        private String tmnYn;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String abusTc;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026")
        private String bseYy;

        /** 관련전산업무비번호 (계속항목인 경우 전년도 항목의 관리번호) */
        @Schema(description = "관련전산업무비번호")
        private String cncdRfrNo;

        /** 금융정보단말기 목록 (1:N) */
        @Schema(description = "금융정보단말기 목록 (1:N)")
        private List<TerminalDto> terminals;

        /** 작성완료 저장 여부. 화면 요청에는 필수이고 내부 반입 경로는 null을 허용합니다. */
        @NotNull(message = "저장 종류(complete)는 필수입니다.")
        @Schema(
                description = "작성완료 여부 (true=저장, false=임시저장)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean complete;

        /** 조회 응답에서 받은 개정본 동시성 스탬프. 사용자 저장 경로에서 필수다. */
        @Schema(
                description = "조회 시 받은 개정본 동시성 스탬프",
                example = "9f2c1d0ab34e5f6789012345678901234567890123456789012345678901abcd",
                nullable = true)
        private String concurrencyStamp;
    }

    /**
     * 전산관리비 조회 응답 DTO
     *
     * <p>{@link Bcostm} 엔티티의 모든 필드를 포함합니다. {@link #fromEntity(Bcostm)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(
            name = "CostDto.Response",
            description = "전산업무비 응답",
            requiredProperties = {
                "costBgNo",
                "bgSno",
                "lstYn",
                "ioeC",
                "cttNm",
                "cttOppNm",
                "costTotXpAmt",
                "dfrCleC",
                "fstDfrDt",
                "curC",
                "xcr",
                "fcAmt",
                "xcrBseDt",
                "sectSysUtzYn",
                "indRsn",
                "cgprId",
                "costSvnDpmC",
                "svnTemC",
                "bgUntAbusC",
                "bgUntAbusCNm",
                "ioeCNm",
                "dfrCleCNm",
                "tmnYnNm",
                "abusTcNm",
                "tmnYn",
                "abusTc",
                "bseYy",
                "cncdRfrNo",
                "terminals",
                "costSvnDpmNm",
                "svnTemNm",
                "cgprNm",
                "cgprPtCNm",
                "assetBg",
                "dvcBg",
                "hwBg",
                "swBg",
                "costBg",
                "dupBgAmt",
                "assetDupBg",
                "costDupBg",
                "prevBgAmt",
                "prevDupBg",
                "delYn",
                "apfMngNo",
                "apfSts",
                "apfStsC",
                "fstEnrDtm",
                "fstEnrUsid",
                "fstEnrUsNm",
                "lstChgDtm",
                "lstChgUsid",
                "lstChgUsNm"
            })
    public static class Response {
        /** 전산관리비관리번호 (BG_NO) */
        @Schema(description = "전산업무비코드 (IT관리비관리번호)", example = "COST_2026_0001", nullable = true)
        private String costBgNo;

        /** 전산관리비일련번호 (BG_SNO, 이력 순번) */
        @Schema(description = "전산업무비일련번호 (IT관리비일련번호)", example = "1", nullable = true)
        private Integer bgSno;

        /** 최종여부 ("Y": 최신 이력, "N": 과거 이력) */
        @Schema(
                description = "최종여부",
                example = "Y",
                nullable = true,
                allowableValues = {"Y", "N"})
        private String lstYn;

        /** 비목코드 */
        @Schema(description = "비목코드", example = "IOE001")
        private String ioeC;

        /** 계약명 */
        @Schema(description = "계약명", example = "2026년 서버 유지보수 계약")
        private String cttNm;

        /** 계약상대처 */
        @Schema(description = "계약상대처", example = "(주)IT솔루션")
        private String cttOppNm;

        /** 전산관리비예산 */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal costTotXpAmt;

        /** 지급주기 */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCleC;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private String fstDfrDt;

        /** 통화 코드 */
        @Schema(description = "통화", example = "KRW")
        private String curC;

        /** 환율 */
        @Schema(description = "환율", example = "1300", nullable = true)
        private BigDecimal xcr;

        /** 외화금액(외화 원금. 원화 행은 null. 프론트는 curC === 'KRW' ? costTotXpAmt : fcAmt 분기로 표시) */
        @Schema(description = "외화금액 (외화 원금. 원화 행은 null)", example = "1000", nullable = true)
        private BigDecimal fcAmt;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자", example = "2026-01-01", nullable = true)
        private String xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N") */
        @Schema(
                description = "정보보호여부",
                example = "N",
                allowableValues = {"Y", "N"})
        private String sectSysUtzYn;

        /** 증감사유 */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 */
        @Schema(description = "담당자", example = "홍길동")
        private String cgprId;

        /** 담당부서 */
        @Schema(description = "담당부서", example = "001")
        private String costSvnDpmC;

        /** 담당팀 */
        @Schema(description = "담당팀", example = "00101")
        private String svnTemC;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String bgUntAbusC;

        /** 사업코드명: bgUntAbusC(사업코드) 기준 TPRMPP_CCODEM에서 C_NM 조회 */
        @Schema(description = "사업코드명", nullable = true)
        private String bgUntAbusCNm;

        /** 비목코드명: ioeC(비목코드) 기준 TPRMPP_CCODEM CDVA_DTL 마지막 항목 */
        @Schema(description = "비목코드명", nullable = true)
        private String ioeCNm;

        /** 지급주기명: dfrCleC(지급주기) 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "지급주기명", nullable = true)
        private String dfrCleCNm;

        /** 전산업무비유형명: tmnYn 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "전산업무비유형명", nullable = true)
        private String tmnYnNm;

        /** 전산업무비구분명: abusTc 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "전산업무비구분명", nullable = true)
        private String abusTcNm;

        /** 전산업무비유형 */
        @Schema(
                description = "전산업무비유형",
                example = "Y",
                allowableValues = {"Y", "N"})
        private String tmnYn;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String abusTc;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026", nullable = true)
        private String bseYy;

        /** 관련전산업무비번호 (계속항목인 경우 전년도 항목의 관리번호) */
        @Schema(description = "관련전산업무비번호", nullable = true)
        private String cncdRfrNo;

        /** 금융정보단말기 목록 (1:N) */
        @Schema(description = "금융정보단말기 목록 (1:N)", nullable = true)
        private List<TerminalDto> terminals;

        /** 담당부서명: costSvnDpmC(부서코드) 기준 TPRMPP_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "담당부서명", nullable = true)
        private String costSvnDpmNm;

        /** 담당팀명: svnTemC(팀코드) 기준 TPRMPP_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "담당팀명", nullable = true)
        private String svnTemNm;

        /** 담당자명: cgprId(사번) 기준 TPRMPP_CUSERI에서 USR_NM 조회 */
        @Schema(description = "담당자명", nullable = true)
        private String cgprNm;

        /** 담당자 직위명: cgprId(사번) 기준 TPRMPP_CUSERI에서 PT_C_NM 조회 */
        @Schema(description = "담당자 직위명", nullable = true)
        private String cgprPtCNm;

        /** 자본예산: ioeC(비목코드)가 공통코드 코드값구분 IOE_CPIT에 해당하면 costTotXpAmt, 아니면 0 */
        @Schema(description = "자본예산", nullable = true)
        private java.math.BigDecimal assetBg;

        /** 개발비: 자본예산 중 코드설명(cdDes)이 '개발비'인 경우 costTotXpAmt, 아니면 0 */
        @Schema(description = "개발비", nullable = true)
        private java.math.BigDecimal dvcBg;

        /** 기계장치: 자본예산 중 코드설명(cdDes)이 '기계장치'인 경우 costTotXpAmt, 아니면 0 */
        @Schema(description = "기계장치", nullable = true)
        private java.math.BigDecimal hwBg;

        /** 기타무형자산: 자본예산 중 코드설명(cdDes)이 '기타무형자산'인 경우 costTotXpAmt, 아니면 0 */
        @Schema(description = "기타무형자산", nullable = true)
        private java.math.BigDecimal swBg;

        /**
         * 일반관리비: ioeC(비목코드)가 공통코드 코드값구분 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE에 해당하면 costTotXpAmt,
         * 아니면 0
         */
        @Schema(description = "일반관리비", nullable = true)
        private java.math.BigDecimal costBg;

        /** TPRMPP_BBUGTM 기준 편성예산 합계 (요청금액 × 편성률/100, 서비스에서 일괄 조회 시 설정) */
        @Schema(description = "편성예산 (BBUGTM 기준, 편성률 반영)", nullable = true)
        private java.math.BigDecimal dupBgAmt;

        /** BBUGTM 기준 자본예산 편성예산 (ioeC IOE_CPIT 계열인 경우 dupBgAmt, 아니면 0) */
        @Schema(description = "자본예산 편성예산 (BBUGTM 기준)", nullable = true)
        private java.math.BigDecimal assetDupBg;

        /** BBUGTM 기준 일반관리비 편성예산 (ioeC IOE_IDR/SEVS/XPN/LEAFE 계열인 경우 dupBgAmt, 아니면 0) */
        @Schema(description = "일반관리비 편성예산 (BBUGTM 기준)", nullable = true)
        private java.math.BigDecimal costDupBg;

        /**
         * 전년도 예산: abusTc=20(계속)이면 bseYy-1 연도 예산 합계, 신규(abusTc=10)이면 0. 외화(curC≠'KRW') 행은
         * FC_AMT(외화금액), 원화 행은 AMT(전산업무비예산금액) 기준.
         */
        @Schema(description = "전년도 예산 (계속 항목은 전년도 예산 합계 — 외화 행은 외화금액 기준, 신규는 0)", nullable = true)
        private BigDecimal prevBgAmt;

        /** 전년도 BBUGTM 편성예산: 계속 항목의 cncdRfrNo 기준 bseYy-1 DUP_BG 합계, 신규는 0 */
        @Schema(
                description = "전년도 편성예산 (계속 항목은 cncdRfrNo 기준 전년도 BBUGTM DUP_BG 합계, 신규는 0)",
                nullable = true)
        private BigDecimal prevDupBg;

        /** 삭제여부 (Soft Delete 상태, "Y": 삭제됨, "N": 정상) */
        @Schema(
                description = "삭제여부",
                example = "N",
                nullable = true,
                allowableValues = {"Y", "N"})
        private String delYn;

        /** 연결된 신청서관리번호 (서비스에서 설정) */
        @Schema(description = "신청서관리번호", example = "APPL_2026_0001", nullable = true)
        private String apfMngNo;

        /** 연결된 신청서 결재상태 (서비스에서 설정) */
        @Schema(description = "신청서상태", example = "결재중", nullable = true)
        private String apfSts;

        /**
         * 연결된 신청서 결재상태 코드 (서비스에서 설정, 예: "02"=결재완료)
         *
         * <p>{@link #apfSts}는 표시용 라벨이므로 화면 업무 분기는 이 코드값으로 한다.
         */
        @Schema(description = "신청서상태코드", example = "02", nullable = true)
        private String apfStsC;

        /** 신청서 상세 정보 (신청서명, 신청자, 결재자 목록 등) */
        @Schema(description = "신청서 상세 정보")
        private ApplicationInfoDto applicationInfo;

        /** 최초 등록 일시 (BaseEntity FST_ENR_DTM) */
        @Schema(description = "최초생성시간", example = "2026-01-05T09:12:00", nullable = true)
        private LocalDateTime fstEnrDtm;

        /** 최초 등록자 사번 (BaseEntity FST_ENR_USID) */
        @Schema(description = "최초생성자 사번", example = "K140024", nullable = true)
        private String fstEnrUsid;

        /** 최초 등록자 이름 — 사번 조회로 채우며 미해석(퇴직·DB 기본값)이면 null */
        @Schema(description = "최초생성자명", example = "홍길동", nullable = true)
        private String fstEnrUsNm;

        /** 최종변경일시 (BaseEntity LST_CHG_DTM — 목록 기본 정렬(최근 수정순)에 사용) */
        @Schema(description = "최종변경일시", example = "2026-07-01T10:30:00", nullable = true)
        private LocalDateTime lstChgDtm;

        /** 마지막 수정자 사번 (BaseEntity LST_CHG_USID) */
        @Schema(description = "마지막수정자 사번", example = "K140024", nullable = true)
        private String lstChgUsid;

        /** 마지막 수정자 이름 — 사번 조회로 채우며 미해석이면 null */
        @Schema(description = "마지막수정자명", example = "홍길동", nullable = true)
        private String lstChgUsNm;

        /** 개정본 동시성 스탬프. 상세 조회에서만 채우며, 저장 요청에 그대로 되돌려 보낸다. */
        @Schema(
                description = "개정본 동시성 스탬프 (저장 요청에 그대로 되돌려 보낸다)",
                example = "9f2c1d0ab34e5f6789012345678901234567890123456789012345678901abcd",
                nullable = true)
        private String concurrencyStamp;

        /**
         * {@link Bcostm} 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param entity 변환할 Bcostm 엔티티
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(Bcostm entity) {
            return Response.builder()
                    .costBgNo(entity.getCostBgNo()) // 전산관리비관리번호
                    .bgSno(entity.getBgSno()) // 전산관리비일련번호
                    .lstYn(entity.getLstYn()) // 최종여부
                    .ioeC(entity.getIoeC()) // 비목코드
                    .cttNm(entity.getCttNm()) // 계약명
                    .cttOppNm(entity.getCttOppNm()) // 계약상대처
                    .costTotXpAmt(entity.getCostTotXpAmt()) // 전산관리비예산
                    .dfrCleC(entity.getDfrCleC()) // 지급주기
                    .fstDfrDt(entity.getFstDfrDt()) // 최초지급일자
                    .curC(entity.getCurC()) // 통화
                    .xcr(entity.getXcr()) // 환율
                    .xcrBseDt(entity.getXcrBseDt()) // 환율기준일자
                    .sectSysUtzYn(entity.getSectSysUtzYn()) // 정보보호여부
                    .indRsn(entity.getIndRsn()) // 증감사유
                    .cgprId(entity.getCgprId()) // 담당자
                    .cgprNm(entity.getCgprNm()) // 담당자명 스냅샷 (행번 미해석 시 표시 폴백)
                    .costSvnDpmC(entity.getCostSvnDpmC()) // 담당부서
                    .svnTemC(entity.getSvnTemC()) // 담당팀
                    .bgUntAbusC(entity.getBgUntAbusC()) // 사업코드
                    .tmnYn(normalizeTmnYn(entity.getTmnYn())) // 단말여부 (구 "1"/"0" 데이터를 "Y"/"N"으로 정규화)
                    .abusTc(entity.getAbusTc()) // 전산업무비구분
                    .bseYy(entity.getBseYy()) // 예산연도
                    .cncdRfrNo(entity.getCncdRfrNo()) // 관련전산업무비번호
                    .fcAmt(entity.getFcAmt()) // 외화금액
                    .delYn(entity.getDelYn()) // 삭제여부
                    .fstEnrDtm(entity.getFstEnrDtm()) // 최초생성시간
                    .fstEnrUsid(entity.getFstEnrUsid()) // 최초생성자 사번
                    .lstChgDtm(entity.getLstChgDtm()) // 최종변경일시 (목록 기본 정렬용)
                    .lstChgUsid(entity.getLstChgUsid()) // 마지막수정자 사번
                    .build();
        }

        /**
         * 단말여부 코드 정규화.
         *
         * <p>레거시 데이터는 구 IT_MNGC_TP 값("1"=단말, "0"=비단말)으로 저장되어 있고, 신규 데이터는 "Y"/"N"으로 저장된다. 프론트 표시·체크
         * 로직은 "Y"/"N" 단일 기준이므로 응답 경계에서 "1"→"Y", "0"→"N"으로 변환하여 일관성을 보장한다.
         *
         * @param value DB 원본 단말여부 ("Y"/"N"/"1"/"0"/null)
         * @return 정규화된 "Y"/"N" (그 외 값은 원본 그대로)
         */
        private static String normalizeTmnYn(String value) {
            if ("1".equals(value)) {
                return "Y";
            }
            if ("0".equals(value)) {
                return "N";
            }
            return value;
        }
    }

    /**
     * 전산관리비 일괄 조회 결과 DTO (부분 성공)
     *
     * <p>조회에 성공한 항목({@code items})과 미존재로 조회에 실패한 전산관리비관리번호 목록({@code failedIds})을 함께 반환합니다. 누락 건을
     * 조용히 버리지 않고 호출자에게 노출하기 위함입니다.
     *
     * @param items 조회 성공 항목 목록
     * @param failedIds 조회 실패(미존재) 전산관리비관리번호 목록
     */
    @Schema(
            name = "CostBulkResponse",
            description = "전산관리비 일괄 조회 결과 (부분 성공)",
            requiredProperties = {"items", "failedIds"})
    public record BulkResponse(
            @Schema(description = "조회 성공 항목") List<Response> items,
            @Schema(description = "조회 실패(미존재) 전산관리비관리번호 목록") List<String> failedIds) {}
}
