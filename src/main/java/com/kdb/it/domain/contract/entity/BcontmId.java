package com.kdb.it.domain.contract.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 입찰계약 마스터(Bcontm) 복합 기본키. (문서관리번호 + 문서버전일련번호) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BcontmId implements Serializable {
    private String docMngNo;
    private Integer docVrsSno;
}
