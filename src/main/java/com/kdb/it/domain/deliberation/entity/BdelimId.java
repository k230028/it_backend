package com.kdb.it.domain.deliberation.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 과업심의 마스터(Bdelim) 복합 기본키. (문서관리번호 + 문서버전일련번호) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BdelimId implements Serializable {
    private String docMngNo;
    private Integer docVrsSno;
}
