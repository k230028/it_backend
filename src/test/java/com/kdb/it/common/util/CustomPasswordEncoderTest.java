package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CustomPasswordEncoder 단위 테스트
 *
 * <p>순수 Java 클래스로 Spring 의존성이 없으므로 직접 인스턴스화하여 테스트합니다.
 */
class CustomPasswordEncoderTest {

    private CustomPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new CustomPasswordEncoder();
    }

    @Test
    @DisplayName("encode - 평문 입력 시 non-empty Base64 해시 반환")
    void encode_평문입력_Base64해시반환() {
        // given
        String rawPassword = "password123";

        // when
        String encoded = encoder.encode(rawPassword);

        // then
        assertThat(encoded).isNotNull().isNotEmpty();
        // Base64 문자셋 검증 (A-Za-z0-9+/=)
        assertThat(encoded).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    @DisplayName("encode - 동일 평문은 항상 동일 해시 반환 (결정론적)")
    void encode_동일평문_동일해시반환() {
        // given
        String rawPassword = "myPassword";

        // when
        String first = encoder.encode(rawPassword);
        String second = encoder.encode(rawPassword);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("encode - 서로 다른 평문은 서로 다른 해시 반환")
    void encode_다른평문_다른해시반환() {
        // given & when
        String hash1 = encoder.encode("password1");
        String hash2 = encoder.encode("password2");

        // then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("matches - 일치하는 비밀번호는 true 반환")
    void matches_일치하는비밀번호_true반환() {
        // given
        String rawPassword = "mySecretPassword";
        String encodedPassword = encoder.encode(rawPassword);

        // when & then
        assertThat(encoder.matches(rawPassword, encodedPassword)).isTrue();
    }

    @Test
    @DisplayName("matches - 불일치 비밀번호는 false 반환")
    void matches_불일치비밀번호_false반환() {
        // given
        String rawPassword = "correctPassword";
        String encodedPassword = encoder.encode(rawPassword);

        // when & then
        assertThat(encoder.matches("wrongPassword", encodedPassword)).isFalse();
    }

    @Test
    @DisplayName("encode - 빈 문자열 입력 시 알려진 SHA-256 Base64 해시 반환")
    void encode_빈문자열_알려진해시반환() {
        // given: SHA-256("") = e3b0c44298fc1c149afb... → Base64 =
        // "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
        String emptyInput = "";

        // when
        String encoded = encoder.encode(emptyInput);

        // then
        assertThat(encoded).isEqualTo("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=");
    }
}
