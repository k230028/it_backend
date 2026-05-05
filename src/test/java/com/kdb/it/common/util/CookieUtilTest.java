package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * CookieUtil 단위 테스트
 *
 * <p>
 * JWT 쿠키의 보안 속성과 만료 시간이 인증 설정과 어긋나지 않는지 검증합니다.
 * </p>
 */
class CookieUtilTest {

    @Test
    @DisplayName("createAccessTokenCookie - Access Token 쿠키는 15분 후 만료된다")
    void createAccessTokenCookie_만료시간_15분() {
        // given
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());

        // when
        ResponseCookie cookie = cookieUtil.createAccessTokenCookie("access-token");

        // then
        assertThat(cookie.getName()).isEqualTo(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(15 * 60);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("createRefreshTokenCookie - Refresh Token 쿠키는 인증 경로로만 제한된다")
    void createRefreshTokenCookie_경로제한() {
        // given
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());

        // when
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie("refresh-token");

        // then
        assertThat(cookie.getName()).isEqualTo(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(cookie.isHttpOnly()).isTrue();
    }
}
