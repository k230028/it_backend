package com.kdb.it.domain.budget.project.dto;

import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정보화사업(IT 프로젝트) 관련 DTO 클래스 모음
 *
 * <p>
 * 정보화사업(TAAABB_BPROJM) 엔티티의 생성, 수정, 조회, 일괄 조회 및
 * 연관 품목(TAAABB_BITEMM) 정보 전달에 사용되는 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <p>
 * 포함된 DTO:
 * </p>
 * <ul>
 * <li>{@link CreateRequest}: 정보화사업 생성 요청 (품목 목록 포함)</li>
 * <li>{@link UpdateRequest}: 정보화사업 수정 요청 (품목 동기화 포함)</li>
 * <li>{@link Response}: 정보화사업 조회 응답 (신청서 정보, 품목 목록 포함)</li>
 * <li>{@link BitemmDto}: 품목 정보 DTO (생성/수정/조회 공통)</li>
 * <li>{@link BulkGetRequest}: 일괄 조회 요청</li>
 * </ul>
 */
public class ProjectDto {

    /**
     * 정보화사업 생성 요청 DTO
     *
     * <p>
     * 신규 정보화사업을 등록할 때 사용합니다.
     * 약 30개 이상의 필드로 구성된 대형 DTO입니다.
     * </p>
     *
     * <p>
     * {@code prjMngNo}가 null 또는 빈 문자열이면 서비스에서 Oracle 시퀀스로 자동 채번합니다.
     * 형식: {@code PRJ-{bgYy}-{seq:04d}} (예: "PRJ-2026-0001")
     * </p>
     *
     * <p>
     * {@link #toEntity()} 메서드로 {@link Bprojm} 엔티티로 변환합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ProjectCreateRequest")
    public static class CreateRequest {
        /**
         * 프로젝트관리번호 (PRJ_MNG_NO, PK)
         * <p>
         * null 또는 빈 문자열이면 자동 채번됩니다. 형식: {@code PRJ-{bgYy}-{seq:04d}}
         * </p>
         */
        @Schema(description = "프로젝트관리번호")
        private String prjMngNo;

        /** 프로젝트명 */
        @Schema(description = "프로젝트명")
        private String prjNm;

        /** 프로젝트유형 (예: "신규", "유지보수", "고도화") */
        @Schema(description = "프로젝트유형")
        private String prjTp;

        /** 주관부서 코드 또는 명칭 */
        @Schema(description = "주관부서")
        private String svnDpm;

        /** IT부서 코드 또는 명칭 */
        @Schema(description = "IT부서")
        private String itDpm;

        /** 프로젝트예산 (금액) */
        @Schema(description = "프로젝트예산")
        private BigDecimal prjBg;

        /** 익년프로젝트예산 (다음 해 예산 금액) */
        @Schema(description = "익년프로젝트예산")
        private BigDecimal nyyPrjBg;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDt;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDt;

        /** 주관부서담당자 (담당자명 또는 사번) */
        @Schema(description = "주관부서담당자")
        private String svnDpmCgpr;

        /** IT부서담당자 (담당자명 또는 사번) */
        @Schema(description = "IT부서담당자")
        private String itDpmCgpr;

        /** 주관부서담당팀장 */
        @Schema(description = "주관부서담당팀장")
        private String svnDpmTlr;

        /** IT부서담당팀장 */
        @Schema(description = "IT부서담당팀장")
        private String itDpmTlr;

        /** 주관본부/부문 */
        @Schema(description = "주관본부/부문")
        private String svnHdq;

        /** 전결권 (결재 권한 범위) */
        @Schema(description = "전결권")
        private String edrt;

        /** 사업설명 */
        @Schema(description = "사업설명")
        private String prjDes;

        /** 현황 (현재 사업 진행 현황) */
        @Schema(description = "현황")
        private String saf;

        /** 필요성 (사업 추진 필요성) */
        @Schema(description = "필요성")
        private String ncs;

        /** 기대효과 (사업 완료 후 기대 효과) */
        @Schema(description = "기대효과")
        private String xptEff;

        /** 문제 (현재 문제점 또는 이슈) */
        @Schema(description = "문제")
        private String plm;

        /** 사업범위 (프로젝트 적용 범위) */
        @Schema(description = "사업범위")
        private String prjRng;

        /** 추진경과 (현재까지의 진행 경과) */
        @Schema(description = "추진경과")
        private String pulPsg;

        /** 향후계획 (앞으로의 추진 계획) */
        @Schema(description = "향후계획")
        private String hrfPln;

        /** 업무구분 (예: "개발", "운영", "기획") */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 기술유형 (예: "Java", "Python", "클라우드") */
        @Schema(description = "기술유형")
        private String tchnTp;

        /** 주요사용자 (시스템 주요 사용 부서 또는 역할) */
        @Schema(description = "주요사용자")
        private String mnUsr;

        /** 중복여부 ("Y": 타 사업과 중복, "N": 비중복, 기본값 "N") */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 (법령 등에 따른 의무 완료 기한) */
        @Schema(description = "의무완료기한")
        private LocalDate lblFsgTlm;

        /** 보고상태 (보고 진행 상태) */
        @Schema(description = "보고상태")
        private String rprSts;

        /** 프로젝트추진가능성 (0~100 정수, NUMBER(3,0)) */
        @Schema(description = "프로젝트추진가능성 (0~100)")
        private Integer prjPulPtt;

        /** 프로젝트상태 (예: "계획", "진행중", "완료", "취소") */
        @Schema(description = "프로젝트상태")
        private String prjSts;

        /** 사업연도 (YYYY 형식, 예: "2026") */
        @Schema(description = "사업연도")
        private String bgYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String ornYn;

        /** 사업구분 ('신규', '계속') */
        @Schema(description = "사업구분")
        private String pulDtt;

        /**
         * 품목 목록
         * <p>
         * 프로젝트와 함께 등록할 품목({@link BitemmDto}) 목록입니다.
         * 생성 시 품목도 함께 저장됩니다.
         * </p>
         */
        @Schema(description = "품목 목록")
        private java.util.List<BitemmDto> items;

        /**
         * 요청 DTO를 {@link Bprojm} 엔티티로 변환하는 메서드
         *
         * <p>
         * {@code dplYn}이 null인 경우 기본값 "N"으로 설정합니다.
         * </p>
         *
         * @return 변환된 Bprojm 엔티티
         */
        public Bprojm toEntity() {
            return Bprojm.builder()
                    .prjMngNo(prjMngNo) // 프로젝트관리번호
                    .prjSno(1) // 프로젝트순번 (신규 생성 시 1로 고정)
                    .prjNm(prjNm) // 프로젝트명
                    .prjTp(prjTp) // 프로젝트유형
                    .svnDpm(svnDpm) // 주관부서
                    .itDpm(itDpm) // IT부서
                    .prjBg(prjBg) // 프로젝트예산
                    .nyyPrjBg(nyyPrjBg) // 익년프로젝트예산
                    .sttDt(sttDt) // 시작일자
                    .endDt(endDt) // 종료일자
                    .svnDpmCgpr(svnDpmCgpr) // 주관부서담당자
                    .itDpmCgpr(itDpmCgpr) // IT부서담당자
                    .svnHdq(svnHdq) // 주관본부/부문
                    .svnDpmTlr(svnDpmTlr) // 주관부서담당팀장
                    .itDpmTlr(itDpmTlr) // IT부서담당팀장
                    .edrt(edrt) // 전결권
                    .prjDes(prjDes) // 사업설명
                    .saf(saf) // 현황
                    .ncs(ncs) // 필요성
                    .xptEff(xptEff) // 기대효과
                    .plm(plm) // 문제
                    .prjRng(prjRng) // 사업범위
                    .pulPsg(pulPsg) // 추진경과
                    .hrfPln(hrfPln) // 향후계획
                    .bzDtt(bzDtt) // 업무구분
                    .tchnTp(tchnTp) // 기술유형
                    .mnUsr(mnUsr) // 주요사용자
                    .dplYn(dplYn == null ? "N" : dplYn) // 중복여부 (기본값 "N")
                    .lblFsgTlm(lblFsgTlm) // 의무완료기한
                    .rprSts(rprSts) // 보고상태
                    .lstYn("Y") // 최종여부: 신규 등록은 항상 최신 레코드
                    .prjPulPtt(prjPulPtt) // 프로젝트추진가능성
                    .prjSts(prjSts) // 프로젝트상태
                    .bgYy(bgYy) // 사업연도
                    .ornYn(ornYn) // 경상여부
                    .pulDtt(pulDtt) // 사업구분
                    .build();
        }
    }

    /**
     * 정보화사업 수정 요청 DTO
     *
     * <p>
     * 기존 정보화사업 정보를 수정할 때 사용합니다.
     * {@code prjMngNo}는 URL PathVariable로 받으므로 이 DTO에는 포함하지 않습니다.
     * </p>
     *
     * <p>
     * 품목({@code items}) 목록을 포함하며, 동기화 로직(추가/수정/삭제)은
     * {@link com.kdb.it.domain.budget.project.service.ProjectService#updateProject}에서 처리합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ProjectUpdateRequest")
    public static class UpdateRequest {
        /** 프로젝트명 */
        @Schema(description = "프로젝트명")
        private String prjNm;

        /** 프로젝트유형 */
        @Schema(description = "프로젝트유형")
        private String prjTp;

        /** 주관부서 */
        @Schema(description = "주관부서")
        private String svnDpm;

        /** IT부서 */
        @Schema(description = "IT부서")
        private String itDpm;

        /** 프로젝트예산 */
        @Schema(description = "프로젝트예산")
        private BigDecimal prjBg;

        /** 익년프로젝트예산 (다음 해 예산 금액) */
        @Schema(description = "익년프로젝트예산")
        private BigDecimal nyyPrjBg;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDt;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDt;

        /** 주관부서담당자 */
        @Schema(description = "주관부서담당자")
        private String svnDpmCgpr;

        /** IT부서담당자 */
        @Schema(description = "IT부서담당자")
        private String itDpmCgpr;

        /** 주관부서담당팀장 */
        @Schema(description = "주관부서담당팀장")
        private String svnDpmTlr;

        /** 주관본부/부문 */
        @Schema(description = "주관본부/부문")
        private String svnHdq;

        /** IT부서담당팀장 */
        @Schema(description = "IT부서담당팀장")
        private String itDpmTlr;

        /** 전결권 */
        @Schema(description = "전결권")
        private String edrt;

        /** 사업설명 */
        @Schema(description = "사업설명")
        private String prjDes;

        /** 현황 */
        @Schema(description = "현황")
        private String saf;

        /** 필요성 */
        @Schema(description = "필요성")
        private String ncs;

        /** 기대효과 */
        @Schema(description = "기대효과")
        private String xptEff;

        /** 문제 */
        @Schema(description = "문제")
        private String plm;

        /** 사업범위 */
        @Schema(description = "사업범위")
        private String prjRng;

        /** 추진경과 */
        @Schema(description = "추진경과")
        private String pulPsg;

        /** 향후계획 */
        @Schema(description = "향후계획")
        private String hrfPln;

        /** 업무구분 */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 기술유형 */
        @Schema(description = "기술유형")
        private String tchnTp;

        /** 주요사용자 */
        @Schema(description = "주요사용자")
        private String mnUsr;

        /** 중복여부 */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 */
        @Schema(description = "의무완료기한")
        private LocalDate lblFsgTlm;

        /** 보고상태 */
        @Schema(description = "보고상태")
        private String rprSts;

        /** 프로젝트추진가능성 (0~100 정수, NUMBER(3,0)) */
        @Schema(description = "프로젝트추진가능성 (0~100)")
        private Integer prjPulPtt;

        /** 프로젝트상태 */
        @Schema(description = "프로젝트상태")
        private String prjSts;

        /** 사업연도 */
        @Schema(description = "사업연도")
        private String bgYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String ornYn;

        /** 사업구분 ('신규', '계속') */
        @Schema(description = "사업구분")
        private String pulDtt;

        /**
         * 품목 목록 (동기화 대상)
         * <p>
         * 수정 요청에 포함된 목록을 기준으로 기존 품목과 비교하여
         * 추가/수정/삭제가 처리됩니다.
         * </p>
         */
        @Schema(description = "품목 목록")
        private java.util.List<BitemmDto> items;
    }

    /**
     * 정보화사업 조회 응답 DTO
     *
     * <p>
     * 프로젝트의 모든 정보를 반환합니다. {@link Bprojm} 엔티티 필드 외에
     * 조직명, 상태 등의 추가 정보를 API 응답에 맞춰 제공합니다.
     * </p>
     *
     * <p>
     * 품목 정보({@code items})는 배열 형태로 포함됩니다.
     * </p>
     *
     * <p>
     * {@link #fromEntity(Bprojm)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     * 신청서 정보와 품목 목록은 서비스에서 별도로 {@code setApfMngNo()}, {@code setItems()}로 설정합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ProjectResponse")
    public static class Response {
        /** 프로젝트관리번호 (PK) */
        @Schema(description = "프로젝트관리번호")
        private String prjMngNo;

        /** 프로젝트순번 (PRJ_SNO, Oracle 트리거로 자동 증가) */
        @Schema(description = "프로젝트순번")
        private Integer prjSno;

        /** 프로젝트명 */
        @Schema(description = "프로젝트명")
        private String prjNm;

        /** 프로젝트유형 */
        @Schema(description = "프로젝트유형")
        private String prjTp;

        /** 주관부서 */
        @Schema(description = "주관부서")
        private String svnDpm;

        /** IT부서 */
        @Schema(description = "IT부서")
        private String itDpm;

        /** 프로젝트예산 */
        @Schema(description = "프로젝트예산")
        private BigDecimal prjBg;

        /** 익년프로젝트예산 (다음 해 예산 금액) */
        @Schema(description = "익년프로젝트예산")
        private BigDecimal nyyPrjBg;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDt;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDt;

        /** 주관부서담당자 */
        @Schema(description = "주관부서담당자")
        private String svnDpmCgpr;

        /** IT부서담당자 */
        @Schema(description = "IT부서담당자")
        private String itDpmCgpr;

        /** 주관본부/부문 */
        @Schema(description = "주관본부/부문")
        private String svnHdq;

        /** 주관부서담당팀장 */
        @Schema(description = "주관부서담당팀장")
        private String svnDpmTlr;

        /** IT부서담당팀장 */
        @Schema(description = "IT부서담당팀장")
        private String itDpmTlr;

        /** 전결권 */
        @Schema(description = "전결권")
        private String edrt;

        /** 사업설명 */
        @Schema(description = "사업설명")
        private String prjDes;

        /** 현황 */
        @Schema(description = "현황")
        private String saf;

        /** 필요성 */
        @Schema(description = "필요성")
        private String ncs;

        /** 기대효과 */
        @Schema(description = "기대효과")
        private String xptEff;

        /** 문제 */
        @Schema(description = "문제")
        private String plm;

        /** 사업범위 */
        @Schema(description = "사업범위")
        private String prjRng;

        /** 추진경과 */
        @Schema(description = "추진경과")
        private String pulPsg;

        /** 향후계획 */
        @Schema(description = "향후계획")
        private String hrfPln;

        /** 업무구분 */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 기술유형 */
        @Schema(description = "기술유형")
        private String tchnTp;

        /** 주요사용자 */
        @Schema(description = "주요사용자")
        private String mnUsr;

        /** 중복여부 ("Y" 또는 "N") */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 */
        @Schema(description = "의무완료기한")
        private LocalDate lblFsgTlm;

        /** 보고상태 */
        @Schema(description = "보고상태")
        private String rprSts;

        /** 프로젝트추진가능성 (0~100 정수, NUMBER(3,0)) */
        @Schema(description = "프로젝트추진가능성 (0~100)")
        private Integer prjPulPtt;

        /** 프로젝트상태 */
        @Schema(description = "프로젝트상태")
        private String prjSts;

        /** 삭제여부 (Soft Delete 상태, "Y": 삭제됨, "N": 정상) */
        @Schema(description = "삭제여부")
        private String delYn;

        /** 사업연도 (YYYY 형식) */
        @Schema(description = "사업연도")
        private String bgYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String ornYn;

        /** 사업구분 ('신규', '계속') */
        @Schema(description = "사업구분")
        private String pulDtt;

        /** 최초 등록 일시 (JPA Auditing) */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초 등록자 사번 (JPA Auditing) */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /** 마지막 수정 일시 (JPA Auditing) */
        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        /** 마지막 수정자 사번 (JPA Auditing) */
        @Schema(description = "마지막수정자")
        private String lstChgUsid;

        /**
         * 연결된 신청서관리번호
         * <p>
         * 서비스에서 {@link com.kdb.it.common.approval.entity.Cappla}를 통해 조회하여 설정합니다.
         * 신청서가 없으면 null입니다.
         * </p>
         */
        @Schema(description = "신청서관리번호")
        private String apfMngNo;

        /**
         * 신청서 결재상태
         * <p>
         * 연결된 신청서의 현재 결재 상태 (예: "결재중", "결재완료", "반려").
         * 신청서가 없으면 null입니다.
         * </p>
         */
        @Schema(description = "신청서상태")
        private String apfSts;

        /**
         * 품목 목록
         * <p>
         * 단건 조회 시에만 포함됩니다. 목록 조회 시에는 포함되지 않습니다 (성능 최적화).
         * </p>
         */
        @Schema(description = "품목 목록")
        private java.util.List<BitemmDto> items;

        /** IT부서명: itDpm(부서코드) 기준 TAAABB_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "IT부서명")
        private String itDpmNm;

        /** 주관부서명: svnDpm(부서코드) 기준 TAAABB_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "주관부서명")
        private String svnDpmNm;

        /** IT담당자명: itDpmCgpr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 */
        @Schema(description = "IT담당자명")
        private String itDpmCgprNm;

        /** IT담당팀장명: itDpmTlr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 */
        @Schema(description = "IT담당팀장명")
        private String itDpmTlrNm;

        /** 주관부서담당자명: svnDpmCgpr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 */
        @Schema(description = "주관부서담당자명")
        private String svnDpmCgprNm;

        /** 주관부서담당팀장명: svnDpmTlr(사번) 기준 TAAABB_CUSERI에서 USR_NM 조회 */
        @Schema(description = "주관부서담당팀장명")
        private String svnDpmTlrNm;

        /** 자본예산: Bitemm의 gclDtt(비목코드)가 공통코드 코드값구분 IOE_CPIT에 해당하는 항목의 gclAmt 합계 */
        @Schema(description = "자본예산")
        private BigDecimal assetBg;

        /** 개발비: 자본예산 중 코드설명(cdDes)이 '개발비'인 품목의 gclAmt 합계 (IOE-351-*) */
        @Schema(description = "개발비")
        private BigDecimal devBg;

        /** 기계장치: 자본예산 중 코드설명(cdDes)이 '기계장치'인 품목의 gclAmt 합계 (IOE-304-*) */
        @Schema(description = "기계장치")
        private BigDecimal machBg;

        /** 기타무형자산: 자본예산 중 코드설명(cdDes)이 '기타무형자산'인 품목의 gclAmt 합계 (IOE-359-*) */
        @Schema(description = "기타무형자산")
        private BigDecimal intanBg;

        /** 일반관리비: Bitemm의 gclDtt(비목코드)가 공통코드 코드값구분 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE에 해당하는 항목의 gclAmt 합계 */
        @Schema(description = "일반관리비")
        private BigDecimal costBg;

        /** TAAABB_BBUGTM 기준 편성예산 합계 (요청금액 × 편성률/100, 서비스에서 일괄 조회 시 설정) */
        @Schema(description = "편성예산 (BBUGTM 기준, 편성률 반영)")
        private BigDecimal dupBg;

        /** BBUGTM 기준 자본예산 편성예산 (gclDtt IOE_CPIT 계열 품목의 DUP_BG 합계) */
        @Schema(description = "자본예산 편성예산 (BBUGTM 기준)")
        private BigDecimal assetDupBg;

        /** BBUGTM 기준 일반관리비 편성예산 (gclDtt IOE_IDR/SEVS/XPN/LEAFE 계열 품목의 DUP_BG 합계) */
        @Schema(description = "일반관리비 편성예산 (BBUGTM 기준)")
        private BigDecimal costDupBg;

        /** 예산 합계 일괄 설정 (Lombok 어노테이션 프로세싱 문제 방지용 명시적 메서드) */
        public void setBudgetAmounts(BigDecimal assetBg, BigDecimal devBg, BigDecimal machBg,
                BigDecimal intanBg, BigDecimal costBg) {
            this.assetBg = assetBg;
            this.devBg = devBg;
            this.machBg = machBg;
            this.intanBg = intanBg;
            this.costBg = costBg;
        }

        /** 신청서 상세 정보 (신청서명, 신청자, 결재자 목록 등) */
        @Schema(description = "신청서 상세 정보")
        private ApplicationInfoDto applicationInfo;

        /**
         * {@link Bprojm} 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * <p>
         * 엔티티의 모든 필드를 DTO로 복사합니다.
         * 연결된 품목 리스트 및 조직, 예산 요약 정보 등은
         * 서비스 계층에서 추가로 세팅해야 합니다.
         * </p>
         *
         * @param project 변환할 Bprojm 엔티티
         * @return 변환된 ProjectDto.Response DTO
         */
        public static Response fromEntity(Bprojm project) {
            return Response.builder()
                    .prjMngNo(project.getPrjMngNo()) // 프로젝트관리번호
                    .prjSno(project.getPrjSno()) // 프로젝트순번
                    .prjNm(project.getPrjNm()) // 프로젝트명
                    .prjTp(project.getPrjTp()) // 프로젝트유형
                    .svnDpm(project.getSvnDpm()) // 주관부서
                    .itDpm(project.getItDpm()) // IT부서
                    .prjBg(project.getPrjBg()) // 프로젝트예산
                    .nyyPrjBg(project.getNyyPrjBg()) // 익년프로젝트예산
                    .sttDt(project.getSttDt()) // 시작일자
                    .endDt(project.getEndDt()) // 종료일자
                    .svnHdq(project.getSvnHdq()) // 주관본부/부문
                    .svnDpmCgpr(project.getSvnDpmCgpr()) // 주관부서담당자
                    .itDpmCgpr(project.getItDpmCgpr()) // IT부서담당자
                    .svnDpmTlr(project.getSvnDpmTlr()) // 주관부서담당팀장
                    .itDpmTlr(project.getItDpmTlr()) // IT부서담당팀장
                    .edrt(project.getEdrt()) // 전결권
                    .prjDes(project.getPrjDes()) // 사업설명
                    .saf(project.getSaf()) // 현황
                    .ncs(project.getNcs()) // 필요성
                    .xptEff(project.getXptEff()) // 기대효과
                    .plm(project.getPlm()) // 문제
                    .prjRng(project.getPrjRng()) // 사업범위
                    .pulPsg(project.getPulPsg()) // 추진경과
                    .hrfPln(project.getHrfPln()) // 향후계획
                    .bzDtt(project.getBzDtt()) // 업무구분
                    .tchnTp(project.getTchnTp()) // 기술유형
                    .mnUsr(project.getMnUsr()) // 주요사용자
                    .dplYn(project.getDplYn()) // 중복여부
                    .lblFsgTlm(project.getLblFsgTlm()) // 의무완료기한
                    .rprSts(project.getRprSts()) // 보고상태
                    .prjPulPtt(project.getPrjPulPtt()) // 프로젝트추진가능성
                    .prjSts(project.getPrjSts()) // 프로젝트상태
                    .delYn(project.getDelYn()) // 삭제여부
                    .bgYy(project.getBgYy()) // 사업연도
                    .ornYn(project.getOrnYn()) // 경상여부
                    .pulDtt(project.getPulDtt()) // 사업구분
                    .fstEnrDtm(project.getFstEnrDtm()) // 최초 등록 일시
                    .fstEnrUsid(project.getFstEnrUsid()) // 최초 등록자
                    .lstChgDtm(project.getLstChgDtm()) // 마지막 수정 일시
                    .lstChgUsid(project.getLstChgUsid()) // 마지막 수정자
                    .build();
        }
    }

    /**
     * 품목(Bitemm) 정보 DTO
     *
     * <p>
     * 정보화사업 생성/수정 요청 및 조회 응답에 공통으로 사용됩니다.
     * {@link com.kdb.it.domain.budget.project.entity.Bitemm} 엔티티와 매핑됩니다.
     * </p>
     *
     * <p>
     * 수정 시 동작:
     * </p>
     * <ul>
     * <li>{@code gclMngNo}가 있는 경우: 기존 품목 수정</li>
     * <li>{@code gclMngNo}가 null 또는 빈 문자열인 경우: 신규 품목 추가</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "BitemmDto", description = "품목 정보 DTO")
    public static class BitemmDto {
        /**
         * 품목관리번호 (GCL_MNG_NO, PK 일부)
         * <p>
         * 수정 시 기존 품목 식별에 사용. 신규 추가 시 null 또는 빈 문자열.
         * 형식: {@code GCL-{yyyy}-{seq:04d}} (예: "GCL-2026-0001")
         * </p>
         */
        @Schema(description = "품목관리번호")
        private String gclMngNo;

        /**
         * 품목일련번호 (GCL_SNO, PK 일부)
         * <p>
         * 동일 관리번호 내의 순번. 신규는 MAX+1로 자동 설정.
         * </p>
         */
        @Schema(description = "품목일련번호")
        private Integer gclSno;

        /** 품목구분 (예: "HW", "SW", "용역") */
        @Schema(description = "품목구분")
        private String gclDtt;

        /** 품목명 (도입 또는 구매할 품목의 이름) */
        @Schema(description = "품목명")
        private String gclNm;

        /** 품목수량 */
        @Schema(description = "품목수량")
        private BigDecimal gclQtt;

        /** 통화 코드 (예: "KRW", "USD") */
        @Schema(description = "통화")
        private String cur;

        /** 환율 */
        @Schema(description = "환율")
        private BigDecimal xcr;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자")
        private LocalDate xcrBseDt;

        /** 예산근거 (예산 산정 근거 설명) */
        @Schema(description = "예산근거")
        private String bgFdtn;

        /** 도입시기 (예: "2026년 1분기") */
        @Schema(description = "도입시기")
        private String itdDt;

        /** 지급주기 (예: "일시불", "매월") */
        @Schema(description = "지급주기")
        private String dfrCle;

        /** 정보보호여부 ("Y" 또는 "N", 기본값 "N") */
        @Schema(description = "정보보호여부")
        private String infPrtYn;

        /** 통합인프라여부 ("Y" 또는 "N", 기본값 "N") */
        @Schema(description = "통합인프라여부")
        private String itrInfrYn;

        /** 최종여부 ("Y": 최신 이력, "N": 과거 이력) */
        @Schema(description = "최종여부")
        private String lstYn;

        /** 품목금액 (단가 × 수량) */
        @Schema(description = "품목금액")
        private BigDecimal gclAmt;

        /**
         * {@link com.kdb.it.domain.budget.project.entity.Bitemm} 엔티티를 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param bitemm 변환할 Bitemm 엔티티
         * @return 변환된 BitemmDto
         */
        public static BitemmDto fromEntity(com.kdb.it.domain.budget.project.entity.Bitemm bitemm) {
            return BitemmDto.builder()
                    .gclMngNo(bitemm.getGclMngNo()) // 품목관리번호
                    .gclSno(bitemm.getGclSno()) // 품목일련번호
                    .gclDtt(bitemm.getGclDtt()) // 품목구분
                    .gclNm(bitemm.getGclNm()) // 품목명
                    .gclQtt(bitemm.getGclQtt()) // 품목수량
                    .cur(bitemm.getCur()) // 통화
                    .xcr(bitemm.getXcr()) // 환율
                    .xcrBseDt(bitemm.getXcrBseDt()) // 환율기준일자
                    .bgFdtn(bitemm.getBgFdtn()) // 예산근거
                    .itdDt(bitemm.getItdDt()) // 도입시기
                    .dfrCle(bitemm.getDfrCle()) // 지급주기
                    .infPrtYn(bitemm.getInfPrtYn()) // 정보보호여부
                    .itrInfrYn(bitemm.getItrInfrYn()) // 통합인프라여부
                    .lstYn(bitemm.getLstYn()) // 최종여부
                    .gclAmt(bitemm.getGclAmt()) // 품목금액
                    .build();
        }
    }

    /**
     * 정보화사업 목록 조회 검색 조건 DTO
     *
     * <p>
     * {@code GET /api/projects} 엔드포인트의 Query Parameter로 전달됩니다.
     * 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     * </p>
     *
     * <p>
     * {@code apfSts} 값 규칙:
     * </p>
     * <ul>
     * <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회</li>
     * <li>{@code "none"}: 신청서가 없는 프로젝트 (apfSts IS NULL)</li>
     * <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 프로젝트</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectSearchCondition", description = "정보화사업 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         * <p>
         * "none" → 신청서가 없는 프로젝트, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 프로젝트
         * null 또는 미입력 → 필터 없음 (전체 조회)
         * </p>
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 사업연도 필터 (예: "2026"). null이면 전체 연도 조회 */
        @Schema(description = "사업연도 (예: 2026). 미입력 시 전체 조회")
        private String bgYy;

        /** 프로젝트상태 필터 (예: "계획", "진행중", "완료"). null이면 전체 상태 조회 */
        @Schema(description = "프로젝트상태 (예: 계획, 진행중, 완료). 미입력 시 전체 조회")
        private String prjSts;

        /** 프로젝트유형 필터 (예: "신규", "유지보수", "고도화"). null이면 전체 유형 조회 */
        @Schema(description = "프로젝트유형 (예: 신규, 유지보수, 고도화). 미입력 시 전체 조회")
        private String prjTp;

        /** IT부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "IT부서 코드. 미입력 시 전체 조회")
        private String itDpm;

        /** 주관부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "주관부서 코드. 미입력 시 전체 조회")
        private String svnDpm;

        /**
         * 경상여부 필터
         * <p>
         * "Y" → 경상사업만 조회, "N" → 일반 정보화사업만 조회 (ORN_YN IS NULL 또는 'N')
         * null 또는 미입력 → 필터 없음 (전체 조회)
         * </p>
         */
        @Schema(description = "경상여부 (Y=경상사업만, N=일반사업만). 미입력 시 전체 조회")
        private String ornYn;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return isBlank(apfSts) && isBlank(bgYy) && isBlank(prjSts)
                    && isBlank(prjTp) && isBlank(itDpm) && isBlank(svnDpm) && isBlank(ornYn);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * 정보화사업 일괄 조회 요청 DTO
     *
     * <p>
     * 여러 프로젝트관리번호를 한 번에 조회할 때 사용합니다.
     * 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectBulkGetRequest", description = "일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 프로젝트관리번호 목록 (예: ["PRJ-2026-0001", "PRJ-2026-0002"]) */
        @Schema(description = "조회할 프로젝트관리번호 목록")
        private java.util.List<String> prjMngNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TAAABB_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bgYy;
    }
}
