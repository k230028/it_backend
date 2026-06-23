package com.kdb.it.domain.budget.document.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BrdocmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 요구사항 정의서 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_BRDOCM}
 * </p>
 *
 * <p>
 * IT 프로젝트의 요구사항 정의서를 관리합니다.
 * </p>
 *
 * <p>
 * 관리번호 형식: {@code DOC-{연도}-{4자리 시퀀스}} (예: {@code DOC-2026-0001})
 * </p>
 */
@LogTarget(entity = BrdocmL.class)
@Entity
@Table(name = "TPRMPP_BRDOCM", comment = "요구사항 정의서")
@IdClass(BrdocmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Brdocm extends BaseEntity {

    /** 문서관리번호: 복합 기본키의 첫 번째 컬럼 (예: DOC-2026-0001) */
    @Id
    @Column(name = "DOC_MNG_NO", nullable = false, length = 20, comment = "문서관리번호")
    private String docMngNo;

    /**
     * 문서버전: 복합 기본키의 두 번째 컬럼.
     *
     * <p>물리 컬럼은 Oracle {@code NUMBER(9,0)}(정수)입니다. 소수 버전(0.01 단위)을 그대로 저장하면
     * 절삭되어 PK가 충돌하므로, 화면 소수 버전 × 100을 정수로 저장합니다.
     * (예: 화면 0.01 → 저장 1, 화면 1.00 → 저장 100). 화면 변환은
     * {@link com.kdb.it.domain.budget.document.util.DocVersionCodec} 참조.</p>
     */
    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, precision = 9, scale = 0, comment = "문서버전 (물리컬럼 DOC_VRS_SNO=문서버전일련번호, NUMBER(9,0) 정수저장=화면버전×100)")
    private BigDecimal docVrsSno;

    /** 요구사항명: 요구사항의 제목 (최대 500자) */
    @Column(name = "REQ_TTL", length = 500, comment = "요구사항명 (물리컬럼 REQ_TTL=요청제목)")
    private String reqTtl;

    /** 요구사항정보: 요구사항 상세 내용 (CLOB, HTML 포함 가능) */
    @Lob
    @Column(name = "REDT_CONE_INF", comment = "요구사항정보 (물리컬럼 REDT_CONE_INF=보고서내용정보)")
    private String redtConeInf;

    /** 요구사항구분: 요구사항 분류 코드 (최대 2자) */
    @Column(name = "REQ_DTT_NO", length = 2, comment = "요구사항구분 (물리컬럼 REQ_DTT_NO=요청구분번호)")
    private String reqDttNo;

    /** 업무구분: 업무 영역 분류 코드 (최대 100자) */
    @Column(name = "BZ_DTT_NM", length = 100, comment = "업무구분 (물리컬럼 BZ_DTT_NM=업무구분명)")
    private String bzDttNm;

    /** 완료기한: 요구사항 처리 완료 기한 (YYYYMMDD, 8자리) */
    @Column(name = "RVW_FSG_TLM_DT", length = 8, comment = "완료기한 (물리컬럼 RVW_FSG_TLM_DT=리뷰완료기한일자)")
    private String rvwFsgTlmDt;

    /**
     * 요구사항 정의서 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다.
     * </p>
     *
     * @param reqTtl      요구사항명
     * @param redtConeInf 요구사항정보 (CLOB)
     * @param reqDttNo    요구사항구분
     * @param bzDttNm     업무구분
     * @param rvwFsgTlmDt 완료기한
     */
    public void update(String reqTtl, String redtConeInf, String reqDttNo, String bzDttNm, String rvwFsgTlmDt) {
        this.reqTtl = reqTtl;
        this.redtConeInf = redtConeInf;
        this.reqDttNo = reqDttNo;
        this.bzDttNm = bzDttNm;
        this.rvwFsgTlmDt = rvwFsgTlmDt;
    }

    /**
     * 새 버전 엔티티 생성 메서드
     *
     * <p>
     * 현재 엔티티의 업무 필드({@code reqTtl}, {@code redtConeInf}, {@code reqDttNo},
     * {@code bzDttNm}, {@code rvwFsgTlmDt})를 복제하여 지정된 버전({@code nextVrs})의
     * 새 {@link Brdocm} 인스턴스를 반환합니다.
     * </p>
     *
     * <p>
     * {@link com.kdb.it.domain.entity.BaseEntity#prePersist()} 가 {@code delYn},
     * {@code guid}, {@code guidPrgSno} 및 JPA Auditing 필드({@code fstEnrDtm},
     * {@code fstEnrUsid} 등)를 INSERT 시점에 자동 설정하므로, 이 메서드에서는
     * 해당 필드들을 별도로 지정하지 않습니다.
     * </p>
     *
     * @param nextVrs 새로 생성할 문서버전 (저장용 정수값 = 화면버전 × 100, 예: 화면 1.01 → 101)
     * @return 새 버전의 {@link Brdocm} 인스턴스 (영속화 전 상태)
     */
    public Brdocm newVersion(BigDecimal nextVrs) {
        return Brdocm.builder()
            .docMngNo(this.docMngNo)
            .docVrsSno(nextVrs)
            .reqTtl(this.reqTtl)
            .redtConeInf(this.redtConeInf)
            .reqDttNo(this.reqDttNo)
            .bzDttNm(this.bzDttNm)
            .rvwFsgTlmDt(this.rvwFsgTlmDt)
            .build();
    }
}
