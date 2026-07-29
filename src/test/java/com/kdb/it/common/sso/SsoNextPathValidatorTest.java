package com.kdb.it.common.sso;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SsoNextPathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/info/projects", "/info/projects?tab=1#top"})
    void safePath_내부경로는허용(String value) {
        assertThat(SsoNextPathValidator.safePath(value)).contains(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "https://evil.example",
                "//evil.example",
                "/\\evil.example",
                "/login",
                "/login?next=/",
                "/login/callback"
            })
    void safePath_외부경로와로그인재진입은거부(String value) {
        assertThat(SsoNextPathValidator.safePath(value)).isEmpty();
        assertThat(SsoNextPathValidator.safePathOrRoot(value)).isEqualTo("/");
    }
}
