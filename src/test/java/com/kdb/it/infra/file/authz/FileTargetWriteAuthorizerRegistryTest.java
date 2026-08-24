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
        given(authorizer.supportedApgFlKdNms()).willReturn(Set.of("검토의견"));
        given(authorizer.canWrite("101", user)).willReturn(false);
        FileTargetWriteAuthorizerRegistry registry = registryOf(List.of(authorizer));

        assertThatThrownBy(() -> registry.verifyTargetWriteAccess("검토의견", "101", user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("첨부 대상 쓰기 권한이 없습니다");
    }

    @Test
    @DisplayName("등록 종류의 대상 판정이 허용되면 통과한다")
    void registeredKindAllowed() {
        FileTargetWriteAuthorizer authorizer = mock(FileTargetWriteAuthorizer.class);
        given(authorizer.supportedApgFlKdNms()).willReturn(Set.of("공통게시판"));
        given(authorizer.canWrite("NAC-2026-0001", user)).willReturn(true);
        FileTargetWriteAuthorizerRegistry registry = registryOf(List.of(authorizer));

        assertThatCode(() -> registry.verifyTargetWriteAccess("공통게시판", "NAC-2026-0001", user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등록된 일반 종류도 별도 차단 정책이 없으면 generic 수정을 계속 허용한다")
    void registeredLegacyKindAllowsGenericMutationByDefault() {
        FileTargetWriteAuthorizer legacyAuthorizer =
                new FileTargetWriteAuthorizer() {
                    @Override
                    public Set<String> supportedApgFlKdNms() {
                        return Set.of("공통게시판");
                    }

                    @Override
                    public boolean canWrite(String apgFlLnkCtzNm, CustomUserDetails currentUser) {
                        return true;
                    }
                };
        FileTargetWriteAuthorizerRegistry registry = registryOf(List.of(legacyAuthorizer));

        assertThatCode(() -> registry.verifyGenericMutationAllowed("공통게시판"))
                .doesNotThrowAnyException();
    }

    /**
     * 판정기 목록으로 레지스트리를 만든다.
     *
     * <p>아는 종류 목록은 판정기가 선언한 종류의 합집합이므로, 여기에 넘긴 판정기의 종류가 곧 아는 종류가 된다. 미등록 종류를 시험하는 테스트는 그 종류를 아는 것으로
     * 만들기 위해 읽기 판정기를 따로 넘긴다.
     */
    private static FileTargetWriteAuthorizerRegistry registryOf(
            List<FileTargetWriteAuthorizer> authorizers) {
        return new FileTargetWriteAuthorizerRegistry(
                authorizers, new FileKindRegistry(List.of(), authorizers));
    }

    /** 읽기 판정기만 있는(= 쓰기 전용 규칙이 없는) 종류까지 아는 레지스트리. */
    private static FileTargetWriteAuthorizerRegistry registryKnowing(String... kinds) {
        FileReadAuthorizer reader = mock(FileReadAuthorizer.class);
        given(reader.supportedApgFlKdNms()).willReturn(Set.of(kinds));
        return new FileTargetWriteAuthorizerRegistry(
                List.of(), new FileKindRegistry(List.of(reader), List.of()));
    }

    @Test
    @DisplayName("아는 종류지만 전용 writer가 없으면 대상 검증을 생략한다 — 기존 동작 보존")
    void knownKindWithoutWriterPreservesExistingBehavior() {
        FileTargetWriteAuthorizerRegistry registry = registryKnowing("요구사항정의서");

        assertThatCode(() -> registry.verifyTargetWriteAccess("요구사항정의서", "DOC-2026-0001", user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("알 수 없는 종류는 거부한다 — 임의 종류로 부모 권한 검사를 피해 가는 경로를 막는다(SEC-14)")
    void unknownKindRejected() {
        FileTargetWriteAuthorizerRegistry registry = registryKnowing("요구사항정의서");

        assertThatThrownBy(() -> registry.verifyTargetWriteAccess("내가만든종류", "ANY-1", user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("알 수 없는 파일 종류");
    }

    @Test
    @DisplayName("종류가 null이면 종류 검사를 건너뛴다 — 메타 수정에서 종류를 바꾸지 않는 경우다")
    void nullKindSkipsKnownCheck() {
        FileTargetWriteAuthorizerRegistry registry = registryKnowing("요구사항정의서");

        assertThatCode(() -> registry.verifyTargetWriteAccess(null, "DOC-1", user))
                .doesNotThrowAnyException();
    }
}
