package com.kdb.it.domain.budget.project.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BprojmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정보화사업(IT 프로젝트) 마스터 엔티티
 *
 * <p>
 * DB 테이블: {@code BPROJM}
 * </p>
 *
 * <p>
 * IT 부문의 정보화사업(신규 시스템 도입, 인프라 구축 등) 계획 및 현황을 관리합니다.
 * 품목 정보({@link Bitemm})와 신청서와 연관됩니다.
 * </p>
 *
 * <p>
 * 관리번호 형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})
 * </p>
 *
 * <p>
 * 주의: {@code update()} 메서드가 두 개 존재합니다 (오버로딩).
 * 하나는 {@code prjSno}를 포함하고, 다른 하나는 포함하지 않습니다.
 * </p>
 */
@LogTarget(entity = BprojmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TAAABB_BPROJM", comment = "정보화사업(IT 프로젝트) 마스터") // 매핑할 DB 테이블명
@IdClass(BprojmId.class) // 복합키 클래스 지정 (PRJ_MNG_NO + PRJ_SNO)
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bprojm extends BaseEntity {

    /** 프로젝트관리번호: 기본키 (예: PRJ-2026-0001) */
    @Id
    @Column(name = "PRJ_MNG_NO", nullable = false, length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 프로젝트순번: 복합 기본키의 두 번째 컬럼 (동일 관리번호 내 버전 구분, 1부터 시작) */
    @Id
    @Column(name = "PRJ_SNO", nullable = false, comment = "프로젝트순번")
    private Integer prjSno;

    /** 프로젝트명: 사업의 공식 명칭 (최대 200자) */
    @Column(name = "PRJ_NM", length = 200, comment = "프로젝트명")
    private String prjNm;

    /** 프로젝트유형: 사업의 성격 분류 (최대 100자, 예: 신규개발, 고도화, 유지보수) */
    @Column(name = "PRJ_TP", length = 100, comment = "프로젝트유형")
    private String prjTp;

    /** 주관부서: 사업을 주관하는 업무 부서 코드/명 (최대 100자) */
    @Column(name = "SVN_DPM", length = 100, comment = "주관부서")
    private String svnDpm;

    /** IT부서: 사업을 담당하는 IT 부서 코드/명 (최대 100자) */
    @Column(name = "IT_DPM", length = 100, comment = "IT부서")
    private String itDpm;

    /** 프로젝트예산: 사업 총 예산 금액 (최대 15자리, 소수점 2자리) */
    @Column(name = "PRJ_BG", precision = 15, scale = 2, comment = "프로젝트예산")
    private BigDecimal prjBg;

    /** 익년프로젝트예산: 익년(다음 해) 사업 예산 금액 (최대 15자리, 소수점 2자리) */
    @Column(name = "NYY_PRJ_BG", precision = 15, scale = 2, comment = "익년프로젝트예산")
    private BigDecimal nyyPrjBg;

    /** 시작일자: 사업 개시 예정일 */
    @Column(name = "STT_DT", comment = "시작일자")
    private LocalDate sttDt;

    /** 종료일자: 사업 완료 예정일 */
    @Column(name = "END_DT", comment = "종료일자")
    private LocalDate endDt;

    /** 주관부서담당자: 주관부서 담당자 사번 또는 이름 (최대 32자) */
    @Column(name = "SVN_DPM_CGPR", length = 32, comment = "주관부서담당자")
    private String svnDpmCgpr;

    /** IT부서담당자: IT부서 담당자 사번 또는 이름 (최대 32자) */
    @Column(name = "IT_DPM_CGPR", length = 32, comment = "IT부서담당자")
    private String itDpmCgpr;

    /** 주관부서담당팀장: 주관부서 담당 팀장 사번 또는 이름 (최대 32자) */
    @Column(name = "SVN_DPM_TLR", length = 32, comment = "주관부서담당팀장")
    private String svnDpmTlr;

    /** IT부서담당팀장: IT부서 담당 팀장 사번 또는 이름 (최대 32자) */
    @Column(name = "IT_DPM_TLR", length = 32, comment = "IT부서담당팀장")
    private String itDpmTlr;

    /** 전결권: 결재 전결 권한자 구분 (최대 32자) */
    @Column(name = "EDRT", length = 32, comment = "전결권")
    private String edrt;

    /** 사업설명: 사업의 전반적인 설명 (최대 1000자) */
    @Column(name = "PRJ_DES", length = 1000, comment = "사업설명")
    private String prjDes;

    /** 현황: 현재 업무/시스템의 현황 분석 내용 (최대 1000자) */
    @Column(name = "SAF", length = 1000, comment = "현황")
    private String saf;

    /** 필요성: 사업의 필요성 및 당위성 (최대 1000자) */
    @Column(name = "NCS", length = 1000, comment = "필요성")
    private String ncs;

    /** 기대효과: 사업 완료 후 기대되는 효과 (최대 1000자) */
    @Column(name = "XPT_EFF", length = 1000, comment = "기대효과")
    private String xptEff;

    /** 문제: 현재 문제점 또는 개선이 필요한 사항 (최대 1000자) */
    @Column(name = "PLM", length = 1000, comment = "문제")
    private String plm;

    /** 사업범위: 사업의 대상 범위 및 경계 (최대 1000자) */
    @Column(name = "PRJ_RNG", length = 1000, comment = "사업범위")
    private String prjRng;

    /** 추진경과: 사업 추진 진행 상황 및 경과 내용 (최대 1000자) */
    @Column(name = "PUL_PSG", length = 1000, comment = "추진경과")
    private String pulPsg;

    /** 향후계획: 앞으로의 추진 계획 (최대 1000자) */
    @Column(name = "HRF_PLN", length = 1000, comment = "향후계획")
    private String hrfPln;

    /** 업무구분: 사업이 속하는 업무 영역 구분 (최대 32자, 예: 리테일, 기업금융) */
    @Column(name = "BZ_DTT", length = 32, comment = "업무구분")
    private String bzDtt;

    /** 기술유형: 사업에 적용되는 기술 분류 (최대 32자, 예: 웹, 앱, AI, 빅데이터) */
    @Column(name = "TCHN_TP", length = 32, comment = "기술유형")
    private String tchnTp;

    /** 주요사용자: 시스템의 주요 사용자 그룹 (최대 32자, 예: 내부직원, 고객, 전체) */
    @Column(name = "MN_USR", length = 32, comment = "주요사용자")
    private String mnUsr;

    /** 중복여부: 기존 유사 사업과의 중복 여부 ('Y'=중복, 'N'=미중복) */
    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    /** 의무완료기한: 법적/규정상 반드시 완료해야 하는 기한 */
    @Column(name = "LBL_FSG_TLM", comment = "의무완료기한")
    private LocalDate lblFsgTlm;

    /** 보고상태: 상위 보고 단계의 상태 (최대 32자) */
    @Column(name = "RPR_STS", length = 32, comment = "보고상태")
    private String rprSts;

    /** 최종여부: 현재 유효한 레코드 여부 ('Y'=최신, 'N'=이전 버전) */
    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    /** 프로젝트추진가능성: 사업 추진 가능성 평가 결과 (0~100 정수, NUMBER(3,0)) */
    @Column(name = "PRJ_PUL_PTT", precision = 3, scale = 0, comment = "프로젝트추진가능성")
    private Integer prjPulPtt;

    /** 프로젝트상태: 사업의 현재 진행 상태 (최대 32자, 예: 계획, 진행중, 완료, 취소) */
    @Column(name = "PRJ_STS", length = 32, comment = "프로젝트상태")
    private String prjSts;

    /** 예산연도: 예산 연도 (4자리 숫자, 예: "2026") */
    @Column(name = "BG_YY", length = 4, comment = "예산연도")
    private String bgYy;

    /** 주관본부/부문: 사업을 총괄하는 본부 또는 부문 명칭 (최대 32자) */
    @Column(name = "SVN_HDQ", length = 32, comment = "주관본부")
    private String svnHdq;

    /** 경상여부: 경상사업 여부 ('Y'=경상사업, null 또는 'N'=일반 정보화사업) */
    @Column(name = "ORN_YN", length = 1, comment = "경상여부")
    private String ornYn;

    /** 사업구분: 사업의 신규/계속 여부 (최대 32자, 예: '신규', '계속') */
    @Column(name = "PUL_DTT", length = 32, comment = "사업구분")
    private String pulDtt;

    /**
     * 정보화사업 정보 업데이트 메서드 (prjSno 포함)
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 모든 필드를 변경합니다.
     * 프로젝트 순번(prjSno)도 함께 변경합니다.
     * </p>
     *
     * @param prjNm      프로젝트명
     * @param prjTp      프로젝트유형
     * @param svnDpm     주관부서
     * @param itDpm      IT부서
     * @param prjBg      프로젝트예산
     * @param nyyPrjBg   익년프로젝트예산
     * @param sttDt      시작일자
     * @param endDt      종료일자
     * @param svnDpmCgpr 주관부서담당자
     * @param itDpmCgpr  IT부서담당자
     * @param svnDpmTlr  주관부서담당팀장
     * @param itDpmTlr   IT부서담당팀장
     * @param edrt       전결권
     * @param prjDes     사업설명
     * @param saf        현황
     * @param ncs        필요성
     * @param xptEff     기대효과
     * @param plm        문제
     * @param prjRng     사업범위
     * @param pulPsg     추진경과
     * @param hrfPln     향후계획
     * @param bzDtt      업무구분
     * @param tchnTp     기술유형
     * @param mnUsr      주요사용자
     * @param dplYn      중복여부
     * @param lblFsgTlm  의무완료기한
     * @param rprSts     보고상태
     * @param prjPulPtt  프로젝트추진가능성 (0~100 정수)
     * @param prjSts     프로젝트상태
     * @param bgYy       예산연도
     * @param svnHdq     주관본부/부문
     * @param prjSno     프로젝트순번
     * @param ornYn      경상여부 ('Y'=경상사업, 'N'=일반 정보화사업)
     * @param pulDtt     사업구분 ('신규', '계속')
     */
    public void update(String prjNm, String prjTp, String svnDpm, String itDpm, BigDecimal prjBg,
            BigDecimal nyyPrjBg, LocalDate sttDt, LocalDate endDt, String svnDpmCgpr, String itDpmCgpr,
            String svnDpmTlr, String itDpmTlr, String edrt, String prjDes,
            String saf, String ncs, String xptEff, String plm, String prjRng, String pulPsg,
            String hrfPln, String bzDtt, String tchnTp, String mnUsr, String dplYn,
            LocalDate lblFsgTlm, String rprSts, Integer prjPulPtt, String prjSts, String bgYy, String svnHdq,
            Integer prjSno, String ornYn, String pulDtt) {
        this.prjSno = prjSno;
        this.prjNm = prjNm;
        // ... (나머지 필드 업데이트)
        this.prjTp = prjTp;
        this.svnDpm = svnDpm;
        this.itDpm = itDpm;
        this.prjBg = prjBg;
        this.nyyPrjBg = nyyPrjBg;
        this.sttDt = sttDt;
        this.endDt = endDt;
        this.svnDpmCgpr = svnDpmCgpr;
        this.itDpmCgpr = itDpmCgpr;
        this.svnDpmTlr = svnDpmTlr;
        this.itDpmTlr = itDpmTlr;
        this.edrt = edrt;
        this.prjDes = prjDes;
        this.saf = saf;
        this.ncs = ncs;
        this.xptEff = xptEff;
        this.plm = plm;
        this.prjRng = prjRng;
        this.pulPsg = pulPsg;
        this.hrfPln = hrfPln;
        this.bzDtt = bzDtt;
        this.tchnTp = tchnTp;
        this.mnUsr = mnUsr;
        this.dplYn = dplYn;
        this.lblFsgTlm = lblFsgTlm;
        this.rprSts = rprSts;
        this.prjPulPtt = prjPulPtt;
        this.prjSts = prjSts;
        this.bgYy = bgYy;
        this.svnHdq = svnHdq;
        this.ornYn = ornYn;
        this.pulDtt = pulDtt;
    }

    /**
     * 정보화사업 정보 업데이트 메서드 (prjSno 제외)
     *
     * <p>
     * 프로젝트 순번(prjSno)은 변경하지 않고 나머지 필드만 업데이트합니다.
     * 일반적인 수정 API에서 사용됩니다.
     * </p>
     *
     * @param prjNm      프로젝트명
     * @param prjTp      프로젝트유형
     * @param svnDpm     주관부서
     * @param itDpm      IT부서
     * @param prjBg      프로젝트예산
     * @param nyyPrjBg   익년프로젝트예산
     * @param sttDt      시작일자
     * @param endDt      종료일자
     * @param svnDpmCgpr 주관부서담당자
     * @param itDpmCgpr  IT부서담당자
     * @param svnDpmTlr  주관부서담당팀장
     * @param itDpmTlr   IT부서담당팀장
     * @param edrt       전결권
     * @param prjDes     사업설명
     * @param saf        현황
     * @param ncs        필요성
     * @param xptEff     기대효과
     * @param plm        문제
     * @param prjRng     사업범위
     * @param pulPsg     추진경과
     * @param hrfPln     향후계획
     * @param bzDtt      업무구분
     * @param tchnTp     기술유형
     * @param mnUsr      주요사용자
     * @param dplYn      중복여부
     * @param lblFsgTlm  의무완료기한
     * @param rprSts     보고상태
     * @param prjPulPtt  프로젝트추진가능성 (0~100 정수)
     * @param prjSts     프로젝트상태
     * @param bgYy       예산연도
     * @param svnHdq     주관본부/부문
     * @param ornYn      경상여부 ('Y'=경상사업, 'N'=일반 정보화사업)
     * @param pulDtt     사업구분 ('신규', '계속')
     */
    public void update(String prjNm, String prjTp, String svnDpm, String itDpm, BigDecimal prjBg,
            BigDecimal nyyPrjBg, LocalDate sttDt, LocalDate endDt, String svnDpmCgpr, String itDpmCgpr,
            String svnDpmTlr, String itDpmTlr, String edrt, String prjDes,
            String saf, String ncs, String xptEff, String plm, String prjRng, String pulPsg,
            String hrfPln, String bzDtt, String tchnTp, String mnUsr, String dplYn,
            LocalDate lblFsgTlm, String rprSts, Integer prjPulPtt, String prjSts, String bgYy, String svnHdq,
            String ornYn, String pulDtt) {
        this.prjNm = prjNm;
        this.prjTp = prjTp;
        this.svnDpm = svnDpm;
        this.itDpm = itDpm;
        this.prjBg = prjBg;
        this.nyyPrjBg = nyyPrjBg;
        this.sttDt = sttDt;
        this.endDt = endDt;
        this.svnDpmCgpr = svnDpmCgpr;
        this.itDpmCgpr = itDpmCgpr;
        this.svnDpmTlr = svnDpmTlr;
        this.itDpmTlr = itDpmTlr;
        this.edrt = edrt;
        this.prjDes = prjDes;
        this.saf = saf;
        this.ncs = ncs;
        this.xptEff = xptEff;
        this.plm = plm;
        this.prjRng = prjRng;
        this.pulPsg = pulPsg;
        this.hrfPln = hrfPln;
        this.bzDtt = bzDtt;
        this.tchnTp = tchnTp;
        this.mnUsr = mnUsr;
        this.dplYn = dplYn;
        this.lblFsgTlm = lblFsgTlm;
        this.rprSts = rprSts;
        this.prjPulPtt = prjPulPtt;
        this.prjSts = prjSts;
        this.bgYy = bgYy;
        this.svnHdq = svnHdq;
        this.ornYn = ornYn;
        this.pulDtt = pulDtt;
    }
}
