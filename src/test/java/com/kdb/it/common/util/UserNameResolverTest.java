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
    @DisplayName("조회 실패 + 저장값이 사번 형태가 아니면 저장값을 이름으로 사용")
    void resolve_storedNameFallback() {
        assertThat(UserNameResolver.resolve("홍길동", null)).isEqualTo("홍길동");
        assertThat(UserNameResolver.resolve("김보람", "")).isEqualTo("김보람");
        assertThat(UserNameResolver.resolve("  박문수  ", null)).isEqualTo("박문수");
    }

    @Test
    @DisplayName("영문 성명은 사번으로 오인하지 않고 이름으로 사용")
    void resolve_englishName_isName() {
        // ASCII만 보면 사번으로 오인해 이름 자리가 비고 화면에 조회 불가 링크가 걸린다
        assertThat(UserNameResolver.resolve("Luke Buckingham-Brown", null))
                .isEqualTo("Luke Buckingham-Brown");
        assertThat(UserNameResolver.resolve("Buckingham", null)).isEqualTo("Buckingham");
        assertThat(UserNameResolver.isStoredName("Luke Buckingham-Brown", null)).isTrue();
        assertThat(UserNameResolver.isStoredName("Buckingham", null)).isTrue();
    }

    @Test
    @DisplayName("조회 실패 + 저장값이 사번 형태면 미등록·퇴직 사번으로 보고 노출하지 않음")
    void resolve_employeeNoWithoutUser_null() {
        assertThat(UserNameResolver.resolve("K230033", null)).isNull();
        assertThat(UserNameResolver.resolve("k000001", "  ")).isNull();
        assertThat(UserNameResolver.resolve("TEST001", null)).isNull();
        assertThat(UserNameResolver.resolve("12345678", null)).isNull();
        assertThat(UserNameResolver.isStoredName("K230033", null)).isFalse();
    }

    @Test
    @DisplayName("저장값이 비어 있으면 이름도 아니고 노출도 하지 않음")
    void resolve_blankStoredValue_null() {
        assertThat(UserNameResolver.resolve(null, null)).isNull();
        assertThat(UserNameResolver.resolve("   ", null)).isNull();
        assertThat(UserNameResolver.isStoredName(null, null)).isFalse();
        assertThat(UserNameResolver.isStoredName("   ", null)).isFalse();
    }

    @Test
    @DisplayName("조회된 사용자명이 있으면 사번을 이름으로 바꾸지 않는다")
    void isStoredName_lookedUpNamePresent_false() {
        assertThat(UserNameResolver.isStoredName("Luke Buckingham-Brown", "루크")).isFalse();
        assertThat(UserNameResolver.isStoredName("홍길동", "홍길동")).isFalse();
    }
}
