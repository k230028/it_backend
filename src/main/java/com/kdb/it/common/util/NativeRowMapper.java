package com.kdb.it.common.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 네이티브 쿼리 {@code Object[]} 결과의 환경별 타입 차이를 안전하게 변환한다(§5.5.4).
 *
 * <p>Oracle JDBC + Hibernate 6 조합에서 VARCHAR2(1)은 Character/String, TIMESTAMP는
 * Timestamp/LocalDateTime, DATE는 java.sql.Date/LocalDate/LocalDateTime/String(yyyyMMdd)으로 혼용 반환된다.
 * 직접 캐스트(예: {@code (String) r[i]})는 금지하고 본 헬퍼만 사용한다.
 */
public final class NativeRowMapper {

    private static final Logger log = LoggerFactory.getLogger(NativeRowMapper.class);

    /** 경고 로그 최소 재발행 간격(클래스 전체 공통 단일 윈도우, ERR-09). */
    private static final long WARN_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);

    /** 로그에 남기는 정제된 값의 최대 길이. 초과분은 말줄임표로 축약한다. */
    private static final int SANITIZED_VALUE_MAX_LENGTH = 128;

    /** 다음 경고를 남길 수 있는 시각(nanoTime 기준). 0으로 시작해 최초 실패는 항상 경고가 허용된다. */
    private static final AtomicLong nextWarnNanos = new AtomicLong(0L);

    /** 현재 윈도우에서 억제된(경고를 남기지 못한) 변환 실패 건수. */
    private static final AtomicLong suppressedCount = new AtomicLong(0L);

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
                    // 날짜로 파싱할 수 없는 값 — DB NULL과 구분되는 실제 변환 실패이므로 경고한다(스택트레이스는 남기지 않음).
                    warnConversionFailure(s);
                    return null;
                }
            }
            if (s.isBlank()) {
                // 공백/빈 문자열은 사실상 NULL과 동등하게 취급한다 — 데이터 품질 경고 대상이 아니다.
                return null;
            }
        }
        // 지원하지 않는 DATE 표현(8자리 미만 숫자 문자열 포함) — DB NULL과 구분되는 실제 변환 실패이므로 경고한다.
        warnConversionFailure(v);
        return null;
    }

    /**
     * DATE 컬럼 변환 실패를 안전하게 경고 로그로 남긴다(ERR-09).
     *
     * <p>{@link #toLd(Object)}가 반환하는 {@code null}은 두 가지 상황을 구분해야 한다: DB에 실제로 저장된 NULL(정상)과, 값은 있으나
     * 지원하지 않는 표현이라 변환에 실패한 경우(비정상)다. 이 메서드는 후자만 호출되며, 목록·대시보드 조회를 깨뜨리지 않도록 예외를 던지지 않고 경고 로그만 남긴다.
     *
     * <p>스택트레이스, SQL 원문, 사용자·문서 컨텍스트는 포함하지 않고 실패한 값의 실제 타입명과 {@link #sanitizeForLog(Object)}로 정제한
     * 값만 기록한다. 값·타입별로 개별 추적하면 무한정 늘어날 수 있으므로, 클래스 전체에 대해 단일 전역 윈도우로 1분에 최대 1건만 경고하고(로그 폭주 방지), 윈도우
     * 내 억제된 건수는 다음 허용 시점에 합산해 함께 보고한다.
     *
     * @param value 변환에 실패한 원본 값(이 시점에는 항상 null이 아님)
     */
    private static void warnConversionFailure(Object value) {
        long now = System.nanoTime();
        long allowedAt = nextWarnNanos.get();
        if (now - allowedAt < 0) {
            // 아직 윈도우가 끝나지 않음 — 로그를 남기지 않고 억제 건수만 누적한다.
            suppressedCount.incrementAndGet();
            return;
        }
        if (!nextWarnNanos.compareAndSet(allowedAt, now + WARN_WINDOW_NANOS)) {
            // 동시에 다른 스레드가 먼저 윈도우를 갱신했다면 이번 호출은 억제로 처리한다.
            suppressedCount.incrementAndGet();
            return;
        }
        long suppressed = suppressedCount.getAndSet(0L);
        String type = value.getClass().getName();
        String sanitized = sanitizeForLog(value);
        if (suppressed > 0) {
            log.warn(
                    "DATE 변환 실패 - 타입: {}, 값: {} (직전 1분간 억제된 경고 {}건 포함)",
                    type,
                    sanitized,
                    suppressed);
        } else {
            log.warn("DATE 변환 실패 - 타입: {}, 값: {}", type, sanitized);
        }
    }

    /**
     * 경고 로그에 남기기 전 원본 값을 안전하게 정제한다.
     *
     * <p>개행(LF)·캐리지리턴(CR)·탭 문자를 공백으로 치환해 로그 위조(log forging)를 막고, 128자를 넘는 값은 말줄임표로 축약해 과도한 로그 크기를
     * 방지한다.
     *
     * @param value 정제 대상 원본 값(null 허용)
     * @return 정제된 문자열 표현
     */
    private static String sanitizeForLog(Object value) {
        String raw = String.valueOf(value);
        String cleaned = raw.replaceAll("[\r\n\t]", " ");
        if (cleaned.length() <= SANITIZED_VALUE_MAX_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, SANITIZED_VALUE_MAX_LENGTH - 1) + "…";
    }

    /** 테스트 전용: 정적 레이트 리미터 상태를 초기화한다(테스트 간 격리 목적). */
    static void resetLogLimiter() {
        nextWarnNanos.set(0L);
        suppressedCount.set(0L);
    }

    /** 테스트 전용: 억제 건수는 유지한 채 윈도우만 즉시 만료시켜, 실시간 대기 없이 윈도우 경과를 시뮬레이션한다. */
    static void expireWarnWindowForTest() {
        nextWarnNanos.set(0L);
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
