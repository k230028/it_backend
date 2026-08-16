package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserNameResolverTest {

    @Test
    @DisplayName("조회된 사용자명이 있으면 그대로 사용")
    void resolve_lookedUpName_wins() {
        assertThat(UserNameResolver.resolve("K230033", "홍길동")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("조회 실패 + 저장값에 비ASCII가 섞이면 저장값을 이름으로 사용")
    void resolve_storedNameFallback() {
        assertThat(UserNameResolver.resolve("홍길동", null)).isEqualTo("홍길동");
        assertThat(UserNameResolver.resolve("김보람", "")).isEqualTo("김보람");
        assertThat(UserNameResolver.resolve("  박문수  ", null)).isEqualTo("박문수");
    }

    @Test
    @DisplayName("조회 실패 + 저장값이 ASCII면 미등록·퇴직 사번으로 보고 노출하지 않음")
    void resolve_employeeNoWithoutUser_null() {
        assertThat(UserNameResolver.resolve("K230033", null)).isNull();
        assertThat(UserNameResolver.resolve("k000001", "  ")).isNull();
        assertThat(UserNameResolver.resolve("TEST001", null)).isNull();
    }

    @Test
    @DisplayName("저장값이 비어 있으면 null")
    void resolve_blankStoredValue_null() {
        assertThat(UserNameResolver.resolve(null, null)).isNull();
        assertThat(UserNameResolver.resolve("   ", null)).isNull();
    }
}
