package com.kdb.it.domain.budget.cost.dto;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전산관리비(IT 관리비) 관련 DTO 클래스 모음
 *
 * <p>
 * 전산관리비(TAAABB_BCOSTM) 엔티티의 생성, 수정, 조회, 일괄 조회에 사용되는
 * Request/Response DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <p>
 * 포함된 DTO:
 * </p>
 * <ul>
 * <li>{@link CreateRequest}: 전산관리비 생성 요청</li>
 * <li>{@link UpdateRequest}: 전산관리비 수정 요청</li>
 * <li>{@link Response}: 전산관리비 조회 응답</li>
 * <li>{@link BulkGetRequest}: 일괄 조회 요청</li>
 * </ul>
 */
public class CostDto {

    /**
     * 전산관리비 생성 요청 DTO
     *
     * <p>
     * 신규 전산관리비 항목을 등록할 때 사용합니다.
     * </p>
     *
     * <p>
     * {@code itMngcNo}가 null 또는 빈 문자열이면 서비스에서 Oracle 시퀀스로 자동 채번합니다.
     * </p>
     *
     * <p>
     * {@link #toEntity(Integer)} 메서드로 엔티티로 변환할 수 있습니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CostDto.CreateRequest", description = "전산업무비 생성 요청")
    public static class CreateRequest {
        /**
         * 전산관리비관리번호 (IT_MNGC_NO)
         * <p>
         * null 또는 빈 문자열이면 서비스에서 자동 채번됩니다.
         * 형식: {@code COST_{yyyy}_{seq:04d}} (예: "COST_2026_0001")
         * </p>
         */
        @Schema(description = "전산업무비코드 (IT관리비관리번호)", example = "COST_2026_0001")
        private String itMngcNo;

        /** 비목코드 */
        @Schema(description = "비목코드", example = "IOE001")
        private String ioeC;

        /** 계약명 (계약서 명칭) */
        @Schema(description = "계약명", example = "2026년 서버 유지보수 계약")
        private String cttNm;

        /** 계약상대처 (계약 업체명) */
        @Schema(description = "계약상대처", example = "(주)IT솔루션")
        private String cttOpp;

        /** 전산관리비예산 (금액, 소수점 포함 가능) */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal itMngcBg;

        /** 지급주기 (예: "매월", "분기", "연") */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCle;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private LocalDate fstDfrDt;

        /** 통화 코드 (예: "KRW", "USD") */
        @Schema(description = "통화", example = "KRW")
        private String cur;

        /** 환율 (외화인 경우 원화 환산 기준) */
        @Schema(description = "환율", example = "1300")
        private BigDecimal xcr;

        /** 환율기준일자 (환율 적용 기준 날짜) */
        @Schema(description = "환율기준일자", example = "2026-01-01")
        private LocalDate xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N", 기본값 "N") */
        @Schema(description = "정보보호여부", example = "N")
        private String infPrtYn;

        /** 증감사유 (예산 증감 이유) */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 (담당자명) */
        @Schema(description = "담당자", example = "홍길동")
        private String cgpr;

        /** 담당부서 (부서코드) */
        @Schema(description = "담당부서", example = "001")
        private String biceDpm;

        /** 담당팀 (팀코드) */
        @Schema(description = "담당팀", example = "00101")
        private String biceTem;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String abusC;

        /** 전산업무비유형 */
        @Schema(description = "전산업무비유형", example = "TP01")
        private String itMngcTp;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String pulDtt;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026")
        private String bgYy;

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
                    .itMngcNo(this.itMngcNo) // 전산관리비관리번호
                    .itMngcSno(nextSno) // 전산관리비일련번호
                    .ioeC(this.ioeC) // 비목코드
                    .cttNm(this.cttNm) // 계약명
                    .cttOpp(this.cttOpp) // 계약상대처
                    .itMngcBg(this.itMngcBg) // 전산관리비예산
                    .dfrCle(this.dfrCle) // 지급주기
                    .fstDfrDt(this.fstDfrDt) // 최초지급일자
                    .cur(this.cur) // 통화
                    .xcr(this.xcr) // 환율
                    .xcrBseDt(this.xcrBseDt) // 환율기준일자
                    .infPrtYn(this.infPrtYn == null ? "N" : this.infPrtYn) // 정보보호여부 (기본값 "N")
                    .indRsn(this.indRsn) // 증감사유
                    .cgpr(this.cgpr) // 담당자
                    .biceDpm(this.biceDpm) // 담당부서
                    .biceTem(this.biceTem) // 담당팀
                    .abusC(this.abusC) // 사업코드
                    .itMngcTp(this.itMngcTp) // 전산업무비유형
                    .pulDtt(this.pulDtt) // 전산업무비구분
                    .bgYy(this.bgYy) // 예산연도
                    .lstYn("Y") // 최종여부: 신규는 항상 최신
                    .build();
        }
    }

    /**
     * 전산관리비 수정 요청 DTO
     *
     * <p>
     * 기존 전산관리비 항목의 내용을 수정할 때 사용합니다.
     * {@code IT_MNGC_NO}는 URL PathVariable로 받으므로 이 DTO에는 포함하지 않습니다.
     * </p>
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
        private String cttOpp;

        /** 전산관리비예산 */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal itMngcBg;

        /** 지급주기 */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCle;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private LocalDate fstDfrDt;

        /** 통화 코드 */
        @Schema(description = "통화", example = "KRW")
        private String cur;

        /** 환율 */
        @Schema(description = "환율", example = "1300")
        private BigDecimal xcr;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자", example = "2026-01-01")
        private LocalDate xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N") */
        @Schema(description = "정보보호여부", example = "N")
        private String infPrtYn;

        /** 증감사유 */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 */
        @Schema(description = "담당자", example = "홍길동")
        private String cgpr;

        /** 담당부서 */
        @Schema(description = "담당부서", example = "001")
        private String biceDpm;

        /** 담당팀 */
        @Schema(description = "담당팀", example = "00101")
        private String biceTem;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String abusC;

        /** 전산업무비유형 */
        @Schema(description = "전산업무비유형", example = "TP01")
        private String itMngcTp;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String pulDtt;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026")
        private String bgYy;

        /** 금융정보단말기 목록 (1:N) */
        @Schema(description = "금융정보단말기 목록 (1:N)")
        private List<TerminalDto> terminals;
    }

    /**
     * 전산관리비 조회 응답 DTO
     *
     * <p>
     * {@link Bcostm} 엔티티의 모든 필드를 포함합니다.
     * {@link #fromEntity(Bcostm)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CostDto.Response", description = "전산업무비 응답")
    public static class Response {
        /** 전산관리비관리번호 (IT_MNGC_NO) */
        @Schema(description = "전산업무비코드 (IT관리비관리번호)", example = "COST_2026_0001")
        private String itMngcNo;

        /** 전산관리비일련번호 (IT_MNGC_SNO, 이력 순번) */
        @Schema(description = "전산업무비일련번호 (IT관리비일련번호)", example = "1")
        private Integer itMngcSno;

        /** 최종여부 ("Y": 최신 이력, "N": 과거 이력) */
        @Schema(description = "최종여부", example = "Y")
        private String lstYn;

        /** 비목코드 */
        @Schema(description = "비목코드", example = "IOE001")
        private String ioeC;

        /** 계약명 */
        @Schema(description = "계약명", example = "2026년 서버 유지보수 계약")
        private String cttNm;

        /** 계약상대처 */
        @Schema(description = "계약상대처", example = "(주)IT솔루션")
        private String cttOpp;

        /** 전산관리비예산 */
        @Schema(description = "전산업무비예산", example = "10000000")
        private BigDecimal itMngcBg;

        /** 지급주기 */
        @Schema(description = "지급주기", example = "매월")
        private String dfrCle;

        /** 지급예정월 / 최초지급일자 */
        @Schema(description = "지급예정월(최초지급일자)", example = "2026-01-25")
        private LocalDate fstDfrDt;

        /** 통화 코드 */
        @Schema(description = "통화", example = "KRW")
        private String cur;

        /** 환율 */
        @Schema(description = "환율", example = "1300")
        private BigDecimal xcr;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자", example = "2026-01-01")
        private LocalDate xcrBseDt;

        /** 정보보호여부 ("Y" 또는 "N") */
        @Schema(description = "정보보호여부", example = "N")
        private String infPrtYn;

        /** 증감사유 */
        @Schema(description = "증감사유", example = "물가 상승 반영")
        private String indRsn;

        /** 담당자 */
        @Schema(description = "담당자", example = "홍길동")
        private String cgpr;

        /** 담당부서 */
        @Schema(description = "담당부서", example = "001")
        private String biceDpm;

        /** 담당팀 */
        @Schema(description = "담당팀", example = "00101")
        private String biceTem;

        /** 사업코드 */
        @Schema(description = "사업코드", example = "ABUS01")
        private String abusC;

        /** 전산업무비유형 */
        @Schema(description = "전산업무비유형", example = "TP01")
        private String itMngcTp;

        @Schema(description = "전산업무비구분", example = "DTT01")
        private String pulDtt;

        /** 예산연도 */
        @Schema(description = "예산연도", example = "2026")
        private String bgYy;

        /** 금융정보단말기 목록 (1:N) */
        @Schema(description = "금융정보단말기 목록 (1:N)")
        private List<TerminalDto> terminals;

        /** 담당부서명: biceDpm(부서코드) 기준 TAAABB_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "담당부서명")
        private String biceDpmNm;

        /** 담당팀명: biceTem(팀코드) 기준 TAAABB_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "담당팀명")
        private String biceTemNm;

        /** 담당자명: cgpr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 */
        @Schema(description = "담당자명")
        private String cgprNm;

        /** 자본예산: ioeC(비목코드)가 공통코드 코드값구분 IOE_CPIT에 해당하면 itMngcBg, 아니면 0 */
        @Schema(description = "자본예산")
        private java.math.BigDecimal assetBg;

        /** 개발비: 자본예산 중 코드설명(cdDes)이 '개발비'인 경우 itMngcBg, 아니면 0 */
        @Schema(description = "개발비")
        private java.math.BigDecimal devBg;

        /** 기계장치: 자본예산 중 코드설명(cdDes)이 '기계장치'인 경우 itMngcBg, 아니면 0 */
        @Schema(description = "기계장치")
        private java.math.BigDecimal machBg;

        /** 기타무형자산: 자본예산 중 코드설명(cdDes)이 '기타무형자산'인 경우 itMngcBg, 아니면 0 */
        @Schema(description = "기타무형자산")
        private java.math.BigDecimal intanBg;

        /** 일반관리비: ioeC(비목코드)가 공통코드 코드값구분 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE에 해당하면 itMngcBg, 아니면 0 */
        @Schema(description = "일반관리비")
        private java.math.BigDecimal costBg;

        /** TAAABB_BBUGTM 기준 편성예산 합계 (요청금액 × 편성률/100, 서비스에서 일괄 조회 시 설정) */
        @Schema(description = "편성예산 (BBUGTM 기준, 편성률 반영)")
        private java.math.BigDecimal dupBg;

        /** BBUGTM 기준 자본예산 편성예산 (ioeC IOE_CPIT 계열인 경우 dupBg, 아니면 0) */
        @Schema(description = "자본예산 편성예산 (BBUGTM 기준)")
        private java.math.BigDecimal assetDupBg;

        /** BBUGTM 기준 일반관리비 편성예산 (ioeC IOE_IDR/SEVS/XPN/LEAFE 계열인 경우 dupBg, 아니면 0) */
        @Schema(description = "일반관리비 편성예산 (BBUGTM 기준)")
        private java.math.BigDecimal costDupBg;

        /** 삭제여부 (Soft Delete 상태, "Y": 삭제됨, "N": 정상) */
        @Schema(description = "삭제여부", example = "N")
        private String delYn;

        /** 연결된 신청서관리번호 (서비스에서 설정) */
        @Schema(description = "신청서관리번호", example = "APPL_2026_0001")
        private String apfMngNo;

        /** 연결된 신청서 결재상태 (서비스에서 설정) */
        @Schema(description = "신청서상태", example = "결재중")
        private String apfSts;

        /** 신청서 상세 정보 (신청서명, 신청자, 결재자 목록 등) */
        @Schema(description = "신청서 상세 정보")
        private ApplicationInfoDto applicationInfo;

        /**
         * {@link Bcostm} 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param entity 변환할 Bcostm 엔티티
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(Bcostm entity) {
            return Response.builder()
                    .itMngcNo(entity.getItMngcNo()) // 전산관리비관리번호
                    .itMngcSno(entity.getItMngcSno()) // 전산관리비일련번호
                    .lstYn(entity.getLstYn()) // 최종여부
                    .ioeC(entity.getIoeC()) // 비목코드
                    .cttNm(entity.getCttNm()) // 계약명
                    .cttOpp(entity.getCttOpp()) // 계약상대처
                    .itMngcBg(entity.getItMngcBg()) // 전산관리비예산
                    .dfrCle(entity.getDfrCle()) // 지급주기
                    .fstDfrDt(entity.getFstDfrDt()) // 최초지급일자
                    .cur(entity.getCur()) // 통화
                    .xcr(entity.getXcr()) // 환율
                    .xcrBseDt(entity.getXcrBseDt()) // 환율기준일자
                    .infPrtYn(entity.getInfPrtYn()) // 정보보호여부
                    .indRsn(entity.getIndRsn()) // 증감사유
                    .cgpr(entity.getCgpr()) // 담당자
                    .biceDpm(entity.getBiceDpm()) // 담당부서
                    .biceTem(entity.getBiceTem()) // 담당팀
                    .abusC(entity.getAbusC()) // 사업코드
                    .itMngcTp(entity.getItMngcTp()) // 전산업무비유형
                    .pulDtt(entity.getPulDtt()) // 전산업무비구분
                    .bgYy(entity.getBgYy()) // 예산연도
                    .delYn(entity.getDelYn()) // 삭제여부
                    .build();
        }
    }

    /**
     * 전산관리비 목록 조회 검색 조건 DTO
     *
     * <p>
     * {@code GET /api/cost} 엔드포인트의 Query Parameter로 전달됩니다.
     * 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     * </p>
     *
     * <p>
     * {@code apfSts} 값 규칙:
     * </p>
     * <ul>
     * <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회</li>
     * <li>{@code "none"}: 신청서가 없는 전산관리비 (apfSts IS NULL)</li>
     * <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 전산관리비</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CostSearchCondition", description = "전산관리비 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         * <p>
         * "none" → 신청서가 없는 전산관리비, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 전산관리비
         * null 또는 미입력 → 필터 없음 (전체 조회)
         * </p>
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 연관부서 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관부서 코드. 미입력 시 전체 조회")
        private String biceDpm;

        /** 연관팀 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관팀 코드. 미입력 시 전체 조회")
        private String biceTem;

        /** 정보보호여부 필터 ('Y'=정보보호, 'N'=일반). null이면 전체 조회 */
        @Schema(description = "정보보호여부 (Y/N). 미입력 시 전체 조회")
        private String infPrtYn;

        /** 예산연도 필터 (예: "2026"). null이면 전체 조회 */
        @Schema(description = "예산연도 (예: 2026). 미입력 시 전체 조회")
        private String bgYy;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return isBlank(apfSts) && isBlank(biceDpm) && isBlank(biceTem) && isBlank(infPrtYn) && isBlank(bgYy);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * 전산관리비 일괄 조회 요청 DTO
     *
     * <p>
     * 여러 전산관리비관리번호를 한 번에 조회할 때 사용합니다.
     * 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CostDto.BulkGetRequest", description = "전산업무비 일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 전산관리비관리번호 목록 */
        @Schema(description = "전산업무비코드 목록", example = "[\"COST_2026_0001\", \"COST_2026_0002\"]")
        private List<String> itMngcNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TAAABB_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bgYy;
    }

    /**
     * 금융정보단말기 정보 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CostDto.TerminalDto", description = "금융정보단말기 정보")
    public static class TerminalDto {
        @Schema(description = "단말기관리번호", example = "TER_2026_0001")
        private String tmnMngNo;

        @Schema(description = "단말기일련번호", example = "1")
        private String tmnSno;

        @Schema(description = "단말기명", example = "대면업무용 단말기")
        private String tmnNm;

        @Schema(description = "단말기이용방법", example = "본회선 활용")
        private String tmnTuzManr;

        @Schema(description = "단말기용도", example = "창구업무 및 대민지원")
        private String tmnUsg;

        @Schema(description = "단말기서비스", example = "인터넷/금융 전용망")
        private String tmnSvc;

        @Schema(description = "단말기금액", example = "1500000")
        private BigDecimal tmlAmt;

        @Schema(description = "통화", example = "KRW")
        private String cur;

        @Schema(description = "환율", example = "1")
        private BigDecimal xcr;

        @Schema(description = "환율기준일자", example = "2026-04-03")
        private LocalDate xcrBseDt;

        @Schema(description = "지급주기", example = "매월")
        private String dfrCle;

        @Schema(description = "증감사유", example = "노후 교체에 따른 한시적 인상")
        private String indRsn;

        @Schema(description = "담당자", example = "홍길동")
        private String cgpr;

        /** 담당자명: cgpr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 (응답 전용) */
        @Schema(description = "담당자명")
        private String cgprNm;

        @Schema(description = "담당팀", example = "00101")
        private String biceTem;

        @Schema(description = "담당부서", example = "001")
        private String biceDpm;

        @Schema(description = "비고", example = "특이사항 없음")
        private String rmk;

        /** DTO → Entity 변환 (itMngcNo, itMngcSno는 서비스에서 설정) */
        public com.kdb.it.domain.budget.cost.entity.Btermm toEntity() {
            return com.kdb.it.domain.budget.cost.entity.Btermm.builder()
                    .tmnMngNo(this.tmnMngNo)
                    .tmnSno(this.tmnSno)
                    .tmnNm(this.tmnNm)
                    .tmnTuzManr(this.tmnTuzManr)
                    .tmnUsg(this.tmnUsg)
                    .tmnSvc(this.tmnSvc)
                    .tmlAmt(this.tmlAmt)
                    .cur(this.cur)
                    .xcr(this.xcr)
                    .xcrBseDt(this.xcrBseDt)
                    .dfrCle(this.dfrCle)
                    .indRsn(this.indRsn)
                    .cgpr(this.cgpr)
                    .biceTem(this.biceTem)
                    .biceDpm(this.biceDpm)
                    .rmk(this.rmk)
                    .delYn("N")
                    .build();
        }

        /** Entity → DTO 변환 */
        public static TerminalDto fromEntity(com.kdb.it.domain.budget.cost.entity.Btermm entity) {
            return TerminalDto.builder()
                    .tmnMngNo(entity.getTmnMngNo())
                    .tmnSno(entity.getTmnSno())
                    .tmnNm(entity.getTmnNm())
                    .tmnTuzManr(entity.getTmnTuzManr())
                    .tmnUsg(entity.getTmnUsg())
                    .tmnSvc(entity.getTmnSvc())
                    .tmlAmt(entity.getTmlAmt())
                    .cur(entity.getCur())
                    .xcr(entity.getXcr())
                    .xcrBseDt(entity.getXcrBseDt())
                    .dfrCle(entity.getDfrCle())
                    .indRsn(entity.getIndRsn())
                    .cgpr(entity.getCgpr())
                    .biceTem(entity.getBiceTem())
                    .biceDpm(entity.getBiceDpm())
                    .rmk(entity.getRmk())
                    .build();
        }
    }
}
