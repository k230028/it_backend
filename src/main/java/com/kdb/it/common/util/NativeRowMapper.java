package com.kdb.it.common.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 네이티브 쿼리 {@code Object[]} 결과의 환경별 타입 차이를 안전하게 변환한다(§5.5.4).
 *
 * <p>Oracle JDBC + Hibernate 6 조합에서 VARCHAR2(1)은 Character/String, TIMESTAMP는
 * Timestamp/LocalDateTime, DATE는 java.sql.Date/LocalDate/LocalDateTime/String(yyyyMMdd)으로 혼용 반환된다.
 * 직접 캐스트(예: {@code (String) r[i]})는 금지하고 본 헬퍼만 사용한다.
 */
public final class NativeRowMapper {

    private NativeRowMapper() {}

    /** VARCHAR2(1) 포함 모든 문자열 컬럼 안전 변환(Character/String 혼용 대응). */
    public static String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    /** TIMESTAMP 컬럼 → LocalDateTime (Timestamp/LocalDateTime 혼용 대응). */
    public static LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalStateException("지원하지 않는 시각 타입: " + v.getClass());
    }

    /** DATE 컬럼 → LocalDate (java.sql.Date/LocalDate/LocalDateTime/String(yyyyMMdd) 혼용 대응). */
    public static LocalDate toLd(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (v instanceof String s) {
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.length() >= 8) {
                try {
                    return LocalDate.of(
                            Integer.parseInt(digits.substring(0, 4)),
                            Integer.parseInt(digits.substring(4, 6)),
                            Integer.parseInt(digits.substring(6, 8)));
                } catch (NumberFormatException | java.time.DateTimeException e) {
                    // TODO: 변환 실패를 DB NULL과 구분하도록 원본 값·타입을 포함한 예외 또는 중앙 진단 로그를 제공한다.
                    return null; // 호출부가 필요 시 원본 로깅
                }
            }
        }
        // TODO: 지원하지 않는 DATE 표현을 DB NULL과 구분하도록 원본 값·타입을 포함한 예외 또는 중앙 진단 로그를 제공한다.
        return null;
    }

    /** NUMBER 컬럼 → Long (BigDecimal/Long/Integer 혼용 대응). null 허용. */
    public static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        throw new IllegalStateException("지원하지 않는 숫자 타입: " + v.getClass());
    }

    /** NUMBER 컬럼 → int (null이면 기본값). */
    public static int toInt(Object v, int defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        throw new IllegalStateException("지원하지 않는 숫자 타입: " + v.getClass());
    }
}
