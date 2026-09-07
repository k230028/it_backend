package com.kdb.it.domain.council.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CouncilProjectRow} record 단위 테스트.
 *
 * <p>18 필드 생성자, 접근자, equals/hashCode, toString, fromRow 팩토리(정상·NULL placeholder 가드·컬럼 수 예외 경로)를
 * 검증한다.
 */
@DisplayName("CouncilProjectRow")
class CouncilProjectRowTest {

    // ------------------------------------------------------------------ 샘플 인스턴스 헬퍼
    private static CouncilProjectRow sample() {
        return new CouncilProjectRow(
                "MNG-001", // abusMngNo
                1, // sno
                "테스트 사업", // abusNm
                "ASCT-001", // itPtlAsctId
                "진행중", // itPtlAsctPrgStsTc
                "일반", // itPtlAsctDbrTc
                LocalDate.of(2026, 1, 1), // cnrcDt
                "09", // cnrcSttTm
                false, // applied
                "2026", // prjYy
                "IT", // prjTp
                "개발팀", // svnDpm
                LocalDate.of(2026, 3, 1), // sttDt
                LocalDate.of(2026, 12, 31), // endDt
                "IT부문", // itDpm
                "클라우드 전환", // abusPulConeInf
                "Y", // csfHeldYn
                false // hasInfoSecResource
                );
    }

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("18개 필드로 생성 시 모든 접근자가 올바른 값을 반환한다")
        void constructor_allFields_accessorsReturnCorrectValues() {
            // Arrange & Act
            CouncilProjectRow row = sample();

            // Assert
            assertThat(row.abusMngNo()).isEqualTo("MNG-001");
            assertThat(row.sno()).isEqualTo(1);
            assertThat(row.abusNm()).isEqualTo("테스트 사업");
            assertThat(row.itPtlAsctId()).isEqualTo("ASCT-001");
            assertThat(row.itPtlAsctPrgStsTc()).isEqualTo("진행중");
            assertThat(row.itPtlAsctDbrTc()).isEqualTo("일반");
            assertThat(row.cnrcDt()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(row.cnrcSttTm()).isEqualTo("09");
            assertThat(row.applied()).isFalse();
            assertThat(row.prjYy()).isEqualTo("2026");
            assertThat(row.prjTp()).isEqualTo("IT");
            assertThat(row.svnDpm()).isEqualTo("개발팀");
            assertThat(row.sttDt()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(row.endDt()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(row.itDpm()).isEqualTo("IT부문");
            assertThat(row.abusPulConeInf()).isEqualTo("클라우드 전환");
            assertThat(row.csfHeldYn()).isEqualTo("Y");
            assertThat(row.hasInfoSecResource()).isFalse();
        }

        @Test
        @DisplayName("applied=true 로 생성 시 접근자가 true 를 반환한다")
        void constructor_appliedTrue_returnsTrue() {
            // Arrange & Act
            CouncilProjectRow row =
                    new CouncilProjectRow(
                            "MNG-002",
                            null,
                            "사업2",
                            "ASCT-002",
                            "완료",
                            "특수",
                            null,
                            null,
                            true,
                            "2026",
                            "BIZ",
                            null,
                            null,
                            null,
                            null,
                            null,
                            "N",
                            false);

            // Assert
            assertThat(row.applied()).isTrue();
        }

        @Test
        @DisplayName("null 허용 필드들은 null 로 저장된다")
        void constructor_nullableFields_remainNull() {
            // Arrange & Act
            CouncilProjectRow row =
                    new CouncilProjectRow(
                            null, null, null, null, null, null, null, null, false, null, null, null,
                            null, null, null, null, null, false);

            // Assert
            assertThat(row.abusMngNo()).isNull();
            assertThat(row.sno()).isNull();
            assertThat(row.cnrcDt()).isNull();
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
            CouncilProjectRow a = sample();
            CouncilProjectRow b = sample();

            // Assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("동일한 필드 값을 가진 두 인스턴스의 hashCode 는 동일하다")
        void hashCode_sameFieldValues_returnsSameCode() {
            // Arrange
            CouncilProjectRow a = sample();
            CouncilProjectRow b = sample();

            // Assert
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("abusMngNo 가 다르면 equals 가 false 다")
        void equals_differentAbusMngNo_returnsFalse() {
            // Arrange
            CouncilProjectRow a = sample();
            CouncilProjectRow b =
                    new CouncilProjectRow(
                            "MNG-999",
                            1,
                            "테스트 사업",
                            "ASCT-001",
                            "진행중",
                            "일반",
                            LocalDate.of(2026, 1, 1),
                            "09",
                            false,
                            "2026",
                            "IT",
                            "개발팀",
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 12, 31),
                            "IT부문",
                            "클라우드 전환",
                            "Y",
                            false);

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("applied 가 다르면 equals 가 false 다")
        void equals_differentApplied_returnsFalse() {
            // Arrange
            CouncilProjectRow a = sample(); // applied=false
            CouncilProjectRow b =
                    new CouncilProjectRow(
                            "MNG-001",
                            1,
                            "테스트 사업",
                            "ASCT-001",
                            "진행중",
                            "일반",
                            LocalDate.of(2026, 1, 1),
                            "09",
                            true,
                            "2026",
                            "IT",
                            "개발팀",
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 12, 31),
                            "IT부문",
                            "클라우드 전환",
                            "Y",
                            false);

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("cnrcDt 가 다르면 equals 가 false 다")
        void equals_differentCnrcDt_returnsFalse() {
            // Arrange
            CouncilProjectRow a = sample();
            CouncilProjectRow b =
                    new CouncilProjectRow(
                            "MNG-001",
                            1,
                            "테스트 사업",
                            "ASCT-001",
                            "진행중",
                            "일반",
                            LocalDate.of(2025, 12, 31),
                            "09",
                            false,
                            "2026",
                            "IT",
                            "개발팀",
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 12, 31),
                            "IT부문",
                            "클라우드 전환",
                            "Y",
                            false);

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
            assertThat(sample()).isNotEqualTo("MNG-001");
        }

        @Test
        @DisplayName("동일 인스턴스와 비교 시 equals 가 true 다")
        void equals_sameInstance_returnsTrue() {
            CouncilProjectRow row = sample();
            assertThat(row).isEqualTo(row);
        }
    }

    // ------------------------------------------------------------------ toString
    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 에 abusMngNo 가 포함된다")
        void toString_containsAbusMngNo() {
            assertThat(sample().toString()).contains("MNG-001");
        }
    }

    // ------------------------------------------------------------------ fromRow 팩토리
    @Nested
    @DisplayName("fromRow 팩토리")
    class FromRow {

        /** 19컬럼 정상 배열(12번 = null placeholder). */
        private static Object[] validRow() {
            return new Object[] {
                "MNG-001", // [0] abusMngNo
                1L, // [1] sno (Number)
                "테스트 사업", // [2] abusNm
                "ASCT-001", // [3] itPtlAsctId
                "진행중", // [4] itPtlAsctPrgStsTc
                "일반", // [5] itPtlAsctDbrTc
                LocalDate.of(2026, 1, 1), // [6] cnrcDt
                "09", // [7] cnrcSttTm
                new BigDecimal("1"), // [8] applied (NUMBER 0/1)
                "2026", // [9] prjYy
                "IT", // [10] prjTp
                "개발팀", // [11] svnDpm
                null, // [12] rqmBgAmt (NULL placeholder)
                LocalDate.of(2026, 3, 1), // [13] sttDt
                LocalDate.of(2026, 12, 31), // [14] endDt
                "IT부문", // [15] itDpm
                "클라우드 전환", // [16] abusPulConeInf
                "Y", // [17] csfHeldYn
                new BigDecimal("1") // [18] hasInfoSecResource (NUMBER 0/1)
            };
        }

        @Test
        @DisplayName("19컬럼 정상 배열로 올바르게 매핑된다")
        void fromRow_validArray_mapsAllColumns() {
            // Act
            CouncilProjectRow row = CouncilProjectRow.fromRow(validRow());

            // Assert
            assertThat(row.abusMngNo()).isEqualTo("MNG-001");
            assertThat(row.sno()).isEqualTo(1);
            assertThat(row.abusNm()).isEqualTo("테스트 사업");
            assertThat(row.itPtlAsctId()).isEqualTo("ASCT-001");
            assertThat(row.applied()).isTrue(); // NUMBER 1 → true
            assertThat(row.prjYy()).isEqualTo("2026");
            assertThat(row.cnrcDt()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(row.sttDt()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(row.csfHeldYn()).isEqualTo("Y");
            assertThat(row.hasInfoSecResource()).isTrue(); // [18]=1 → true
        }

        @Test
        @DisplayName("applied=0 이면 false 로 매핑된다")
        void fromRow_appliedZero_mapsFalse() {
            // Arrange
            Object[] r = validRow();
            r[8] = new BigDecimal("0");

            // Act
            CouncilProjectRow row = CouncilProjectRow.fromRow(r);

            // Assert
            assertThat(row.applied()).isFalse();
        }

        @Test
        @DisplayName("[1]=null 이면 sno 가 null 이다")
        void fromRow_nullSno_mapsToNull() {
            // Arrange
            Object[] r = validRow();
            r[1] = null;

            // Act
            CouncilProjectRow row = CouncilProjectRow.fromRow(r);

            // Assert
            assertThat(row.sno()).isNull();
        }

        @Test
        @DisplayName("null 배열 전달 시 IllegalStateException 이 발생한다")
        void fromRow_nullArray_throwsIllegalStateException() {
            assertThatThrownBy(() -> CouncilProjectRow.fromRow(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("컬럼 수가 17개이면 IllegalStateException 이 발생한다")
        void fromRow_wrongColumnCount_throwsIllegalStateException() {
            // Arrange — 17컬럼 (부족)
            Object[] r = new Object[17];

            // Act & Assert
            assertThatThrownBy(() -> CouncilProjectRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("12번 컬럼이 null 이 아니면 IllegalStateException 이 발생한다")
        void fromRow_column12NotNull_throwsIllegalStateException() {
            // Arrange
            Object[] r = validRow();
            r[12] = new BigDecimal("50000"); // NULL placeholder 자리에 값이 들어온 상황

            // Act & Assert
            assertThatThrownBy(() -> CouncilProjectRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("12번 컬럼");
        }

        @Test
        @DisplayName("cnrcDt 를 문자열 yyyyMMdd 로 전달해도 LocalDate 로 파싱된다")
        void fromRow_cnrcDtAsYyyyMmDdString_parsedToLocalDate() {
            // Arrange
            Object[] r = validRow();
            r[6] = "20260101"; // String yyyyMMdd 형식

            // Act
            CouncilProjectRow row = CouncilProjectRow.fromRow(r);

            // Assert
            assertThat(row.cnrcDt()).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }
}
