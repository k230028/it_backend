package com.kdb.it.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Yyyymmdd8DateConverterTest {

    private final Yyyymmdd8DateConverter converter = new Yyyymmdd8DateConverter();

    @Test
    @DisplayName("convertToDatabaseColumn: LocalDate를 yyyyMMdd 문자열로 변환한다")
    void convertToDatabaseColumn_formatsDate() {
        assertThat(converter.convertToDatabaseColumn(LocalDate.of(2026, 5, 29)))
                .isEqualTo("20260529");
    }

    @Test
    @DisplayName("convertToDatabaseColumn: null은 null로 유지한다")
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute: yyyyMMdd 문자열을 LocalDate로 변환한다")
    void convertToEntityAttribute_parsesDate() {
        assertThat(converter.convertToEntityAttribute("20260203"))
                .isEqualTo(LocalDate.of(2026, 2, 3));
    }

    @Test
    @DisplayName("convertToEntityAttribute: null과 공백은 null로 변환한다")
    void convertToEntityAttribute_blank() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute: 앞뒤 공백을 제거한 뒤 파싱한다")
    void convertToEntityAttribute_trims() {
        assertThat(converter.convertToEntityAttribute(" 20240229 "))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("convertToEntityAttribute: 잘못된 날짜 형식은 파싱 예외를 던진다")
    void convertToEntityAttribute_invalid() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("2026-05-29"))
                .isInstanceOf(DateTimeParseException.class);
    }
}
