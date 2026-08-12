package com.kdb.it.domain.budget.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ProjectListRow} record 단위 테스트.
 *
 * <p>이 record는 QueryDSL {@code Projections.constructor}가 **위치 기반**으로 채웁니다. 컴포넌트 순서가 {@code
 * ProjectRepositoryImpl.searchListByCondition}의 select 인자 순서와 어긋나면 타입이 같은 인접 컬럼끼리 조용히 바뀌어 들어갑니다(문자열
 * 컬럼이 9개 연속입니다). 그래서 접근자 검증만으로 끝내지 않고, 컴포넌트 순서 자체를 리플렉션으로 고정합니다.
 *
 * <p>{@code PendingApprovalRow}·{@code RecentReviewingRow}와 같은 목록 프로젝션 record이며 테스트 구성도 그 선례를 따릅니다.
 */
@DisplayName("ProjectListRow")
class ProjectListRowTest {

    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    private static ProjectListRow sample() {
        return new ProjectListRow(
                "PRJ-2026-0001",
                1,
                "차세대 원장 재구축",
                "법률/규제대응",
                "0910",
                "0180",
                START,
                END,
                "2026",
                "N",
                "10",
                "21",
                "N");
    }

    // ------------------------------------------------------------------ 생성 / 접근자
    @Nested
    @DisplayName("생성 및 접근자")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("정상 값으로 생성 시 각 접근자가 해당 값을 반환한다")
        void constructor_validValues_accessorsReturnCorrectValues() {
            // Act
            ProjectListRow row = sample();

            // Assert
            assertThat(row.abusMngNo()).isEqualTo("PRJ-2026-0001");
            assertThat(row.sno()).isEqualTo(1);
            assertThat(row.abusNm()).isEqualTo("차세대 원장 재구축");
            assertThat(row.bzTpC()).isEqualTo("법률/규제대응");
            assertThat(row.svnDpmC()).isEqualTo("0910");
            assertThat(row.dvmDpmC()).isEqualTo("0180");
            assertThat(row.sttDtm()).isEqualTo(START);
            assertThat(row.endDtm()).isEqualTo(END);
            assertThat(row.bseYy()).isEqualTo("2026");
            assertThat(row.odnYn()).isEqualTo("N");
            assertThat(row.abusTc()).isEqualTo("10");
            assertThat(row.rprStsTc()).isEqualTo("21");
            assertThat(row.delYn()).isEqualTo("N");
        }

        @Test
        @DisplayName("null 값으로 생성 시 접근자가 null 을 반환한다")
        void constructor_nullValues_accessorsReturnNull() {
            // Arrange & Act — 목록 프로젝션은 nullable 컬럼(경상여부·보고상태 등)을 그대로 담는다
            ProjectListRow row =
                    new ProjectListRow(
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            null);

            // Assert
            assertThat(row.abusMngNo()).isNull();
            assertThat(row.sno()).isNull();
            assertThat(row.sttDtm()).isNull();
            assertThat(row.endDtm()).isNull();
            assertThat(row.odnYn()).isNull();
            assertThat(row.rprStsTc()).isNull();
        }
    }

    // ------------------------------------------------------------------ 컴포넌트 순서 계약
    @Nested
    @DisplayName("컴포넌트 순서")
    class ComponentOrder {

        @Test
        @DisplayName("QueryDSL select 인자 순서와 같은 순서·타입으로 고정된다")
        void components_matchProjectionOrder() {
            // Act
            List<String> names =
                    java.util.Arrays.stream(ProjectListRow.class.getRecordComponents())
                            .map(component -> component.getName())
                            .toList();

            // Assert — ProjectRepositoryImpl.searchListByCondition의 select 인자 순서와 같아야 한다
            assertThat(names)
                    .containsExactly(
                            "abusMngNo",
                            "sno",
                            "abusNm",
                            "bzTpC",
                            "svnDpmC",
                            "dvmDpmC",
                            "sttDtm",
                            "endDtm",
                            "bseYy",
                            "odnYn",
                            "abusTc",
                            "rprStsTc",
                            "delYn");
        }
    }

    // ------------------------------------------------------------------ 값 의미
    @Nested
    @DisplayName("equals · hashCode · toString")
    class ValueSemantics {

        @Test
        @DisplayName("동일한 필드 값을 가진 두 인스턴스는 equals 이고 hashCode 도 같다")
        void equals_sameFieldValues_returnsTrue() {
            assertThat(sample()).isEqualTo(sample()).hasSameHashCodeAs(sample());
        }

        @Test
        @DisplayName("사업관리번호가 다르면 equals 가 false 다")
        void equals_differentAbusMngNo_returnsFalse() {
            // Arrange
            ProjectListRow other =
                    new ProjectListRow(
                            "PRJ-2026-0002",
                            1,
                            "차세대 원장 재구축",
                            "법률/규제대응",
                            "0910",
                            "0180",
                            START,
                            END,
                            "2026",
                            "N",
                            "10",
                            "21",
                            "N");

            // Assert
            assertThat(sample()).isNotEqualTo(other);
        }

        @Test
        @DisplayName("toString 에 사업관리번호가 포함된다")
        void toString_containsAbusMngNo() {
            assertThat(sample().toString()).contains("PRJ-2026-0001");
        }
    }
}
