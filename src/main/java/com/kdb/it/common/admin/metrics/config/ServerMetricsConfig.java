package com.kdb.it.common.admin.metrics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 서버 자원 사용량 수집 설정 등록. 피어 호출용 RestClient는 {@code wasLogPeerRestClient}를 재사용한다. */
@Configuration
@EnableConfigurationProperties(ServerMetricsProperties.class)
public class ServerMetricsConfig {}
