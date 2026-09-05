package com.kdb.it.common.approval.itbudget.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 전산예산 미리보기 토큰의 활성·직전 HMAC 키와 수명을 바인딩한다. */
@ConfigurationProperties("app.approval.it-budget.preview")
public record ItBudgetPreviewProperties(
        String activeKeyId,
        String activeSigningKey,
        String previousKeyId,
        String previousSigningKey,
        Duration ttl) {}
