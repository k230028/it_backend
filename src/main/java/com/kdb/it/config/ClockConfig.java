package com.kdb.it.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 시스템 기본 시간대의 {@link Clock} 빈을 제공한다.
 *
 * <p>서비스 계층이 {@code LocalDateTime.now()} 대신 주입된 {@code Clock}을 사용하면 테스트에서 고정 시각을 주입해 결정적 검증이 가능하다.
 *
 * <p>EAI 전용 {@code eaiClock} 등 같은 타입의 빈이 공존하므로 전역 기본값으로 {@link Primary}를 지정한다. 한정자 없는 {@code Clock}
 * 주입은 이 빈으로 해석되고, EAI 측은 {@code @Qualifier("eaiClock")}로 명시 주입한다. 파라미터 이름 정보 ({@code -parameters})에
 * 의존하지 않으므로 IDE 직접 실행에서도 안전하다.
 */
@Configuration
public class ClockConfig {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
