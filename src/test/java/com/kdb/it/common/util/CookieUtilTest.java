package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.dto.AuthDto;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test
    @DisplayName("deleteAccessTokenCookie - Access Token 삭제 쿠키는 즉시 만료된다")
    void deleteAccessTokenCookie_즉시만료() {
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());

        ResponseCookie cookie = cookieUtil.deleteAccessTokenCookie();

        assertThat(cookie.getName()).isEqualTo(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("deleteRefreshTokenCookie - Refresh Token 삭제 쿠키는 인증 경로에서 즉시 만료된다")
    void deleteRefreshTokenCookie_인증경로_즉시만료() {
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());

        ResponseCookie cookie = cookieUtil.deleteRefreshTokenCookie();

        assertThat(cookie.getName()).isEqualTo(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("createUserInfoCookie - 프론트 복원용 사용자 정보만 담은 쿠키를 생성한다")
    void createUserInfoCookie_사용자정보쿠키_생성() {
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());
        AuthDto.LoginResponse response = AuthDto.LoginResponse.builder()
                .eno("10001")
                .empNm("홍길동")
                .athIds(List.of("ITPZZ001", "ITPAD001"))
                .bbrC("D001")
                .temC("T001")
                .build();

        ResponseCookie cookie = cookieUtil.createUserInfoCookie(response);
        String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

        assertThat(cookie.getName()).isEqualTo("it-portal-user");
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(decoded).contains("\"eno\":\"10001\"");
        assertThat(decoded).contains("\"empNm\":\"홍길동\"");
        assertThat(decoded).contains("\"athIds\":[\"ITPZZ001\",\"ITPAD001\"]");
    }

    @Test
    @DisplayName("createUserInfoCookie - 사용자 정보 직렬화 실패 시 IllegalStateException을 던진다")
    void createUserInfoCookie_직렬화실패_IllegalStateException발생() throws Exception {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.BDDMockito.given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .willThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        CookieUtil cookieUtil = new CookieUtil(objectMapper);

        assertThatThrownBy(() -> cookieUtil.createUserInfoCookie(AuthDto.LoginResponse.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용자 정보 쿠키 직렬화 실패");
    }
}
