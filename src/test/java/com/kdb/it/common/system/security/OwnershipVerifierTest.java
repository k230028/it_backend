package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * OwnershipVerifier 단위 테스트 — 관리자/소유자/타인/널 분기 검증.
 */
class OwnershipVerifierTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    @DisplayName("관리자 전용 검증은 관리자만 통과시킨다")
    void verifyAdmin_관리자_통과() {
        assertThatCode(() -> OwnershipVerifier.verifyAdmin(user("E0099", "ITPAD001")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자 전용 검증은 일반사용자를 거부한다")
    void verifyAdmin_일반사용자_거부() {
        assertThatThrownBy(() -> OwnershipVerifier.verifyAdmin(user("E0001", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("관리자");
    }

    @Test
    @DisplayName("관리자 전용 검증은 인증 정보가 없으면 거부한다")
    void verifyAdmin_인증정보없음_거부() {
        assertThatThrownBy(() -> OwnershipVerifier.verifyAdmin(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("인증 정보");
    }

    @Test
    @DisplayName("생성자는 수정할 수 있다")
    void verifyModifiable_생성자_허용() {
        setUser("10001", "D001", false);

        assertThatCode(() -> OwnershipVerifier.verifyModifiable("10001", "D999"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자는 수정할 수 있다")
    void verifyModifiable_관리자_허용() {
        setUser("90000", "D999", true);

        assertThatCode(() -> OwnershipVerifier.verifyModifiable("10001", "D001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 부서 사용자는 수정할 수 있다")
    void verifyModifiable_같은부서_허용() {
        setUser("10002", "D001", false);

        assertThatCode(() -> OwnershipVerifier.verifyModifiable("10001", "D001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성자도 관리자도 같은 부서도 아니면 거부한다")
    void verifyModifiable_권한없음_거부() {
        setUser("10002", "D002", false);

        assertThatThrownBy(() -> OwnershipVerifier.verifyModifiable("10001", "D001"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("유틸리티 클래스 생성자는 외부에서 호출할 수 없다")
    void constructor_리플렉션호출_비공개생성자확인() throws Exception {
        var constructor = OwnershipVerifier.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatCode(constructor::newInstance).doesNotThrowAnyException();
    }

    private void setUser(String eno, String bbrC, boolean admin) {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        given(principal.getEno()).willReturn(eno);
        given(principal.getBbrC()).willReturn(bbrC);
        given(principal.isAdmin()).willReturn(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
