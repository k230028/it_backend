package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;

/** 공개 고정 소수 문자열과 해시용 BigDecimal payload의 왕복 변환을 한 곳에 둔다. */
public final class ItBudgetSnapshotCodec {
    private ItBudgetSnapshotCodec() {}

    /** 내부 숫자의 scale과 null을 보존한다. 직렬화할 수 없는 모델은 변환 오류로 실패한다. */
    public static ItBudgetApprovalDto.Payload toPublic(
            ObjectMapper mapper, ItBudgetCanonicalJson canonical, ItBudgetSnapshot.Payload payload)
            throws JsonProcessingException {
        return mapper.readValue(canonical.write(payload), ItBudgetApprovalDto.Payload.class);
    }

    /** 검증된 공개 payload를 원래 숫자 토큰의 해시 모델로 복원한다. 문자열 JSON 자체를 해시하지 않는다. */
    public static ItBudgetSnapshot.Payload toInternal(
            ObjectMapper mapper, ItBudgetApprovalDto.Payload payload) {
        return mapper.convertValue(payload, ItBudgetSnapshot.Payload.class);
    }
}
