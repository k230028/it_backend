package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BannerFileReadAuthorizerTest {

    private final BannerFileReadAuthorizer authorizer = new BannerFileReadAuthorizer();

    @Test
    @DisplayName("배너 종류를 담당한다")
    void supports_banner() {
        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("배너");
    }

    @Test
    @DisplayName("인증 사용자는 읽기 가능(전사 공개)")
    void authenticatedUser_canRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
        assertThat(authorizer.canRead(mock(Cfilem.class), user)).isTrue();
    }

    @Test
    @DisplayName("비인증(null) 사용자는 읽기 불가")
    void nullUser_cannotRead() {
        assertThat(authorizer.canRead(mock(Cfilem.class), null)).isFalse();
    }
}
