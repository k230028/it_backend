package com.kdb.it.common.code;

/**
 * NOT NULL 코드 컬럼의 빈값 정규화 유틸.
 *
 * <p>Oracle은 빈 문자열을 NULL로 저장하므로, 화면에서 코드를 고르지 않은 채 넘어온 값을 그대로 저장하면 NOT NULL 제약에 걸린다. 운영 스키마에서 NOT
 * NULL인 코드 컬럼(지급주기코드, 사업구분코드 등)은 저장 직전 이 유틸로 '해당없음' 코드값으로 정규화한다.
 */
public final class CodeDefaults {

    /** 공통코드 '해당없음' 코드값. DFR_CLE_C, ABUS_TC 등이 공유한다. */
    public static final String NOT_APPLICABLE = "0";

    private CodeDefaults() {}

    /**
     * 코드값이 비어 있으면 '해당없음'으로 대체합니다.
     *
     * @param code 화면에서 전달된 코드값 (null 또는 공백 허용)
     * @return 값이 있으면 원본, 비어 있으면 {@link #NOT_APPLICABLE}
     */
    public static String orNotApplicable(String code) {
        return (code == null || code.isBlank()) ? NOT_APPLICABLE : code;
    }
}
