package com.kdb.it.common.util;

/**
 * {@code (라벨, COUNT)} 2컬럼 native 집계 결과 공용 DTO(#6).
 *
 * <p>월별 결재/문서 추이, 일별 로그인 통계처럼 {@code [0]=문자열 라벨, [1]=건수(NUMBER)} 형태가 반복되므로 공용 record 하나로 통합한다.
 * 의미(월/일자 등)는 호출부 메서드명·주석으로 구분한다. 매핑은 §5.5.4 {@link NativeRowMapper} 헬퍼로 봉인한다.
 *
 * @param label 라벨(월/일자 등 문자열)
 * @param count 건수
 */
public record LabeledCountRow(String label, long count) {
    /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
    private static final int EXPECTED_COLUMNS = 2;

    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다.
     *
     * @param r [0]=라벨(VARCHAR), [1]=건수(NUMBER, null이면 0)
     * @return 매핑된 DTO
     * @throws IllegalStateException 컬럼 수가 2가 아니면(SQL/팩토리 불일치 조기 검출)
     */
    public static LabeledCountRow fromRow(Object[] r) {
        if (r == null || r.length != EXPECTED_COLUMNS) {
            throw new IllegalStateException(
                    "컬럼 수 불일치: 기대=" + EXPECTED_COLUMNS + ", 실제=" + (r == null ? "null" : r.length));
        }
        Long cnt = NativeRowMapper.toLong(r[1]);
        return new LabeledCountRow(NativeRowMapper.toStr(r[0]), cnt == null ? 0L : cnt);
    }
}
