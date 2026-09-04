package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiagramFileReadAuthorizerTest {

    private final DiagramFileReadAuthorizer authorizer = new DiagramFileReadAuthorizer();

    @Test
    @DisplayName("다이어그램은 화면과 부서에 관계없이 인증 사용자가 읽을 수 있다")
    void authenticatedUser_canReadFromEveryScreen() {
        Cfilem file = mock(Cfilem.class);
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D001");

        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("다이어그램");
        assertThat(authorizer.canRead(file, user)).isTrue();
    }

    @Test
    @DisplayName("다이어그램은 비인증 사용자에게 공개하지 않는다")
    void anonymousUser_cannotRead() {
        assertThat(authorizer.canRead(mock(Cfilem.class), null)).isFalse();
    }
}
