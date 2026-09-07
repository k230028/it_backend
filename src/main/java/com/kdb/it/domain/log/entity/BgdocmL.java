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

/** 가이드 문서(TPRMPP_BGDOCM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BGDOCL", comment = "가이드 문서 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BgdocmL extends BaseLogEntity {

    @Column(name = "DOC_MNG_NO", length = 32, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_TTL_CONE", length = 200, comment = "문서명")
    private String docTtlCone;

    @Column(name = "DOC_DTL_ITM_C", length = 2, comment = "문서상세항목코드")
    private String docDtlItmC;

    @Lob
    @Column(name = "NAC_TXT_INF", comment = "문서정보")
    private String nacTxtInf;
}
