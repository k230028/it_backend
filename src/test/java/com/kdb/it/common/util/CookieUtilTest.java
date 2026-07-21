package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.dto.AuthDto;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CookieUtil 단위 테스트
 *
 * <p>JWT 쿠키의 보안 속성과 만료 시간이 인증 설정과 어긋나지 않는지 검증합니다. secureCookie 분기(true/false), SSO 쿠키 생성/삭제, 사용자 정보
 * 쿠키 등 모든 퍼블릭 메서드와 분기 경로를 커버합니다.
 */
class CookieUtilTest {

    // ─────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────

    /** secureCookie=false(기본값)인 CookieUtil 인스턴스를 생성합니다. */
    private CookieUtil cookieUtil() {
        return new CookieUtil(new ObjectMapper());
    }

    /**
     * secureCookie 플래그를 원하는 값으로 설정한 CookieUtil 인스턴스를 생성합니다.
     *
     * @param secure Secure 플래그 값
     */
    private CookieUtil cookieUtil(boolean secure) {
        CookieUtil util = new CookieUtil(new ObjectMapper());
        ReflectionTestUtils.setField(util, "secureCookie", secure);
        return util;
    }

    // ─────────────────────────────────────────────────────────────────
    // createAccessTokenCookie
    // ─────────────────────────────────────────────────────────────────

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
    @DisplayName("createAccessTokenCookie - secure=false 일 때 Secure 플래그가 없다")
    void createAccessTokenCookie_secureFalse_플래그없음() {
        // given
        CookieUtil util = cookieUtil(false);

        // when
        ResponseCookie cookie = util.createAccessTokenCookie("token");

        // then
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    @DisplayName("createAccessTokenCookie - secure=true 일 때 Secure 플래그가 설정된다")
    void createAccessTokenCookie_secureTrue_플래그설정() {
        // given
        CookieUtil util = cookieUtil(true);

        // when
        ResponseCookie cookie = util.createAccessTokenCookie("token");

        // then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getName()).isEqualTo(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(15 * 60L);
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("createAccessTokenCookie - 토큰 값이 쿠키 값으로 설정된다")
    void createAccessTokenCookie_토큰값_검증() {
        // given
        String token = "eyJhbGciOiJIUzI1NiJ9.test";
        CookieUtil util = cookieUtil();

        // when
        ResponseCookie cookie = util.createAccessTokenCookie(token);

        // then
        assertThat(cookie.getValue()).isEqualTo(token);
    }

    // ─────────────────────────────────────────────────────────────────
    // createRefreshTokenCookie
    // ─────────────────────────────────────────────────────────────────

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
    @DisplayName("createRefreshTokenCookie - secure=false 일 때 Secure 플래그가 없다")
    void createRefreshTokenCookie_secureFalse_플래그없음() {
        // given
        CookieUtil util = cookieUtil(false);

        // when
        ResponseCookie cookie = util.createRefreshTokenCookie("refresh-token");

        // then
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("createRefreshTokenCookie - secure=true 일 때 Secure 플래그가 설정된다")
    void createRefreshTokenCookie_secureTrue_플래그설정() {
        // given
        CookieUtil util = cookieUtil(true);

        // when
        ResponseCookie cookie = util.createRefreshTokenCookie("refresh-token");

        // then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getName()).isEqualTo(CookieUtil.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(7 * 24 * 60 * 60L);
    }

    // ─────────────────────────────────────────────────────────────────
    // deleteAccessTokenCookie
    // ─────────────────────────────────────────────────────────────────

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
    @DisplayName("deleteAccessTokenCookie - secure=true 일 때 Secure 플래그가 설정된다")
    void deleteAccessTokenCookie_secureTrue_플래그설정() {
        // given
        CookieUtil util = cookieUtil(true);

        // when
        ResponseCookie cookie = util.deleteAccessTokenCookie();

        // then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("deleteAccessTokenCookie - secure=false 일 때 Secure 플래그가 없다")
    void deleteAccessTokenCookie_secureFalse_플래그없음() {
        // given
        CookieUtil util = cookieUtil(false);

        // when
        ResponseCookie cookie = util.deleteAccessTokenCookie();

        // then
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    // ─────────────────────────────────────────────────────────────────
    // deleteRefreshTokenCookie
    // ─────────────────────────────────────────────────────────────────

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
    @DisplayName("deleteRefreshTokenCookie - secure=true 일 때 Secure 플래그가 설정된다")
    void deleteRefreshTokenCookie_secureTrue_플래그설정() {
        // given
        CookieUtil util = cookieUtil(true);

        // when
        ResponseCookie cookie = util.deleteRefreshTokenCookie();

        // then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
    }

    @Test
    @DisplayName("deleteRefreshTokenCookie - secure=false 일 때 Secure 플래그가 없다")
    void deleteRefreshTokenCookie_secureFalse_플래그없음() {
        // given
        CookieUtil util = cookieUtil(false);

        // when
        ResponseCookie cookie = util.deleteRefreshTokenCookie();

        // then
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    // ─────────────────────────────────────────────────────────────────
    // createSsoNextCookie
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSsoNextCookie")
    class CreateSsoNextCookieTest {

        @Test
        @DisplayName("정상 경로값을 URL 인코딩하여 sso-next 쿠키를 생성한다")
        void 정상경로_URL인코딩_쿠키생성() {
            // given
            CookieUtil util = cookieUtil();
            String next = "/info/projects/123?tab=detail";

            // when
            ResponseCookie cookie = util.createSsoNextCookie(next);
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_NEXT_COOKIE);
            assertThat(decoded).isEqualTo(next);
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            // SSO 상태 쿠키는 10분(600초) 유지
            assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(10 * 60L);
        }

        @Test
        @DisplayName("null 값 전달 시 빈 문자열로 URL 인코딩한다")
        void null값_빈문자열_인코딩() {
            // given
            CookieUtil util = cookieUtil();

            // when
            ResponseCookie cookie = util.createSsoNextCookie(null);
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_NEXT_COOKIE);
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열 전달 시 빈 값으로 쿠키를 생성한다")
        void 빈문자열_빈값_쿠키생성() {
            // given
            CookieUtil util = cookieUtil();

            // when
            ResponseCookie cookie = util.createSsoNextCookie("");
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("secure=true 일 때 Secure 플래그가 설정된다")
        void secureTrue_플래그설정() {
            // given
            CookieUtil util = cookieUtil(true);

            // when
            ResponseCookie cookie = util.createSsoNextCookie("/home");

            // then
            assertThat(cookie.isSecure()).isTrue();
        }

        @Test
        @DisplayName("secure=false 일 때 Secure 플래그가 없다")
        void secureFalse_플래그없음() {
            // given
            CookieUtil util = cookieUtil(false);

            // when
            ResponseCookie cookie = util.createSsoNextCookie("/home");

            // then
            assertThat(cookie.isSecure()).isFalse();
        }

        @Test
        @DisplayName("특수문자(한글, 쿼리 파라미터)가 포함된 경로도 인코딩된다")
        void 특수문자포함_경로_인코딩() {
            // given
            CookieUtil util = cookieUtil();
            String next = "/info/projects?id=1&type=A";

            // when
            ResponseCookie cookie = util.createSsoNextCookie(next);
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(decoded).isEqualTo(next);
            // 원본 쿠키 값은 인코딩되어 있으므로 원문과 달라야 함
            assertThat(cookie.getValue()).isNotEqualTo(next);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // createSsoOriginCookie
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSsoOriginCookie")
    class CreateSsoOriginCookieTest {

        @Test
        @DisplayName("정상 origin 값을 URL 인코딩하여 sso-origin 쿠키를 생성한다")
        void 정상origin_URL인코딩_쿠키생성() {
            // given
            CookieUtil util = cookieUtil();
            String origin = "http://10.9.16.109:28080";

            // when
            ResponseCookie cookie = util.createSsoOriginCookie(origin);
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_ORIGIN_COOKIE);
            assertThat(decoded).isEqualTo(origin);
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(10 * 60L);
        }

        @Test
        @DisplayName("null 값 전달 시 빈 문자열로 인코딩한다")
        void null값_빈문자열_인코딩() {
            // given
            CookieUtil util = cookieUtil();

            // when
            ResponseCookie cookie = util.createSsoOriginCookie(null);
            String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_ORIGIN_COOKIE);
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("secure=true 일 때 Secure 플래그가 설정된다")
        void secureTrue_플래그설정() {
            // given
            CookieUtil util = cookieUtil(true);

            // when
            ResponseCookie cookie = util.createSsoOriginCookie("https://it.kdb.co.kr");

            // then
            assertThat(cookie.isSecure()).isTrue();
        }

        @Test
        @DisplayName("secure=false 일 때 Secure 플래그가 없다")
        void secureFalse_플래그없음() {
            // given
            CookieUtil util = cookieUtil(false);

            // when
            ResponseCookie cookie = util.createSsoOriginCookie("http://localhost:3000");

            // then
            assertThat(cookie.isSecure()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // deleteSsoNextCookie
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteSsoNextCookie")
    class DeleteSsoNextCookieTest {

        @Test
        @DisplayName("sso-next 삭제 쿠키는 maxAge=0, 빈 값, path=/ 을 갖는다")
        void 삭제쿠키_속성검증() {
            // given
            CookieUtil util = cookieUtil();

            // when
            ResponseCookie cookie = util.deleteSsoNextCookie();

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_NEXT_COOKIE);
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getMaxAge().getSeconds()).isZero();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }

        @Test
        @DisplayName("secure=true 일 때 Secure 플래그가 설정된다")
        void secureTrue_플래그설정() {
            // given
            CookieUtil util = cookieUtil(true);

            // when
            ResponseCookie cookie = util.deleteSsoNextCookie();

            // then
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getMaxAge().getSeconds()).isZero();
        }

        @Test
        @DisplayName("secure=false 일 때 Secure 플래그가 없다")
        void secureFalse_플래그없음() {
            // given
            CookieUtil util = cookieUtil(false);

            // when
            ResponseCookie cookie = util.deleteSsoNextCookie();

            // then
            assertThat(cookie.isSecure()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // deleteSsoOriginCookie
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteSsoOriginCookie")
    class DeleteSsoOriginCookieTest {

        @Test
        @DisplayName("sso-origin 삭제 쿠키는 maxAge=0, 빈 값, path=/ 을 갖는다")
        void 삭제쿠키_속성검증() {
            // given
            CookieUtil util = cookieUtil();

            // when
            ResponseCookie cookie = util.deleteSsoOriginCookie();

            // then
            assertThat(cookie.getName()).isEqualTo(CookieUtil.SSO_ORIGIN_COOKIE);
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getMaxAge().getSeconds()).isZero();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }

        @Test
        @DisplayName("secure=true 일 때 Secure 플래그가 설정된다")
        void secureTrue_플래그설정() {
            // given
            CookieUtil util = cookieUtil(true);

            // when
            ResponseCookie cookie = util.deleteSsoOriginCookie();

            // then
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getMaxAge().getSeconds()).isZero();
        }

        @Test
        @DisplayName("secure=false 일 때 Secure 플래그가 없다")
        void secureFalse_플래그없음() {
            // given
            CookieUtil util = cookieUtil(false);

            // when
            ResponseCookie cookie = util.deleteSsoOriginCookie();

            // then
            assertThat(cookie.isSecure()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // createUserInfoCookie
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createUserInfoCookie - 프론트 복원용 사용자 정보만 담은 쿠키를 생성한다")
    void createUserInfoCookie_사용자정보쿠키_생성() {
        CookieUtil cookieUtil = new CookieUtil(new ObjectMapper());
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
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
    @DisplayName("createUserInfoCookie - it-portal-user 쿠키는 7일 만료이고 httpOnly=false이다")
    void createUserInfoCookie_7일만료_httpOnlyFalse() {
        // given
        CookieUtil util = cookieUtil();
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("99999")
                        .empNm("테스트유저")
                        .athIds(List.of("ITPZZ001"))
                        .bbrC("D999")
                        .temC("T999")
                        .build();

        // when
        ResponseCookie cookie = util.createUserInfoCookie(response);

        // then
        // refresh token 수명과 동일한 7일(604800초)이어야 함
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(7 * 24 * 60 * 60L);
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName(
            "createUserInfoCookie - JSON 키 순서가 LinkedHashMap 삽입 순서(eno→empNm→athIds→bbrC→temC)와 일치한다")
    void createUserInfoCookie_JSON키순서_검증() {
        // given
        CookieUtil util = cookieUtil();
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .athIds(List.of("ITPAD001"))
                        .bbrC("D001")
                        .temC("T001")
                        .build();

        // when
        ResponseCookie cookie = util.createUserInfoCookie(response);
        String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

        // then — 삽입 순서 확인 (각 키의 위치 비교)
        int enoIdx = decoded.indexOf("\"eno\"");
        int empNmIdx = decoded.indexOf("\"empNm\"");
        int athIdsIdx = decoded.indexOf("\"athIds\"");
        int bbrCIdx = decoded.indexOf("\"bbrC\"");
        int temCIdx = decoded.indexOf("\"temC\"");

        assertThat(enoIdx).isLessThan(empNmIdx);
        assertThat(empNmIdx).isLessThan(athIdsIdx);
        assertThat(athIdsIdx).isLessThan(bbrCIdx);
        assertThat(bbrCIdx).isLessThan(temCIdx);
    }

    @Test
    @DisplayName("createUserInfoCookie - bbrC/temC null 필드도 JSON에 포함된다")
    void createUserInfoCookie_null필드_포함() {
        // given
        CookieUtil util = cookieUtil();
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("00001")
                        .empNm("김테스트")
                        .athIds(null)
                        .bbrC(null)
                        .temC(null)
                        .build();

        // when
        ResponseCookie cookie = util.createUserInfoCookie(response);
        String decoded = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

        // then — eno/empNm 필드는 반드시 포함
        assertThat(decoded).contains("\"eno\":\"00001\"");
        assertThat(decoded).contains("\"bbrC\"");
    }

    @Test
    @DisplayName("createUserInfoCookie - secure=true 일 때 Secure 플래그가 설정된다")
    void createUserInfoCookie_secureTrue_플래그설정() {
        // given
        CookieUtil util = cookieUtil(true);
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .athIds(List.of("ITPAD001"))
                        .bbrC("D001")
                        .temC("T001")
                        .build();

        // when
        ResponseCookie cookie = util.createUserInfoCookie(response);

        // then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("createUserInfoCookie - secure=false 일 때 Secure 플래그가 없다")
    void createUserInfoCookie_secureFalse_플래그없음() {
        // given
        CookieUtil util = cookieUtil(false);
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .athIds(List.of("ITPAD001"))
                        .bbrC("D001")
                        .temC("T001")
                        .build();

        // when
        ResponseCookie cookie = util.createUserInfoCookie(response);

        // then
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    @DisplayName("createUserInfoCookie - 사용자 정보 직렬화 실패 시 IllegalStateException을 던진다")
    void createUserInfoCookie_직렬화실패_IllegalStateException발생() throws Exception {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.BDDMockito.given(
                        objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .willThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        CookieUtil cookieUtil = new CookieUtil(objectMapper);

        assertThatThrownBy(
                        () ->
                                cookieUtil.createUserInfoCookie(
                                        AuthDto.LoginResponse.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용자 정보 쿠키 직렬화 실패");
    }

    // ─────────────────────────────────────────────────────────────────
    // 상수 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("쿠키 이름 상수가 정책에 정의된 값과 일치한다")
    void 쿠키이름_상수_검증() {
        assertThat(CookieUtil.ACCESS_TOKEN_COOKIE).isEqualTo("accessToken");
        assertThat(CookieUtil.REFRESH_TOKEN_COOKIE).isEqualTo("refreshToken");
        assertThat(CookieUtil.SSO_NEXT_COOKIE).isEqualTo("sso-next");
        assertThat(CookieUtil.SSO_ORIGIN_COOKIE).isEqualTo("sso-origin");
    }
}
