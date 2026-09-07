package com.kdb.it.domain.budget.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.system.validation.NotBlankUnlessAdmin;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정보화사업 생성·수정·조회와 연관 품목 전달에 사용하는 DTO 모음입니다. */
public class ProjectDto {

    /** 정보화사업 생성 요청입니다. 관리번호가 비어 있으면 서비스에서 자동 채번합니다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ProjectCreateRequest")
    public static class CreateRequest {
        /**
         * 프로젝트관리번호 (ABUS_MNG_NO, PK)
         *
         * <p>null 또는 빈 문자열이면 자동 채번됩니다. 형식: {@code PRJ-{bseYy}-{seq:04d}}
         */
        @Schema(description = "프로젝트관리번호")
        private String abusMngNo;

        @Schema(description = "프로젝트명")
        private String abusNm;

        /** 프로젝트유형 (예: "신규", "유지보수", "고도화") */
        @Schema(description = "프로젝트유형")
        private String bzTpC;

        /** 주관부서 코드 또는 명칭 */
        @Schema(description = "주관부서")
        private String svnDpmC;

        /** 주관팀 코드 (SVN_TEM_C, VARCHAR2(5)) */
        @Schema(description = "주관팀")
        private String svnTemC;

        /** IT부서 코드 또는 명칭 */
        @Schema(description = "IT부서")
        private String dvmDpmC;

        /** 개발팀 코드 (DVM_TEM_C, VARCHAR2(5)) */
        @Schema(description = "개발팀")
        private String dvmTemC;

        @Schema(description = "시작일자")
        private LocalDate sttDtm;

        @Schema(description = "종료일자")
        private LocalDate endDtm;

        /** 주관부서담당자 (담당자명 또는 사번) */
        @Schema(description = "주관부서담당자")
        private String usid;

        /** IT부서담당자 (담당자명 또는 사번) */
        @Schema(description = "IT부서담당자")
        private String dvmUsid;

        @Schema(description = "주관부서담당팀장")
        private String tlrUsid;

        @Schema(description = "IT부서담당팀장")
        private String dvmTlrUsid;

        @Schema(description = "주관본부/부문")
        private String prlmHrkOgzCCone;

        /** 주관팀명. 팀코드를 얻을 수 없는 경로(편성요청서 반입)의 대체값이며 다른 근거가 있으면 그쪽이 우선입니다. */
        @Schema(description = "주관팀명 (팀코드를 확정할 수 없을 때의 대체값)")
        private String svnTemNm;

        /** 전결권 (결재 권한 범위) */
        @Schema(description = "전결권")
        private String edrtTc;

        @Schema(description = "사업추진내용정보")
        private String abusPulConeInf;

        /** 현황 (현재 사업 진행 현황) */
        @Schema(description = "현황")
        private String cpnSafCone;

        /** 사업 추진 필요성 */
        @Schema(description = "사업추진필요성정보")
        private String abusPulNcsInf;

        /** 운영 DB의 BYTE 시맨틱 사업 본문 컬럼 상한을 확인합니다. */
        @JsonIgnore
        @AssertTrue(message = "사업 본문 입력값이 DB Byte 상한을 초과했습니다.")
        public boolean isTextFieldsWithinByteLimit() {
            return ProjectDtoSupport.isTextWithinByteLimit(this);
        }

        /** 사업 완료 후 기대 효과 */
        @Schema(description = "사업기대효과정보")
        private String abusXptEffInf;

        /** 문제 (현재 문제점 또는 이슈) */
        @Schema(description = "문제")
        private String plmDes;

        /** 사업 추진 방향 */
        @Schema(description = "사업추진방향정보")
        private String abusPulDrcnInf;

        /** 추진경과 (현재까지의 진행 경과) */
        @Schema(description = "추진경과")
        private String mnPrgCone;

        /** 향후계획 (앞으로의 추진 계획) */
        @Schema(description = "향후계획")
        private String hrfPlnCone;

        /** 업무구분 (예: "개발", "운영", "기획") */
        @Schema(description = "업무구분")
        private String bzDttNm;

        /** 기술유형 (예: "Java", "Python", "클라우드") */
        @Schema(description = "기술유형")
        private String sklTpTc;

        /** 주요사용자 (시스템 주요 사용 부서 또는 역할) */
        @Schema(description = "주요사용자")
        private String cstTpTc;

        /** 중복여부 ("Y": 타 사업과 중복, "N": 비중복, 기본값 "N") */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 (법령 등에 따른 의무 완료 기한) */
        @Schema(description = "의무완료기한")
        private String flfFsgDt;

        /** 보고상태 (보고 진행 상태) */
        @Schema(description = "보고상태")
        private String rprStsTc;

        /** 프로젝트추진가능성 — 공통코드 EXE_PTT_YN 코드값('1' 확정·'2' 미정). 물리 컬럼은 VARCHAR2(1)이라 라벨을 담지 못한다. */
        @Schema(description = "프로젝트추진가능성 (공통코드 EXE_PTT_YN 코드값, 1자)")
        private String exePttYn;

        /** 프로젝트상태 (예: "계획", "진행중", "완료", "취소") */
        @Schema(description = "프로젝트상태")
        private String stsTc;

        /** 사업연도 (YYYY 형식, 예: "2026") */
        @Schema(description = "사업연도")
        private String bseYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String odnYn;

        /** 사업구분 — 신규('10')·계속('20')만 허용. 공통코드의 '해당없음'('0')은 사업 유효값이 아니다 (BE-19). */
        @Schema(description = "사업구분 (10=신규, 20=계속)")
        @NotBlank(message = "사업구분은 필수입니다.")
        @Pattern(regexp = "10|20", message = "사업구분은 신규(10) 또는 계속(20)만 가능합니다.")
        private String abusTc;

        /** 관련프로젝트관리번호 (계속사업인 경우 전년도 사업의 관리번호) */
        @Schema(description = "관련프로젝트관리번호")
        private String cncdRfrNo;

        /** 원화 지급금액: 이미 지급한 금액. 미전송이면 0으로 저장한다. */
        @Schema(description = "원화 지급금액", nullable = true)
        private BigDecimal dfrAmt;

        /**
         * 품목 목록
         *
         * <p>프로젝트와 함께 등록할 품목({@link BitemmDto}) 목록입니다. 생성 시 품목도 함께 저장됩니다.
         */
        @Schema(description = "품목 목록")
        @Valid
        private java.util.List<BitemmDto> items;

        /** 작성완료 저장 여부. 화면 요청에는 필수이고 내부 반입 경로는 null을 허용합니다. */
        @NotNull(message = "저장 종류(complete)는 필수입니다.")
        @Schema(
                description = "작성완료 여부 (true=저장, false=임시저장)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean complete;

        /**
         * 요청 DTO를 {@link Bprojm} 엔티티로 변환하는 메서드
         *
         * <p>{@code dplYn}이 null인 경우 기본값 "N"으로 설정합니다.
         *
         * @return 변환된 Bprojm 엔티티
         */
        public Bprojm toEntity() {
            return ProjectDtoSupport.toEntity(this);
        }
    }

    /** 정보화사업 수정 요청입니다. 관리번호는 URL 경로로 받고 품목 목록은 서비스에서 동기화합니다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ProjectUpdateRequest")
    public static class UpdateRequest {
        @Schema(description = "프로젝트명")
        private String abusNm;

        @Schema(description = "프로젝트유형")
        private String bzTpC;

        /** 주관부서 */
        @Schema(description = "주관부서")
        private String svnDpmC;

        /** IT부서 */
        @Schema(description = "IT부서")
        private String dvmDpmC;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDtm;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDtm;

        /** 주관부서담당자 */
        @Schema(description = "주관부서담당자")
        private String usid;

        /** IT부서담당자 */
        @Schema(description = "IT부서담당자")
        private String dvmUsid;

        /** 주관본부/부문 */
        @Schema(description = "주관본부/부문")
        private String prlmHrkOgzCCone;

        /** 주관부서담당팀장 */
        @Schema(description = "주관부서담당팀장")
        private String tlrUsid;

        /** IT부서담당팀장 */
        @Schema(description = "IT부서담당팀장")
        private String dvmTlrUsid;

        /** 전결권 */
        @Schema(description = "전결권")
        private String edrtTc;

        /** 사업 추진 내용 */
        @Schema(description = "사업추진내용정보")
        private String abusPulConeInf;

        /** 현황 */
        @Schema(description = "현황")
        private String cpnSafCone;

        /** 사업 추진 필요성 */
        @Schema(description = "사업추진필요성정보")
        private String abusPulNcsInf;

        /** 운영 DB의 BYTE 시맨틱 사업 본문 컬럼 상한을 확인합니다. */
        @JsonIgnore
        @AssertTrue(message = "사업 본문 입력값이 DB Byte 상한을 초과했습니다.")
        public boolean isTextFieldsWithinByteLimit() {
            return ProjectDtoSupport.isTextWithinByteLimit(this);
        }

        /** 사업 기대 효과 */
        @Schema(description = "사업기대효과정보")
        private String abusXptEffInf;

        /** 문제 */
        @Schema(description = "문제")
        private String plmDes;

        /** 사업 추진 방향 */
        @Schema(description = "사업추진방향정보")
        private String abusPulDrcnInf;

        /** 추진경과 */
        @Schema(description = "추진경과")
        private String mnPrgCone;

        /** 향후계획 */
        @Schema(description = "향후계획")
        private String hrfPlnCone;

        /** 업무구분 */
        @Schema(description = "업무구분")
        private String bzDttNm;

        /** 기술유형 */
        @Schema(description = "기술유형")
        private String sklTpTc;

        /** 주요사용자 */
        @Schema(description = "주요사용자")
        private String cstTpTc;

        /** 중복여부 */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 */
        @Schema(description = "의무완료기한")
        private String flfFsgDt;

        /** 보고상태 */
        @Schema(description = "보고상태")
        private String rprStsTc;

        /** 프로젝트추진가능성 — 공통코드 EXE_PTT_YN 코드값('1' 확정·'2' 미정). 물리 컬럼은 VARCHAR2(1)이라 라벨을 담지 못한다. */
        @Schema(description = "프로젝트추진가능성 (공통코드 EXE_PTT_YN 코드값, 1자)")
        private String exePttYn;

        /** 프로젝트상태 */
        @Schema(description = "프로젝트상태")
        private String stsTc;

        /** 사업연도 */
        @Schema(description = "사업연도")
        private String bseYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String odnYn;

        /** 사업구분 — 생성과 같은 규칙. 수정으로 값을 비우거나 '0'으로 되돌리는 구멍을 막는다 (BE-19). */
        @Schema(description = "사업구분 (10=신규, 20=계속)")
        @NotBlankUnlessAdmin(
                message = "사업구분은 필수입니다.",
                pattern = "10|20",
                patternMessage = "사업구분은 신규(10) 또는 계속(20)만 가능합니다.")
        private String abusTc;

        /** 관련프로젝트관리번호 (계속사업인 경우 전년도 사업의 관리번호) */
        @Schema(description = "관련프로젝트관리번호")
        private String cncdRfrNo;

        /** 원화 지급금액: 이미 지급한 금액. 미전송이면 0으로 저장한다. */
        @Schema(description = "원화 지급금액", nullable = true)
        private BigDecimal dfrAmt;

        /**
         * 품목 목록 (동기화 대상)
         *
         * <p>수정 요청에 포함된 목록을 기준으로 기존 품목과 비교하여 추가/수정/삭제가 처리됩니다.
         */
        @Schema(description = "품목 목록")
        @Valid
        private java.util.List<BitemmDto> items;

        /** 작성완료 저장 여부. 화면 요청에는 필수이고 내부 반입 경로는 null을 허용합니다. */
        @NotNull(message = "저장 종류(complete)는 필수입니다.")
        @Schema(
                description = "작성완료 여부 (true=저장, false=임시저장)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean complete;
    }

    /**
     * 정보화사업 조회 응답 DTO. {@link Bprojm} 엔티티 필드 외에 조직명과 상태를 API 응답에 맞춰 제공합니다.
     *
     * <p>품목 정보({@code items})는 배열 형태로 포함됩니다. {@link ProjectResponseMapper#fromEntity(Bprojm)}로
     * 엔티티에서 변환하고, 서비스가 신청서 정보와 품목 목록을 별도로 설정합니다.
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
        private String abusMngNo;

        /** 프로젝트순번 (SNO, Oracle 트리거로 자동 증가) */
        @Schema(description = "프로젝트순번")
        private Integer sno;

        /** 최종여부 (Y=현재 최종본, N=과거 이력 또는 재신청 초안) */
        @Schema(
                description = "최종여부",
                allowableValues = {"Y", "N"})
        private String lstYn;

        /** 프로젝트명 */
        @Schema(description = "프로젝트명")
        private String abusNm;

        /** 프로젝트유형 */
        @Schema(description = "프로젝트유형")
        private String bzTpC;

        /** 주관부서 */
        @Schema(description = "주관부서")
        private String svnDpmC;

        /** 주관팀 코드 */
        @Schema(description = "주관팀 코드")
        private String svnTemC;

        /** BPROJM에 저장된 주관부서명 스냅샷 */
        @Schema(description = "주관부서명 스냅샷")
        private String svnDpmNm;

        /** BPROJM에 저장된 주관팀명 스냅샷 */
        @Schema(description = "주관팀명 스냅샷")
        private String svnTemNm;

        /** IT부서 */
        @Schema(description = "IT부서")
        private String dvmDpmC;

        /** IT 담당팀 코드 */
        @Schema(description = "IT 담당팀 코드")
        private String dvmTemC;

        /**
         * IT 담당팀명: IT부서담당팀장(DVM_TLR_USID) 소속 팀명. 팀코드는 CORGNI에 없어 CUSERI.TEM_NM으로 해석하며 미조회 시 null.
         */
        @Schema(description = "IT 담당팀명 (IT부서담당팀장 소속팀명)")
        private String dvmTemNm;

        /** BPROJM 총소요금액 스냅샷 */
        @Schema(description = "BPROJM 총소요금액 스냅샷")
        private BigDecimal totRqmAmt;

        @Schema(description = "당해 요청금액", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal tyyBgAmt;

        /** 예정자본금액 (파생값: 품목 mplAmt 자본예산 합산) */
        @Schema(description = "예정자본금액 (파생값)")
        private BigDecimal mplCpitAmt;

        /** 예정관리비금액 (파생값: 품목 mplAmt 관리비 합산) */
        @Schema(description = "예정관리비금액 (파생값)")
        private BigDecimal mplMngcAmt;

        @Schema(description = "총소요금액", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal prjBgAmt;

        @Schema(description = "원화 환산 예정금액", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal mplAmt;

        /** 원화 지급금액 (BPROJM.DFR_AMT 컬럼값). */
        @Schema(
                description = "원화 지급금액",
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true)
        private BigDecimal dfrAmt;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDtm;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDtm;

        /** 주관부서담당자 */
        @Schema(description = "주관부서담당자")
        private String usid;

        /** IT부서담당자 */
        @Schema(description = "IT부서담당자")
        private String dvmUsid;

        /** 주관본부/부문 */
        @Schema(description = "주관본부/부문")
        private String prlmHrkOgzCCone;

        /** 주관부서담당팀장 */
        @Schema(description = "주관부서담당팀장")
        private String tlrUsid;

        /** IT부서담당팀장 */
        @Schema(description = "IT부서담당팀장")
        private String dvmTlrUsid;

        /** 전결권 */
        @Schema(description = "전결권")
        private String edrtTc;

        /** 사업설명 */
        @Schema(description = "사업설명")
        private String abusPulConeInf;

        /** 현황 */
        @Schema(description = "현황")
        private String cpnSafCone;

        /** 필요성 */
        @Schema(description = "필요성")
        private String abusPulNcsInf;

        /** 기대효과 */
        @Schema(description = "기대효과")
        private String abusXptEffInf;

        /** 문제 */
        @Schema(description = "문제")
        private String plmDes;

        /** 사업범위 */
        @Schema(description = "사업범위")
        private String abusPulDrcnInf;

        /** 추진경과 */
        @Schema(description = "추진경과")
        private String mnPrgCone;

        /** 향후계획 */
        @Schema(description = "향후계획")
        private String hrfPlnCone;

        /** 업무구분 */
        @Schema(description = "업무구분")
        private String bzDttNm;

        /** 기술유형 */
        @Schema(description = "기술유형")
        private String sklTpTc;

        /** 주요사용자 */
        @Schema(description = "주요사용자")
        private String cstTpTc;

        /** 중복여부 ("Y" 또는 "N") */
        @Schema(description = "중복여부")
        private String dplYn;

        /** 의무완료기한 */
        @Schema(description = "의무완료기한")
        private String flfFsgDt;

        /** 보고상태 */
        @Schema(description = "보고상태")
        private String rprStsTc;

        /** 프로젝트추진가능성 — 공통코드 EXE_PTT_YN 코드값('1' 확정·'2' 미정). 물리 컬럼은 VARCHAR2(1)이라 라벨을 담지 못한다. */
        @Schema(description = "프로젝트추진가능성 (공통코드 EXE_PTT_YN 코드값, 1자)")
        private String exePttYn;

        /** 프로젝트상태 */
        @Schema(description = "프로젝트상태")
        private String stsTc;

        /** 해당 사업의 활성 BPROJA 단계 상태코드 목록(IT_PTL_STS_TC) */
        @Schema(description = "해당 사업의 활성 BPROJA 단계 상태코드 목록(IT_PTL_STS_TC)")
        private java.util.List<String> bprojaStsCodes;

        /** 사업계획서 사업일정 최소 시작일 (YYYYMMDD, 대시보드 진행현황 간트용, 사업계획 미작성 시 null) */
        @Schema(description = "사업계획서 사업일정 최소 시작일(YYYYMMDD)")
        private String bizplanSttDt;

        /** 사업계획서 사업일정 최대 종료일 (YYYYMMDD, 대시보드 진행현황 간트용, 사업계획 미작성 시 null) */
        @Schema(description = "사업계획서 사업일정 최대 종료일(YYYYMMDD)")
        private String bizplanEndDt;

        /** 삭제여부 (Soft Delete 상태, "Y": 삭제됨, "N": 정상) */
        @Schema(description = "삭제여부")
        private String delYn;

        /** 사업연도 (YYYY 형식) */
        @Schema(description = "사업연도")
        private String bseYy;

        /** 경상여부 ('Y'=경상사업, 'N'=일반 정보화사업) */
        @Schema(description = "경상여부")
        private String odnYn;

        /** 사업구분 ('신규', '계속') */
        @Schema(description = "사업구분")
        private String abusTc;

        /** 관련프로젝트관리번호 (계속사업인 경우 전년도 사업의 관리번호) */
        @Schema(description = "관련프로젝트관리번호")
        private String cncdRfrNo;

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
         *
         * <p>서비스에서 {@link com.kdb.it.common.approval.entity.Cappla}를 통해 조회하여 설정합니다. 신청서가 없으면
         * null입니다.
         */
        @Schema(description = "신청서관리번호")
        private String apfMngNo;

        /** 신청서 결재상태 표시 라벨 (예: "결재중"·"결재완료"·"반려"). 신청서가 없으면 null입니다. */
        @Schema(description = "신청서상태")
        private String apfSts;

        /** 신청서 결재상태 코드 (예: "02"=결재완료). 업무 분기는 라벨이 아니라 이 값으로 한다. */
        @Schema(description = "신청서상태코드")
        private String apfStsC;

        /**
         * 품목 목록
         *
         * <p>단건 조회 시에만 포함됩니다. 목록 조회 시에는 포함되지 않습니다 (성능 최적화).
         */
        @Schema(description = "품목 목록")
        private java.util.List<BitemmDto> items;

        /** IT부서명: dvmDpmC(부서코드) 기준 TPRMPP_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "IT부서명")
        private String dvmDpmCNm;

        /** 주관부서명: svnDpmC(부서코드) 기준 TPRMPP_CORGNI에서 BBR_NM 조회 */
        @Schema(description = "주관부서명")
        private String svnDpmCNm;

        /** IT담당자명: dvmUsid(사번) 기준 TPRMPP_CUSERI에서 USR_NM 조회 */
        @Schema(description = "IT담당자명")
        private String dvmUsidNm;

        /** 주관부서담당팀장명: tlrUsid(사번) 기준 TPRMPP_CUSERI에서 USR_NM 조회 */
        @Schema(description = "주관부서담당팀장명")
        private String tlrUsidNm;

        /** 주관부서담당자명: usid(사번) 기준 TPRMPP_CUSERI에서 USR_NM 조회 */
        @Schema(description = "주관부서담당자명")
        private String usidNm;

        /** IT부서담당팀장명: dvmTlrUsid(사번) 기준 TPRMPP_CUSERI에서 USR_NM 조회 */
        @Schema(description = "IT부서담당팀장명")
        private String dvmTlrUsidNm;

        /** IT담당자 직위명: dvmUsid(사번) 기준 TPRMPP_CUSERI에서 PT_C_NM 조회 */
        @Schema(description = "IT담당자 직위명")
        private String dvmUsidPtCNm;

        /** 주관부서담당팀장 직위명: tlrUsid(사번) 기준 TPRMPP_CUSERI에서 PT_C_NM 조회 */
        @Schema(description = "주관부서담당팀장 직위명")
        private String tlrUsidPtCNm;

        /** 주관부서담당자 직위명: usid(사번) 기준 TPRMPP_CUSERI에서 PT_C_NM 조회 */
        @Schema(description = "주관부서담당자 직위명")
        private String usidPtCNm;

        /** IT부서담당팀장 직위명: dvmTlrUsid(사번) 기준 TPRMPP_CUSERI에서 PT_C_NM 조회 */
        @Schema(description = "IT부서담당팀장 직위명")
        private String dvmTlrUsidPtCNm;

        /** 전결권명: edrtTc 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "전결권명")
        private String edrtTcNm;

        /** 사업유형명: 컬럼(ABUS_PPO_CONE)에 코드값명을 직접 저장하므로 bzTpC 원본값과 동일 */
        @Schema(description = "사업유형명")
        private String bzTpCNm;

        /** 업무구분명: 컬럼(BZ_DTT_NM)에 코드값명을 직접 저장하므로 bzDttNm 원본값과 동일 */
        @Schema(description = "업무구분명")
        private String bzDttNmNm;

        /** 기술분야명: 컬럼(SKL_FLD_NM)에 코드값명을 직접 저장하므로 sklTpTc 원본값과 동일 */
        @Schema(description = "기술분야명")
        private String sklTpTcNm;

        /** 주요사용자명: 컬럼(CST_TP_TC_NM)에 코드값명을 직접 저장하므로 cstTpTc 원본값과 동일 */
        @Schema(description = "주요사용자명")
        private String cstTpTcNm;

        /** 보고상태명: rprStsTc 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "보고상태명")
        private String rprStsTcNm;

        /** 프로젝트추진가능성명: exePttYn 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "프로젝트추진가능성명")
        private String exePttYnNm;

        /** 사업구분명: abusTc 기준 TPRMPP_CCODEM C_NM */
        @Schema(description = "사업구분명")
        private String abusTcNm;

        /** 자본예산: Bitemm의 ioeC(비목코드)가 공통코드 코드값구분 IOE_CPIT에 해당하는 항목의 amt 합계 */
        @Schema(description = "자본예산")
        private BigDecimal assetBg;

        /** 개발비: 자본예산 중 코드설명(cdDes)이 '개발비'인 품목의 amt 합계 (IOE-351-*) */
        @Schema(description = "개발비")
        private BigDecimal dvcBg;

        /** 기계장치: 자본예산 중 코드설명(cdDes)이 '기계장치'인 품목의 amt 합계 (IOE-304-*) */
        @Schema(description = "기계장치")
        private BigDecimal hwBg;

        /** 기타무형자산: 자본예산 중 코드설명(cdDes)이 '기타무형자산'인 품목의 amt 합계 (IOE-359-*) */
        @Schema(description = "기타무형자산")
        private BigDecimal swBg;

        /**
         * 일반관리비: Bitemm의 ioeC(비목코드)가 공통코드 코드값구분 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE에 해당하는 항목의 amt
         * 합계
         */
        @Schema(description = "일반관리비")
        private BigDecimal costBg;

        /** TPRMPP_BBUGTM 기준 편성예산 합계 (요청금액 × 편성률/100, 서비스에서 일괄 조회 시 설정) */
        @Schema(description = "편성예산 (BBUGTM 기준, 편성률 반영)")
        private BigDecimal dupBgAmt;

        /** BBUGTM 기준 자본예산 편성예산 (ioeC IOE_CPIT 계열 품목의 DUP_BG 합계) */
        @Schema(description = "자본예산 편성예산 (BBUGTM 기준)")
        private BigDecimal assetDupBg;

        /** BBUGTM 기준 일반관리비 편성예산 (ioeC IOE_IDR/SEVS/XPN/LEAFE 계열 품목의 DUP_BG 합계) */
        @Schema(description = "일반관리비 편성예산 (BBUGTM 기준)")
        private BigDecimal costDupBg;

        /** 예산 합계 일괄 설정 (Lombok 어노테이션 프로세싱 문제 방지용 명시적 메서드) */
        public void setBudgetAmounts(
                BigDecimal assetBg,
                BigDecimal dvcBg,
                BigDecimal hwBg,
                BigDecimal swBg,
                BigDecimal costBg) {
            this.assetBg = assetBg;
            this.dvcBg = dvcBg;
            this.hwBg = hwBg;
            this.swBg = swBg;
            this.costBg = costBg;
        }

        /** 신청서 상세 정보 (신청서명, 신청자, 결재자 목록 등) */
        @Schema(description = "신청서 상세 정보")
        private ApplicationInfoDto applicationInfo;
    }

    /**
     * 품목(Bitemm) 정보 DTO
     *
     * <p>정보화사업 생성/수정 요청 및 조회 응답에 공통으로 사용됩니다. {@link com.kdb.it.domain.budget.project.entity.Bitemm}
     * 엔티티와 매핑됩니다.
     *
     * <p>수정 시 동작:
     *
     * <ul>
     *   <li>{@code gclMngNo}가 있는 경우: 기존 품목 수정
     *   <li>{@code gclMngNo}가 null 또는 빈 문자열인 경우: 신규 품목 추가
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
         *
         * <p>수정 시 기존 품목 식별에 사용. 신규 추가 시 null 또는 빈 문자열. 형식: {@code GCL-{yyyy}-{seq:04d}} (예:
         * "GCL-2026-0001")
         */
        @Schema(description = "품목관리번호")
        private String gclMngNo;

        /**
         * 품목일련번호 (SNO, PK 일부)
         *
         * <p>동일 관리번호 내의 순번. 신규는 MAX+1로 자동 설정.
         */
        @Schema(description = "품목일련번호")
        private Integer sno;

        /** 품목구분 (예: "HW", "SW", "용역") */
        @Schema(description = "품목구분")
        private String ioeC;

        /** 품목구분명 (IOE 코드 표시명, 서비스에서 별도 설정) */
        @Schema(description = "품목구분명")
        private String ioeCNm;

        /** 품목명 (도입 또는 구매할 품목의 이름) */
        @Schema(description = "품목명")
        private String gclNm;

        /** 품목수량 */
        @Schema(description = "품목수량")
        private BigDecimal qty;

        /** 통화 코드 (예: "KRW", "USD") */
        @Schema(description = "통화")
        private String curC;

        /** 환율 */
        @Schema(description = "환율")
        private BigDecimal xcr;

        /** 환율기준일자 */
        @Schema(description = "환율기준일자")
        private String xcrBseDt;

        /** 예산근거 (예산 산정 근거 설명) */
        @Schema(description = "예산근거")
        private String cncdFdtnCone;

        /** 도입시기 (예: "2026년 1분기") */
        @Schema(description = "도입시기")
        private String bseYm;

        /** 지급주기 (예: "일시불", "매월") */
        @Schema(description = "지급주기")
        private String dfrCleC;

        /** 정보보호여부 ("Y" 또는 "N", 미기재는 null) */
        @Schema(description = "정보보호여부")
        private String sectSysUtzYn;

        /** 통합인프라여부 ("Y" 또는 "N", 미기재는 null) */
        @Schema(description = "통합인프라여부")
        private String itrInfrYn;

        /** 최종여부 ("Y": 최신 이력, "N": 과거 이력) */
        @Schema(description = "최종여부")
        private String lstYn;

        /** 당해 외화 원금 (외화 품목만 입력, 원화 품목은 null) */
        @Schema(description = "당해 외화 원금 (외화 품목만 입력, 원화 품목은 null)", example = "1000")
        private BigDecimal fcAmt;

        /** 당해 요청금액 (원화, 외화 품목은 서버 환율로 환산) */
        @Schema(description = "당해 요청금액 (원화)")
        private BigDecimal amt;

        /** 내년 이후 요청금액 (원화 품목은 원화, 외화 품목은 외화 원금; 당해 요청금액과 독립, 기본 0) */
        @Schema(description = "내년 이후 요청금액 (통화별 원금)")
        private BigDecimal mplAmt;

        /** 운영 DB의 BYTE 시맨틱 품목명·예산근거 컬럼 상한을 확인합니다. */
        @JsonIgnore
        @AssertTrue(message = "소요자원 입력값이 DB Byte 상한을 초과했습니다.")
        public boolean isTextFieldsWithinByteLimit() {
            return ProjectDtoSupport.isTextWithinByteLimit(this);
        }

        /**
         * {@link com.kdb.it.domain.budget.project.entity.Bitemm} 엔티티를 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param bitemm 변환할 Bitemm 엔티티
         * @return 변환된 BitemmDto
         */
        public static BitemmDto fromEntity(com.kdb.it.domain.budget.project.entity.Bitemm bitemm) {
            return ProjectDtoSupport.fromEntity(bitemm);
        }
    }

    /**
     * 정보화사업 목록 조회 검색 조건 DTO
     *
     * <p>{@code GET /api/projects} 엔드포인트의 Query Parameter로 전달됩니다. 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     *
     * <p>{@code apfSts} 값 규칙:
     *
     * <ul>
     *   <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회
     *   <li>{@code "none"}: 신청서가 없는 프로젝트 (apfSts IS NULL)
     *   <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 프로젝트
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectSearchCondition", description = "정보화사업 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         *
         * <p>"none" → 신청서가 없는 프로젝트, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 프로젝트 null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 사업연도 필터 (예: "2026"). null이면 전체 연도 조회 */
        @Schema(description = "사업연도 (예: 2026). 미입력 시 전체 조회")
        private String bseYy;

        /** 프로젝트상태 필터 (예: "계획", "진행중", "완료"). null이면 전체 상태 조회 */
        @Schema(description = "프로젝트상태 (예: 계획, 진행중, 완료). 미입력 시 전체 조회")
        private String stsTc;

        /** 프로젝트유형 필터 (예: "신규", "유지보수", "고도화"). null이면 전체 유형 조회 */
        @Schema(description = "프로젝트유형 (예: 신규, 유지보수, 고도화). 미입력 시 전체 조회")
        private String bzTpC;

        /** IT부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "IT부서 코드. 미입력 시 전체 조회")
        private String dvmDpmC;

        /** 주관부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "주관부서 코드. 미입력 시 전체 조회")
        private String svnDpmC;

        /**
         * 경상여부 필터
         *
         * <p>"Y" → 경상사업만 조회, "N" → 일반 정보화사업만 조회 (ODN_YN IS NULL 또는 'N') null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "경상여부 (Y=경상사업만, N=일반사업만). 미입력 시 전체 조회")
        private String odnYn;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return ProjectDtoSupport.isEmpty(this);
        }
    }

    /**
     * 정보화사업 일괄 조회 요청 DTO
     *
     * <p>여러 프로젝트관리번호를 한 번에 조회할 때 사용합니다. 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectBulkGetRequest", description = "일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 프로젝트관리번호 목록 (예: ["PRJ-2026-0001", "PRJ-2026-0002"]) */
        @NotEmpty
        @Schema(description = "조회할 프로젝트관리번호 목록")
        private java.util.List<@NotBlank @Size(max = 30) String> prjMngNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TPRMPP_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bseYy;

        /**
         * 개정본을 명시하는 항목 (선택). 비우면 최종본({@code LST_YN='Y'})을 반환합니다.
         *
         * <p>재상신 초안을 화면에 띄운 채 보고서를 만들 때 최종본이 아니라 그 초안의 수치를 써야 합니다. 지정하지 않은 관리번호는 종전대로 최종본을 씁니다.
         */
        @Schema(description = "개정본 지정 (선택). 비우면 최종본을 반환")
        private java.util.List<VersionRef> versions;
    }

    /**
     * 개정본 지정 참조입니다.
     *
     * @param mngNo 사업관리번호
     * @param sno 개정 순번
     */
    @Schema(name = "ProjectDto.VersionRef", description = "개정본 지정 참조")
    public record VersionRef(String mngNo, Integer sno) {}

    /**
     * 정보화사업 일괄 조회 결과 DTO (부분 성공)
     *
     * <p>조회에 성공한 항목({@code items})과 미존재로 조회에 실패한 프로젝트관리번호 목록({@code failedIds})을 함께 반환합니다. 누락 건을
     * 조용히 버리지 않고 호출자에게 노출하기 위함입니다.
     *
     * @param items 조회 성공 항목 목록
     * @param failedIds 조회 실패(미존재) 프로젝트관리번호 목록
     */
    @Schema(name = "ProjectBulkResponse", description = "정보화사업 일괄 조회 결과 (부분 성공)")
    public record BulkResponse(
            @Schema(description = "조회 성공 항목") java.util.List<Response> items,
            @Schema(description = "조회 실패(미존재) 프로젝트관리번호 목록") java.util.List<String> failedIds) {}
}
