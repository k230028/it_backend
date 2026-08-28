package com.kdb.it.common.mfa.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MFA 공급자 선택과 외부 연동에 사용하는 설정이다.
 *
 * <p>{@code fingerVeinFixedKey}는 지정맥인증 서버와 공유하는 비밀값이므로 기본값을 두지 않고 환경변수로 주입한다.
 *
 * @param fidoRejectedStatuses 공급자 규격에서 사용자 거부로 확정한 FIDO 거래 상태값
 */
@ConfigurationProperties(prefix = "app.mfa")
public record MfaProperties(
        String endpoint,
        String siteId,
        String svcId,
        Duration connectTimeout,
        Duration readTimeout,
        boolean mockEnabled,
        Duration challengeTtl,
        int maxFailures,
        String fingerVeinFixedKey,
        Set<String> fidoRejectedStatuses) {

    private static final String DEFAULT_SITE_ID = "SIT01KDBBANK00000000";
    private static final String DEFAULT_SVC_ID = "SVC12SIT01KDBBANK000";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_CHALLENGE_TTL = Duration.ofSeconds(90);
    private static final int DEFAULT_MAX_FAILURES = 5;

    /** 누락된 선택 속성에 안전한 기본값을 적용하고 유효하지 않은 제한값을 거부한다. */
    public MfaProperties {
        endpoint = endpoint == null ? "" : endpoint.trim();
        siteId = defaultIfBlank(siteId, DEFAULT_SITE_ID);
        svcId = defaultIfBlank(svcId, DEFAULT_SVC_ID);
        connectTimeout = defaultIfNull(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        readTimeout = defaultIfNull(readTimeout, DEFAULT_READ_TIMEOUT);
        challengeTtl = defaultIfNull(challengeTtl, DEFAULT_CHALLENGE_TTL);
        maxFailures = maxFailures <= 0 ? DEFAULT_MAX_FAILURES : maxFailures;
        fingerVeinFixedKey = fingerVeinFixedKey == null ? "" : fingerVeinFixedKey.trim();
        fidoRejectedStatuses =
                fidoRejectedStatuses == null
                        ? Set.of()
                        : fidoRejectedStatuses.stream()
                                .filter(value -> value != null && !value.isBlank())
                                .map(String::trim)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        requirePositive(connectTimeout, "app.mfa.connect-timeout");
        requirePositive(readTimeout, "app.mfa.read-timeout");
        requirePositive(challengeTtl, "app.mfa.challenge-ttl");
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static Duration defaultIfNull(Duration value, Duration defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static void requirePositive(Duration value, String propertyName) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalStateException(propertyName + " must be positive");
        }
    }
}
