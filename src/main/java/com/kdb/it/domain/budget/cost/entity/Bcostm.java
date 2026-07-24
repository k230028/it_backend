package com.kdb.it.domain.budget.cost.entity;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BcostmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 전산관리비(IT 관리비) 마스터 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BCOSTM}
 *
 * <p>전산관리비는 IT 인프라 유지보수, 소프트웨어 라이선스, 클라우드 서비스 등 IT 운영과 관련된 정기 지출 항목을 관리합니다.
 *
 * <p>복합키 구조: ({@code BG_NO}, {@code BG_SNO}) 동일 관리번호에 여러 버전(일련번호)이 존재할 수 있으며, {@code LST_YN='Y'}인
 * 레코드가 현재 유효한 버전입니다.
 *
 * <p>공통 감사 정보({@link BaseEntity})를 상속하여 생성일시, 수정일시 등을 자동 관리합니다.
 */
@LogTarget(entity = BcostmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TPRMPP_BCOSTM", comment = "전산관리비(IT 관리비) 마스터") // 매핑할 DB 테이블명
@IdClass(BcostmId.class) // 복합키 클래스 지정
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bcostm extends BaseEntity {

    /** 전산업무비코드(IT관리비관리번호): 복합 기본키의 첫 번째 컬럼 (예: COST_2026_0001) */
    @Id
    @Column(name = "BG_NO", nullable = false, length = 15, comment = "전산업무비코드 (물리컬럼 BG_NO=예산번호)")
    private String costBgNo;

    /** 전산업무비일련번호(IT관리비일련번호): 복합 기본키의 두 번째 컬럼 (버전 구분용, 1부터 시작) */
    @Id
    @Column(name = "BG_SNO", nullable = false, precision = 9, comment = "예산일련번호")
    private Integer bgSno;

    /** 최종여부: 'Y'=현재 유효한 레코드, 'N'=이전 버전 레코드 */
    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    /** 비목코드 */
    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    /** 계약명: 실제 계약서상의 명칭 (예: 2026년 서버 유지보수 계약) */
    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    /** 계약상대처: 계약 상대방 업체명 (예: (주)IT솔루션) */
    @Column(name = "CTT_OPP_NM", length = 100, comment = "계약상대처명")
    private String cttOppNm;

    /** 전산업무비예산: 해당 항목의 연간 예산 금액 (최대 18자리, 소수점 3자리) */
    @Column(name = "AMT", precision = 18, scale = 3, comment = "전산업무비예산금액 (물리컬럼 AMT=금액)")
    private BigDecimal costTotXpAmt;

    /** 지급주기코드: 비용 지급 주기 코드 (예: 매월, 분기, 반기, 연간) */
    @Column(name = "DFR_CLE_C", length = 1, nullable = false, comment = "지급주기코드")
    private String dfrCleC;

    /** 지급예정월(최초지급일자): 첫 번째 지급 예정 날짜 (YYYYMMDD, 8자리) */
    @Column(name = "FST_DFR_DT", length = 8, comment = "지급예정월 (물리컬럼 FST_DFR_DT=최초지급일자)")
    private String fstDfrDt;

    /** 통화: 비용 통화 코드 (예: KRW, USD, EUR) */
    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    /** 환율: 외화 계약 시 적용 환율 (최대 9자리, 소수점 이하 4자리) */
    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    /** 환율기준일자: 환율을 적용한 기준 날짜 (YYYYMMDD, 8자리) */
    @Column(name = "XCR_BSE_DT", length = 8, comment = "환율기준일자")
    private String xcrBseDt;

    /** 정보보호여부: 정보보호 관련 항목 여부 (Y/N) */
    @Column(
            name = "SECT_SYS_UTZ_YN",
            length = 1,
            comment = "정보보호여부 (물리컬럼 SECT_SYS_UTZ_YN=보안시스템운용여부)")
    private String sectSysUtzYn;

    /** 증감사유: 전년 대비 예산 증감 이유 (최대 200자) */
    @Column(name = "IND_RSN", length = 200, comment = "증감사유")
    private String indRsn;

    /** 담당자: 해당 비용 항목의 담당자 사번 또는 이름 (최대 14자) */
    @Column(name = "CGPR_ID", length = 14, comment = "담당자행번 (물리컬럼 CGPR_ID=담당자ID)")
    private String cgprId;

    /** 인사상위조직: 해당 비용 항목 작성자 소속 조직의 상위조직코드내용 (신규 생성 시 작성자 기준 자동 설정, 최대 100자) */
    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 100, comment = "인사상위조직코드내용")
    private String prlmHrkOgzCCone;

    /** 담당부서: 해당 비용 항목의 담당 부서 코드 (최대 20자) */
    @Column(name = "SVN_DPM_C", length = 20, comment = "담당부서코드 (물리컬럼 SVN_DPM_C=주관부서코드)")
    private String costSvnDpmC;

    /** 담당팀: 해당 비용 항목의 담당 팀 코드 (최대 5자) */
    @Column(name = "SVN_TEM_C", length = 5, comment = "담당팀코드 (물리컬럼 SVN_TEM_C=주관팀코드)")
    private String svnTemC;

    /** 주관부서명: 코드 설정 시점 CORGNI 조회 스냅샷 (조직명 변경 시에도 과거 기록 유지, 최대 100자) */
    @Column(name = "SVN_DPM_NM", length = 100, comment = "주관부서명")
    private String svnDpmNm;

    /** 주관팀명: 코드 설정 시점 CORGNI 조회 스냅샷 (최대 100자) */
    @Column(name = "SVN_TEM_NM", length = 100, comment = "주관팀명")
    private String svnTemNm;

    /** 예산연도 (4자리 숫자, 예: 2026) */
    @Column(name = "BSE_YY", length = 4, comment = "예산연도 (물리컬럼 BSE_YY=기준연도)")
    private String bseYy;

    /** 사업코드 (최대 3자) */
    @Column(name = "BG_UNT_ABUS_C", length = 3, comment = "사업코드 (물리컬럼 BG_UNT_ABUS_C=예산단위사업코드)")
    private String bgUntAbusC;

    /** 단말여부: 금융정보단말기 항목 여부 (Y=단말, N=비단말; 구 IT_MNGC_TP→TMN_YN, 1→Y/0→N) */
    @Column(name = "TMN_YN", length = 1, comment = "단말여부")
    private String tmnYn;

    /** 전산업무비구분 (최대 2자) */
    @Column(name = "ABUS_TC", length = 2, nullable = false, comment = "전산업무비구분 (물리컬럼 ABUS_TC=사업구분코드)")
    private String abusTc;

    /** 관련전산업무비번호: 계속항목인 경우 전년도 항목의 관리번호 (최대 30자) */
    @Column(name = "CNCD_RFR_NO", length = 30, comment = "관련전산업무비번호 (물리컬럼 CNCD_RFR_NO=관련참조번호)")
    private String cncdRfrNo;

    /**
     * 외화금액(외화 통화 원금 — 환율 적용 전 값).
     *
     * <p>원화(KRW) 행은 NULL. 외화 행은 사용자 입력 외화 원금이며, 서버 재계산 로직(plan 03/04)에서 {@code costTotXpAmt = fcAmt
     * × xcr}로 환산된다. 참고: CONTEXT.md 결정 B (KRW 행 FC_AMT = NULL).
     */
    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;

    /**
     * 전산관리비 정보 업데이트 메서드
     *
     * <p>JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다. 변경된 필드는 트랜잭션 종료 시 자동으로 DB에 반영됩니다.
     *
     * @param ioeC 비목코드
     * @param cttNm 계약명
     * @param cttOppNm 계약상대처
     * @param costTotXpAmt 전산업무비예산
     * @param dfrCleC 지급주기
     * @param fstDfrDt 지급예정월(최초지급일자)
     * @param curC 통화
     * @param xcr 환율
     * @param xcrBseDt 환율기준일자
     * @param sectSysUtzYn 정보보호여부
     * @param indRsn 증감사유
     * @param cgprId 담당자
     * @param costSvnDpmC 담당부서
     * @param svnTemC 담당팀
     * @param bgUntAbusC 사업코드
     * @param tmnYn 단말여부 (Y=단말, N=비단말)
     * @param abusTc 전산업무비구분
     * @param bseYy 예산연도
     * @param cncdRfrNo 관련전산업무비번호 (계속항목인 경우 전년도 관리번호)
     * @param fcAmt 외화금액 (원화 행은 null, 외화 행은 사용자 입력 외화 원금)
     */
    public void update(
            String ioeC,
            String cttNm,
            String cttOppNm,
            BigDecimal costTotXpAmt,
            String dfrCleC,
            String fstDfrDt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            String sectSysUtzYn,
            String indRsn,
            String cgprId,
            String costSvnDpmC,
            String svnTemC,
            String bgUntAbusC,
            String tmnYn,
            String abusTc,
            String bseYy,
            String cncdRfrNo,
            BigDecimal fcAmt) {
        this.ioeC = ioeC;
        this.cttNm = cttNm;
        this.cttOppNm = cttOppNm;
        this.costTotXpAmt = costTotXpAmt;
        this.dfrCleC = CodeDefaults.orNotApplicable(dfrCleC);
        this.fstDfrDt = fstDfrDt;
        this.curC = curC;
        this.xcr = xcr;
        this.xcrBseDt = xcrBseDt;
        this.sectSysUtzYn = sectSysUtzYn;
        this.indRsn = indRsn;
        this.cgprId = cgprId;
        this.costSvnDpmC = costSvnDpmC;
        this.svnTemC = svnTemC;
        this.bgUntAbusC = bgUntAbusC;
        this.tmnYn = tmnYn;
        this.abusTc = CodeDefaults.orNotApplicable(abusTc);
        this.bseYy = bseYy;
        this.cncdRfrNo = cncdRfrNo;
        this.fcAmt = fcAmt;
    }

    /**
     * 작성자 기준 인사상위조직코드내용(PRLM_HRK_OGZ_C_CONE) 설정.
     *
     * <p>신규 생성 시 작성자(현재 로그인 사용자) 소속 조직의 상위조직코드로 채웁니다. 변경 로그 스냅샷이 값을 복사하도록 반드시 INSERT 이전(save 호출 전)에
     * 호출합니다.
     *
     * @param prlmHrkOgzCCone 작성자 소속 인사상위조직코드내용
     */
    public void assignPrlmHrkOgzCCone(String prlmHrkOgzCCone) {
        this.prlmHrkOgzCCone = prlmHrkOgzCCone;
    }

    /**
     * 주관부서명/주관팀명 스냅샷 설정.
     *
     * <p>현재 엔티티에 설정된 담당부서코드/담당팀코드에 대응하는 조직명을 저장합니다. 코드가 설정/변경되는 지점(생성·수정) 직후, INSERT/UPDATE flush
     * 이전에 호출합니다.
     *
     * @param svnDpmNm 주관부서명 (코드 미등록 시 null 허용)
     * @param svnTemNm 주관팀명 (코드 미등록 시 null 허용)
     */
    public void assignSvnOrgNames(String svnDpmNm, String svnTemNm) {
        this.svnDpmNm = svnDpmNm;
        this.svnTemNm = svnTemNm;
    }
}
