package com.kdb.it.common.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** ClientIpResolver 단위 테스트 (T9c) — 멀티 IP 분리 + 신뢰 프록시 게이트. */
class ClientIpResolverTest {

    private HttpServletRequest req(String remoteAddr, String xff) {
        HttpServletRequest r = Mockito.mock(HttpServletRequest.class);
        Mockito.when(r.getRemoteAddr()).thenReturn(remoteAddr);
        Mockito.when(r.getHeader("X-Forwarded-For")).thenReturn(xff);
        return r;
    }

    @Test
    @DisplayName("신뢰 프록시에서 온 멀티 IP XFF는 첫 번째 IP만 반환한다")
    void trustedProxy_multiIp_firstOnly() {
        HttpServletRequest r = req("10.0.0.1", "203.0.113.7, 10.0.0.1");
        assertThat(ClientIpResolver.resolve(r, Set.of("10.0.0.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("신뢰되지 않은 직접 연결이면 XFF를 무시하고 remoteAddr을 반환한다")
    void untrustedProxy_ignoresXff() {
        HttpServletRequest r = req("198.51.100.9", "1.1.1.1");
        assertThat(ClientIpResolver.resolve(r, Set.of("10.0.0.1"))).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("allowlist가 비어 있으면(미설정) remoteAddr을 그대로 사용한다")
    void emptyAllowlist_usesRemoteAddr() {
        HttpServletRequest r = req("203.0.113.50", "1.1.1.1, 2.2.2.2");
        assertThat(ClientIpResolver.resolve(r, Set.of())).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("XFF가 없으면 remoteAddr을 반환한다")
    void noXff_remoteAddr() {
        HttpServletRequest r = req("203.0.113.77", null);
        assertThat(ClientIpResolver.resolve(r, Set.of("203.0.113.77"))).isEqualTo("203.0.113.77");
    }

    // ─── 추가: 미커버 분기 보완 ───────────────────────────────────────

    @Nested
    @DisplayName("trustedProxies null 처리")
    class NullTrustedProxies {

        @Test
        @DisplayName("trustedProxies가 null이면 XFF를 신뢰하지 않고 remoteAddr을 반환한다")
        void nullTrustedProxies_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.5", "1.2.3.4");

            // Act
            String result = ClientIpResolver.resolve(r, null);

            // Assert
            assertThat(result).isEqualTo("10.0.0.5");
        }
    }

    @Nested
    @DisplayName("신뢰 프록시 + 단일 XFF IP")
    class TrustedProxySingleIp {

        @Test
        @DisplayName("신뢰 프록시에서 온 단일 IP XFF는 그 IP를 그대로 반환한다")
        void trustedProxy_singleIp_returnsXffIp() {
            // Arrange
            HttpServletRequest r = req("10.0.0.2", "203.0.113.99");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.2"));

            // Assert
            assertThat(result).isEqualTo("203.0.113.99");
        }
    }

    @Nested
    @DisplayName("XFF 빈값/공백 처리")
    class BlankXff {

        @Test
        @DisplayName("신뢰 프록시이지만 XFF가 빈 문자열이면 remoteAddr을 반환한다")
        void trustedProxy_blankXff_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.3", "");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.3"));

            // Assert
            assertThat(result).isEqualTo("10.0.0.3");
        }

        @Test
        @DisplayName("신뢰 프록시이지만 XFF가 공백만 있으면 remoteAddr을 반환한다")
        void trustedProxy_whitespaceOnlyXff_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.4", "   ");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.4"));

            // Assert
            assertThat(result).isEqualTo("10.0.0.4");
        }
    }

    @Nested
    @DisplayName("XFF 값이 'unknown'인 경우")
    class UnknownXff {

        @Test
        @DisplayName("신뢰 프록시이지만 XFF가 'unknown'(소문자)이면 remoteAddr을 반환한다")
        void trustedProxy_unknownXff_lowercase_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.6", "unknown");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.6"));

            // Assert
            assertThat(result).isEqualTo("10.0.0.6");
        }

        @Test
        @DisplayName("신뢰 프록시이지만 XFF가 'UNKNOWN'(대문자)이면 remoteAddr을 반환한다")
        void trustedProxy_unknownXff_uppercase_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.7", "UNKNOWN");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.7"));

            // Assert
            assertThat(result).isEqualTo("10.0.0.7");
        }

        @Test
        @DisplayName("신뢰 프록시이지만 XFF가 'Unknown'(혼합 대소문자)이면 remoteAddr을 반환한다")
        void trustedProxy_unknownXff_mixedCase_usesRemoteAddr() {
            // Arrange
            HttpServletRequest r = req("10.0.0.8", "Unknown");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.8"));

            // Assert
            assertThat(result).isEqualTo("10.0.0.8");
        }
    }

    @Nested
    @DisplayName("XFF 앞에 공백이 포함된 멀티 IP")
    class XffWithSpaces {

        @Test
        @DisplayName("신뢰 프록시 + 공백 포함 멀티 IP XFF에서 최좌측 IP를 트림해 반환한다")
        void trustedProxy_multiIpWithLeadingSpace_firstTrimmed() {
            // Arrange: 첫 번째 토큰이 " 203.0.113.1" 처럼 앞에 공백 있을 수 있음
            HttpServletRequest r = req("10.0.0.9", " 203.0.113.1, 10.0.0.9");

            // Act
            String result = ClientIpResolver.resolve(r, Set.of("10.0.0.9"));

            // Assert: split(",")[0].trim() 결과
            assertThat(result).isEqualTo("203.0.113.1");
        }
    }
}
