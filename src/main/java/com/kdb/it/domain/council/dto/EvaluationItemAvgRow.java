package com.kdb.it.domain.council.dto;

import com.kdb.it.common.util.NativeRowMapper;

import java.math.BigDecimal;

/**
 * 평가 항목별 평균점수 native 결과(2컬럼) DTO.
 *
 * <p>컬럼: [0]=IT_PTL_CKG_ITM_TC(점검항목코드), [1]=AVG(QUEL_RCRD)(평균점수).</p>
 */
public record EvaluationItemAvgRow(String itPtlCkgItmTc, BigDecimal avgScore) {
    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(#6).
     *
     * @param r [0]=항목코드, [1]=평균점수(NUMBER)
     * @return 매핑된 DTO
     */
    public static EvaluationItemAvgRow fromRow(Object[] r) {
        return new EvaluationItemAvgRow(
                NativeRowMapper.toStr(r[0]),
                r[1] == null ? null : new BigDecimal(r[1].toString())); // NUMBER AVG → BigDecimal 정밀 보존
    }
}
