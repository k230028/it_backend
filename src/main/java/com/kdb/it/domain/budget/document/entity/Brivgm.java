package com.kdb.it.domain.budget.document.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BrivgmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 문서 검토의견 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_BRIVGM}
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
@Table(name = "TAAABB_BRIVGM", comment = "문서 검토의견")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brivgm extends BaseEntity {

    /** 의견일련번호: UUID v4 기반 32자 식별자(하이픈 제거) */
    @Id
    @Column(name = "IVG_SNO", length = 32, nullable = false, comment = "의견일련번호")
    private String ivgSno;

    /** 문서관리번호: {@link Brdocm#getDocMngNo()} 참조 (예: DOC-2026-0001) */
    @Column(name = "DOC_MNG_NO", length = 32, nullable = false, comment = "문서관리번호")
    private String docMngNo;

    /** 문서버전: {@link Brdocm#getDocVrs()} 참조 (Oracle NUMBER(5,2), 예: 1.00, 1.01) */
    @Column(name = "DOC_VRS", precision = 5, scale = 2, nullable = false, comment = "문서버전")
    private BigDecimal docVrs;

    /** 의견유형: {@code I}=인라인, {@code G}=전반 */
    @Column(name = "IVG_TP", length = 1, nullable = false, comment = "의견유형")
    private String ivgTp;

    /** 의견내용: 리뷰 코멘트 본문 (VARCHAR2(4000)) */
    @Column(name = "IVG_CONE", length = 4000, comment = "의견내용")
    private String ivgCone;

    /** 표시ID: 인라인 코멘트 에디터 하이라이트 매핑 키 */
    @Column(name = "IDC_ID", length = 64, comment = "표시ID")
    private String idcId;

    /** 인용내용: 인라인 코멘트 선택 텍스트 스냅샷 */
    @Column(name = "QOT_CONE", length = 4000, comment = "인용내용")
    private String qotCone;

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
        // 의견일련번호 자동 생성: null이면 UUID v4 기반 32자 문자열로 초기화 (하이픈 제거)
        if (this.ivgSno == null) {
            this.ivgSno = UUID.randomUUID().toString().replace("-", "");
        }
        // 완료여부 기본값 설정: null이면 'N'(미완료)으로 초기화
        if (this.fsgYn == null) {
            this.fsgYn = "N";
        }
    }

    /**
     * 검토의견 생성 팩토리 메서드
     *
     * @param docMngNo 대상 문서관리번호
     * @param docVrs   대상 문서버전
     * @param ivgTp    의견유형 ({@code I}=인라인, {@code G}=전반)
     * @param ivgCone  의견내용 (CLOB)
     * @param idcId    인라인 전용 Tiptap 표시 ID (전반 코멘트의 경우 {@code null})
     * @param qotCone  인라인 전용 인용내용 (전반 코멘트의 경우 {@code null})
     * @return 영속화 전 상태의 {@link Brivgm} 인스턴스
     */
    public static Brivgm create(String docMngNo, BigDecimal docVrs,
                                String ivgTp, String ivgCone,
                                String idcId, String qotCone) {
        Brivgm b = new Brivgm();
        b.docMngNo = docMngNo;
        b.docVrs = docVrs;
        b.ivgTp = ivgTp;
        b.ivgCone = ivgCone;
        b.idcId = idcId;
        b.qotCone = qotCone;
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

