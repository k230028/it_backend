package com.kdb.it.common.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ClientIpResolver 단위 테스트 (T9c) — 멀티 IP 분리 + 신뢰 프록시 게이트.
 */
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
}
