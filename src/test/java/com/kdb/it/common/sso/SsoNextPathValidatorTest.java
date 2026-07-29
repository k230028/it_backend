package com.kdb.it.common.sso;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SsoNextPathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/info/projects", "/info/projects?tab=1#top"})
    void safePath_내부경로는허용(String value) {
        assertThat(SsoNextPathValidator.safePath(value)).contains(value);
    }

    @ParameterizedTest
    @CsvSource({
        "'/a/../info?q=1#top', '/info?q=1#top'",
        "'/%2e/info/projects', '/info/projects'",
        "'/a/%2E%2e/info', '/info'",
        "'/info/%70rojects', '/info/projects'",
        "'/loginish', '/loginish'"
    })
    void safePath_내부경로는브라우저기준으로정규화(String value, String expected) {
        assertThat(SsoNextPathValidator.safePath(value)).contains(expected);
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
                "/LOGIN",
                "/login?next=/",
                "/login/callback",
                "/%6cogin",
                "/../LOGIN",
                "/%2e%2e/LOGIN",
                "/info\r\nLocation:https://evil.example",
                "/info%0d%0aLocation:evil",
                "/invalid%"
            })
    void safePath_외부경로와로그인재진입은거부(String value) {
        assertThat(SsoNextPathValidator.safePath(value)).isEmpty();
        assertThat(SsoNextPathValidator.safePathOrRoot(value)).isEqualTo("/");
    }
}
