package com.kdb.it.common.sso;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * SSO 연동 인프라 빈 구성.
 *
 * <p>{@link SsoProperties} 바인딩을 활성화하고, ESSO 인증서버 통신용 {@link RestClient} (연결/읽기 타임아웃 적용)를 등록합니다. 구
 * JSP Web Agent의 commons-httpclient를 대체합니다.
 */
@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoInfraConfig {

    /** ESSO 전용 RestClient — checkserver/token 검증 호출에 타임아웃을 적용합니다. */
    @Bean
    public RestClient ssoRestClient(SsoProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(props.connectTimeout()));
        rf.setReadTimeout(Duration.ofMillis(props.readTimeout()));
        return RestClient.builder().requestFactory(rf).build();
    }
}
