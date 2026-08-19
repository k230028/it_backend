package com.kdb.it.common.admin.waslog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** WAS 로그 뷰어 설정 등록. */
@Configuration
@EnableConfigurationProperties(WasLogProperties.class)
public class WasLogConfig {}
