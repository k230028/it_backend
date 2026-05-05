package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 가이드 문서(TAAABB_BGDOCM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BGDOCL", comment = "가이드 문서 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BgdocmL extends BaseLogEntity {

    @Column(name = "DOC_MNG_NO", length = 32, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_NM", length = 200, comment = "문서명")
    private String docNm;

    @Lob
    @Column(name = "DOC_CONE", comment = "문서내용")
    private byte[] docCone;
}
