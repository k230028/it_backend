package com.kdb.it.common.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DevAuthController Web 계층 테스트
 *
 * <p>개발용 사용자 목록 필터링과 사용자 전환 시 쿠키 발급 계약을 검증합니다.
 */
@WebMvcTest(DevAuthController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = "app.dev.user-switch.enabled=true")
class DevAuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserRepository userRepository;

    @MockitoBean private AuthService authService;

    @MockitoBean private CookieUtil cookieUtil;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/auth/dev/users: 삭제되지 않은 사용자만 목록으로 반환한다")
    void listUsers_활성사용자만반환() throws Exception {
        CuserI active = CuserI.builder().eno("10001").usrNm("홍길동").bbrC("101").delYn("N").build();
        CuserI deleted = CuserI.builder().eno("99999").usrNm("삭제사용자").delYn("Y").build();
        given(userRepository.findAllByOrderByUsrNmAsc()).willReturn(List.of(active, deleted));

        mockMvc.perform(get("/api/auth/dev/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eno").value("10001"))
                .andExpect(jsonPath("$[0].usrNm").value("홍길동"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/auth/dev/switch-user: 토큰 쿠키 세 개와 사용자 응답을 반환한다")
    void switchUser_정상요청_쿠키발급() throws Exception {
        DevAuthController.SwitchRequest request = new DevAuthController.SwitchRequest();
        request.setEno("10001");
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueDevSwitchTokens("10001")).willReturn(response);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from("access_token", "access-token").path("/").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(
                        ResponseCookie.from("refresh_token", "refresh-token").path("/").build());
        given(cookieUtil.createUserInfoCookie(response))
                .willReturn(ResponseCookie.from("it-portal-user", "user-info").path("/").build());

        mockMvc.perform(
                        post("/api/auth/dev/switch-user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(
                        header().stringValues(
                                        "Set-Cookie",
                                        "access_token=access-token; Path=/",
                                        "refresh_token=refresh-token; Path=/",
                                        "it-portal-user=user-info; Path=/"))
                .andExpect(jsonPath("$.eno").value("10001"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/dev/switch-user: 사번이 비어있으면 요청을 거부한다")
    void switchUser_사번없음_400() throws Exception {
        mockMvc.perform(
                        post("/api/auth/dev/switch-user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"eno\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).issueDevSwitchTokens(anyString());
    }

    // ─── SwitchRequest DTO 단위 테스트 — Spring 컨텍스트 불필요 ────────

    /**
     * SwitchRequest DTO의 접근자·동등성·toString 분기 커버리지 보완.
     *
     * <p>Lombok {@code @Getter}/{@code @Setter}/{@code @NoArgsConstructor} 기반이므로 equals/hashCode는
     * {@link Object} 기본 구현(참조 동일성)을 사용합니다. JaCoCo가 집계하는 분기는 Lombok 접근자 null 체크·생성자 경로 등입니다.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("SwitchRequest DTO 단위 테스트")
    class SwitchRequestTest {

        @Test
        @DisplayName("기본 생성자로 생성하면 eno는 null이다")
        void noArgsConstructor_enoIsNull() {
            // Arrange & Act
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();

            // Assert
            assertThat(req.getEno()).isNull();
        }

        @Test
        @DisplayName("setEno 후 getEno로 동일 값을 조회할 수 있다")
        void setter_getter_roundTrip() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();

            // Act
            req.setEno("12345");

            // Assert
            assertThat(req.getEno()).isEqualTo("12345");
        }

        @Test
        @DisplayName("eno를 다른 값으로 덮어쓰면 최신 값이 반환된다")
        void setter_overwrite_returnsLatestValue() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("00001");

            // Act
            req.setEno("99999");

            // Assert
            assertThat(req.getEno()).isEqualTo("99999");
        }

        @Test
        @DisplayName("같은 인스턴스를 equals로 비교하면 true이다 (참조 동일성)")
        void equals_sameInstance_true() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("10001");

            // Act & Assert
            assertThat(req.equals(req)).isTrue();
        }

        @Test
        @DisplayName("eno가 같아도 다른 인스턴스를 equals로 비교하면 false이다 (Object 기본 동일성)")
        void equals_differentInstance_sameEno_false() {
            // Arrange
            DevAuthController.SwitchRequest req1 = new DevAuthController.SwitchRequest();
            req1.setEno("10001");
            DevAuthController.SwitchRequest req2 = new DevAuthController.SwitchRequest();
            req2.setEno("10001");

            // Act & Assert — Object.equals()는 참조 동일성이므로 false
            assertThat(req1.equals(req2)).isFalse();
        }

        @Test
        @DisplayName("eno가 다른 두 인스턴스를 equals로 비교하면 false이다")
        void equals_differentInstance_differentEno_false() {
            // Arrange
            DevAuthController.SwitchRequest req1 = new DevAuthController.SwitchRequest();
            req1.setEno("10001");
            DevAuthController.SwitchRequest req2 = new DevAuthController.SwitchRequest();
            req2.setEno("20002");

            // Act & Assert
            assertThat(req1.equals(req2)).isFalse();
        }

        @Test
        @DisplayName("null과 equals로 비교하면 false이다")
        void equals_null_false() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("10001");

            // Act & Assert
            assertThat(req.equals(null)).isFalse();
        }

        @Test
        @DisplayName("다른 타입(String)과 equals로 비교하면 false이다")
        void equals_differentType_false() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("10001");

            // Act & Assert
            assertThat(req.equals("10001")).isFalse();
        }

        @Test
        @DisplayName("hashCode는 두 번 호출해도 동일한 값을 반환한다 (안정성)")
        void hashCode_stable() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("10001");

            // Act
            int h1 = req.hashCode();
            int h2 = req.hashCode();

            // Assert
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("toString은 null이 아닌 문자열을 반환한다")
        void toString_returnsNonNull() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("10001");

            // Act
            String s = req.toString();

            // Assert
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("eno에 null을 설정하면 getEno가 null을 반환한다")
        void setEno_null_getEnoReturnsNull() {
            // Arrange
            DevAuthController.SwitchRequest req = new DevAuthController.SwitchRequest();
            req.setEno("before");

            // Act
            req.setEno(null);

            // Assert
            assertThat(req.getEno()).isNull();
        }
    }
}
