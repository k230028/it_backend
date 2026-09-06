package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** 전산예산 v2 해시 입력을 고정 JSON 형태와 SHA-256 다이제스트로 변환한다. */
@Component
public final class ItBudgetCanonicalJson {

    private final ObjectWriter writer;

    /**
     * 전역 설정을 변경하지 않도록 ObjectMapper 복제본에 전산예산 해시 전용 설정을 적용한다.
     *
     * @param source Java time 모듈을 포함한 애플리케이션 ObjectMapper
     */
    public ItBudgetCanonicalJson(ObjectMapper source) {
        ObjectMapper mapper = source.copy().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        SerializationConfig canonicalConfig =
                (SerializationConfig)
                        mapper.getSerializationConfig()
                                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        canonicalConfig =
                canonicalConfig
                        .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .without(SerializationFeature.INDENT_OUTPUT);
        mapper.setConfig(canonicalConfig);
        this.writer = mapper.writer().with(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    /**
     * 금액을 소수점 셋째 자리까지 정확히 표현한다.
     *
     * @param value 원장 금액 또는 null
     * @return null은 보존하고, 나머지는 scale 3으로 맞춘 금액
     * @throws IllegalArgumentException 반올림이 필요한 정밀도인 경우
     */
    public BigDecimal money(BigDecimal value) {
        return exact(value, 3, "금액");
    }

    /**
     * 환율을 소수점 넷째 자리까지 정확히 표현한다.
     *
     * @param value 원장 환율 또는 null
     * @return null은 보존하고, 나머지는 scale 4로 맞춘 환율
     * @throws IllegalArgumentException 반올림이 필요한 정밀도인 경우
     */
    public BigDecimal exchangeRate(BigDecimal value) {
        return exact(value, 4, "환율");
    }

    /**
     * 수량을 정수 scale로 정확히 표현한다.
     *
     * @param value 원장 수량 또는 null
     * @return null은 보존하고, 나머지는 scale 0으로 맞춘 수량
     * @throws IllegalArgumentException 반올림이 필요한 정밀도인 경우
     */
    public BigDecimal quantity(BigDecimal value) {
        return exact(value, 0, "수량");
    }

    /**
     * 입력을 key 순서가 고정된 compact JSON으로 직렬화한다.
     *
     * @param value 해시할 값
     * @return UTF-8 변환 전의 canonical JSON 문자열
     * @throws IllegalStateException 직렬화할 수 없는 값인 경우
     */
    public String write(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("스냅샷 정규화에 실패했습니다.", ex);
        }
    }

    /**
     * canonical JSON의 UTF-8 SHA-256을 64자리 소문자 hexadecimal으로 계산한다.
     *
     * @param value 해시할 값
     * @return 64자리 소문자 SHA-256 다이제스트
     * @throws IllegalStateException SHA-256 알고리즘을 사용할 수 없는 경우
     */
    public String digest(Object value) {
        try {
            byte[] bytes = write(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", ex);
        }
    }

    private BigDecimal exact(BigDecimal value, int scale, String label) {
        if (value == null) return null;
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(label + " 소수 자릿수가 올바르지 않습니다.", ex);
        }
    }
}
