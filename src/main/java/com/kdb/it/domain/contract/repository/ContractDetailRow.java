package com.kdb.it.domain.contract.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 입찰계약 상세 조회에 필요한 스칼라 값만 담는 행입니다. */
public record ContractDetailRow(
        String docMngNo,
        Integer docVrsSno,
        String ioeC,
        String cncdRfrNo,
        String tgtNm,
        String stsTc,
        String reqCone,
        String itPtlCttManrC,
        String cttManrRsn,
        String cttNm,
        BigDecimal cttAmt,
        String cttOppNm,
        String cttDt,
        String reqUsid,
        LocalDateTime reqDtm) {}
