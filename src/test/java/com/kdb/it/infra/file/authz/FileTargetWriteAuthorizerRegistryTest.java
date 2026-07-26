package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FileTargetWriteAuthorizerRegistryTest {

    private final CustomUserDetails user =
            new CustomUserDetails("E001", List.of(CustomUserDetails.ATH_USER), "D001");

    @Test
    @DisplayName("등록 종류의 대상 판정이 거부되면 403 예외를 던진다")
    void registeredKindDenied() {
        FileTargetWriteAuthorizer authorizer = mock(FileTargetWriteAuthorizer.class);
        given(authorizer.supportedPkColNms()).willReturn(Set.of("검토의견"));
        given(authorizer.canWrite("101", user)).willReturn(false);
        FileTargetWriteAuthorizerRegistry registry =
                new FileTargetWriteAuthorizerRegistry(List.of(authorizer));

        assertThatThrownBy(() -> registry.verifyTargetWriteAccess("검토의견", "101", user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("첨부 대상 쓰기 권한이 없습니다");
    }

    @Test
    @DisplayName("등록 종류의 대상 판정이 허용되면 통과한다")
    void registeredKindAllowed() {
        FileTargetWriteAuthorizer authorizer = mock(FileTargetWriteAuthorizer.class);
        given(authorizer.supportedPkColNms()).willReturn(Set.of("공통게시판"));
        given(authorizer.canWrite("NAC-2026-0001", user)).willReturn(true);
        FileTargetWriteAuthorizerRegistry registry =
                new FileTargetWriteAuthorizerRegistry(List.of(authorizer));

        assertThatCode(() -> registry.verifyTargetWriteAccess("공통게시판", "NAC-2026-0001", user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("미등록 레거시 종류는 기존 동작을 보존해 대상 검증을 생략한다")
    void unregisteredLegacyKindPreservesExistingBehavior() {
        FileTargetWriteAuthorizerRegistry registry =
                new FileTargetWriteAuthorizerRegistry(List.of());

        assertThatCode(() -> registry.verifyTargetWriteAccess("요구사항정의서", "DOC-2026-0001", user))
                .doesNotThrowAnyException();
    }
}
