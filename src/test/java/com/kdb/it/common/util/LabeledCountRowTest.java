package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LabeledCountRow} record 단위 테스트.
 *
 * <p>생성자, 접근자, equals/hashCode, toString, fromRow 팩토리(정상·null count→0 폴백·컬럼 수 예외)를 검증한다.
 */
@DisplayName("LabeledCountRow")
class LabeledCountRowTest {

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("정상 값으로 생성 시 각 접근자가 해당 값을 반환한다")
        void constructor_validValues_accessorsReturnCorrectValues() {
            // Arrange
            String label = "2026-07";
            long count = 42L;

            // Act
            LabeledCountRow row = new LabeledCountRow(label, count);

            // Assert
            assertThat(row.label()).isEqualTo(label);
            assertThat(row.count()).isEqualTo(count);
        }

        @Test
        @DisplayName("count=0 으로 생성 시 0 이 반환된다")
        void constructor_zeroCount_returnsZero() {
            // Arrange & Act
            LabeledCountRow row = new LabeledCountRow("2026-01", 0L);

            // Assert
            assertThat(row.count()).isEqualTo(0L);
        }

        @Test
        @DisplayName("label 이 null 이면 접근자가 null 을 반환한다")
        void constructor_nullLabel_returnsNull() {
            // Arrange & Act
            LabeledCountRow row = new LabeledCountRow(null, 10L);

            // Assert
            assertThat(row.label()).isNull();
        }

        @Test
        @DisplayName("count 가 Long.MAX_VALUE 일 때 정상 반환된다(경계값)")
        void constructor_maxLongCount_returnsCorrectly() {
            // Arrange & Act
            LabeledCountRow row = new LabeledCountRow("MAX", Long.MAX_VALUE);

            // Assert
            assertThat(row.count()).isEqualTo(Long.MAX_VALUE);
        }
    }

    // ------------------------------------------------------------------ equals / hashCode
    @Nested
    @DisplayName("equals 및 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("동일한 label 과 count 를 가진 두 인스턴스는 equals 다")
        void equals_sameValues_returnsTrue() {
            // Arrange
            LabeledCountRow a = new LabeledCountRow("2026-07", 100L);
            LabeledCountRow b = new LabeledCountRow("2026-07", 100L);

            // Assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("동일한 두 인스턴스의 hashCode 는 동일하다")
        void hashCode_equalInstances_returnsSameCode() {
            // Arrange
            LabeledCountRow a = new LabeledCountRow("2026-07", 100L);
            LabeledCountRow b = new LabeledCountRow("2026-07", 100L);

            // Assert
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("label 이 다르면 equals 가 false 다")
        void equals_differentLabel_returnsFalse() {
            // Arrange
            LabeledCountRow a = new LabeledCountRow("2026-07", 100L);
            LabeledCountRow b = new LabeledCountRow("2026-08", 100L);

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("count 가 다르면 equals 가 false 다")
        void equals_differentCount_returnsFalse() {
            // Arrange
            LabeledCountRow a = new LabeledCountRow("2026-07", 100L);
            LabeledCountRow b = new LabeledCountRow("2026-07", 200L);

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("null 과 비교 시 equals 가 false 다")
        void equals_null_returnsFalse() {
            LabeledCountRow row = new LabeledCountRow("2026-07", 100L);
            assertThat(row).isNotEqualTo(null);
        }

        @Test
        @DisplayName("다른 타입과 비교 시 equals 가 false 다")
        void equals_differentType_returnsFalse() {
            LabeledCountRow row = new LabeledCountRow("2026-07", 100L);
            assertThat(row).isNotEqualTo("2026-07");
        }

        @Test
        @DisplayName("동일 인스턴스와 비교 시 equals 가 true 다")
        void equals_sameInstance_returnsTrue() {
            LabeledCountRow row = new LabeledCountRow("2026-07", 100L);
            assertThat(row).isEqualTo(row);
        }
    }

    // ------------------------------------------------------------------ toString
    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 에 label 이 포함된다")
        void toString_containsLabel() {
            LabeledCountRow row = new LabeledCountRow("LABEL-UNIQUE-XYZ", 7L);
            assertThat(row.toString()).contains("LABEL-UNIQUE-XYZ");
        }
    }

    // ------------------------------------------------------------------ fromRow 팩토리
    @Nested
    @DisplayName("fromRow 팩토리")
    class FromRow {

        @Test
        @DisplayName("String 라벨과 BigDecimal count 로 정상 매핑된다")
        void fromRow_validArray_mapsCorrectly() {
            // Arrange
            Object[] r = {"2026-07", new BigDecimal("55")};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.label()).isEqualTo("2026-07");
            assertThat(row.count()).isEqualTo(55L);
        }

        @Test
        @DisplayName("Long 타입 count 로도 정상 매핑된다")
        void fromRow_longCount_mapsCorrectly() {
            // Arrange
            Object[] r = {"2026-08", Long.valueOf(99L)};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.count()).isEqualTo(99L);
        }

        @Test
        @DisplayName("Integer 타입 count 로도 정상 매핑된다")
        void fromRow_integerCount_mapsCorrectly() {
            // Arrange
            Object[] r = {"2026-09", Integer.valueOf(7)};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.count()).isEqualTo(7L);
        }

        @Test
        @DisplayName("[1]=null 이면 count 가 0 으로 폴백된다")
        void fromRow_nullCount_fallsBackToZero() {
            // Arrange
            Object[] r = {"2026-07", null};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.count()).isEqualTo(0L);
        }

        @Test
        @DisplayName("[0]=null 이면 label 이 null 이다")
        void fromRow_nullLabel_mapsToNull() {
            // Arrange
            Object[] r = {null, Long.valueOf(3L)};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.label()).isNull();
        }

        @Test
        @DisplayName("VARCHAR2(1) 처럼 Character 타입 라벨도 String 으로 변환된다")
        void fromRow_characterLabel_convertedToString() {
            // Arrange
            Object[] r = {Character.valueOf('M'), Long.valueOf(1L)};

            // Act
            LabeledCountRow row = LabeledCountRow.fromRow(r);

            // Assert
            assertThat(row.label()).isEqualTo("M");
        }

        @Test
        @DisplayName("null 배열 전달 시 IllegalStateException 이 발생한다")
        void fromRow_nullArray_throwsIllegalStateException() {
            assertThatThrownBy(() -> LabeledCountRow.fromRow(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("컬럼 수가 1개이면 IllegalStateException 이 발생한다")
        void fromRow_oneColumn_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"2026-07"};

            // Act & Assert
            assertThatThrownBy(() -> LabeledCountRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("컬럼 수가 3개이면 IllegalStateException 이 발생한다")
        void fromRow_threeColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"2026-07", Long.valueOf(1L), "extra"};

            // Act & Assert
            assertThatThrownBy(() -> LabeledCountRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }
    }
}
