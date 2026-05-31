package com.kdb.it.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시스템 기본 시간대의 {@link Clock} 빈을 제공한다.
 *
 * <p>서비스 계층이 {@code LocalDateTime.now()} 대신 주입된 {@code Clock}을 사용하면
 * 테스트에서 고정 시각을 주입해 결정적 검증이 가능하다.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
