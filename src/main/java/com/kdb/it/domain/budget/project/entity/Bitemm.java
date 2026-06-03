package com.kdb.it.domain.budget.project.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BitemmL;
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
 * 정보화사업 품목 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_BITEMM}
 * </p>
 *
 * <p>
 * 정보화사업({@link Bprojm})에 속하는 개별 도입 품목(소프트웨어, 하드웨어, 서비스 등)을
 * 관리합니다. 하나의 사업에 여러 품목이 존재할 수 있습니다.
 * </p>
 *
 * <p>
 * 복합키 구조: ({@code GCL_MNG_NO}, {@code SNO})
 * </p>
 *
 * <p>
 * 연관 관계: {@code PRJ_MNG_NO} + {@code FNT_TB_CRY_SNO}로 {@link Bprojm}와 연결됩니다.
 * </p>
 */
@LogTarget(entity = BitemmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TPRMPP_BITEMM", comment = "정보화사업 품목") // 매핑할 DB 테이블명
@IdClass(BitemmId.class) // 복합키 클래스 지정
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class Bitemm extends BaseEntity {

    /** 품목관리번호: 복합 기본키의 첫 번째 컬럼 (형식: GCL-{연도}-{4자리 시퀀스}, 예: GCL-2026-0001) */
    @Id
    @Column(name = "GCL_MNG_NO", nullable = false, length = 16, comment = "품목관리번호")
    private String gclMngNo;

    /** 품목일련번호: 복합 기본키의 두 번째 컬럼 (같은 관리번호 내 순번) */
    @Id
    @Column(name = "SNO", nullable = false, precision = 9, comment = "일련번호")
    private Integer sno;

    /** 사업관리번호: 이 품목이 속한 정보화사업의 관리번호 (Bprojm.abusMngNo 참조) */
    @Column(name = "ABUS_MNG_NO", nullable = false, length = 30, comment = "사업관리번호")
    private String abusMngNo;

    /** 원천테이블적재일련번호 (FNT_TB_CRY_SNO). 현재 품목↔정보화사업 연관(Bprojm.sno)을 잇는 순번 용도로 사용 */
    @Column(name = "FNT_TB_CRY_SNO", precision = 10, comment = "원천테이블적재일련번호")
    private Integer fntTbCrySno;

    /** 품목구분: 품목의 카테고리 (예: 소프트웨어, 하드웨어, 서비스, 컨설팅) */
    @Column(name = "IOE_C", length = 7, comment = "품목구분 (물리컬럼 IOE_C=비목코드)")
    private String ioeC;

    /** 품목명: 도입할 품목의 명칭 (예: Oracle DB 라이선스, 서버 장비) */
    @Column(name = "GCL_NM", length = 100, comment = "품목명")
    private String gclNm;

    /** 품목수량: 도입 수량 (최대 10자리 숫자) */
    @Column(name = "QTY", precision = 10, comment = "품목수량 (물리컬럼 QTY=수량)")
    private BigDecimal qty;

    /** 통화코드: 가격 통화 코드 (예: KRW, USD, EUR) */
    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    /** 환율: 외화 품목의 적용 환율 (최대 9자리 수, 소수점 이하 4자리) */
    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    /** 환율기준일자: 환율 적용 기준일 (YYYYMMDD, 8자리) */
    @Column(name = "XCR_BSE_DT", length = 8, comment = "환율기준일자")
    private String xcrBseDt;

    /** 예산근거내용: 이 품목의 예산 산정 근거 또는 참고 자료 (최대 600자) */
    @Column(name = "CNCD_FDTN_CONE", length = 600, comment = "예산근거내용 (물리컬럼 CNCD_FDTN_CONE=관련근거내용)")
    private String cncdFdtnCone;

    /** 추진년월: 품목 도입 예정 년월 (YYYYMM, 6자리) */
    @Column(name = "BSE_YM", length = 6, comment = "추진년월 (물리컬럼 BSE_YM=기준년월)")
    private String bseYm;

    /** 지급주기코드: 비용 지급 주기 코드 (예: 일시, 매월, 분기) */
    @Column(name = "DFR_CLE_C", length = 1, comment = "지급주기코드")
    private String dfrCleC;

    /** 정보보호여부: 이 품목이 정보보호 관련 항목인지 여부 (Y/N) */
    @Column(name = "SECT_SYS_UTZ_YN", length = 1, comment = "정보보호여부 (물리컬럼 SECT_SYS_UTZ_YN=보안시스템운용여부)")
    private String sectSysUtzYn;

    /** 통합인프라여부: 통합인프라(공동 인프라) 관련 항목인지 여부 (Y/N) */
    @Column(name = "ITR_INFR_YN", length = 1, comment = "통합인프라여부")
    private String itrInfrYn;

    /** 최종여부: 'Y'=현재 유효한 레코드, 'N'=이전 버전 레코드 */
    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    /** 품목금액: 이 품목의 총 금액 (수량 × 단가, 최대 15자리) */
    @Column(name = "AMT", precision = 18, scale = 3, comment = "품목금액 (물리컬럼 AMT=금액)")
    private BigDecimal amt;

    /**
     * 외화금액(품목 외화 원금 — 환율 적용 전).
     * <p>
     * 원화(KRW) 행은 NULL. 외화 행은 사용자 입력 외화 원금이며,
     * Service에서 {@code gclAmt = fcAmt × xcr}로 재계산한다 (수량 무관).
     * 참고: CONTEXT.md 결정 B/C.
     * </p>
     */
    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;
}

