package com.kdb.it.domain.budget.project.entity;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BprojmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Imported;

/**
 * 정보화사업(IT 프로젝트) 마스터 엔티티
 *
 * <p>DB 테이블: {@code BPROJM}
 *
 * <p>IT 부문의 정보화사업(신규 시스템 도입, 인프라 구축 등) 계획 및 현황을 관리합니다. 품목 정보({@link Bitemm})와 신청서와 연관됩니다.
 *
 * <p>관리번호 형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})
 *
 * <p>주의: {@code update()} 메서드가 두 개 존재합니다 (오버로딩). 하나는 {@code sno}를 포함하고, 다른 하나는 포함하지 않습니다.
 */
@LogTarget(entity = BprojmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TPRMPP_BPROJM", comment = "정보화사업(IT 프로젝트) 마스터") // 매핑할 DB 테이블명
@IdClass(BprojmId.class) // 복합키 클래스 지정 (PRJ_MNG_NO + PRJ_SNO)
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bprojm extends BaseEntity {

    /** JPA가 정보화사업 엔티티를 복원할 때 사용하는 기본 생성자입니다. */
    protected Bprojm() {}

    /** 프로젝트관리번호: 기본키 (예: PRJ-2026-0001) */
    @Id
    @Column(
            name = "ABUS_MNG_NO",
            nullable = false,
            length = 30,
            comment = "프로젝트관리번호 (물리컬럼 ABUS_MNG_NO=사업관리번호)")
    private String abusMngNo;

    /** 프로젝트순번: 복합 기본키의 두 번째 컬럼 (동일 관리번호 내 버전 구분, 1부터 시작) */
    @Id
    @Column(name = "SNO", nullable = false, precision = 9, comment = "프로젝트순번 (물리컬럼 SNO=일련번호)")
    private Integer sno;

    /** 사업명: 사업의 공식 명칭 (최대 100자) */
    @Column(name = "ABUS_NM", length = 100, comment = "사업명")
    private String abusNm;

    /**
     * 사업유형: 코드값이 아닌 코드값명(공통코드 ABUS_PPO)을 직접 저장. 물리컬럼 ABUS_PPO_CONE(사업목적내용). Java 필드명 bzTpC는 API 계약
     * 안정성을 위해 유지.
     */
    @Column(
            name = "ABUS_PPO_CONE",
            length = 300,
            comment = "사업유형명 (물리컬럼 ABUS_PPO_CONE=사업목적내용, 공통코드 ABUS_PPO 코드값명 저장)")
    private String bzTpC;

    /** 주관부서: 사업을 주관하는 업무 부서 코드 (최대 20자) */
    @Column(name = "SVN_DPM_C", length = 20, comment = "주관부서 (물리컬럼 SVN_DPM_C=주관부서코드)")
    private String svnDpmC;

    /** 주관팀: 사업을 주관하는 업무 팀 코드 (신규 생성 시 작성자 소속 팀코드로 자동 설정, 최대 5자) */
    @Column(name = "SVN_TEM_C", length = 5, comment = "주관팀 (물리컬럼 SVN_TEM_C=주관팀코드)")
    private String svnTemC;

    /** 주관부서명: 코드 설정 시점 CORGNI 조회 스냅샷 (조직명 변경 시에도 과거 기록 유지, 최대 100자) */
    @Column(name = "SVN_DPM_NM", length = 100, comment = "주관부서명")
    private String svnDpmNm;

    /** 주관팀명: 코드 설정 시점 CORGNI 조회 스냅샷 (최대 100자) */
    @Column(name = "SVN_TEM_NM", length = 100, comment = "주관팀명")
    private String svnTemNm;

    /** IT부서: 사업을 담당하는 IT 부서 코드 (최대 20자) */
    @Column(name = "DVM_DPM_C", length = 20, comment = "IT부서 (물리컬럼 DVM_DPM_C=개발부서코드)")
    private String dvmDpmC;

    /** 개발팀: IT부서담당자(DVM_USID) 소속 팀 코드 스냅샷 (생성·수정 시 담당자 기준 갱신, 최대 5자) */
    @Column(name = "DVM_TEM_C", length = 5, comment = "개발팀 (물리컬럼 DVM_TEM_C=개발팀코드)")
    private String dvmTemC;

    /** 시작일자: 사업 개시 예정일 (DDL DATE 타입을 LocalDate로 매핑) */
    @Column(name = "STT_DTM", comment = "시작일자 (물리컬럼 STT_DTM=시작일시)")
    private LocalDate sttDtm;

    /** 종료일자: 사업 완료 예정일 (DDL DATE 타입을 LocalDate로 매핑) */
    @Column(name = "END_DTM", comment = "종료일자 (물리컬럼 END_DTM=종료일시)")
    private LocalDate endDtm;

    /**
     * 총소요금액: 활성 품목 AMT 합계 스냅샷 (화면 [총 예산]).
     *
     * <p>조회는 품목 합산 파생값을 쓰므로 이 컬럼은 저장 시점 기록용이다. 드롭 전(V20260622_007) 같은 이름의 컬럼은 당해예산을 담았으나 지금은 총
     * 예산이다.
     */
    @Column(name = "TOT_RQM_AMT", precision = 18, scale = 3, comment = "총소요금액 (총 예산 스냅샷)")
    private BigDecimal totRqmAmt;

    /** 예정금액: 활성 품목 MPL_AMT 합계 스냅샷 (화면 [예산연도+1년 이후 예산]). */
    @Column(name = "MPL_AMT", precision = 18, scale = 3, comment = "예정금액 (익년 이후 예산 스냅샷)")
    private BigDecimal mplAmt;

    /** 지급금액: 사용자가 입력하는 기 지급예산. 이 컬럼이 유일한 출처다. */
    @Column(name = "DFR_AMT", precision = 18, scale = 3, comment = "지급금액 (기 지급예산)")
    private BigDecimal dfrAmt;

    /** 주관부서담당자: 주관부서 담당자 사번 또는 이름 (최대 14자) */
    @Column(name = "USID", length = 14, comment = "주관부서담당자 (물리컬럼 USID=사용자ID)")
    private String usid;

    /** IT부서담당자: IT부서 담당자 사번 또는 이름 (최대 14자) */
    @Column(name = "DVM_USID", length = 14, comment = "IT부서담당자 (물리컬럼 DVM_USID=개발사용자ID)")
    private String dvmUsid;

    /** 주관부서담당팀장: 주관부서 담당 팀장 사번 또는 이름 (최대 14자) */
    @Column(name = "TLR_USID", length = 14, comment = "주관부서담당팀장 (물리컬럼 TLR_USID=팀장사용자ID)")
    private String tlrUsid;

    /** 주관부서담당팀장명 스냅샷. 퇴사 후에도 남기기 위해 저장한다 — {@link #assignPersonNames} 참고. */
    @Column(name = "TLR_NM", length = 100, comment = "주관부서담당팀장명")
    private String tlrNm;

    /** 주관부서담당자명 스냅샷. 퇴사 후에도 남기기 위해 저장한다 — {@link #assignPersonNames} 참고. */
    @Column(name = "USR_NM", length = 100, comment = "주관부서담당자명")
    private String usrNm;

    /** IT부서담당팀장: IT부서 담당 팀장 사번 또는 이름 (최대 14자) */
    @Column(name = "DVM_TLR_USID", length = 14, comment = "IT부서담당팀장 (물리컬럼 DVM_TLR_USID=개발팀장사용자ID)")
    private String dvmTlrUsid;

    /** 전결권: 결재 전결 권한자 구분 (최대 2자) */
    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "전결권 (물리컬럼 IT_PTL_EDRT_TC=전결권구분코드)")
    private String edrtTc;

    /** 사업설명: 사업의 전반적인 설명 (최대 1000자) */
    @Column(name = "ABUS_CONE", length = 1000, comment = "사업설명 (물리컬럼 ABUS_CONE=사업내용)")
    private String abusCone;

    /** 현황: 현재 업무/시스템의 현황 분석 내용 (최대 1000자) */
    @Column(name = "CPN_SAF_CONE", length = 1000, comment = "현황 (물리컬럼 CPN_SAF_CONE=회사현황내용)")
    private String cpnSafCone;

    /** 필요성: 사업의 필요성 및 당위성 (최대 300자) */
    @Column(name = "ABUS_NCS_CONE", length = 300, comment = "필요성 (물리컬럼 ABUS_NCS_CONE=사업필요성내용)")
    private String abusNcsCone;

    /** 기대효과: 사업 완료 후 기대되는 효과 (최대 4000자) */
    @Column(name = "DGOG_PPO_CONE", length = 4000, comment = "기대효과 (물리컬럼 DGOG_PPO_CONE=효과성목적내용)")
    private String dgogPpoCone;

    /** 문제: 현재 문제점 또는 개선이 필요한 사항 (최대 4000자) */
    @Column(name = "PLM_DES", length = 4000, comment = "문제 (물리컬럼 PLM_DES=문제설명)")
    private String plmDes;

    /** 사업범위내용: 사업의 대상 범위 및 경계 (최대 600자) */
    @Column(name = "ABUS_RNG_CONE", length = 600, comment = "사업범위내용")
    private String abusRngCone;

    /** 주요진행내용: 사업 추진 진행 상황 및 경과 내용 (최대 2000자) */
    @Column(name = "MN_PRG_CONE", length = 2000, comment = "주요진행내용")
    private String mnPrgCone;

    /** 향후계획: 앞으로의 추진 계획 (최대 300자) */
    @Column(name = "HRF_PLN_CONE", length = 300, comment = "향후계획 (물리컬럼 HRF_PLN_CONE=향후계획내용)")
    private String hrfPlnCone;

    /** 업무구분: 코드값이 아닌 코드값명(공통코드 BZ_DTT)을 직접 저장 (최대 100자, 예: 리테일, 기업금융) */
    @Column(name = "BZ_DTT_NM", length = 100, comment = "업무구분명 (공통코드 BZ_DTT 코드값명 저장)")
    private String bzDttNm;

    /**
     * 기술분야: 코드값이 아닌 코드값명(공통코드 SKL_FLD)을 직접 저장. 물리컬럼 SKL_FLD_NM. Java 필드명 sklTpTc는 API 계약 안정성을 위해
     * 유지.
     */
    @Column(
            name = "SKL_FLD_NM",
            length = 500,
            comment = "기술분야명 (물리컬럼 SKL_FLD_NM, 공통코드 SKL_FLD 코드값명 저장)")
    private String sklTpTc;

    /**
     * 주요사용자/고객유형: 코드값이 아닌 코드값명(공통코드 CST_TP_TC)을 직접 저장. 물리컬럼 CST_TP_TC_NM. Java 필드명 cstTpTc는 API 계약
     * 안정성을 위해 유지.
     */
    @Column(
            name = "CST_TP_TC_NM",
            length = 1000,
            comment = "고객유형구분코드명 (물리컬럼 CST_TP_TC_NM, 공통코드 CST_TP_TC 코드값명 저장)")
    private String cstTpTc;

    /** 중복여부: 기존 유사 사업과의 중복 여부 ('Y'=중복, 'N'=미중복) */
    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    /** 의무완료기한: 법적/규정상 반드시 완료해야 하는 기한 (YYYYMMDD, 8자리) */
    @Column(name = "FLF_FSG_DT", length = 8, comment = "의무완료기한 (물리컬럼 FLF_FSG_DT=이행완료일자)")
    private String flfFsgDt;

    /** 보고상태: 상위 보고 단계의 상태 (최대 1자) */
    @Column(
            name = "IT_PTL_RPR_STS_TC",
            length = 2,
            comment = "보고상태 (물리컬럼 IT_PTL_RPR_STS_TC=보고상태구분코드, 공통코드 2자리)")
    private String rprStsTc;

    /** 최종여부: 현재 유효한 레코드 여부 ('Y'=최신, 'N'=이전 버전) */
    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    /** 프로젝트추진가능성: 공통코드 PRJ_PUL_PTT cdva 값 (VARCHAR2(1), 예: "1", "2") */
    @Column(
            name = "EXE_PTT_YN",
            length = 1,
            comment = "프로젝트추진가능성 (물리컬럼 EXE_PTT_YN=실행가능성여부, 공통코드 EXE_PTT_YN 1자리)")
    private String exePttYn;

    /** 예산연도: 예산 연도 (4자리 숫자, 예: "2026") */
    @Column(name = "BSE_YY", length = 4, comment = "예산연도 (물리컬럼 BSE_YY=기준연도)")
    private String bseYy;

    /** 주관본부/부문: 사업을 총괄하는 본부 또는 부문 명칭 (최대 100자) */
    @Column(
            name = "PRLM_HRK_OGZ_C_CONE",
            length = 100,
            comment = "주관본부 (물리컬럼 PRLM_HRK_OGZ_C_CONE=인사상위조직코드내용)")
    private String prlmHrkOgzCCone;

    /** 경상여부: 경상사업 여부 ('Y'=경상사업, null 또는 'N'=일반 정보화사업) */
    @Column(name = "ODN_YN", length = 1, comment = "경상여부")
    private String odnYn;

    /** 사업구분: 공통코드 ABUS_TC의 코드값 ('10'=신규, '20'=계속, '0'=해당없음) */
    @Column(name = "ABUS_TC", length = 2, nullable = false, comment = "사업구분 (물리컬럼 ABUS_TC=사업구분코드)")
    private String abusTc;

    /** 관련프로젝트관리번호: 계속사업인 경우 전년도 사업의 관리번호 (최대 30자) */
    @Column(name = "CNCD_RFR_NO", length = 30, comment = "관련프로젝트관리번호 (물리컬럼 CNCD_RFR_NO=관련참조번호)")
    private String cncdRfrNo;

    /**
     * 프로젝트 수정 파라미터 레코드 (DB-06)
     *
     * <p>35+ 개별 파라미터를 하나의 레코드로 압축하여 메서드 시그니처 가독성을 개선합니다. {@code ProjectService}의 수정 로직에서 사용합니다.
     *
     * @param abusNm 사업명
     * @param bzTpC 사업유형명 (공통코드 ABUS_PPO 코드값명)
     * @param svnDpmC 주관부서코드
     * @param dvmDpmC IT부서코드 (물리컬럼 DVM_DPM_C=개발부서코드)
     * @param sttDtm 시작일자
     * @param endDtm 종료일자
     * @param usid 주관부서담당자 사번
     * @param dvmUsid IT부서담당자 사번
     * @param tlrUsid 주관부서담당팀장 사번
     * @param dvmTlrUsid IT부서담당팀장 사번
     * @param edrtTc 전결권구분코드
     * @param abusCone 사업설명 (물리컬럼 ABUS_CONE=사업내용)
     * @param cpnSafCone 현황 (물리컬럼 CPN_SAF_CONE=회사현황내용)
     * @param abusNcsCone 필요성 (물리컬럼 ABUS_NCS_CONE=사업필요성내용)
     * @param dgogPpoCone 기대효과 (물리컬럼 DGOG_PPO_CONE=효과성목적내용)
     * @param plmDes 문제 (물리컬럼 PLM_DES=문제설명)
     * @param abusRngCone 사업범위내용
     * @param mnPrgCone 주요진행내용
     * @param hrfPlnCone 향후계획 (물리컬럼 HRF_PLN_CONE=향후계획내용)
     * @param bzDttNm 업무구분명 (공통코드 BZ_DTT 코드값명)
     * @param sklTpTc 기술분야명 (물리컬럼 SKL_FLD_NM, 공통코드 SKL_FLD 코드값명)
     * @param cstTpTc 고객유형구분코드명 (물리컬럼 CST_TP_TC_NM, 공통코드 CST_TP_TC 코드값명)
     * @param dplYn 중복여부 ('Y'=중복, 'N'=미중복)
     * @param flfFsgDt 의무완료기한 (물리컬럼 FLF_FSG_DT=이행완료일자, YYYYMMDD)
     * @param rprStsTc 보고상태구분코드
     * @param exePttYn 프로젝트추진가능성 (공통코드 EXE_PTT_YN 1자리)
     * @param bseYy 예산연도 (물리컬럼 BSE_YY=기준연도, 4자리)
     * @param prlmHrkOgzCCone 주관본부 (물리컬럼 PRLM_HRK_OGZ_C_CONE=인사상위조직코드내용)
     * @param odnYn 경상여부 ('Y'=경상사업, null 또는 'N'=일반 정보화사업)
     * @param abusTc 사업구분코드 (예: '신규', '계속')
     * @param cncdRfrNo 관련프로젝트관리번호 (계속사업인 경우 전년도 사업 관리번호)
     */
    public record UpdateCommand(
            String abusNm,
            String bzTpC,
            String svnDpmC,
            String dvmDpmC,
            LocalDate sttDtm,
            LocalDate endDtm,
            String usid,
            String dvmUsid,
            String tlrUsid,
            String dvmTlrUsid,
            String edrtTc,
            String abusCone,
            String cpnSafCone,
            String abusNcsCone,
            String dgogPpoCone,
            String plmDes,
            String abusRngCone,
            String mnPrgCone,
            String hrfPlnCone,
            String bzDttNm,
            String sklTpTc,
            String cstTpTc,
            String dplYn,
            String flfFsgDt,
            String rprStsTc,
            String exePttYn,
            String bseYy,
            String prlmHrkOgzCCone,
            String odnYn,
            String abusTc,
            String cncdRfrNo) {}

    /**
     * UpdateCommand 레코드로 프로젝트 정보를 업데이트합니다 (sno 제외).
     *
     * @param cmd 수정 파라미터 레코드
     */
    public void update(UpdateCommand cmd) {
        update(
                cmd.abusNm(),
                cmd.bzTpC(),
                cmd.svnDpmC(),
                cmd.dvmDpmC(),
                cmd.sttDtm(),
                cmd.endDtm(),
                cmd.usid(),
                cmd.dvmUsid(),
                cmd.tlrUsid(),
                cmd.dvmTlrUsid(),
                cmd.edrtTc(),
                cmd.abusCone(),
                cmd.cpnSafCone(),
                cmd.abusNcsCone(),
                cmd.dgogPpoCone(),
                cmd.plmDes(),
                cmd.abusRngCone(),
                cmd.mnPrgCone(),
                cmd.hrfPlnCone(),
                cmd.bzDttNm(),
                cmd.sklTpTc(),
                cmd.cstTpTc(),
                cmd.dplYn(),
                cmd.flfFsgDt(),
                cmd.rprStsTc(),
                cmd.exePttYn(),
                cmd.bseYy(),
                cmd.prlmHrkOgzCCone(),
                cmd.odnYn(),
                cmd.abusTc(),
                cmd.cncdRfrNo());
    }

    /**
     * 프로젝트 순번을 포함한 정보화사업 정보를 변경합니다.
     *
     * <p>JPA Dirty Checking을 활용하여 트랜잭션 내에서 모든 필드를 변경합니다.
     *
     * @param abusNm 사업명
     * @param bzTpC 사업유형명
     * @param svnDpmC 주관부서코드
     * @param dvmDpmC 개발부서코드
     * @param sttDtm 시작일자
     * @param endDtm 종료일자
     * @param usid 주관부서 담당자 사용자 ID
     * @param dvmUsid 개발부서 담당자 사용자 ID
     * @param tlrUsid 주관부서 담당 팀장 사용자 ID
     * @param dvmTlrUsid 개발부서 담당 팀장 사용자 ID
     * @param edrtTc IT포탈 전결권 구분 코드
     * @param abusCone 사업내용
     * @param cpnSafCone 회사현황내용
     * @param abusNcsCone 사업필요성내용
     * @param dgogPpoCone 효과성목적내용
     * @param plmDes 문제설명
     * @param abusRngCone 사업범위내용
     * @param mnPrgCone 주요진행내용
     * @param hrfPlnCone 향후계획내용
     * @param bzDttNm 업무구분명
     * @param sklTpTc 기술분야명
     * @param cstTpTc 고객유형구분코드명
     * @param dplYn 중복여부
     * @param flfFsgDt 이행완료일자
     * @param rprStsTc 보고상태구분코드
     * @param exePttYn 실행가능성여부
     * @param bseYy 기준연도
     * @param prlmHrkOgzCCone 인사상위조직코드내용
     * @param sno 프로젝트 일련번호
     * @param odnYn 경상여부
     * @param abusTc 사업구분코드
     * @param cncdRfrNo 관련참조번호
     */
    public void update(
            String abusNm,
            String bzTpC,
            String svnDpmC,
            String dvmDpmC,
            LocalDate sttDtm,
            LocalDate endDtm,
            String usid,
            String dvmUsid,
            String tlrUsid,
            String dvmTlrUsid,
            String edrtTc,
            String abusCone,
            String cpnSafCone,
            String abusNcsCone,
            String dgogPpoCone,
            String plmDes,
            String abusRngCone,
            String mnPrgCone,
            String hrfPlnCone,
            String bzDttNm,
            String sklTpTc,
            String cstTpTc,
            String dplYn,
            String flfFsgDt,
            String rprStsTc,
            String exePttYn,
            String bseYy,
            String prlmHrkOgzCCone,
            Integer sno,
            String odnYn,
            String abusTc,
            String cncdRfrNo) {
        this.sno = sno;
        this.abusNm = abusNm;
        this.bzTpC = bzTpC;
        this.svnDpmC = svnDpmC;
        this.dvmDpmC = dvmDpmC;
        this.sttDtm = sttDtm;
        this.endDtm = endDtm;
        this.usid = usid;
        this.dvmUsid = dvmUsid;
        this.tlrUsid = tlrUsid;
        this.dvmTlrUsid = dvmTlrUsid;
        this.edrtTc = edrtTc;
        this.abusCone = abusCone;
        this.cpnSafCone = cpnSafCone;
        this.abusNcsCone = abusNcsCone;
        this.dgogPpoCone = dgogPpoCone;
        this.plmDes = plmDes;
        this.abusRngCone = abusRngCone;
        this.mnPrgCone = mnPrgCone;
        this.hrfPlnCone = hrfPlnCone;
        this.bzDttNm = bzDttNm;
        this.sklTpTc = sklTpTc;
        this.cstTpTc = cstTpTc;
        this.dplYn = dplYn;
        this.flfFsgDt = flfFsgDt;
        this.rprStsTc = rprStsTc;
        this.exePttYn = exePttYn;
        this.bseYy = bseYy;
        this.prlmHrkOgzCCone = prlmHrkOgzCCone;
        this.odnYn = odnYn;
        this.abusTc = CodeDefaults.orNotApplicable(abusTc);
        this.cncdRfrNo = cncdRfrNo;
    }

    /**
     * 프로젝트 순번을 유지하면서 정보화사업 정보를 변경합니다.
     *
     * <p>프로젝트 순번(sno)은 변경하지 않고 나머지 필드만 업데이트합니다.
     *
     * @param abusNm 사업명
     * @param bzTpC 사업유형명
     * @param svnDpmC 주관부서코드
     * @param dvmDpmC 개발부서코드
     * @param sttDtm 시작일자
     * @param endDtm 종료일자
     * @param usid 주관부서 담당자 사용자 ID
     * @param dvmUsid 개발부서 담당자 사용자 ID
     * @param tlrUsid 주관부서 담당 팀장 사용자 ID
     * @param dvmTlrUsid 개발부서 담당 팀장 사용자 ID
     * @param edrtTc IT포탈 전결권 구분 코드
     * @param abusCone 사업내용
     * @param cpnSafCone 회사현황내용
     * @param abusNcsCone 사업필요성내용
     * @param dgogPpoCone 효과성목적내용
     * @param plmDes 문제설명
     * @param abusRngCone 사업범위내용
     * @param mnPrgCone 주요진행내용
     * @param hrfPlnCone 향후계획내용
     * @param bzDttNm 업무구분명
     * @param sklTpTc 기술분야명
     * @param cstTpTc 고객유형구분코드명
     * @param dplYn 중복여부
     * @param flfFsgDt 이행완료일자
     * @param rprStsTc 보고상태구분코드
     * @param exePttYn 실행가능성여부
     * @param bseYy 기준연도
     * @param prlmHrkOgzCCone 인사상위조직코드내용
     * @param odnYn 경상여부
     * @param abusTc 사업구분코드
     * @param cncdRfrNo 관련참조번호
     */
    public void update(
            String abusNm,
            String bzTpC,
            String svnDpmC,
            String dvmDpmC,
            LocalDate sttDtm,
            LocalDate endDtm,
            String usid,
            String dvmUsid,
            String tlrUsid,
            String dvmTlrUsid,
            String edrtTc,
            String abusCone,
            String cpnSafCone,
            String abusNcsCone,
            String dgogPpoCone,
            String plmDes,
            String abusRngCone,
            String mnPrgCone,
            String hrfPlnCone,
            String bzDttNm,
            String sklTpTc,
            String cstTpTc,
            String dplYn,
            String flfFsgDt,
            String rprStsTc,
            String exePttYn,
            String bseYy,
            String prlmHrkOgzCCone,
            String odnYn,
            String abusTc,
            String cncdRfrNo) {
        this.abusNm = abusNm;
        this.bzTpC = bzTpC;
        this.svnDpmC = svnDpmC;
        this.dvmDpmC = dvmDpmC;
        this.sttDtm = sttDtm;
        this.endDtm = endDtm;
        this.usid = usid;
        this.dvmUsid = dvmUsid;
        this.tlrUsid = tlrUsid;
        this.dvmTlrUsid = dvmTlrUsid;
        this.edrtTc = edrtTc;
        this.abusCone = abusCone;
        this.cpnSafCone = cpnSafCone;
        this.abusNcsCone = abusNcsCone;
        this.dgogPpoCone = dgogPpoCone;
        this.plmDes = plmDes;
        this.abusRngCone = abusRngCone;
        this.mnPrgCone = mnPrgCone;
        this.hrfPlnCone = hrfPlnCone;
        this.bzDttNm = bzDttNm;
        this.sklTpTc = sklTpTc;
        this.cstTpTc = cstTpTc;
        this.dplYn = dplYn;
        this.flfFsgDt = flfFsgDt;
        this.rprStsTc = rprStsTc;
        this.exePttYn = exePttYn;
        this.bseYy = bseYy;
        this.prlmHrkOgzCCone = prlmHrkOgzCCone;
        this.odnYn = odnYn;
        this.abusTc = CodeDefaults.orNotApplicable(abusTc);
        this.cncdRfrNo = cncdRfrNo;
    }

    /**
     * 담당자 기준 주관팀코드(SVN_TEM_C)/개발팀코드(DVM_TEM_C) 설정.
     *
     * <p>주관부서담당자(USID)/IT부서담당자(DVM_USID)의 소속 팀코드를 스냅샷으로 채웁니다. 생성·수정 시 담당자가 바뀌면 함께 갱신하며, 이름 스냅샷은
     * {@link #assignSvnOrgNames}로 저장합니다. 변경 로그 스냅샷이 값을 복사하도록 반드시 INSERT/UPDATE flush 이전에 호출합니다.
     *
     * @param svnTemC 주관부서담당자 소속 팀코드 (담당자 미지정·미조회 시 null)
     * @param dvmTemC IT부서담당자 소속 팀코드 (담당자 미지정·미조회 시 null)
     */
    public void assignTeamCodes(String svnTemC, String dvmTemC) {
        this.svnTemC = svnTemC;
        this.dvmTemC = dvmTemC;
    }

    /**
     * 주관부서명/주관팀명 스냅샷 설정.
     *
     * <p>현재 엔티티에 설정된 주관부서코드/주관팀코드에 대응하는 조직명을 저장합니다. 코드가 설정/변경되는 지점(생성·수정) 직후, INSERT/UPDATE flush
     * 이전에 호출합니다.
     *
     * @param svnDpmNm 주관부서명 (코드 미등록 시 null 허용)
     * @param svnTemNm 주관팀명 (코드 미등록 시 null 허용)
     */
    public void assignSvnOrgNames(String svnDpmNm, String svnTemNm) {
        this.svnDpmNm = svnDpmNm;
        this.svnTemNm = svnTemNm;
    }

    /**
     * 담당팀장명/담당자명 스냅샷 설정.
     *
     * <p><b>해석에 실패하면 기존 값을 유지합니다.</b> 담당자가 퇴사하면 {@code TPRMPP_CUSERI} 조인이 비는데, 그때 null로 덮으면
     * 이 컬럼을 둔 이유(퇴사자 이름 보존)가 사라집니다. 조인으로는 되살릴 수 없는 값이므로 한 번 채운 뒤에는 덮어쓰지 않는 쪽이 안전합니다.
     *
     * <p>{@link #assignSvnOrgNames}와 같은 자리(생성·수정 직후, flush 이전)에서 호출합니다.
     *
     * @param tlrNm 담당팀장명. 해석 실패 시 null — 기존 값을 유지합니다
     * @param usrNm 담당자명. 해석 실패 시 null — 기존 값을 유지합니다
     */
    public void assignPersonNames(String tlrNm, String usrNm) {
        if (tlrNm != null && !tlrNm.isBlank()) this.tlrNm = tlrNm;
        if (usrNm != null && !usrNm.isBlank()) this.usrNm = usrNm;
    }

    /**
     * 사업 단위 금액 스냅샷 설정.
     *
     * <p>총 예산·익년 이후 예산은 품목 저장이 끝난 뒤의 합계이고, 기 지급예산은 사용자 입력값이다. 세 값이 항상 같은 시점을 가리키도록 한 번에 설정한다.
     *
     * @param totRqmAmt 총 예산 (활성 품목 AMT 합계)
     * @param mplAmt 예산연도+1 이후 예산 (활성 품목 MPL_AMT 합계)
     * @param dfrAmt 기 지급예산 (검증을 통과한 값)
     */
    public void assignAmountSnapshot(BigDecimal totRqmAmt, BigDecimal mplAmt, BigDecimal dfrAmt) {
        this.totRqmAmt = totRqmAmt;
        this.mplAmt = mplAmt;
        this.dfrAmt = dfrAmt;
    }

    /**
     * 드롭다운/참조용 경량 DTO.
     *
     * <p>{@link Imported} 어노테이션은 JPQL {@code new} 생성자 표현식에서 짧은 이름({@code new Ref(...)})으로 참조하기 위해
     * 필요합니다. Hibernate HQL 파서는 nested class를 FQN의 점 표기로 해석하지 못하므로, {@code Bprojm.Ref}를 그대로 사용하려면
     * {@code Bprojm$Ref} 표기 또는 {@code @Imported} 등록이 필요합니다.
     *
     * @param code 프로젝트관리번호 (ABUS_MNG_NO)
     * @param name 사업명 (ABUS_NM)
     */
    @Imported
    public record Ref(String code, String name) {}
}
