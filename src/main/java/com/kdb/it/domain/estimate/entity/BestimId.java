package com.kdb.it.domain.estimate.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소요예산 산정 마스터(Bestim) 복합 기본키. (문서번호 + 문서버전일련번호) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BestimId implements Serializable {

    @Column(name = "RQM_BG_REQ_DOC_NO", comment = "소요예산요청문서번호")
    private String rqmBgReqDocNo;

    @Column(name = "DOC_VRS_SNO", comment = "문서버전일련번호")
    private Integer docVrsSno;
}
