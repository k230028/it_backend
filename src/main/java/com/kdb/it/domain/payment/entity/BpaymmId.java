package com.kdb.it.domain.payment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대금지급 마스터(Bpaymm) 복합 기본키. (문서관리번호 + 문서버전일련번호) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BpaymmId implements Serializable {
    private String docMngNo;
    private Integer docVrsSno;
}
