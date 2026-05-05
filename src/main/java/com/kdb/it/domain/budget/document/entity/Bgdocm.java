package com.kdb.it.domain.budget.document.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BgdocmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 가이드 문서 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_BGDOCM}
 * </p>
 *
 * <p>
 * IT 포털의 가이드 문서를 관리합니다.
 * </p>
 *
 * <p>
 * 관리번호 형식: {@code GDOC-{연도}-{4자리 시퀀스}} (예: {@code GDOC-2026-0001})
 * </p>
 */
@LogTarget(entity = BgdocmL.class)
@Entity
@Table(name = "TAAABB_BGDOCM", comment = "가이드 문서")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bgdocm extends BaseEntity {

    /** 문서관리번호: 기본키 (예: GDOC-2026-0001) */
    @Id
    @Column(name = "DOC_MNG_NO", nullable = false, length = 32, comment = "문서관리번호")
    private String docMngNo;

    /** 문서명: 가이드 문서의 제목 (최대 200자) */
    @Column(name = "DOC_NM", length = 200, comment = "문서명")
    private String docNm;

    /** 문서내용: 가이드 문서 상세 내용 (BLOB, HTML 포함 가능) */
    @Lob
    @Column(name = "DOC_CONE", comment = "문서내용")
    private byte[] docCone;

    /**
     * 가이드 문서 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다.
     * </p>
     *
     * @param docNm  문서명
     * @param docCone 문서내용 (BLOB)
     */
    public void update(String docNm, byte[] docCone) {
        this.docNm = docNm;
        this.docCone = docCone;
    }
}
