package com.kdb.it.domain.council.dto;

import com.kdb.it.common.util.NativeRowMapper;

import java.math.BigDecimal;

/**
 * 평가 항목별 평균점수 native 결과(2컬럼) DTO.
 *
 * <p>컬럼: [0]=IT_PTL_CKG_ITM_TC(점검항목코드), [1]=AVG(QUEL_RCRD)(평균점수).</p>
 */
public record EvaluationItemAvgRow(String itPtlCkgItmTc, BigDecimal avgScore) {
    /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
    private static final int EXPECTED_COLUMNS = 2;

    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(#6).
     *
     * @param r [0]=항목코드, [1]=평균점수(NUMBER)
     * @return 매핑된 DTO
     * @throws IllegalStateException 컬럼 수가 2가 아니면(SQL/팩토리 불일치 조기 검출)
     */
    public static EvaluationItemAvgRow fromRow(Object[] r) {
        if (r == null || r.length != EXPECTED_COLUMNS) {
            throw new IllegalStateException("컬럼 수 불일치: 기대=" + EXPECTED_COLUMNS + ", 실제=" + (r == null ? "null" : r.length));
        }
        return new EvaluationItemAvgRow(
                NativeRowMapper.toStr(r[0]),
                // 이미 BigDecimal이면 그대로 사용해 IEEE-754 변환 아티팩트 회피, 아니면 toString 경유로 정밀 보존
                r[1] == null ? null : (r[1] instanceof BigDecimal bd ? bd : new BigDecimal(r[1].toString())));
    }
}
