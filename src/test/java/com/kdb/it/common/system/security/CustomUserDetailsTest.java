package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * CustomUserDetails 단위 테스트
 *
 * <p>
 * JWT 클레임에서 복원되는 사용자 권한 객체의 기본 자격등급, 권한 매핑,
 * UserDetails 계약을 검증합니다.
 * </p>
 */
class CustomUserDetailsTest {

    @Test
    @DisplayName("자격등급이 없으면 일반사용자 기본 권한을 적용한다")
    void constructor_자격등급없음_일반사용자기본권한() {
        CustomUserDetails details = new CustomUserDetails("10001", null, "D001");

        assertThat(details.getAthIds()).containsExactly(CustomUserDetails.ATH_USER);
        assertThat(authorityNames(details)).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("관리자는 관리자 권한과 부서관리 권한을 모두 가진다")
    void admin_관리자_부서관리권한포함() {
        CustomUserDetails details = new CustomUserDetails(
            "10001",
            List.of(CustomUserDetails.ATH_ADMIN),
            "D001"
        );

        assertThat(details.isAdmin()).isTrue();
        assertThat(details.isDeptManager()).isTrue();
        assertThat(details.hasAthId(CustomUserDetails.ATH_ADMIN)).isTrue();
    }

    @Test
    @DisplayName("부서관리자는 ROLE_DEPT_MANAGER 권한으로 매핑된다")
    void authorities_부서관리자_역할매핑() {
        CustomUserDetails details = new CustomUserDetails(
            "10001",
            List.of(CustomUserDetails.ATH_DEPT_MGR),
            "D001"
        );

        assertThat(details.isAdmin()).isFalse();
        assertThat(details.isDeptManager()).isTrue();
        assertThat(authorityNames(details)).containsExactly("ROLE_DEPT_MANAGER");
    }

    @Test
    @DisplayName("중복 자격등급은 권한 목록에서 제거된다")
    void authorities_중복자격등급_권한중복제거() {
        CustomUserDetails details = new CustomUserDetails(
            "10001",
            List.of(CustomUserDetails.ATH_USER, CustomUserDetails.ATH_USER),
            "D001"
        );

        assertThat(authorityNames(details)).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("UserDetails 계약은 Stateless JWT 사용자에 맞는 고정값을 반환한다")
    void userDetails_계약_고정값반환() {
        CustomUserDetails details = new CustomUserDetails("10001", List.of(), "D001");

        assertThat(details.getUsername()).isEqualTo("10001");
        assertThat(details.getPassword()).isEmpty();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getBbrC()).isEqualTo("D001");
    }

    private List<String> authorityNames(CustomUserDetails details) {
        return details.getAuthorities().stream()
            .map(value -> value.getAuthority())
            .toList();
    }
}
