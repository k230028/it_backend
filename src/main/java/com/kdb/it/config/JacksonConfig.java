package com.kdb.it.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper 전역 설정 클래스
 *
 * <p>Spring MVC의 HTTP 요청/응답 본문(JSON) 직렬화/역직렬화에 사용되는 {@link ObjectMapper}를 커스터마이징합니다.
 *
 * <p>주요 설정:
 *
 * <ul>
 *   <li>{@link JavaTimeModule} 등록: Java 8의 {@code LocalDate}, {@code LocalDateTime} 등 날짜/시간 타입을
 *       JSON으로 올바르게 변환
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} 비활성화: 날짜를 타임스탬프(숫자) 대신 ISO-8601 형식 문자열(예: "2026-01-15")로
 *       직렬화
 * </ul>
 */
@Configuration // Spring 설정 클래스로 등록
public class JacksonConfig {

    /**
     * 전역 ObjectMapper 빈 등록
     *
     * <p>애플리케이션 전체에서 사용할 {@link ObjectMapper} 인스턴스를 Spring 컨텍스트에 빈으로 등록합니다.
     *
     * <p>설정 내용:
     *
     * <ul>
     *   <li>{@link JavaTimeModule}: {@code LocalDate} → {@code "2026-01-15"}, {@code LocalDateTime}
     *       → {@code "2026-01-15T10:30:00"} 형식으로 변환
     *   <li>{@code WRITE_DATES_AS_TIMESTAMPS(false)}: 날짜를 타임스탬프(1736900400000)가 아닌 사람이 읽을 수 있는
     *       ISO-8601 문자열로 출력
     * </ul>
     *
     * @return 커스터마이징된 {@link ObjectMapper} 인스턴스
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Java 8 날짜/시간 타입(LocalDate, LocalDateTime 등) 지원 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());
        // 날짜를 타임스탬프(숫자) 대신 ISO-8601 문자열("2026-01-15")로 직렬화
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }
}
