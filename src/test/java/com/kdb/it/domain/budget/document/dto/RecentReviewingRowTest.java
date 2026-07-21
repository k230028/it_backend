package com.kdb.it.domain.budget.document.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RecentReviewingRow} record 단위 테스트.
 *
 * <p>생성자, 접근자, equals/hashCode, toString, fromRow 팩토리(정상·DATE/Timestamp/LocalDateTime 혼용·컬럼 수 예외)를
 * 검증한다.
 */
@DisplayName("RecentReviewingRow")
class RecentReviewingRowTest {

    // ------------------------------------------------------------------ 샘플 인스턴스 헬퍼
    private static RecentReviewingRow sample() {
        return new RecentReviewingRow(
                "DOC-2026-0001", // docMngNo
                "IT 예산 사전협의 검토", // reqTtl
                "홍길동", // usrNm
                "2026-07-01", // createdAt
                LocalDate.of(2026, 8, 31) // fsgTlm
                );
    }

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("정상 값으로 생성 시 모든 접근자가 해당 값을 반환한다")
        void constructor_validValues_accessorsReturnCorrectValues() {
            // Arrange & Act
            RecentReviewingRow row = sample();

            // Assert
            assertThat(row.docMngNo()).isEqualTo("DOC-2026-0001");
            assertThat(row.reqTtl()).isEqualTo("IT 예산 사전협의 검토");
            assertThat(row.usrNm()).isEqualTo("홍길동");
            assertThat(row.createdAt()).isEqualTo("2026-07-01");
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("fsgTlm 이 null 이면 접근자가 null 을 반환한다")
        void constructor_nullFsgTlm_returnsNull() {
            // Arrange & Act
            RecentReviewingRow row =
                    new RecentReviewingRow("DOC-2026-0002", "제목", "이름", "2026-07-01", null);

            // Assert
            assertThat(row.fsgTlm()).isNull();
        }

        @Test
        @DisplayName("모든 필드가 null 이어도 생성된다")
        void constructor_allNullFields_createsRow() {
            // Arrange & Act
            RecentReviewingRow row = new RecentReviewingRow(null, null, null, null, null);

            // Assert
            assertThat(row.docMngNo()).isNull();
            assertThat(row.reqTtl()).isNull();
            assertThat(row.usrNm()).isNull();
            assertThat(row.createdAt()).isNull();
            assertThat(row.fsgTlm()).isNull();
        }
    }

    // ------------------------------------------------------------------ equals / hashCode
    @Nested
    @DisplayName("equals 및 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("동일한 필드 값을 가진 두 인스턴스는 equals 다")
        void equals_sameFieldValues_returnsTrue() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b = sample();

            // Assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("동일한 두 인스턴스의 hashCode 는 동일하다")
        void hashCode_equalInstances_returnsSameCode() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b = sample();

            // Assert
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("docMngNo 가 다르면 equals 가 false 다")
        void equals_differentDocMngNo_returnsFalse() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b =
                    new RecentReviewingRow(
                            "DOC-2026-9999",
                            "IT 예산 사전협의 검토",
                            "홍길동",
                            "2026-07-01",
                            LocalDate.of(2026, 8, 31));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("reqTtl 이 다르면 equals 가 false 다")
        void equals_differentReqTtl_returnsFalse() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b =
                    new RecentReviewingRow(
                            "DOC-2026-0001",
                            "다른 제목",
                            "홍길동",
                            "2026-07-01",
                            LocalDate.of(2026, 8, 31));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("fsgTlm 이 다르면 equals 가 false 다")
        void equals_differentFsgTlm_returnsFalse() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b =
                    new RecentReviewingRow(
                            "DOC-2026-0001",
                            "IT 예산 사전협의 검토",
                            "홍길동",
                            "2026-07-01",
                            LocalDate.of(2026, 9, 30));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("usrNm 이 다르면 equals 가 false 다")
        void equals_differentUsrNm_returnsFalse() {
            // Arrange
            RecentReviewingRow a = sample();
            RecentReviewingRow b =
                    new RecentReviewingRow(
                            "DOC-2026-0001",
                            "IT 예산 사전협의 검토",
                            "이순신",
                            "2026-07-01",
                            LocalDate.of(2026, 8, 31));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("null 과 비교 시 equals 가 false 다")
        void equals_null_returnsFalse() {
            assertThat(sample()).isNotEqualTo(null);
        }

        @Test
        @DisplayName("다른 타입과 비교 시 equals 가 false 다")
        void equals_differentType_returnsFalse() {
            assertThat(sample()).isNotEqualTo("DOC-2026-0001");
        }

        @Test
        @DisplayName("동일 인스턴스와 비교 시 equals 가 true 다")
        void equals_sameInstance_returnsTrue() {
            RecentReviewingRow row = sample();
            assertThat(row).isEqualTo(row);
        }
    }

    // ------------------------------------------------------------------ toString
    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 에 docMngNo 가 포함된다")
        void toString_containsDocMngNo() {
            assertThat(sample().toString()).contains("DOC-2026-0001");
        }
    }

    // ------------------------------------------------------------------ fromRow 팩토리
    @Nested
    @DisplayName("fromRow 팩토리")
    class FromRow {

        /** 5컬럼 정상 배열(fsgTlm = LocalDate). */
        private static Object[] validRow() {
            return new Object[] {
                "DOC-2026-0001", // [0] docMngNo
                "IT 예산 사전협의 검토", // [1] reqTtl
                "홍길동", // [2] usrNm
                "2026-07-01", // [3] createdAt
                LocalDate.of(2026, 8, 31) // [4] fsgTlm
            };
        }

        @Test
        @DisplayName("5컬럼 정상 배열로 올바르게 매핑된다")
        void fromRow_validArray_mapsAllColumns() {
            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(validRow());

            // Assert
            assertThat(row.docMngNo()).isEqualTo("DOC-2026-0001");
            assertThat(row.reqTtl()).isEqualTo("IT 예산 사전협의 검토");
            assertThat(row.usrNm()).isEqualTo("홍길동");
            assertThat(row.createdAt()).isEqualTo("2026-07-01");
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("fsgTlm 컬럼이 java.sql.Timestamp 이면 LocalDate 로 변환된다")
        void fromRow_fsgTlmAsTimestamp_convertedToLocalDate() {
            // Arrange — Oracle JDBC 가 TIMESTAMP 를 반환하는 경우 모사
            Object[] r = validRow();
            r[4] = Timestamp.valueOf(LocalDateTime.of(2026, 8, 31, 23, 59));

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("fsgTlm 컬럼이 LocalDateTime 이면 LocalDate 로 변환된다")
        void fromRow_fsgTlmAsLocalDateTime_convertedToLocalDate() {
            // Arrange — Hibernate 6 가 LocalDateTime 을 반환하는 경우 모사
            Object[] r = validRow();
            r[4] = LocalDateTime.of(2026, 8, 31, 0, 0);

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("fsgTlm 컬럼이 java.sql.Date 이면 LocalDate 로 변환된다")
        void fromRow_fsgTlmAsSqlDate_convertedToLocalDate() {
            // Arrange
            Object[] r = validRow();
            r[4] = java.sql.Date.valueOf(LocalDate.of(2026, 8, 31));

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("fsgTlm 컬럼이 문자열 yyyyMMdd 이면 LocalDate 로 파싱된다")
        void fromRow_fsgTlmAsYyyyMmDdString_parsedToLocalDate() {
            // Arrange
            Object[] r = validRow();
            r[4] = "20260831";

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.fsgTlm()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("[4]=null 이면 fsgTlm 이 null 이다")
        void fromRow_nullFsgTlm_mapsToNull() {
            // Arrange
            Object[] r = validRow();
            r[4] = null;

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.fsgTlm()).isNull();
        }

        @Test
        @DisplayName("VARCHAR2(1) 처럼 Character 타입 usrNm 도 String 으로 변환된다")
        void fromRow_characterUsrNm_convertedToString() {
            // Arrange
            Object[] r = validRow();
            r[2] = Character.valueOf('A');

            // Act
            RecentReviewingRow row = RecentReviewingRow.fromRow(r);

            // Assert
            assertThat(row.usrNm()).isEqualTo("A");
        }

        @Test
        @DisplayName("null 배열 전달 시 IllegalStateException 이 발생한다")
        void fromRow_nullArray_throwsIllegalStateException() {
            assertThatThrownBy(() -> RecentReviewingRow.fromRow(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("컬럼 수가 4개이면 IllegalStateException 이 발생한다")
        void fromRow_fourColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"DOC001", "제목", "이름", "2026-07-01"};

            // Act & Assert
            assertThatThrownBy(() -> RecentReviewingRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("컬럼 수가 6개이면 IllegalStateException 이 발생한다")
        void fromRow_sixColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"DOC001", "제목", "이름", "2026-07-01", LocalDate.of(2026, 8, 31), "extra"};

            // Act & Assert
            assertThatThrownBy(() -> RecentReviewingRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }
    }
}
