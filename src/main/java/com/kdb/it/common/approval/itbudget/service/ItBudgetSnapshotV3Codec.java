package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshotV3;

/** v3 내부 숫자 모델과 공개 고정 소수 문자열 계약을 변환한다. */
public final class ItBudgetSnapshotV3Codec {
    private ItBudgetSnapshotV3Codec() {}

    /** 표시 금액만 고정 scale 문자열로 바꾸고 원장 column 값의 캡처 타입과 값을 보존한다. */
    public static ItBudgetSnapshotV3Dto.Payload toPublic(
            ObjectMapper mapper,
            ItBudgetCanonicalJson canonical,
            ItBudgetSnapshotV3.Payload payload)
            throws JsonProcessingException {
        return mapper.readValue(canonical.write(payload), ItBudgetSnapshotV3Dto.Payload.class);
    }
}
