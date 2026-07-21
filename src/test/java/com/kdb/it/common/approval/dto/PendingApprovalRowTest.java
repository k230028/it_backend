package com.kdb.it.common.approval.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PendingApprovalRow} record 단위 테스트.
 *
 * <p>생성자, 접근자, equals/hashCode, toString, fromRow 팩토리(정상·예외 경로)를 검증한다.
 */
@DisplayName("PendingApprovalRow")
class PendingApprovalRowTest {

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("정상 값으로 생성 시 각 접근자가 해당 값을 반환한다")
        void constructor_validValues_accessorsReturnCorrectValues() {
            // Arrange
            String apfDcmNo = "APF-2026-0001";
            String title = "IT 사업 예산 결재";
            String usrNm = "홍길동";
            String rqsDt = "2026-07-01";

            // Act
            PendingApprovalRow row = new PendingApprovalRow(apfDcmNo, title, usrNm, rqsDt);

            // Assert
            assertThat(row.apfDcmNo()).isEqualTo(apfDcmNo);
            assertThat(row.title()).isEqualTo(title);
            assertThat(row.usrNm()).isEqualTo(usrNm);
            assertThat(row.rqsDt()).isEqualTo(rqsDt);
        }

        @Test
        @DisplayName("null 값으로 생성 시 접근자가 null 을 반환한다")
        void constructor_nullValues_accessorsReturnNull() {
            // Arrange & Act
            PendingApprovalRow row = new PendingApprovalRow(null, null, null, null);

            // Assert
            assertThat(row.apfDcmNo()).isNull();
            assertThat(row.title()).isNull();
            assertThat(row.usrNm()).isNull();
            assertThat(row.rqsDt()).isNull();
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
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목A", "김철수", "2026-06-30");
            PendingApprovalRow b = new PendingApprovalRow("DOC001", "제목A", "김철수", "2026-06-30");

            // Act & Assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("동일한 필드 값을 가진 두 인스턴스의 hashCode 는 동일하다")
        void hashCode_sameFieldValues_returnsSameCode() {
            // Arrange
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목A", "김철수", "2026-06-30");
            PendingApprovalRow b = new PendingApprovalRow("DOC001", "제목A", "김철수", "2026-06-30");

            // Assert
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("apfDcmNo 가 다르면 equals 가 false 다")
        void equals_differentApfDcmNo_returnsFalse() {
            // Arrange
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");
            PendingApprovalRow b = new PendingApprovalRow("DOC002", "제목", "홍길동", "2026-07-01");

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("title 이 다르면 equals 가 false 다")
        void equals_differentTitle_returnsFalse() {
            // Arrange
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목A", "홍길동", "2026-07-01");
            PendingApprovalRow b = new PendingApprovalRow("DOC001", "제목B", "홍길동", "2026-07-01");

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("usrNm 이 다르면 equals 가 false 다")
        void equals_differentUsrNm_returnsFalse() {
            // Arrange
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");
            PendingApprovalRow b = new PendingApprovalRow("DOC001", "제목", "이순신", "2026-07-01");

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("rqsDt 가 다르면 equals 가 false 다")
        void equals_differentRqsDt_returnsFalse() {
            // Arrange
            PendingApprovalRow a = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");
            PendingApprovalRow b = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-02");

            // Assert
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("null 과 비교 시 equals 가 false 다")
        void equals_null_returnsFalse() {
            // Arrange
            PendingApprovalRow row = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");

            // Assert
            assertThat(row).isNotEqualTo(null);
        }

        @Test
        @DisplayName("다른 타입과 비교 시 equals 가 false 다")
        void equals_differentType_returnsFalse() {
            // Arrange
            PendingApprovalRow row = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");

            // Assert
            assertThat(row).isNotEqualTo("DOC001");
        }

        @Test
        @DisplayName("동일 인스턴스와 비교 시 equals 가 true 다")
        void equals_sameInstance_returnsTrue() {
            // Arrange
            PendingApprovalRow row = new PendingApprovalRow("DOC001", "제목", "홍길동", "2026-07-01");

            // Assert
            assertThat(row).isEqualTo(row);
        }
    }

    // ------------------------------------------------------------------ toString
    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 에 apfDcmNo 가 포함된다")
        void toString_containsApfDcmNo() {
            // Arrange
            PendingApprovalRow row =
                    new PendingApprovalRow("APF-2026-9999", "제목", "홍길동", "2026-07-01");

            // Assert
            assertThat(row.toString()).contains("APF-2026-9999");
        }
    }

    // ------------------------------------------------------------------ fromRow 팩토리
    @Nested
    @DisplayName("fromRow 팩토리")
    class FromRow {

        @Test
        @DisplayName("4컬럼 Object[] 로 올바르게 매핑된다")
        void fromRow_validArray_mapsAllColumns() {
            // Arrange
            Object[] r = {"APF-2026-0001", "예산 결재", "홍길동", "2026-07-01"};

            // Act
            PendingApprovalRow row = PendingApprovalRow.fromRow(r);

            // Assert
            assertThat(row.apfDcmNo()).isEqualTo("APF-2026-0001");
            assertThat(row.title()).isEqualTo("예산 결재");
            assertThat(row.usrNm()).isEqualTo("홍길동");
            assertThat(row.rqsDt()).isEqualTo("2026-07-01");
        }

        @Test
        @DisplayName("VARCHAR2(1) 처럼 Character 타입 컬럼도 String 으로 변환된다")
        void fromRow_characterTypeColumn_convertedToString() {
            // Arrange — Oracle JDBC 가 Character 를 반환하는 상황 모사
            Object[] r = {Character.valueOf('X'), "제목", "이름", "2026-01-01"};

            // Act
            PendingApprovalRow row = PendingApprovalRow.fromRow(r);

            // Assert
            assertThat(row.apfDcmNo()).isEqualTo("X");
        }

        @Test
        @DisplayName("null 배열 전달 시 IllegalStateException 이 발생한다")
        void fromRow_nullArray_throwsIllegalStateException() {
            // Act & Assert
            assertThatThrownBy(() -> PendingApprovalRow.fromRow(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("컬럼 수가 3개이면 IllegalStateException 이 발생한다")
        void fromRow_threeColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"DOC001", "제목", "이름"};

            // Act & Assert
            assertThatThrownBy(() -> PendingApprovalRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("컬럼 수가 5개이면 IllegalStateException 이 발생한다")
        void fromRow_fiveColumns_throwsIllegalStateException() {
            // Arrange
            Object[] r = {"DOC001", "제목", "이름", "2026-07-01", "extra"};

            // Act & Assert
            assertThatThrownBy(() -> PendingApprovalRow.fromRow(r))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("컬럼 수 불일치");
        }

        @Test
        @DisplayName("컬럼 값이 null 이면 접근자가 null 을 반환한다")
        void fromRow_nullColumnValues_accessorsReturnNull() {
            // Arrange
            Object[] r = {null, null, null, null};

            // Act
            PendingApprovalRow row = PendingApprovalRow.fromRow(r);

            // Assert
            assertThat(row.apfDcmNo()).isNull();
            assertThat(row.title()).isNull();
            assertThat(row.usrNm()).isNull();
            assertThat(row.rqsDt()).isNull();
        }
    }
}
