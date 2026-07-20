package com.kdb.it.domain.budget.document.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BrivgmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static jakarta.persistence.GenerationType.SEQUENCE;

/**
 * 문서 검토의견 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_BRIVGM}
 * </p>
 *
 * <p>
 * IT 프로젝트 관련 문서({@link Brdocm} 등)에 대한 검토의견(리뷰 코멘트)을 관리합니다.
 * </p>
 *
 * <p>
 * 의견유형({@code IVG_TP}):
 * </p>
 * <ul>
 * <li>{@code I}: 인라인 코멘트 — Tiptap 표시 ID 및 인용내용({@code IDC_ID}, {@code QOT_CONE})을 사용</li>
 * <li>{@code G}: 전반(General) 코멘트 — 문서 전체에 대한 의견</li>
 * </ul>
 */
@LogTarget(entity = BrivgmL.class)
@Entity
@Table(name = "TPRMPP_BRIVGM", comment = "문서 검토의견")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brivgm extends BaseEntity {

    /** 의견일련번호: Oracle 시퀀스 SEQ_BRIVGM에서 자동 채번 */
    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = "brivgm_seq")
    @SequenceGenerator(name = "brivgm_seq", sequenceName = "SEQ_BRIVGM", allocationSize = 1)
    @Column(name = "IPM_OPNN_SNO", nullable = false, precision = 9, comment = "의견일련번호 (물리컬럼 IPM_OPNN_SNO=개선의견일련번호)")
    private Long ipmOpnnSno;

    /** 문서관리번호: {@link Brdocm#getDocMngNo()} 참조 (예: DOC-2026-0001) */
    @Column(name = "DOC_MNG_NO", length = 20, nullable = false, comment = "문서관리번호")
    private String docMngNo;

    /**
     * 문서버전: {@link Brdocm#getDocVrsSno()} 참조.
     * 물리 컬럼은 Oracle {@code NUMBER(9,0)}(정수)이며, Brdocm과 동일하게 화면 소수 버전 × 100을
     * 정수로 저장합니다. (예: 화면 1.01 → 저장 101). 변환은
     * {@link com.kdb.it.domain.budget.document.util.DocVersionCodec} 참조.
     */
    @Column(name = "DOC_VRS_SNO", precision = 9, scale = 0, nullable = false, comment = "문서버전 (물리컬럼 DOC_VRS_SNO=문서버전일련번호, NUMBER(9,0) 정수저장=화면버전×100)")
    private BigDecimal docVrsSno;

    /** 의견유형: {@code I}=인라인, {@code G}=전반 */
    @Column(name = "IT_PTL_RPL_OPNN_TC", length = 2, nullable = false, comment = "IT포탈회신의견구분코드")
    private String itPtlRplOpnnTc;

    /** 의견내용: 리뷰 코멘트 본문 (VARCHAR2(2000)) */
    @Column(name = "IVG_OPNN_CONE", length = 2000, comment = "의견내용 (물리컬럼 IVG_OPNN_CONE=검토의견내용)")
    private String ivgOpnnCone;

    /** 표시ID: 인라인 코멘트 에디터 하이라이트 매핑 키 (최대 14자) */
    @Column(name = "RFR_ID", length = 14, comment = "표시ID (물리컬럼 RFR_ID=참조ID)")
    private String rfrId;

    /** 인용내용: 인라인 코멘트 선택 텍스트 스냅샷 */
    @Column(name = "RFR_CONE", length = 4000, comment = "인용내용 (물리컬럼 RFR_CONE=참조내용)")
    private String rfrCone;

    /** 완료여부: {@code N}=미완료(기본값), {@code Y}=완료 */
    @Column(name = "FSG_YN", length = 1, nullable = false, comment = "완료여부")
    private String fsgYn;

    /**
     * INSERT 시점 기본값 초기화 콜백
     *
     * <p>
     * {@link BaseEntity#prePersist()}가 {@code delYn}, {@code guid}, {@code guidPrgSno}를
     * 초기화하는 것과 별개로, 본 엔티티 고유 필드({@code ivgSno}, {@code fsgYn})의 기본값을 설정합니다.
     * </p>
     *
     * <p>
     * JPA 규약상 부모 클래스의 {@code @PrePersist}와 자식 클래스의 {@code @PrePersist}는
     * 메서드 이름이 다를 경우 모두 호출됩니다(부모 먼저 → 자식).
     * </p>
     */
    @PrePersist
    private void prePersistBrivgm() {
        if (this.fsgYn == null) {
            this.fsgYn = "N";
        }
    }

    /**
     * 검토의견 생성 팩토리 메서드. 의견일련번호는 영속화 시 SEQ_BRIVGM에서 자동 채번됩니다.
     *
     * @param docMngNo    대상 문서관리번호
     * @param docVrsSno   대상 문서버전
     * @param itPtlRplOpnnTc   의견유형 ({@code I}=인라인, {@code G}=전반)
     * @param ivgOpnnCone 의견내용 (CLOB)
     * @param rfrId       인라인 전용 Tiptap 표시 ID (전반 코멘트의 경우 {@code null})
     * @param rfrCone     인라인 전용 인용내용 (전반 코멘트의 경우 {@code null})
     * @return 영속화 전 상태의 {@link Brivgm} 인스턴스
     */
    public static Brivgm create(String docMngNo, BigDecimal docVrsSno,
                                String itPtlRplOpnnTc, String ivgOpnnCone,
                                String rfrId, String rfrCone) {
        Brivgm b = new Brivgm();
        b.docMngNo = docMngNo;
        b.docVrsSno = docVrsSno;
        b.itPtlRplOpnnTc = itPtlRplOpnnTc;
        b.ivgOpnnCone = ivgOpnnCone;
        b.rfrId = rfrId;
        b.rfrCone = rfrCone;
        // 완료여부 기본값을 생성 시점에 설정한다.
        // BaseEntity의 @EntityListeners(ChangeLogEntityListener)는 JPA 규약상
        // 엔티티 자신의 @PrePersist(prePersistBrivgm)보다 먼저 실행되어,
        // 감사 로그(BrivgmL) 스냅샷 시점에는 fsgYn이 아직 null이다.
        // 로그 테이블 TPRMPP_BRIVGL.FSG_YN(NOT NULL) 위반(ORA-01400)을 막기 위해
        // 팩토리에서 미리 'N'으로 채운다. @PrePersist는 방어적 폴백으로 유지한다.
        b.fsgYn = "N";
        return b;
    }

    /**
     * 검토의견 완료 처리
     *
     * <p>
     * {@code FSG_YN}을 {@code 'Y'}로 변경합니다. JPA Dirty Checking으로 트랜잭션 커밋 시 반영됩니다.
     * </p>
     */
    public void resolve() {
        this.fsgYn = "Y";
    }
}

