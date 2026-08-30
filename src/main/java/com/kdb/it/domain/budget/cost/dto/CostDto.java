package com.kdb.it.domain.budget.cost.dto;

import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.system.validation.NotBlankUnlessAdmin;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전산관리비(IT 관리비) 관련 DTO 클래스 모음
 *
 * <p>전산관리비(TPRMPP_BCOSTM) 엔티티의 생성, 수정, 조회, 일괄 조회에 사용되는 Request/Response DTO를 정적 중첩 클래스(Static
 * Nested Class) 형태로 관리합니다.
 *
 * <p>포함된 DTO:
 *
 * <ul>
 *   <li>{@link CreateRequest}: 전산관리비 생성 요청
 *   <li>{@link UpdateRequest}: 전산관리비 수정 요청
 *   <li>{@link Response}: 전산관리비 조회 응답
 *   <li>{@link BulkGetRequest}: 일괄 조회 요청
 * </ul>
 */
public class CostDto extends CostTerminalDto {

    /**
     * 전산관리비 목록 경량 프로젝션 DTO(#7).
     *
     * <p>목록 화면에 필요한 식별/요약 컬럼만 담는다. Bcostm은 1000자+ 대용량 텍스트가 없어 제외 본문은 없으나, 목록에 불필요한 환산/외화/연기/담당자 등
     * 미표시 컬럼을 select에서 빼 적재 폭을 줄인다. 상세는 기존 엔티티 조회 경로를 유지한다(결정 B).
     *
     * @param costBgNo 전산업무비예산번호
     * @param bgSno 예산일련번호
     * @param lstYn 최종여부 ('Y'=현재 유효 레코드)
     * @param ioeC 비목코드
     * @param cttNm 계약명
     * @param cttOppNm 계약상대처명
     * @param costTotXpAmt 전산업무비예산금액
     * @param curC 통화코드
     * @param sectSysUtzYn 정보보호여부 (Y/N)
     * @param costSvnDpmC 담당부서코드 (주관부서코드)
     * @param svnTemC 담당팀코드 (주관팀코드)
     * @param bseYy 예산연도 (기준연도)
     * @param abusTc 사업구분코드
     * @param delYn 삭제여부 (Y/N)
     */
    @Schema(name = "CostListRow")
    public record CostListRow(
            String costBgNo,
            Integer bgSno,
            String lstYn,
            String ioeC,
            String cttNm,
            String cttOppNm,
            BigDecimal costTotXpAmt,
            String curC,
            String sectSysUtzYn,
            String costSvnDpmC,
            String svnTemC,
            String bseYy,
            String abusTc,
            String delYn) {}

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

        /** 담당부서 (부서코드) */
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

        /**
         * 요청 DTO를 {@link Bcostm} 엔티티로 변환합니다.
         *
         * @param nextSno 설정할 전산관리비일련번호 (서비스에서 계산된 다음 SNO)
         * @return 변환된 Bcostm 엔티티 (LST_YN='Y' 기본값 설정)
         */
        public Bcostm toEntity(Integer nextSno) {
            return Bcostm.builder()
                    .costBgNo(this.costBgNo) // 전산관리비관리번호
                    .bgSno(nextSno) // 전산관리비일련번호
                    .ioeC(this.ioeC) // 비목코드
                    .cttNm(this.cttNm) // 계약명
                    .cttOppNm(this.cttOppNm) // 계약상대처
                    .costTotXpAmt(this.costTotXpAmt) // 전산관리비예산
                    .dfrCleC(CodeDefaults.orNotApplicable(this.dfrCleC)) // 지급주기
                    .fstDfrDt(
                            DateFormatUtil.toYmd8(
                                    this.fstDfrDt)) // 최초지급일자 (YYYYMMDD 8자리 정규화 — VARCHAR2(8)
                    // truncate 방지)
                    .curC(this.curC) // 통화
                    .xcr(this.xcr) // 환율
                    .xcrBseDt(DateFormatUtil.toYmd8(this.xcrBseDt)) // 환율기준일자
                    .sectSysUtzYn(
                            this.sectSysUtzYn == null ? "N" : this.sectSysUtzYn) // 정보보호여부 (기본값 "N")
                    .indRsn(this.indRsn) // 증감사유
                    .cgprId(this.cgprId) // 담당자
                    .costSvnDpmC(this.costSvnDpmC) // 담당부서
                    .svnTemC(this.svnTemC) // 담당팀
                    .bgUntAbusC(this.bgUntAbusC) // 사업코드
                    .tmnYn(this.tmnYn) // 전산업무비유형
                    .abusTc(CodeDefaults.orNotApplicable(this.abusTc)) // 전산업무비구분
                    .bseYy(this.bseYy) // 예산연도
                    .cncdRfrNo(this.cncdRfrNo) // 관련전산업무비번호
                    .fcAmt(this.fcAmt) // 외화금액 (plan 04 서버 재계산 결과로 costTotXpAmt와 동기화)
                    .lstYn("Y") // 최종여부: 신규는 항상 최신
                    .build();
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
                "lstChgDtm"
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

        /** 최종변경일시 (BaseEntity LST_CHG_DTM — 목록 기본 정렬(최근 수정순)에 사용) */
        @Schema(description = "최종변경일시", example = "2026-07-01T10:30:00", nullable = true)
        private LocalDateTime lstChgDtm;

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
                    .lstChgDtm(entity.getLstChgDtm()) // 최종변경일시 (목록 기본 정렬용)
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
     * 전산관리비 목록 조회 검색 조건 DTO
     *
     * <p>{@code GET /api/cost} 엔드포인트의 Query Parameter로 전달됩니다. 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     *
     * <p>{@code apfSts} 값 규칙:
     *
     * <ul>
     *   <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회
     *   <li>{@code "none"}: 신청서가 없는 전산관리비 (apfSts IS NULL)
     *   <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 전산관리비
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CostSearchCondition", description = "전산관리비 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         *
         * <p>"none" → 신청서가 없는 전산관리비, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 전산관리비 null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 연관부서 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관부서 코드. 미입력 시 전체 조회")
        private String costSvnDpmC;

        /** 연관팀 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관팀 코드. 미입력 시 전체 조회")
        private String svnTemC;

        /** 정보보호여부 필터 ('Y'=정보보호, 'N'=일반). null이면 전체 조회 */
        @Schema(description = "정보보호여부 (Y/N). 미입력 시 전체 조회")
        private String sectSysUtzYn;

        /** 예산연도 필터 (예: "2026"). null이면 전체 조회 */
        @Schema(description = "예산연도 (예: 2026). 미입력 시 전체 조회")
        private String bseYy;

        /**
         * 소속 부서 한정 조회 여부
         *
         * <p>일반 사용자는 이 값과 무관하게 Service가 인증 사용자의 부점코드로 {@code costSvnDpmC}를 덮어씁니다. 시스템관리자는 true이면 본인
         * 부서, 그 외에는 전체를 조회합니다.
         */
        @Schema(description = "true면 로그인 사용자 소속 부서 항목만 조회 (관리자도 적용). 관리자가 false 또는 미입력 시 전체 조회")
        private Boolean myDeptOnly;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return isBlank(apfSts)
                    && isBlank(costSvnDpmC)
                    && isBlank(svnTemC)
                    && isBlank(sectSysUtzYn)
                    && isBlank(bseYy);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * 전산관리비 일괄 조회 요청 DTO
     *
     * <p>여러 전산관리비관리번호를 한 번에 조회할 때 사용합니다. 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CostDto.BulkGetRequest", description = "전산업무비 일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 전산관리비관리번호 목록 */
        @Schema(description = "전산업무비코드 목록", example = "[\"COST_2026_0001\", \"COST_2026_0002\"]")
        private List<String> costBgNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TPRMPP_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bseYy;
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
