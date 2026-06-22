package com.kdb.it.common.code.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CcodemL;
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

/**
 * 공통코드마스터 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CCODEM} — PK: (CO_C_ID_NM, CDVA_ID, STT_DT)</p>
 * <p>Java 필드명은 기존 명칭을 유지하고 {@code @Column} 매핑만 신규 컬럼에 연결합니다.</p>
 * <p>시작·종료일자(sttDt/endDt)는 {@code VARCHAR2(8)} 'YYYYMMDD' 문자열입니다.</p>
 */
@LogTarget(entity = CcodemL.class)
@Entity
@Table(name = "TPRMPP_CCODEM", comment = "공통코드마스터")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(CcodemId.class)
public class Ccodem extends BaseEntity {

    /** 공통코드ID: 복합 기본키 1 (예: CUR, PRJ_TP). 컬럼 CO_C_ID_NM */
    @Id
    @Column(name = "CO_C_ID_NM", nullable = false, length = 100, comment = "공통코드ID")
    private String cId;

    /** 코드값ID: 복합 기본키 2 (예: 001, STA, END). 컬럼 CDVA_ID */
    @Id
    @Column(name = "CDVA_ID", nullable = false, length = 40, comment = "코드값ID")
    private String cdva;

    /** 코드값명 (예: 개발비, 기계장치, 기타무형자산) */
    @Column(name = "CDVA_NM", length = 200, comment = "코드값명")
    private String cdvaNm;

    /** 시작일자: 복합 기본키 3 (YYYYMMDD). 컬럼 STT_DT */
    @Id
    @Column(name = "STT_DT", nullable = false, length = 8, comment = "시작일자")
    private String sttDt;

    /** 종료일자 (YYYYMMDD). 컬럼 END_DT */
    @Column(name = "END_DT", length = 8, comment = "종료일자")
    private String endDt;

    /** 공통코드명 (Java 필드명 cNm 유지). 컬럼 CO_C_NM */
    @Column(name = "CO_C_NM", length = 100, comment = "공통코드명")
    private String cNm;

    /** 공통코드값약어명 (Java 필드명 cdvaDes 유지). 컬럼 CO_CDVA_ABV_NM */
    @Column(name = "CO_CDVA_ABV_NM", length = 100, comment = "공통코드값약어명")
    private String cdvaDes;

    /** 공통코드값적요 (Java 필드명 cdvaDtl 유지). 컬럼 CO_CDVA_SPS */
    @Column(name = "CO_CDVA_SPS", length = 2000, comment = "공통코드값적요")
    private String cdvaDtl;

    /** 공통코드인스턴스명 (Java 필드명 cTp 유지). 컬럼 CO_C_INTN_NM */
    @Column(name = "CO_C_INTN_NM", length = 200, comment = "공통코드인스턴스명")
    private String cTp;

    /** 공통코드인스턴스내용 (Java 필드명 cTpDes 유지). 컬럼 CO_C_INTN_CONE */
    @Column(name = "CO_C_INTN_CONE", length = 500, comment = "공통코드인스턴스내용")
    private String cTpDes;

    /** 상위코드값ID (Java 필드명 hrkC 유지). 컬럼 HRK_CDVA_ID */
    @Column(name = "HRK_CDVA_ID", length = 40, comment = "상위코드값ID")
    private String hrkC;

    /** 코드순서일련번호 (Java 필드명 cSqn 유지). 컬럼 C_SQN_SNO */
    @Column(name = "C_SQN_SNO", comment = "코드순서일련번호")
    private Integer cSqn;

    /** 공통코드값명 (Java 필드명 cdvaDtlC 유지). 컬럼 CO_CDVA_NM */
    @Column(name = "CO_CDVA_NM", length = 500, comment = "공통코드값명")
    private String cdvaDtlC;

    /**
     * 공통코드 정보 업데이트
     *
     * @param cNm      코드명
     * @param cdvaDes  코드값설명
     * @param cdvaDtl  코드값상세
     * @param cTp      코드타입
     * @param cTpDes   코드타입설명
     * @param hrkC     상위코드
     * @param cSqn     코드순서
     * @param endDt    종료일자 (YYYYMMDD)
     * @param cdvaDtlC 코드값상세코드
     */
    public void update(String cNm, String cdvaDes, String cdvaDtl,
                       String cTp, String cTpDes, String hrkC,
                       Integer cSqn, String endDt, String cdvaDtlC) {
        update(cNm, cdvaDes, cdvaDtl, this.cdvaNm, cTp, cTpDes, hrkC, cSqn, endDt, cdvaDtlC);
    }

    /**
     * 공통코드 정보 업데이트
     *
     * @param cNm      코드명
     * @param cdvaDes  코드값설명
     * @param cdvaDtl  코드값상세
     * @param cdvaNm   코드값명
     * @param cTp      코드타입
     * @param cTpDes   코드타입설명
     * @param hrkC     상위코드
     * @param cSqn     코드순서
     * @param endDt    종료일자 (YYYYMMDD)
     * @param cdvaDtlC 코드값상세코드
     */
    public void update(String cNm, String cdvaDes, String cdvaDtl, String cdvaNm,
                       String cTp, String cTpDes, String hrkC,
                       Integer cSqn, String endDt, String cdvaDtlC) {
        this.cNm      = cNm;
        this.cdvaDes  = cdvaDes;
        this.cdvaDtl  = cdvaDtl;
        this.cdvaNm   = cdvaNm;
        this.cTp      = cTp;
        this.cTpDes   = cTpDes;
        this.hrkC     = hrkC;
        this.cSqn     = cSqn;
        this.endDt    = endDt;
        this.cdvaDtlC = cdvaDtlC;
    }
}
