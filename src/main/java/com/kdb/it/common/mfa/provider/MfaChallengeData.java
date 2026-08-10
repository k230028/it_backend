package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Objects;

/** 화면에 반환 가능한 MFA challenge 정보이며 외부 토큰이나 원본 응답은 포함하지 않는다. */
public record MfaChallengeData(String challengeId, String qrData, Instant expiresAt) {

    public MfaChallengeData {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challenge 식별자는 필수입니다.");
        }
        Objects.requireNonNull(expiresAt, "MFA 만료 시각은 필수입니다.");
    }
}
