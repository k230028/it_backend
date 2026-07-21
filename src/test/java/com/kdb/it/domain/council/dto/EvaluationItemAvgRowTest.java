package com.kdb.it.domain.council.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link EvaluationItemAvgRow} record 단위 테스트.
 *
 * <p>생성자, 접근자, equals/hashCode(BigDecimal 동일 스케일), toString, fromRow 팩토리(정상·BigDecimal 직접
 * 전달·toString 경유·컬럼 수 예외)를 검증한다.
 */
@DisplayName("EvaluationItemAvgRow")
class EvaluationItemAvgRowTest {

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("정상 값으로 생성 시 각 접근자가 해당 값을 반환한다")
        void constructor_validValues_accessorsReturnCorrectValues() {
            // Arrange
            String code = "CHK001";
            BigDecimal avg = new BigDecimal("4.25");

            // Act
            EvaluationItemAvgRow row = new EvaluationItemAvgRow(code, avg);

            // Assert
            assertThat(row.itPtlCkgItmTc()).isEqualTo(code);
            assertThat(row.avgScore()).isEqualByComparingTo(avg);
        }

        @Test
        @DisplayName("avgScore 가 null 이면 접근자가 null 을 반환한다")
        void constructor_nullAvgScore_returnsNull() {
            // Arrange & Act
            EvaluationItemAvgRow row = new EvaluationItemAvgRow("CHK001", null);

            // Assert
            assertThat(row.avgScore()).isNull();
        }

        @Test
        @DisplayName("코드가 null 이면 itPtlCkgItmTc 가 null 을 반환한다")
        void constructor_nullCode_returnsNull() {
            // Arrange & Act
            EvaluationItemAvgRow row = new EvaluationItemAvgRow(null, new BigDecimal("3.00"));

            // Assert
            assertThat(row.itPtlCkgItmTc()).isNull();
        }
    }

    // ------------------------------------------------------------------ equals / hashCode
    @Nested
    @DisplayName("equals 및 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("동일한 코드와 동일 스케일 BigDecimal 을 가진 두 인스턴스는 equals 다")
        void equals_sameCodeAndSameScaleBigDecimal_returnsTrue() {
            // Arrange — BigDecimal.equals 는 스케일 민감이므로 동일 스케일로 구성
            EvaluationItemAvgRow a = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            EvaluationItemAvgRow b = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));

            // Assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("동일한 두 인스턴스의 hashCode 는 동일하다")
        void hashCode_equalInstances_returnsSameCode() {
            // Arrange
            EvaluationItemAvgRow a = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            EvaluationItemAvgRow b = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));

            // Assert
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("코드가 다르면 equals 가 false 다")
        void equals_differentCode_returnsFalse() {
            // Arrange
            EvaluationItemAvgRow a = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            EvaluationItemAvgRow b = new EvaluationItemAvgRow("CHK002", new BigDecimal("3.50"));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("avgScore 가 다르면 equals 가 false 다 (스케일 동일, 값 다름)")
        void equals_differentAvgScore_returnsFalse() {
            // Arrange
            EvaluationItemAvgRow a = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            EvaluationItemAvgRow b = new EvaluationItemAvgRow("CHK001", new BigDecimal("4.50"));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("BigDecimal 스케일이 다르면 equals 가 false 다 (3.5 vs 3.50)")
        void equals_differentScale_returnsFalse() {
            // Arrange — BigDecimal.equals 는 스케일 민감: 3.5 != 3.50
            EvaluationItemAvgRow a = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.5"));
            EvaluationItemAvgRow b = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("null 과 비교 시 equals 가 false 다")
        void equals_null_returnsFalse() {
            EvaluationItemAvgRow row = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            assertThat(row).isNotEqualTo(null);
        }

        @Test
        @DisplayName("다른 타입과 비교 시 equals 가 false 다")
        void equals_differentType_returnsFalse() {
            EvaluationItemAvgRow row = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            assertThat(row).isNotEqualTo("CHK001");
        }

        @Test
        @DisplayName("동일 인스턴스와 비교 시 equals 가 true 다")
        void equals_sameInstance_returnsTrue() {
            EvaluationItemAvgRow row = new EvaluationItemAvgRow("CHK001", new BigDecimal("3.50"));
            assertThat(row).isEqualTo(row);
        }
    }

    // ------------------------------------------------------------------ toString
    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 에 itPtlCkgItmTc 가 포함된다")
        void toString_containsCode() {
            EvaluationItemAvgRow row =
                    new EvaluationItemAvgRow("CHK-UNIQUE-999", new BigDecimal("5.00"));
            assertThat(row.toString()).contains("CHK-UNIQUE-999");
        }
    }

    // ------------------------------------------------------------------ fromRow 팩토리
    @Nested
    @DisplayName("fromRow 팩토리")
    class FromRow {

        @Test
        @DisplayName("BigDecimal 컬럼을 그대로 전달하면 동일 객체를 사용한다")
        void fromRow_bigDecimalValue_usesSameObject() {
            // Arrange
            BigDecimal avg = new BigDecimal("4.50");
            Object[] r = {"CHK001", avg};

            // Act
            EvaluationItemAvgRow row = EvaluationItemAvgRow.fromRow(r);

            // Assert
            assertThat(row.itPtlCkgItmTc()).isEqualTo("CHK001");
            assertThat(row.avgScore()).isSameAs(avg); // BigDecimal 이면 그대로 사용
        }

        @Test
        @DisplayName("Double 타입 컬럼은 toString 경유로 BigDecimal 로 변환된다")
        void fromRow_doubleValue_convertedViaToString() {
            // Arrange — Oracle NUMBER 가 Double 로 반환되는 경우 모사
            Object[] r = {"CHK001", Double.valueOf("3.75")};

            // Act
            EvaluationItemAvgRow row = EvaluationItemAvgRow.fromRow(r);

            // Assert
            assertThat(row.avgScore()).isEqualByComparingTo(new BigDecimal("3.75"));
        }

        @Test
        @DisplayName("Long 타입 컬럼도 toString 경유로 BigDecimal 로 변환된다")
        void fromRow_longValue_convertedViaToString() {
            // Arrange
            Object[] r = {"CHK001", Long.valueOf(5L)};

            // Act
            EvaluationItemAvgRow row = EvaluationItemAvgRow.fromRow(r);

            // Assert
            assertThat(row.avgScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }

        @Test
        @DisplayName("[1]=null 이면 avgScore 가 null 이다")
        void fromRow_nullAvgScore_returnsNull() {
            // Arrange
            Object[] r = {"CHK001", null};

            // Act
            EvaluationItemAvgRow row = EvaluationItemAvgRow.fromRow(r);

            // Assert
            assertThat(row.avgScore()).isNull();
        }

        @Test
        @DisplayName("null 배열 전달 시 IllegalStateException 이 발생한다")
        void fromRow_nullArray_throwsIllegalStateException() {
            assertThatThrownBy(() -> EvaluationItemAvgRow.fromRow(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("컬럼 수가 1개이면 IllegalStateException 이 발생한다")
        void fromRow_oneColumn_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"CHK001"};

            // Act & Assert
            assertThatThrownBy(() -> EvaluationItemAvgRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("컬럼 수가 3개이면 IllegalStateException 이 발생한다")
        void fromRow_threeColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"CHK001", new BigDecimal("3.5"), "extra"};

            // Act & Assert
            assertThatThrownBy(() -> EvaluationItemAvgRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }
    }
}
