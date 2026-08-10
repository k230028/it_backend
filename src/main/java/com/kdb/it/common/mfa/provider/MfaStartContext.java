package com.kdb.it.common.mfa.provider;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import java.time.Instant;
import java.util.Objects;

/** MFA challenge 생성에 필요한 서버 소유 거래 정보이다. */
public record MfaStartContext(
        String transactionId, String eno, MfaPurpose purpose, Instant expiresAt) {

    public MfaStartContext {
        requireText(transactionId, "MFA 거래 식별자");
        requireText(eno, "사원번호");
        Objects.requireNonNull(purpose, "MFA 목적은 필수입니다.");
        Objects.requireNonNull(expiresAt, "MFA 만료 시각은 필수입니다.");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은 필수입니다.");
        }
    }
}
