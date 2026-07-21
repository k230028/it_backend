package com.kdb.it.domain.deliberation.repository;

import java.time.LocalDateTime;

/** 과업심의 상세 조회에 필요한 스칼라 값만 담는 행입니다. */
public record DeliberationDetailRow(
        String docMngNo,
        Integer docVrsSno,
        String ioeC,
        String cncdRfrNo,
        String tgtNm,
        String stsTc,
        String reqCone,
        String taskDbrTc,
        String taskDbrRltTc,
        String taskDbrDt,
        String taskDbrTod,
        String taskDbrOmtYn,
        String taskDbrOmtRsn,
        String opnnCone,
        String apvTrdnRsnCone,
        String reqUsid,
        LocalDateTime reqDtm) {}
