package com.kdb.it.common.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 알림 재시도 스케줄링을 활성화합니다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class NotificationSchedulingConfig {
}
