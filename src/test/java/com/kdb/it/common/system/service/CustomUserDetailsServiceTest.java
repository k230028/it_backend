package com.kdb.it.common.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;

/**
 * CustomUserDetailsService 단위 테스트
 *
 * <p>
 * Spring Security의 UserDetailsService 구현체가 사번으로 사용자를 올바르게 로드하는지
 * 검증합니다. CuserI 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("loadUserByUsername: 존재하는 사번으로 조회하면 UserDetails를 반환한다")
    void loadUserByUsername_존재하는사번_UserDetails반환() {
        // given
        String eno = "E10001";
        CuserI user = mock(CuserI.class);
        given(user.getEno()).willReturn(eno);
        given(user.getUsrEcyPwd()).willReturn("hashed_value");
        given(userRepository.findByEno(eno)).willReturn(Optional.of(user));

        // when
        UserDetails result = customUserDetailsService.loadUserByUsername(eno);

        // then
        assertThat(result.getUsername()).isEqualTo(eno);
        assertThat(result.getPassword()).isEqualTo("hashed_value");
        assertThat(result.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("loadUserByUsername: 존재하지 않는 사번이면 UsernameNotFoundException을 던진다")
    void loadUserByUsername_존재하지않는사번_UsernameNotFoundException발생() {
        // given
        given(userRepository.findByEno("E99999")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("E99999"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("E99999");
    }
}
