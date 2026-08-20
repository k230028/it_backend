package com.kdb.it.common.admin.waslog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** WAS 로그 뷰어 설정 등록과 피어 호출용 RestClient. */
@Configuration
@EnableConfigurationProperties(WasLogProperties.class)
public class WasLogConfig {

    /**
     * 피어 위임 호출 전용 RestClient.
     *
     * <p>화면 응답성을 위해 타임아웃을 짧게 잡는다. 실패는 예외로 올라가 응답의 {@code peerError}로 표면화된다.
     */
    @Bean
    public RestClient wasLogPeerRestClient(WasLogProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
