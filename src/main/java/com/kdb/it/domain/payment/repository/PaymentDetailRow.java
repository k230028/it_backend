package com.kdb.it.domain.payment.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 대금지급 상세 조회에 필요한 스칼라 값만 담는 행입니다. */
public record PaymentDetailRow(
        String docMngNo,
        Integer docVrsSno,
        String ioeC,
        String cncdRfrNo,
        String tgtNm,
        String stsTc,
        String reqCone,
        String cttNm,
        BigDecimal cttAmt,
        String reqUsid,
        LocalDateTime reqDtm
) {}
