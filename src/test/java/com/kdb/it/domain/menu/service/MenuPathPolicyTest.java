package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                "/budget\uFEFFlist",
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
                "https://example.com/a\uFEFFb",
                "https://example.com:70000/manual"
            })
    void unsafeExternalUrls_areRejected(String value) {
        assertThat(MenuPathPolicy.isExternalHttpUrl(value)).isFalse();
    }

    @Test
    @DisplayName("isPreparing: /preparing/ \uC811\uB450\uB97C \uAC00\uC9C4 \uACBD\uB85C\uB9CC \uC900\uBE44\uC911\uC73C\uB85C \uBCF8\uB2E4")
    void isPreparing_\uC811\uB450\uC77C\uCE58\uB9CC\uCC38() {
        assertThat(MenuPathPolicy.isPreparing("/preparing/mnu0001018")).isTrue();
        assertThat(MenuPathPolicy.isPreparing("/preparing/cdp")).isTrue();
        // slug \uC5C6\uB294 `/preparing`\uC740 \uBA54\uB274\uAC00 \uAC00\uB9AC\uD0A4\uB294 \uACBD\uB85C\uAC00 \uC544\uB2C8\uB2E4
        assertThat(MenuPathPolicy.isPreparing("/preparing")).isFalse();
        assertThat(MenuPathPolicy.isPreparing("/budget/list")).isFalse();
        assertThat(MenuPathPolicy.isPreparing(null)).isFalse();
    }
}
