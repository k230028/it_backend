package com.kdb.it.common.approval.itbudget.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** 신규 결재일시는 초 단위로 읽고, 기존 날짜 전용 스냅샷은 자정으로 호환 해석한다. */
public final class ApprovalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    public ApprovalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        String value = parser.getValueAsString();
        try {
            if (value != null && value.length() == 10) {
                return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            }
            return LocalDateTime.parse(value, DATE_TIME_FORMAT);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw InvalidFormatException.from(
                    parser,
                    "결재일시는 yyyy-MM-dd 또는 yyyy-MM-dd'T'HH:mm:ss 형식이어야 합니다.",
                    value,
                    LocalDateTime.class);
        }
    }
}
