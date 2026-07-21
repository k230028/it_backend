package com.kdb.it.domain.payment.repository;

import java.math.BigDecimal;

/** 회차별 지급 응답에 필요한 스칼라 값만 담는 행입니다. */
public record PaymentLineView(
        Integer dfrTod, BigDecimal dfrAmt, String dfrDt, String dfrMplDt, String opnnCone) {}
