package com.kdb.it.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("토큰 지문 전용 키가 32바이트보다 짧으면 생성하지 않는다")
    void rejectsFingerprintSecretShorterThanSha256Minimum() {
        assertThatThrownBy(() -> new TokenFingerprint("short-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32바이트");
    }

    /*
     * 생성자 검증은 "미설정"(null·공백·미치환 placeholder)과 "너무 짧음"을 다른 예외로 구분한다.
     * 앞의 세 가지는 배포 설정 누락이라 IllegalStateException, 마지막은 잘못된 값이라
     * IllegalArgumentException이다. 분기마다 어느 쪽으로 끝나는지 고정한다.
     */

    @Test
    @DisplayName("토큰 지문 전용 키가 없으면 설정 누락으로 실패한다")
    void rejectsNullFingerprintSecret() {
        assertThatThrownBy(() -> new TokenFingerprint(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.token-fingerprint-secret");
    }

    @Test
    @DisplayName("토큰 지문 전용 키가 공백뿐이면 설정 누락으로 실패한다")
    void rejectsBlankFingerprintSecret() {
        assertThatThrownBy(() -> new TokenFingerprint("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.token-fingerprint-secret");
    }

    /* 프로퍼티가 비어 있으면 Spring이 placeholder 원문을 그대로 넘겨 32바이트를 넘겨 버린다. */
    @Test
    @DisplayName("치환되지 않은 placeholder는 길이와 무관하게 설정 누락으로 실패한다")
    void rejectsUnresolvedPlaceholder() {
        assertThatThrownBy(() -> new TokenFingerprint("${security.token-fingerprint-secret}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.token-fingerprint-secret");
    }

    /* '${'로 시작해도 '}'로 끝나지 않으면 placeholder가 아니라 정상 비밀값이다. */
    @Test
    @DisplayName("placeholder 형태가 아니면 달러 기호로 시작해도 지문을 생성한다")
    void acceptsSecretStartingWithDollarBraceButNotClosed() {
        TokenFingerprint fingerprint = new TokenFingerprint("${0123456789abcdef0123456789abcdef");

        assertThat(fingerprint.forRefreshToken("sample-token")).hasSize(64);
    }
}
