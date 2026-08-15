package com.kdb.it.common.i18n.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 언어별구분코드마스터의 복합 기본키입니다. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ClangmId implements Serializable {

    private String tcIdCone;
    private String dttLanC;
    private String tcColNm;
}
