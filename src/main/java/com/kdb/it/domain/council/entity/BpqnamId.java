package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전 질의응답 물리 기본키: (협의회ID, 질의응답ID). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BpqnamId implements Serializable {

    private String itPtlAsctId;
    private String qtnId;
}
