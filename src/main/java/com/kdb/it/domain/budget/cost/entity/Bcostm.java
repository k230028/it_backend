package com.kdb.it.domain.budget.cost.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BcostmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 전산관리비(IT 관리비) 마스터 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_BCOSTM}
 * </p>
 *
 * <p>
 * 전산관리비는 IT 인프라 유지보수, 소프트웨어 라이선스, 클라우드 서비스 등
 * IT 운영과 관련된 정기 지출 항목을 관리합니다.
 * </p>
 *
 * <p>
 * 복합키 구조: ({@code IT_MNGC_NO}, {@code IT_MNGC_SNO})
 * 동일 관리번호에 여러 버전(일련번호)이 존재할 수 있으며, {@code LST_YN='Y'}인
 * 레코드가 현재 유효한 버전입니다.
 * </p>
 *
 * <p>
 * 공통 감사 정보({@link BaseEntity})를 상속하여 생성일시, 수정일시 등을 자동 관리합니다.
 * </p>
 */
@LogTarget(entity = BcostmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TAAABB_BCOSTM", comment = "전산관리비(IT 관리비) 마스터") // 매핑할 DB 테이블명
@IdClass(BcostmId.class) // 복합키 클래스 지정
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bcostm extends BaseEntity {

    /** 전산업무비코드(IT관리비관리번호): 복합 기본키의 첫 번째 컬럼 (예: COST_2026_0001) */
    @Id
    @Column(name = "IT_MNGC_NO", nullable = false, length = 128, comment = "전산업무비코드")
    private String itMngcNo;

    /** 전산업무비일련번호(IT관리비일련번호): 복합 기본키의 두 번째 컬럼 (버전 구분용, 1부터 시작) */
    @Id
    @Column(name = "IT_MNGC_SNO", nullable = false, comment = "전산업무비일련번호")
    private Integer itMngcSno;

    /** 최종여부: 'Y'=현재 유효한 레코드, 'N'=이전 버전 레코드 */
    @Column(name = "LST_YN", length = 4, comment = "최종여부")
    private String lstYn;

    /** 비목코드 */
    @Column(name = "IOE_C", length = 400, comment = "비목코드")
    private String ioeC;

    /** 계약명: 실제 계약서상의 명칭 (예: 2026년 서버 유지보수 계약) */
    @Column(name = "CTT_NM", length = 800, comment = "계약명")
    private String cttNm;

    /** 계약상대처: 계약 상대방 업체�� (예: (주)IT���루션) */
    @Column(name = "CTT_OPP", length = 400, comment = "계약상대처")
    private String cttOpp;

    /** 전산업무비예산: 해당 항목의 연간 예산 금액 (최대 15자리, 소수점 2자리) */
    @Column(name = "IT_MNGC_BG", precision = 15, scale = 2, comment = "전산업무비예산")
    private BigDecimal itMngcBg;

    /** 지급주기: 비용 지급 주기 (예: 매월, 분기, 반기, 연간) */
    @Column(name = "DFR_CLE", length = 40, comment = "지급주기")
    private String dfrCle;

    /** 지급예정월(최초지급일자): 첫 번째 지급 예정 날짜 */
    @Column(name = "FST_DFR_DT", comment = "지급예정월")
    private LocalDate fstDfrDt;

    /** 통화: 비용 통화 코드 (예: KRW, USD, EUR) */
    @Column(name = "CUR", length = 40, comment = "통화")
    private String cur;

    /** 환율: 외화 계약 시 적용 환율 (최대 9자리) */
    @Column(name = "XCR", precision = 9, comment = "환율")
    private BigDecimal xcr;

    /** 환율기준일자: 환율을 적용한 기준 날짜 */
    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private LocalDate xcrBseDt;

    /** 정보보호여부: 정보보호 관련 항목 여부 (Y/N) */
    @Column(name = "INF_PRT_YN", length = 4, comment = "정보보호여부")
    private String infPrtYn;

    /** 증감사유: 전년 대비 예산 증감 이유 (최대 4000자) */
    @Column(name = "IND_RSN", length = 4000, comment = "증감사유")
    private String indRsn;

    /** 담당자: 해당 비용 항목의 담당자 사번 또는 이름 */
    @Column(name = "CGPR", length = 128, comment = "담당자")
    private String cgpr;

    /** 담당부서: 해당 비용 항목의 담당 부서 코드 */
    @Column(name = "BICE_DPM", length = 100, comment = "담당부서")
    private String biceDpm;

    /** 담당팀: 해당 비용 항목의 담당 팀 코드 */
    @Column(name = "BICE_TEM", length = 100, comment = "담당팀")
    private String biceTem;

    /** 예산연도 (4자리 숫자, 예: 2026) */
    @Column(name = "BG_YY", length = 4, comment = "예산연도")
    private String bgYy;

    /** 사업코드 */
    @Column(name = "ABUS_C", length = 100, comment = "사업코드")
    private String abusC;

    /** 전산업무비유형 */
    @Column(name = "IT_MNGC_TP", length = 100, comment = "전산업무비유형")
    private String itMngcTp;

    /** 전산업무비구분 */
    @Column(name = "PUL_DTT", length = 100, comment = "전산업무비구분")
    private String pulDtt;

    /**
     * 전산관리비 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다.
     * 변경된 필드는 트랜잭션 종료 시 자동으로 DB에 반영됩니다.
     * </p>
     *
     * @param ioeC     비목코드
     * @param cttNm    계약명
     * @param cttOpp   계약상대처
     * @param itMngcBg 전산업무비예산
     * @param dfrCle   지급주기
     * @param fstDfrDt 지급예정월(최초지급일자)
     * @param cur      통화
     * @param xcr      환율
     * @param xcrBseDt 환율기준일자
     * @param infPrtYn 정보보호여부
     * @param indRsn   증감사유
     * @param cgpr     담당자
     * @param biceDpm  담당부서
     * @param biceTem  담당팀
     * @param abusC    사업코드
     * @param itMngcTp 전산업무비유형
     * @param pulDtt   전산업무비구분
     * @param bgYy     예산연도
     */
    public void update(String ioeC, String cttNm, String cttOpp, BigDecimal itMngcBg,
            String dfrCle, LocalDate fstDfrDt, String cur, BigDecimal xcr, LocalDate xcrBseDt,
            String infPrtYn, String indRsn, String cgpr, String biceDpm, String biceTem, String abusC, String itMngcTp, String pulDtt, String bgYy) {
        this.ioeC = ioeC;
        this.cttNm = cttNm;
        this.cttOpp = cttOpp;
        this.itMngcBg = itMngcBg;
        this.dfrCle = dfrCle;
        this.fstDfrDt = fstDfrDt;
        this.cur = cur;
        this.xcr = xcr;
        this.xcrBseDt = xcrBseDt;
        this.infPrtYn = infPrtYn;
        this.indRsn = indRsn;
        this.cgpr = cgpr;
        this.biceDpm = biceDpm;
        this.biceTem = biceTem;
        this.abusC = abusC;
        this.itMngcTp = itMngcTp;
        this.pulDtt = pulDtt;
        this.bgYy = bgYy;
    }
}
