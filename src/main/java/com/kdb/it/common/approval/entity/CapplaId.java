package com.kdb.it.common.approval.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공통신청서관계 물리 기본키: (신청서식별번호, 신청서일련번호). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CapplaId implements Serializable {

    private String apfDcmNo;
    private Long apfSno;
}
