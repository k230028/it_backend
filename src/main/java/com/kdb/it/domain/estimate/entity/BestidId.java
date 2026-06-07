package com.kdb.it.domain.estimate.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소요예산 산정 명세(Bestid) 복합 기본키. (문서번호 + 버전 + 팀 + 비목) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BestidId implements Serializable {

    private String rqmBgReqDocNo;

    private Integer docVrsSno;

    private String svnTemC;

    private String ioeC;
}
