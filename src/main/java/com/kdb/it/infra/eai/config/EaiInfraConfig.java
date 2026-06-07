package com.kdb.it.infra.eai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * EAI 인프라 빈 구성.
 *
 * <p>{@link EaiProperties} 바인딩 활성화, 표준전문의 비결정 필드용 운영 시임 빈
 * ({@link Clock}, GUID 난수 공급), EAI 전용 {@link RestClient}(타임아웃 적용)를 등록한다.</p>
 */
@Configuration
@EnableConfigurationProperties(EaiProperties.class)
public class EaiInfraConfig {

    /** GUID 9자리 난수 생성기(스레드 안전). */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** EAI 전용 시각 시임. 빈명을 명시해 전역 Clock과 충돌을 피한다. */
    @Bean
    public Clock eaiClock() {
        return Clock.systemDefaultZone();
    }

    /** GUID 난수부(9자리) 공급. ePAMS getRandomNum(9)와 동일 규격. */
    @Bean
    public Supplier<String> eaiGuidRandom() {
        return () -> String.format("%09d", SECURE_RANDOM.nextInt(999_999_999) + 1);
    }

    /** EAI 전용 RestClient — octet-stream 바이트 송수신, 연결/읽기 타임아웃 적용. */
    @Bean
    public RestClient eaiRestClient(EaiProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(props.connectTimeout()));
        rf.setReadTimeout(Duration.ofMillis(props.readTimeout()));
        return RestClient.builder().requestFactory(rf).build();
    }
}
