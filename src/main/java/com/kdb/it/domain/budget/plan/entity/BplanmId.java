package com.kdb.it.domain.budget.plan.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정보기술부문계획의 부모 계획관리번호와 개정 순번 복합키입니다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BplanmId implements Serializable {

    private String reqDocNo;
    private Integer sno;
}
