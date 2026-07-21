package com.kdb.it.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DT 도메인(VARCHAR2(8) yyyyMMdd) ↔ {@link LocalDate} JPA 변환기.
 *
 * <p>PRD_c_20260518 #1 메타 표준에 따라 날짜 컬럼은 VARCHAR2(8)로 저장하지만, 자바·DTO·프론트 호환을 위해 엔티티 필드 타입은 {@link
 * LocalDate}로 유지합니다. 본 컨버터가 INSERT/SELECT 시점에 자동 변환합니다.
 *
 * <p>적용처: {@code Basctm.cnrcDt}, {@code Bschdm.dsdDt}, {@code Bperfm.msmSttDt}/{@code msmEndDt} 및
 * 모든 *L 미러.
 */
@Converter
public class Yyyymmdd8DateConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.format(FORMATTER);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        return LocalDate.parse(dbData.trim(), FORMATTER);
    }
}
