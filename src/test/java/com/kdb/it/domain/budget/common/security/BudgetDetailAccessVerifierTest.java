package com.kdb.it.domain.budget.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class BudgetDetailAccessVerifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"013", "180", "181", "182", "183", "185"})
    @DisplayName("IT 조직 사용자는 다른 부서 예산 상세를 조회할 수 있다")
    void IT조직_사용자는_다른부서_예산상세를_조회할수있다(String itOrganizationCode) {
        CustomUserDetails actor =
                new CustomUserDetails(
                        "IT-USER", List.of(CustomUserDetails.ATH_USER), itOrganizationCode);

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 부서 사용자는 예산 상세를 조회할 수 있다")
    void 같은부서_사용자는_예산상세를_조회할수있다() {
        CustomUserDetails actor =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D100");

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시스템관리자는 다른 부서 예산 상세를 조회할 수 있다")
    void 시스템관리자는_다른부서_예산상세를_조회할수있다() {
        CustomUserDetails actor =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D200");

        assertThatCode(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 부서 일반 사용자는 예산 상세를 조회할 수 없다")
    void 다른부서_일반사용자는_예산상세를_조회할수없다() {
        CustomUserDetails actor =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D200");

        assertThatThrownBy(() -> BudgetDetailAccessVerifier.verifyReadable("D100", actor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("인증 정보나 유효한 대상 부서가 없으면 상세 조회를 거부한다")
    void 인증정보나_유효한_대상부서가_없으면_상세조회를_거부한다() {
        CustomUserDetails actor =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D100");
        CustomUserDetails actorWithoutDepartment =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), null);

        assertThat(BudgetDetailAccessVerifier.isReadable("D100", null)).isFalse();
        assertThat(BudgetDetailAccessVerifier.isReadable("", actor)).isFalse();
        assertThat(BudgetDetailAccessVerifier.isReadable("D100", actorWithoutDepartment)).isFalse();
    }

    @Test
    @DisplayName("전체 부서 조회는 관리자와 IT 조직에만 허용한다")
    void 전체부서_조회는_관리자와_IT조직에만_허용한다() {
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D200");
        CustomUserDetails itUser =
                new CustomUserDetails("IT-USER", List.of(CustomUserDetails.ATH_USER), "180");
        CustomUserDetails regularUser =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D200");
        CustomUserDetails actorWithoutDepartment =
                new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), null);

        assertThat(BudgetDetailAccessVerifier.canReadAllDepartments(null)).isFalse();
        assertThat(BudgetDetailAccessVerifier.canReadAllDepartments(admin)).isTrue();
        assertThat(BudgetDetailAccessVerifier.canReadAllDepartments(itUser)).isTrue();
        assertThat(BudgetDetailAccessVerifier.canReadAllDepartments(regularUser)).isFalse();
        assertThat(BudgetDetailAccessVerifier.canReadAllDepartments(actorWithoutDepartment))
                .isFalse();
    }
}
