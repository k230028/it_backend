package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileReadAuthorizerRegistryTest {

    private Cfilem fileOfKind(String kind) {
        Cfilem file = mock(Cfilem.class);
        when(file.getApgFlKdNm()).thenReturn(kind);
        return file;
    }

    private FileReadAuthorizer authorizer(Set<String> kinds, boolean result) {
        return new FileReadAuthorizer() {
            @Override
            public Set<String> supportedApgFlKdNms() {
                return kinds;
            }

            @Override
            public boolean canRead(Cfilem file, CustomUserDetails user) {
                return result;
            }
        };
    }

    @Test
    @DisplayName("등록된 종류는 해당 authorizer 결과로 위임한다")
    void registeredKind_delegates() {
        var registry = new FileReadAuthorizerRegistry(List.of(authorizer(Set.of("요구사항정의서"), true)));
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
        assertThat(registry.canRead(fileOfKind("요구사항정의서"), user)).isTrue();
    }

    @Test
    @DisplayName("미등록 종류는 관리자만 허용한다(default-deny)")
    void unregisteredKind_adminOnly() {
        var registry = new FileReadAuthorizerRegistry(List.of(authorizer(Set.of("요구사항정의서"), true)));
        CustomUserDetails normal = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
        CustomUserDetails admin = new CustomUserDetails("A001", List.of("ITPAD001"), "IT001");
        assertThat(registry.canRead(fileOfKind("미지정종류"), normal)).isFalse();
        assertThat(registry.canRead(fileOfKind("미지정종류"), admin)).isTrue();
    }

    @Test
    @DisplayName("종류(APG_FL_KD_NM)가 null이면 관리자만 허용하고 예외를 던지지 않는다(default-deny)")
    void nullKind_adminOnly() {
        var registry = new FileReadAuthorizerRegistry(List.of(authorizer(Set.of("요구사항정의서"), true)));
        CustomUserDetails normal = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
        CustomUserDetails admin = new CustomUserDetails("A001", List.of("ITPAD001"), "IT001");
        assertThat(registry.canRead(fileOfKind(null), normal)).isFalse();
        assertThat(registry.canRead(fileOfKind(null), admin)).isTrue();
    }

    @Test
    @DisplayName("배너는 실제 BannerFileReadAuthorizer로 위임되어 인증 사용자 전체에게 공개된다")
    void bannerKind_delegatesToRealAuthorizer_publicToAuthenticatedUsers() {
        var registry = new FileReadAuthorizerRegistry(List.of(new BannerFileReadAuthorizer()));
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

        assertThat(registry.canRead(fileOfKind("배너"), user)).isTrue();
        assertThat(registry.canRead(fileOfKind("배너"), null)).isFalse();
    }

    @Test
    @DisplayName("동일 종류를 두 authorizer가 등록하면 기동 시 예외로 막는다")
    void duplicateKind_throws() {
        assertThatThrownBy(
                        () ->
                                new FileReadAuthorizerRegistry(
                                        List.of(
                                                authorizer(Set.of("요구사항정의서"), true),
                                                authorizer(Set.of("요구사항정의서"), false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("중복");
    }
}
