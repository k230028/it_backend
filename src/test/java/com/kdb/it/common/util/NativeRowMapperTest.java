package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * NativeRowMapper 단위 테스트.
 *
 * <p>Oracle JDBC + Hibernate 6 조합에서 네이티브 쿼리 결과({@code Object[]})의 환경별 타입 변환 헬퍼(toStr, toLdt, toLd,
 * toLong, toInt)를 검증합니다. 외부 의존 없이 순수 자바 객체로 실행됩니다.
 */
class NativeRowMapperTest {

    // ─────────────────────────────────────────────────────────────────────────
    // toStr
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toStr 메서드 — VARCHAR2(1)/모든 문자열 컬럼 안전 변환")
    class ToStrTests {

        @Test
        @DisplayName("성공: String 값이면 그대로 반환한다")
        void toStr_String_반환() {
            // Arrange / Act / Assert (AAA)
            assertThat(NativeRowMapper.toStr("hello")).isEqualTo("hello");
        }

        @Test
        @DisplayName("성공: Character 값이면 toString()으로 변환해 반환한다")
        void toStr_Character_변환() {
            // Arrange: Oracle VARCHAR2(1)이 Character로 반환되는 경우
            Object v = 'Y';

            // Act / Assert
            assertThat(NativeRowMapper.toStr(v)).isEqualTo("Y");
        }

        @Test
        @DisplayName("성공: Integer 값이면 toString()으로 변환해 반환한다")
        void toStr_Integer_변환() {
            assertThat(NativeRowMapper.toStr(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("경계: null이면 null을 반환한다")
        void toStr_null_null반환() {
            assertThat(NativeRowMapper.toStr(null)).isNull();
        }

        @Test
        @DisplayName("경계: 빈 문자열이면 빈 문자열을 반환한다")
        void toStr_빈문자열_빈문자열반환() {
            assertThat(NativeRowMapper.toStr("")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toLdt
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toLdt 메서드 — TIMESTAMP 컬럼 → LocalDateTime 변환")
    class ToLdtTests {

        @Test
        @DisplayName("성공: LocalDateTime 입력이면 그대로 반환한다")
        void toLdt_LocalDateTime_반환() {
            // Arrange
            LocalDateTime ldt = LocalDateTime.of(2026, 7, 1, 9, 30, 0);

            // Act / Assert
            assertThat(NativeRowMapper.toLdt(ldt)).isEqualTo(ldt);
        }

        @Test
        @DisplayName("성공: java.sql.Timestamp 입력이면 LocalDateTime으로 변환한다")
        void toLdt_Timestamp_변환() {
            // Arrange
            LocalDateTime expected = LocalDateTime.of(2026, 7, 1, 12, 0, 0);
            Timestamp ts = Timestamp.valueOf(expected);

            // Act / Assert
            assertThat(NativeRowMapper.toLdt(ts)).isEqualTo(expected);
        }

        @Test
        @DisplayName("경계: null이면 null을 반환한다")
        void toLdt_null_null반환() {
            assertThat(NativeRowMapper.toLdt(null)).isNull();
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(String)이면 IllegalStateException을 던진다")
        void toLdt_지원없는타입_예외() {
            // Arrange: String은 어떤 instanceof 분기에도 해당하지 않는다
            Object unsupported = "2026-07-01T12:00:00";

            // Act / Assert
            assertThatThrownBy(() -> NativeRowMapper.toLdt(unsupported))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("지원하지 않는 시각 타입");
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(Integer)이면 IllegalStateException을 던진다")
        void toLdt_Integer_예외() {
            assertThatThrownBy(() -> NativeRowMapper.toLdt(20260701))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toLd
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toLd 메서드 — DATE 컬럼 → LocalDate 변환")
    class ToLdTests {

        @Test
        @DisplayName("성공: LocalDate 입력이면 그대로 반환한다")
        void toLd_LocalDate_반환() {
            // Arrange
            LocalDate ld = LocalDate.of(2026, 7, 1);

            // Act / Assert
            assertThat(NativeRowMapper.toLd(ld)).isEqualTo(ld);
        }

        @Test
        @DisplayName("성공: LocalDateTime 입력이면 날짜 부분만 반환한다")
        void toLd_LocalDateTime_날짜반환() {
            // Arrange
            LocalDateTime ldt = LocalDateTime.of(2026, 7, 1, 23, 59, 59);

            // Act / Assert
            assertThat(NativeRowMapper.toLd(ldt)).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("성공: java.sql.Date 입력이면 LocalDate로 변환한다")
        void toLd_SqlDate_변환() {
            // Arrange
            LocalDate expected = LocalDate.of(2026, 7, 1);
            java.sql.Date sqlDate = java.sql.Date.valueOf(expected);

            // Act / Assert
            assertThat(NativeRowMapper.toLd(sqlDate)).isEqualTo(expected);
        }

        @Test
        @DisplayName("성공: java.sql.Timestamp 입력이면 날짜 부분을 LocalDate로 반환한다")
        void toLd_Timestamp_변환() {
            // Arrange
            LocalDateTime ldt = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
            Timestamp ts = Timestamp.valueOf(ldt);

            // Act / Assert
            assertThat(NativeRowMapper.toLd(ts)).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("성공: yyyyMMdd 형식 String이면 LocalDate로 파싱한다")
        void toLd_String_yyyyMMdd_파싱() {
            // Act / Assert
            assertThat(NativeRowMapper.toLd("20260701")).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("성공: yyyy-MM-dd 형식 String(구분자 포함)이면 숫자만 추출하여 파싱한다")
        void toLd_String_yyyy_MM_dd_파싱() {
            // Arrange: replaceAll("[^0-9]","") 후 20260701 → 정상 파싱
            assertThat(NativeRowMapper.toLd("2026-07-01")).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("성공: 8자리 미만 숫자 문자열이면 null을 반환한다")
        void toLd_String_7자리이하_null반환() {
            // Act / Assert
            assertThat(NativeRowMapper.toLd("2026070")).isNull();
        }

        @Test
        @DisplayName("성공: 숫자가 전혀 없는 String이면 null을 반환한다")
        void toLd_String_숫자없음_null반환() {
            // Arrange: digits.length() < 8 분기
            assertThat(NativeRowMapper.toLd("abcdefgh")).isNull();
        }

        @Test
        @DisplayName("성공: 잘못된 날짜 숫자(99월)이면 DateTimeException 캐치 후 null을 반환한다")
        void toLd_String_잘못된날짜_null반환() {
            // Arrange: 99월은 DateTimeException → catch → null
            assertThat(NativeRowMapper.toLd("20269901")).isNull();
        }

        @Test
        @DisplayName("경계: null이면 null을 반환한다")
        void toLd_null_null반환() {
            assertThat(NativeRowMapper.toLd(null)).isNull();
        }

        @Test
        @DisplayName("경계: 지원하지 않는 타입(Long)이면 어떤 instanceof에도 해당하지 않아 null을 반환한다")
        void toLd_지원없는타입_Long_null반환() {
            // Long은 String도 아니고 다른 분기에도 해당하지 않으므로 메서드 끝의 return null에 도달한다
            assertThat(NativeRowMapper.toLd(20260701L)).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toLd — 변환 실패 시 안전한 제한 경고 로그 (ERR-09)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toLd 메서드 — 변환 실패를 DB NULL과 구분하는 안전한 제한 경고 로그(ERR-09)")
    class ToLdWarningLogTests {

        private ListAppender<ILoggingEvent> listAppender;
        private Logger nativeRowMapperLogger;

        @BeforeEach
        void setUp() {
            NativeRowMapper.resetLogLimiter();
            nativeRowMapperLogger = (Logger) LoggerFactory.getLogger(NativeRowMapper.class);
            listAppender = new ListAppender<>();
            listAppender.start();
            nativeRowMapperLogger.addAppender(listAppender);
        }

        @AfterEach
        void tearDown() {
            nativeRowMapperLogger.detachAppender(listAppender);
            listAppender.stop();
            NativeRowMapper.resetLogLimiter();
        }

        @Test
        @DisplayName("경계: null이면 경고를 남기지 않는다")
        void toLd_null_경고없음() {
            assertThat(NativeRowMapper.toLd(null)).isNull();
            assertThat(listAppender.list).isEmpty();
        }

        @Test
        @DisplayName("경계: 빈 문자열/공백 문자열이면 경고를 남기지 않는다")
        void toLd_공백문자열_경고없음() {
            assertThat(NativeRowMapper.toLd("")).isNull();
            assertThat(NativeRowMapper.toLd("   ")).isNull();
            assertThat(listAppender.list).isEmpty();
        }

        @Test
        @DisplayName("성공 경로(지원 타입/정상 문자열)에서는 결과가 그대로이고 경고가 없다")
        void toLd_정상변환_결과불변_경고없음() {
            LocalDate expected = LocalDate.of(2026, 7, 1);
            LocalDateTime ldt = LocalDateTime.of(2026, 7, 1, 9, 0);

            assertThat(NativeRowMapper.toLd(expected)).isEqualTo(expected);
            assertThat(NativeRowMapper.toLd(ldt)).isEqualTo(expected);
            assertThat(NativeRowMapper.toLd(java.sql.Date.valueOf(expected))).isEqualTo(expected);
            assertThat(NativeRowMapper.toLd(Timestamp.valueOf(ldt))).isEqualTo(expected);
            assertThat(NativeRowMapper.toLd("20260701")).isEqualTo(expected);

            assertThat(listAppender.list).isEmpty();
        }

        @Test
        @DisplayName("오류: 잘못된 날짜 문자열이면 null을 반환하고 경고를 정확히 1건 남긴다")
        void toLd_잘못된날짜문자열_경고1건() {
            assertThat(NativeRowMapper.toLd("20269901")).isNull();

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입이면 null을 반환하고 경고를 정확히 1건 남긴다")
        void toLd_지원없는타입_경고1건() {
            assertThat(NativeRowMapper.toLd(20260701L)).isNull();

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        }

        @Test
        @DisplayName("경고 메시지는 클래스명과 정제된 값만 포함하고 스택트레이스를 남기지 않는다")
        void toLd_경고메시지_클래스명과값만_스택트레이스없음() {
            assertThat(NativeRowMapper.toLd(20260701L)).isNull();

            ILoggingEvent event = listAppender.list.get(0);
            assertThat(event.getFormattedMessage()).contains("Long").contains("20260701");
            assertThat(event.getThrowableProxy()).isNull();
        }

        @Test
        @DisplayName("경고 메시지에 SQL/사용자/문서 등 부가 컨텍스트가 포함되지 않는다")
        void toLd_경고메시지_부가컨텍스트없음() {
            assertThat(NativeRowMapper.toLd("bad-date-value")).isNull();

            String message = listAppender.list.get(0).getFormattedMessage();
            assertThat(message).doesNotContainIgnoringCase("select");
            assertThat(message).doesNotContainIgnoringCase("sql");
        }

        @Test
        @DisplayName("정제: 128자를 초과하는 값은 축약되어 로그에 남는다")
        void toLd_긴값_128자이내로축약() {
            String longBadValue = "x".repeat(300);

            assertThat(NativeRowMapper.toLd(longBadValue)).isNull();

            String message = listAppender.list.get(0).getFormattedMessage();
            assertThat(message).doesNotContain("x".repeat(150));
            assertThat(message.length()).isLessThan(250);
        }

        @Test
        @DisplayName("정제: 개행/캐리지리턴/탭 문자는 공백으로 치환되어 로그에 남는다")
        void toLd_제어문자_공백치환() {
            assertThat(NativeRowMapper.toLd("bad\r\n\tvalue")).isNull();

            String message = listAppender.list.get(0).getFormattedMessage();
            assertThat(message).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t");
            assertThat(message).contains("bad").contains("value");
        }

        @Test
        @DisplayName("같은 1분 윈도우 내 반복 실패는 경고를 추가로 남기지 않고 억제 건수만 누적한다")
        void toLd_윈도우내_반복실패_경고1건만() {
            assertThat(NativeRowMapper.toLd(1L)).isNull(); // 첫 실패 — 경고 발행
            assertThat(NativeRowMapper.toLd(2L)).isNull(); // 억제
            assertThat(NativeRowMapper.toLd(3L)).isNull(); // 억제

            assertThat(listAppender.list).hasSize(1);
        }

        @Test
        @DisplayName("윈도우 경과 후 다음 경고는 직전 윈도우의 억제 건수를 포함한다")
        void toLd_윈도우경과후_억제건수_포함() {
            assertThat(NativeRowMapper.toLd(1L)).isNull(); // 첫 실패 — 경고 발행(1건)
            assertThat(NativeRowMapper.toLd(2L)).isNull(); // 억제 1
            assertThat(NativeRowMapper.toLd(3L)).isNull(); // 억제 2

            // 억제 카운트는 유지한 채 윈도우만 경과시켜(테스트 전용 훅) 실시간 대기 없이 검증한다.
            NativeRowMapper.expireWarnWindowForTest();

            assertThat(NativeRowMapper.toLd(4L)).isNull(); // 윈도우 경과 후 재발행 — 억제 2건 포함

            assertThat(listAppender.list).hasSize(2);
            assertThat(listAppender.list.get(1).getFormattedMessage()).contains("2");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toLong
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toLong 메서드 — NUMBER 컬럼 → Long 변환")
    class ToLongTests {

        @Test
        @DisplayName("성공: Long 값이면 그대로 반환한다")
        void toLong_Long_반환() {
            assertThat(NativeRowMapper.toLong(999L)).isEqualTo(999L);
        }

        @Test
        @DisplayName("성공: BigDecimal 값이면 longValue()로 변환한다")
        void toLong_BigDecimal_변환() {
            // Arrange: Oracle NUMBER는 BigDecimal로 반환되는 경우가 많다
            assertThat(NativeRowMapper.toLong(new BigDecimal("12345"))).isEqualTo(12345L);
        }

        @Test
        @DisplayName("성공: Integer 값이면 longValue()로 변환한다")
        void toLong_Integer_변환() {
            assertThat(NativeRowMapper.toLong(42)).isEqualTo(42L);
        }

        @Test
        @DisplayName("경계: null이면 null을 반환한다")
        void toLong_null_null반환() {
            assertThat(NativeRowMapper.toLong(null)).isNull();
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(String)이면 IllegalStateException을 던진다")
        void toLong_지원없는타입_String_예외() {
            assertThatThrownBy(() -> NativeRowMapper.toLong("123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("지원하지 않는 숫자 타입");
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(Boolean)이면 IllegalStateException을 던진다")
        void toLong_지원없는타입_Boolean_예외() {
            assertThatThrownBy(() -> NativeRowMapper.toLong(true))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toInt
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toInt 메서드 — NUMBER 컬럼 → int 변환 (null이면 기본값)")
    class ToIntTests {

        @Test
        @DisplayName("성공: Integer 값이면 intValue()로 반환한다")
        void toInt_Integer_반환() {
            assertThat(NativeRowMapper.toInt(7, 0)).isEqualTo(7);
        }

        @Test
        @DisplayName("성공: Long 값이면 intValue()로 반환한다")
        void toInt_Long_반환() {
            assertThat(NativeRowMapper.toInt(100L, 0)).isEqualTo(100);
        }

        @Test
        @DisplayName("성공: BigDecimal 값이면 intValue()로 반환한다")
        void toInt_BigDecimal_반환() {
            assertThat(NativeRowMapper.toInt(new BigDecimal("55"), -1)).isEqualTo(55);
        }

        @Test
        @DisplayName("경계: null이면 defaultValue를 반환한다")
        void toInt_null_기본값반환() {
            assertThat(NativeRowMapper.toInt(null, 99)).isEqualTo(99);
        }

        @Test
        @DisplayName("경계: null일 때 defaultValue가 0이면 0을 반환한다")
        void toInt_null_기본값0_반환() {
            assertThat(NativeRowMapper.toInt(null, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(String)이면 IllegalStateException을 던진다")
        void toInt_지원없는타입_예외() {
            assertThatThrownBy(() -> NativeRowMapper.toInt("abc", 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("지원하지 않는 숫자 타입");
        }

        @Test
        @DisplayName("오류: 지원하지 않는 타입(Character)이면 IllegalStateException을 던진다")
        void toInt_Character_예외() {
            assertThatThrownBy(() -> NativeRowMapper.toInt('A', 0))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
