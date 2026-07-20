package com.kdb.it.infra.eai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** GWE 전문 인터페이스 식별자 설정입니다. */
@ConfigurationProperties(prefix = "eai.gwe")
public record GweProperties(String ifId) {

    public GweProperties {
        if (ifId == null || ifId.isBlank()) {
            ifId = "IPPG00000001";
        }
        if (ifId.length() != 12) {
            throw new IllegalStateException("eai.gwe.if-id는 12자리여야 합니다: " + ifId);
        }
    }
}
