package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MenuPathPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"/budget/list", "/board/BLBM-0001", "/info/list?status=open"})
    void internalPaths_areAccepted(String value) {
        assertThat(MenuPathPolicy.isInternal(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "//evil.example/x",
                "/\\evil.example/path",
                "budget/list",
                "/budget list",
                "/budget\u00A0list",
                "/budget\u202Flist",
                "https://example.com"
            })
    void nonInternalPaths_areRejected(String value) {
        assertThat(MenuPathPolicy.isInternal(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/manual", "http://docs.example.com:8080/a?b=1"})
    void externalHttpUrls_areAccepted(String value) {
        assertThat(MenuPathPolicy.isExternalHttpUrl(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "data:text/html,test",
                "//example.com/manual",
                "https://user:pass@example.com/manual",
                "https:///missing-host",
                "https://example.com/a b",
                "https://example.com:70000/manual"
            })
    void unsafeExternalUrls_areRejected(String value) {
        assertThat(MenuPathPolicy.isExternalHttpUrl(value)).isFalse();
    }
}
