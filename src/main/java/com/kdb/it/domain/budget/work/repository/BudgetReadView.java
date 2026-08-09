package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;

/** 예산 요약 조회에 필요한 BBUGTM 최소 필드 프로젝션. */
public interface BudgetReadView {
    String getBgNo();

    Integer getSno();

    String getPkColNm();

    String getFntTbNm();

    String getIoeC();

    BigDecimal getBgDupAmt();

    Integer getAsgRt();
}
