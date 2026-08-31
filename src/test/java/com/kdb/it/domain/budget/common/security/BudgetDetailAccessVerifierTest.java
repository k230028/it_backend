package com.kdb.it.domain.budget.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class BudgetDetailAccessVerifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"013", "180", "181", "182", "183", "185"})
    @DisplayName("IT 조직 사용자는 다른 부서 예산 상세를 조회할 수 있다")
    void IT조직_사용자는_다른부서_예산상세를_조회할수있다(String itOrganizationCode) {
        CustomUserDetails actor =
                new CustomUserDetails("IT-USER", List.of(CustomUserDetails.ATH_USER), itOrganizationCode);

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("같은 부서 사용자는 예산 상세를 조회할 수 있다")
    void 같은부서_사용자는_예산상세를_조회할수있다() {
        CustomUserDetails actor =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D100");

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("시스템관리자는 다른 부서 예산 상세를 조회할 수 있다")
    void 시스템관리자는_다른부서_예산상세를_조회할수있다() {
        CustomUserDetails actor =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D200");

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("다른 부서 일반 사용자는 예산 상세를 조회할 수 없다")
    void 다른부서_일반사용자는_예산상세를_조회할수없다() {
        CustomUserDetails actor =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D200");

        assertThatThrownBy(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .isInstanceOf(AccessDeniedException.class);
    }
}
