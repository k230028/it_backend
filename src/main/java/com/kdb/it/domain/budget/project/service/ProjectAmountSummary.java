package com.kdb.it.domain.budget.project.service;

import java.math.BigDecimal;

/** 정보화사업 금액 계산 결과를 표현합니다. */
public record ProjectAmountSummary(
        BigDecimal currentRequestAmt,
        BigDecimal plannedAmt,
        BigDecimal paidAmt,
        BigDecimal totalRequiredAmt) {}
