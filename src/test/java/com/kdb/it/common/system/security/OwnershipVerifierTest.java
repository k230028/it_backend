package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * OwnershipVerifier 단위 테스트 — 관리자/소유자/타인/널 분기 검증.
 */
class OwnershipVerifierTest {

    private CustomUserDetails user(String eno, String ath) {
        // bbrC("18001")는 소유권 검증과 무관 — 임의 부서값
        return new CustomUserDetails(eno, List.of(ath), "18001");
    }

    @Test
    @DisplayName("소유자 본인이면 통과한다")
    void ownerPasses() {
        assertThatCode(() -> OwnershipVerifier.verifyOwnerOrAdmin("E0001", user("E0001", "ITPZZ001")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자이면 소유자가 아니어도 통과한다")
    void adminPasses() {
        assertThatCode(() -> OwnershipVerifier.verifyOwnerOrAdmin("E0001", user("E0099", "ITPAD001")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소유자도 관리자도 아니면 AccessDeniedException을 던진다")
    void otherDenied() {
        assertThatThrownBy(() -> OwnershipVerifier.verifyOwnerOrAdmin("E0001", user("E0002", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("소유자 사번이 null이면 (관리자가 아닌 한) 거부한다")
    void nullOwnerDenied() {
        assertThatThrownBy(() -> OwnershipVerifier.verifyOwnerOrAdmin(null, user("E0001", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("관리자이면 소유자 사번이 null이어도 통과한다")
    void adminPassesWithNullOwner() {
        assertThatCode(() -> OwnershipVerifier.verifyOwnerOrAdmin(null, user("E0099", "ITPAD001")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("user가 null이면 거부한다")
    void nullUserDenied() {
        assertThatThrownBy(() -> OwnershipVerifier.verifyOwnerOrAdmin("E0001", null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
