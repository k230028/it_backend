package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BannerFileTargetWriteAuthorizerTest {

    private static final String ADMIN_ATH = "ITPAD001"; // CustomUserDetails.ATH_ADMIN
    private static final String USER_ATH = "ITPZZ001"; // CustomUserDetails.ATH_USER

    private final BannerFileTargetWriteAuthorizer authorizer =
            new BannerFileTargetWriteAuthorizer();

    @Test
    @DisplayName("배너 종류를 담당한다")
    void supports_banner() {
        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("배너");
    }

    @Test
    @DisplayName("관리자는 배너 위치에 쓰기 가능")
    void admin_canWrite() {
        CustomUserDetails admin = new CustomUserDetails("E001", List.of(ADMIN_ATH), "IT001");
        assertThat(authorizer.canWrite("/info", admin)).isTrue();
    }

    @Test
    @DisplayName("일반 사용자는 쓰기 불가")
    void normalUser_cannotWrite() {
        CustomUserDetails user = new CustomUserDetails("E002", List.of(USER_ATH), "IT001");
        assertThat(authorizer.canWrite("/info", user)).isFalse();
    }

    @Test
    @DisplayName("비인증(null) 사용자는 쓰기 불가")
    void nullUser_cannotWrite() {
        assertThat(authorizer.canWrite("/info", null)).isFalse();
    }

    @Test
    @DisplayName("배너 위치(apgFlLnkCtzNm)가 비면 쓰기 불가")
    void blankApgFlLnkCtzNm_cannotWrite() {
        CustomUserDetails admin = new CustomUserDetails("E001", List.of(ADMIN_ATH), "IT001");
        assertThat(authorizer.canWrite("  ", admin)).isFalse();
        assertThat(authorizer.canWrite(null, admin)).isFalse();
    }

    @Test
    @DisplayName("범용 파일 API의 수정·삭제를 차단한다")
    void genericMutation_isBlocked() {
        assertThat(authorizer.allowsGenericMutation()).isFalse();
    }
}
