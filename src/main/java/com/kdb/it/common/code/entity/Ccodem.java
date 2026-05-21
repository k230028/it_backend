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

import java.time.LocalDate;

/**
 * 공통코드마스터 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CCODEM} — PK: (C_ID, CDVA, STT_DT)</p>
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

    /** 코드ID: 복합 기본키 1 (예: CUR, PRJ_TP) */
    @Id
    @Column(name = "C_ID", nullable = false, length = 32, comment = "코드ID")
    private String cId;

    /** 코드값: 복합 기본키 2 (예: 001, STA, END) */
    @Id
    @Column(name = "CDVA", nullable = false, length = 32, comment = "코드값")
    private String cdva;

    /** 코드값명 (예: 개발비, 기계장치, 기타무형자산) */
    @Column(name = "CDVA_NM", length = 100, comment = "코드값명")
    private String cdvaNm;

    /** 시작일자: 복합 기본키 3 */
    @Id
    @Column(name = "STT_DT", nullable = false, comment = "시작일자")
    private LocalDate sttDt;

    /** 종료일자 */
    @Column(name = "END_DT", comment = "종료일자")
    private LocalDate endDt;

    /** 코드명 (구 CDVA 값, 예: 1400, 신규개발) */
    @Column(name = "C_NM", length = 100, comment = "코드명")
    private String cNm;

    /** 코드값설명 (예: 환율, 사업유형) */
    @Column(name = "CDVA_DES", length = 500, comment = "코드값설명")
    private String cdvaDes;

    /** 코드값상세 (구 C_NM, 예: USD) */
    @Column(name = "CDVA_DTL", length = 100, comment = "코드값상세")
    private String cdvaDtl;

    /** 코드타입 (구 CTT_TP rename, (C_ID,CDVA) 내 추가 구분용) */
    @Column(name = "C_TP", length = 100, comment = "코드타입")
    private String cTp;

    /** 코드타입설명 (구 CTT_TP_DES rename) */
    @Column(name = "C_TP_DES", length = 500, comment = "코드타입설명")
    private String cTpDes;

    /** 상위코드: {C_ID}_{CDVA} 합성 문자열 */
    @Column(name = "HRK_C", length = 65, comment = "상위코드")
    private String hrkC;

    /** 코드순서 */
    @Column(name = "C_SQN", comment = "코드순서")
    private Integer cSqn;

    /** 코드값상세코드 (예: 237-0700, 238-0100 등 비목 계정과목코드) */
    @Column(name = "CDVA_DTL_C", length = 8, comment = "코드값상세코드")
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
     * @param endDt    종료일자
     * @param cdvaDtlC 코드값상세코드
     */
    public void update(String cNm, String cdvaDes, String cdvaDtl,
                       String cTp, String cTpDes, String hrkC,
                       Integer cSqn, LocalDate endDt, String cdvaDtlC) {
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
     * @param endDt    종료일자
     * @param cdvaDtlC 코드값상세코드
     */
    public void update(String cNm, String cdvaDes, String cdvaDtl, String cdvaNm,
                       String cTp, String cTpDes, String hrkC,
                       Integer cSqn, LocalDate endDt, String cdvaDtlC) {
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
