package com.kdb.it.domain.payment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대금지급 명세(Bpaymt) 복합 기본키. (문서번호 + 버전 + 지급회차) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BpaymtId implements Serializable {
    private String docMngNo;
    private Integer docVrsSno;
    private Integer dfrTod;
}
