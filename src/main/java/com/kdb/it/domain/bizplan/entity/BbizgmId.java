package com.kdb.it.domain.bizplan.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사업품목 기본 복합키 (ABUS_MNG_NO + SNO). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BbizgmId implements Serializable {
    private String abusMngNo;
    private Integer sno;
}
