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
import org.hibernate.annotations.Imported;

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
@Table(name = "TPRMPP_BPROJM", comment = "정보화사업(IT 프로젝트) 마스터") // 매핑할 DB 테이블명
@IdClass(BprojmId.class) // 복합키 클래스 지정 (PRJ_MNG_NO + PRJ_SNO)
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bprojm extends BaseEntity {

    /** 프로젝트관리번호: 기본키 (예: PRJ-2026-0001) */
    @Id
    @Column(name = "ABUS_MNG_NO", nullable = false, length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 프로젝트순번: 복합 기본키의 두 번째 컬럼 (동일 관리번호 내 버전 구분, 1부터 시작) */
    @Id
    @Column(name = "SNO", nullable = false, comment = "프로젝트순번")
    private Integer prjSno;

    /** 프로젝트명: 사업의 공식 명칭 (최대 200자) */
    @Column(name = "PRJ_NM", length = 200, comment = "프로젝트명")
    private String prjNm;

    /** 프로젝트유형: 사업의 성격 분류 (최대 100자, 예: 신규개발, 고도화, 유지보수) */
    @Column(name = "PRJ_BZ_TC", length = 100, comment = "프로젝트유형")
    private String prjTp;

    /** 주관부서: 사업을 주관하는 업무 부서 코드/명 (최대 100자) */
    @Column(name = "SVN_DPM_C", length = 100, comment = "주관부서")
    private String svnDpm;

    /** IT부서: 사업을 담당하는 IT 부서 코드/명 (최대 100자) */
    @Column(name = "DVM_DPM_C", length = 100, comment = "IT부서")
    private String itDpm;

    /** 프로젝트예산: 사업 총 예산 금액 (최대 15자리, 소수점 2자리) */
    @Column(name = "RQM_BG_AMT", precision = 15, scale = 2, comment = "프로젝트예산")
    private BigDecimal prjBg;

    /** 익년프로젝트예산: 익년(다음 해) 사업 예산 금액 (최대 15자리, 소수점 2자리) */
    @Column(name = "MPL_AMT", precision = 15, scale = 2, comment = "익년프로젝트예산")
    private BigDecimal nyyPrjBg;

    /** 시작일자: 사업 개시 예정일 */
    @Column(name = "STT_DTM", comment = "시작일자")
    private LocalDate sttDt;

    /** 종료일자: 사업 완료 예정일 */
    @Column(name = "END_DTM", comment = "종료일자")
    private LocalDate endDt;

    /** 주관부서담당자: 주관부서 담당자 사번 또는 이름 (최대 32자) */
    @Column(name = "SVN_DPM_USID", length = 32, comment = "주관부서담당자")
    private String svnDpmCgpr;

    /** IT부서담당자: IT부서 담당자 사번 또는 이름 (최대 32자) */
    @Column(name = "DVM_USID", length = 32, comment = "IT부서담당자")
    private String itDpmCgpr;

    /** 주관부서담당팀장: 주관부서 담당 팀장 사번 또는 이름 (최대 32자) */
    @Column(name = "SVN_DPM_DCD_USID", length = 32, comment = "주관부서담당팀장")
    private String svnDpmTlr;

    /** IT부서담당팀장: IT부서 담당 팀장 사번 또는 이름 (최대 32자) */
    @Column(name = "TLR_USID", length = 32, comment = "IT부서담당팀장")
    private String itDpmTlr;

    /** 전결권: 결재 전결 권한자 구분 (최대 32자) */
    @Column(name = "EDRT_TC", length = 32, comment = "전결권")
    private String edrt;

    /** 사업설명: 사업의 전반적인 설명 (최대 1000자) */
    @Column(name = "ABUS_CONE", length = 1000, comment = "사업설명")
    private String prjDes;

    /** 현황: 현재 업무/시스템의 현황 분석 내용 (최대 1000자) */
    @Column(name = "CPN_SAF_CONE", length = 1000, comment = "현황")
    private String saf;

    /** 필요성: 사업의 필요성 및 당위성 (최대 1000자) */
    @Column(name = "ABUS_NCS_CONE", length = 1000, comment = "필요성")
    private String ncs;

    /** 기대효과: 사업 완료 후 기대되는 효과 (최대 1000자) */
    @Column(name = "DGOG_PPO_CONE", length = 1000, comment = "기대효과")
    private String xptEff;

    /** 문제: 현재 문제점 또는 개선이 필요한 사항 (최대 1000자) */
    @Column(name = "PLM_DES", length = 1000, comment = "문제")
    private String plm;

    /** 사업범위: 사업의 대상 범위 및 경계 (최대 1000자) */
    @Column(name = "PRJ_TGT_RNG_CONE", length = 1000, comment = "사업범위")
    private String prjRng;

    /** 주요진행내용: 사업 추진 진행 상황 및 경과 내용 (최대 2000자) */
    @Column(name = "MN_PRG_CONE", length = 2000, comment = "주요진행내용")
    private String pulPsg;

    /** 향후계획: 앞으로의 추진 계획 (최대 1000자) */
    @Column(name = "HRF_PLN_CONE", length = 1000, comment = "향후계획")
    private String hrfPln;

    /** 업무구분: 사업이 속하는 업무 영역 구분 (최대 32자, 예: 리테일, 기업금융) */
    @Column(name = "BZ_DTT_NM", length = 32, comment = "업무구분")
    private String bzDtt;

    /** 기술유형: 사업에 적용되는 기술 분류 (최대 32자, 예: 웹, 앱, AI, 빅데이터) */
    @Column(name = "SKL_TP_TC", length = 32, comment = "기술유형")
    private String tchnTp;

    /** 주요사용자: 시스템의 주요 사용자 그룹 (최대 32자, 예: 내부직원, 고객, 전체) */
    @Column(name = "CST_TP_TC", length = 32, comment = "주요사용자")
    private String mnUsr;

    /** 중복여부: 기존 유사 사업과의 중복 여부 ('Y'=중복, 'N'=미중복) */
    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    /** 의무완료기한: 법적/규정상 반드시 완료해야 하는 기한 */
    @Column(name = "FLF_FSG_DT", comment = "의무완료기한")
    private String lblFsgTlm;

    /** 보고상태: 상위 보고 단계의 상태 (최대 32자) */
    @Column(name = "RPR_STS_TC", length = 32, comment = "보고상태")
    private String rprSts;

    /** 최종여부: 현재 유효한 레코드 여부 ('Y'=최신, 'N'=이전 버전) */
    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    /** 프로젝트추진가능성: 공통코드 PRJ_PUL_PTT cdva 값 (VARCHAR2(3), 예: "001", "002") */
    @Column(name = "EXE_PTT_YN", length = 3, comment = "프로젝트추진가능성")
    private String prjPulPtt;

    /** 프로젝트상태: 사업의 현재 진행 상태 (최대 32자, 예: 계획, 진행중, 완료, 취소) */
    @Column(name = "STS_TC", length = 32, comment = "프로젝트상태")
    private String prjSts;

    /** 예산연도: 예산 연도 (4자리 숫자, 예: "2026") */
    @Column(name = "BSE_YY", length = 4, comment = "예산연도")
    private String bgYy;

    /** 주관본부/부문: 사업을 총괄하는 본부 또는 부문 명칭 (최대 32자) */
    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 32, comment = "주관본부")
    private String svnHdq;

    /** 경상여부: 경상사업 여부 ('Y'=경상사업, null 또는 'N'=일반 정보화사업) */
    @Column(name = "ODN_YN", length = 1, comment = "경상여부")
    private String ornYn;

    /** 사업구분: 사업의 신규/계속 여부 (최대 32자, 예: '신규', '계속') */
    @Column(name = "ABUS_TC", length = 32, comment = "사업구분")
    private String pulDtt;

    /** 관련프로젝트관리번호: 계속사업인 경우 전년도 사업의 관리번호 */
    @Column(name = "CNCD_RFR_NO", length = 32, comment = "관련프로젝트관리번호")
    private String cncdPrjMngNo;

    /**
     * 프로젝트 수정 파라미터 레코드 (DB-06)
     *
     * <p>35+ 개별 파라미터를 하나의 레코드로 압축하여 메서드 시그니처 가독성을 개선합니다.
     * {@link ProjectService}의 수정 로직에서 사용합니다.</p>
     */
    public record UpdateCommand(
            String prjNm, String prjTp, String svnDpm, String itDpm,
            BigDecimal prjBg, BigDecimal nyyPrjBg,
            LocalDate sttDt, LocalDate endDt,
            String svnDpmCgpr, String itDpmCgpr, String svnDpmTlr, String itDpmTlr,
            String edrt, String prjDes, String saf, String ncs,
            String xptEff, String plm, String prjRng, String pulPsg, String hrfPln,
            String bzDtt, String tchnTp, String mnUsr, String dplYn,
            String lblFsgTlm, String rprSts, String prjPulPtt, String prjSts,
            String bgYy, String svnHdq,
            String ornYn, String pulDtt, String cncdPrjMngNo
    ) {}

    /**
     * UpdateCommand 레코드로 프로젝트 정보를 업데이트합니다 (prjSno 제외).
     *
     * @param cmd 수정 파라미터 레코드
     */
    public void update(UpdateCommand cmd) {
        update(cmd.prjNm(), cmd.prjTp(), cmd.svnDpm(), cmd.itDpm(),
                cmd.prjBg(), cmd.nyyPrjBg(), cmd.sttDt(), cmd.endDt(),
                cmd.svnDpmCgpr(), cmd.itDpmCgpr(), cmd.svnDpmTlr(), cmd.itDpmTlr(),
                cmd.edrt(), cmd.prjDes(), cmd.saf(), cmd.ncs(),
                cmd.xptEff(), cmd.plm(), cmd.prjRng(), cmd.pulPsg(), cmd.hrfPln(),
                cmd.bzDtt(), cmd.tchnTp(), cmd.mnUsr(), cmd.dplYn(),
                cmd.lblFsgTlm(), cmd.rprSts(), cmd.prjPulPtt(), cmd.prjSts(),
                cmd.bgYy(), cmd.svnHdq(), cmd.ornYn(), cmd.pulDtt(), cmd.cncdPrjMngNo());
    }

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
     * @param prjPulPtt  프로젝트추진가능성 (공통코드 PRJ_PUL_PTT cdva)
     * @param prjSts     프로젝트상태
     * @param bgYy       예산연도
     * @param svnHdq     주관본부/부문
     * @param prjSno     프로젝트순번
     * @param ornYn           경상여부 ('Y'=경상사업, 'N'=일반 정보화사업)
     * @param pulDtt          사업구분 ('신규', '계속')
     * @param cncdPrjMngNo    관련프로젝트관리번호 (계속사업인 경우 전년도 관리번호)
     */
    public void update(String prjNm, String prjTp, String svnDpm, String itDpm, BigDecimal prjBg,
            BigDecimal nyyPrjBg, LocalDate sttDt, LocalDate endDt, String svnDpmCgpr, String itDpmCgpr,
            String svnDpmTlr, String itDpmTlr, String edrt, String prjDes,
            String saf, String ncs, String xptEff, String plm, String prjRng, String pulPsg,
            String hrfPln, String bzDtt, String tchnTp, String mnUsr, String dplYn,
            String lblFsgTlm, String rprSts, String prjPulPtt, String prjSts, String bgYy, String svnHdq,
            Integer prjSno, String ornYn, String pulDtt, String cncdPrjMngNo) {
        this.prjSno = prjSno;
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
        this.cncdPrjMngNo = cncdPrjMngNo;
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
     * @param prjPulPtt  프로젝트추진가능성 (공통코드 PRJ_PUL_PTT cdva)
     * @param prjSts     프로젝트상태
     * @param bgYy       예산연도
     * @param svnHdq     주관본부/부문
     * @param ornYn           경상여부 ('Y'=경상사업, 'N'=일반 정보화사업)
     * @param pulDtt          사업구분 ('신규', '계속')
     * @param cncdPrjMngNo    관련프로젝트관리번호 (계속사업인 경우 전년도 관리번호)
     */
    public void update(String prjNm, String prjTp, String svnDpm, String itDpm, BigDecimal prjBg,
            BigDecimal nyyPrjBg, LocalDate sttDt, LocalDate endDt, String svnDpmCgpr, String itDpmCgpr,
            String svnDpmTlr, String itDpmTlr, String edrt, String prjDes,
            String saf, String ncs, String xptEff, String plm, String prjRng, String pulPsg,
            String hrfPln, String bzDtt, String tchnTp, String mnUsr, String dplYn,
            String lblFsgTlm, String rprSts, String prjPulPtt, String prjSts, String bgYy, String svnHdq,
            String ornYn, String pulDtt, String cncdPrjMngNo) {
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
        this.cncdPrjMngNo = cncdPrjMngNo;
    }

    /**
     * 드롭다운/참조용 경량 DTO.
     *
     * <p>{@link Imported} 어노테이션은 JPQL {@code new} 생성자 표현식에서
     * 짧은 이름({@code new Ref(...)})으로 참조하기 위해 필요합니다.
     * Hibernate HQL 파서는 nested class를 FQN의 점 표기로 해석하지 못하므로,
     * {@code Bprojm.Ref}를 그대로 사용하려면 {@code Bprojm$Ref} 표기 또는
     * {@code @Imported} 등록이 필요합니다.</p>
     */
    @Imported
    public record Ref(String code, String name) {}
}
