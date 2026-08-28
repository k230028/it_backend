package com.kdb.it.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenFingerprintTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("Refresh Token 지문은 목적 문자열을 포함한 HMAC-SHA256 고정 벡터와 일치한다")
    void fingerprintsRefreshTokenWithDomainSeparatedHmacSha256() {
        TokenFingerprint fingerprint = new TokenFingerprint(KEY);

        assertThat(fingerprint.forRefreshToken("sample-token"))
                .isEqualTo("c6b557aff144c24eaf3aaa107206d27e70ea96d9814bcd8b62d486a816657972");
    }

    @Test
    @DisplayName("같은 원문도 MFA와 Refresh Token 지문은 서로 다르다")
    void separatesMfaAndRefreshTokenDomains() {
        TokenFingerprint fingerprint = new TokenFingerprint(KEY);

        assertThat(fingerprint.forMfa("sample-token"))
                .isEqualTo("545ecd32668bf3afdb40be141cd1a06d1e3eeb937e32260a9364e29a80827b29")
                .isNotEqualTo(fingerprint.forRefreshToken("sample-token"));
    }
}
