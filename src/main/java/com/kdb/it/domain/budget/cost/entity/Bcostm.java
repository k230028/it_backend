package com.kdb.it.domain.budget.cost.entity;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.util.Utf8ByteLimit;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BcostmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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

    private static final int SNAPSHOT_NAME_MAX_BYTES = 100;

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

    /** 담당자명 스냅샷. 퇴사 후에도 남기기 위해 저장한다(BE-63). */
    @Column(name = "CGPR_NM", length = 100, comment = "담당자명")
    private String cgprNm;

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
    @Column(
            name = "ABUS_TC",
            length = 2,
            nullable = false,
            comment = "전산업무비구분 (물리컬럼 ABUS_TC=사업구분코드)")
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
     * 전산업무비 변경값을 이름이 명시된 단일 명령으로 전달합니다.
     *
     * <p>같은 타입의 필드가 많아 위치 인자로는 값이 뒤바뀌어도 컴파일러가 잡지 못하므로 builder로만 조립합니다.
     *
     * @param ioeC 비목코드
     * @param cttNm 계약명
     * @param cttOppNm 계약상대처명
     * @param costTotXpAmt 전산업무비예산금액
     * @param dfrCleC 지급주기코드 (빈값이면 적용 시 기본값으로 보정)
     * @param fstDfrDt 최초지급일자
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
     * @param abusTc 전산업무비구분 (빈값이면 적용 시 기본값으로 보정)
     * @param bseYy 예산연도
     * @param cncdRfrNo 관련전산업무비번호 (계속항목인 경우 전년도 관리번호)
     * @param fcAmt 외화금액 (원화 행은 null, 외화 행은 사용자 입력 외화 원금)
     */
    @Builder
    public record UpdateCommand(
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
            BigDecimal fcAmt) {}

    /**
     * 명령에 담긴 전산업무비 변경값을 적용합니다.
     *
     * <p>JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다. 변경된 필드는 트랜잭션 종료 시 자동으로 DB에 반영됩니다. 필수 코드인
     * 지급주기코드와 전산업무비구분은 빈값이면 여기에서 기본값으로 보정합니다.
     *
     * @param command 이름이 명시된 전산업무비 변경 명령
     * @throws NullPointerException command가 null인 경우 (어떤 필드도 변경하기 전에 실패)
     */
    public void update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        this.ioeC = command.ioeC();
        this.cttNm = command.cttNm();
        this.cttOppNm = command.cttOppNm();
        this.costTotXpAmt = command.costTotXpAmt();
        this.dfrCleC = CodeDefaults.orNotApplicable(command.dfrCleC());
        this.fstDfrDt = command.fstDfrDt();
        this.curC = command.curC();
        this.xcr = command.xcr();
        this.xcrBseDt = command.xcrBseDt();
        this.sectSysUtzYn = command.sectSysUtzYn();
        this.indRsn = command.indRsn();
        /* 담당자(CGPR_ID)는 빈값 요청이면 기존 값을 유지한다. 이 컬럼은 행번 또는 이름을 담는데,
        행번 미해석 행은 조회 응답에서 행번이 비워져 내려가므로 수정 저장이 빈값을 되돌려 보낸다.
        그대로 덮으면 컬럼에 남아 있던 이름이 삭제된다(사용자 테이블 조인으로 되살릴 수 없음). */
        if (command.cgprId() != null && !command.cgprId().isBlank()) {
            this.cgprId = command.cgprId();
        }
        this.costSvnDpmC = command.costSvnDpmC();
        this.svnTemC = command.svnTemC();
        this.bgUntAbusC = command.bgUntAbusC();
        this.tmnYn = command.tmnYn();
        this.abusTc = CodeDefaults.orNotApplicable(command.abusTc());
        this.bseYy = command.bseYy();
        this.cncdRfrNo = command.cncdRfrNo();
        this.fcAmt = command.fcAmt();
    }

    /**
     * 사업코드를 채웁니다. 편성요구서 종합에만 있는 값이라 편성요청서 반입 경로에서는 비어 있습니다.
     *
     * <p>이미 값이 있으면 덮지 않습니다 — 부서가 적어 낸 값을 종합본이 조용히 바꾸지 않게 합니다.
     *
     * @param bgUntAbusC 사업코드 (최대 3자)
     */
    public void fillBudgetUnitCodeIfAbsent(String bgUntAbusC) {
        if (this.bgUntAbusC == null || this.bgUntAbusC.isBlank()) {
            this.bgUntAbusC = bgUntAbusC;
        }
    }

    /**
     * 작성자 기준 인사상위조직코드내용(PRLM_HRK_OGZ_C_CONE) 설정.
     *
     * <p>신규 생성 시 작성자(현재 로그인 사용자) 소속 조직의 상위조직코드로 채웁니다. 변경 로그 스냅샷이 값을 복사하도록 반드시 INSERT 이전(save 호출 전)에
     * 호출합니다.
     *
     * @param prlmHrkOgzCCone 작성자 소속 인사상위조직코드내용
     */
    /**
     * 연결 단말기가 생겼음을 표시합니다.
     *
     * <p>{@link #update(UpdateCommand)}는 요청에 없는 필드를 null로 덮는 전체 치환이므로, 플래그 하나만 바꾸려고 그 경로를 쓰면 부모의 업무
     * 필드와 단말 행이 함께 사라집니다. 이 메서드는 {@code TMN_YN}만 바꿉니다.
     */
    public void markTerminalLinked() {
        this.tmnYn = "Y";
    }

    public void assignPrlmHrkOgzCCone(String prlmHrkOgzCCone) {
        this.prlmHrkOgzCCone = prlmHrkOgzCCone;
    }

    /**
     * 담당자명 스냅샷 설정.
     *
     * <p>해석에 실패하면(퇴사 등으로 조인이 빔) <b>기존 값을 유지</b>합니다 — 조인으로 되살릴 수 없는 값이라 null로 덮으면 이 컬럼을 둔 이유가
     * 사라집니다(BE-63).
     *
     * @param cgprNm 담당자명. 해석 실패 시 null — 기존 값을 유지합니다
     */
    public void assignCgprName(String cgprNm) {
        if (cgprNm != null && !cgprNm.isBlank()) {
            validateSnapshotName("CGPR_NM", cgprNm);
            this.cgprNm = cgprNm;
        }
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
        validateSnapshotName("SVN_DPM_NM", svnDpmNm);
        validateSnapshotName("SVN_TEM_NM", svnTemNm);
        this.svnDpmNm = svnDpmNm;
        this.svnTemNm = svnTemNm;
    }

    /** 직접 생성·복제된 엔티티도 DB 기록 전에 스냅샷 이름의 BYTE 한도를 검증합니다. */
    @PrePersist
    @PreUpdate
    void validateSnapshotNamesBeforePersist() {
        validateSnapshotName("CGPR_NM", cgprNm);
        validateSnapshotName("SVN_DPM_NM", svnDpmNm);
        validateSnapshotName("SVN_TEM_NM", svnTemNm);
    }

    private static void validateSnapshotName(String columnName, String value) {
        int actualBytes = Utf8ByteLimit.length(value);
        if (actualBytes > SNAPSHOT_NAME_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "전산업무비 "
                            + columnName
                            + "은 UTF-8 기준 100바이트를 초과할 수 없습니다. (현재: "
                            + actualBytes
                            + "바이트)");
        }
    }

    /** 결재 완료본의 모든 업무 값을 보존한 재상신 초안을 만듭니다. */
    public Bcostm createReapplicationDraft(Integer newBgSno) {
        return Bcostm.builder()
                .costBgNo(costBgNo)
                .bgSno(newBgSno)
                .lstYn("N")
                .ioeC(ioeC)
                .cttNm(cttNm)
                .cttOppNm(cttOppNm)
                .costTotXpAmt(costTotXpAmt)
                .dfrCleC(dfrCleC)
                .fstDfrDt(fstDfrDt)
                .curC(curC)
                .xcr(xcr)
                .xcrBseDt(xcrBseDt)
                .sectSysUtzYn(sectSysUtzYn)
                .indRsn(indRsn)
                .cgprId(cgprId)
                .cgprNm(cgprNm)
                .prlmHrkOgzCCone(prlmHrkOgzCCone)
                .costSvnDpmC(costSvnDpmC)
                .svnTemC(svnTemC)
                .svnDpmNm(svnDpmNm)
                .svnTemNm(svnTemNm)
                .bseYy(bseYy)
                .bgUntAbusC(bgUntAbusC)
                .tmnYn(tmnYn)
                .abusTc(abusTc)
                .cncdRfrNo(cncdRfrNo)
                .fcAmt(fcAmt)
                .delYn("N")
                .build();
    }
}
