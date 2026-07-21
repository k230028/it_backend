package com.kdb.it.common.system.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 사용자 상세 정보 로드 서비스
 *
 * <p>{@link UserDetailsService} 인터페이스를 구현하여 Spring Security의 {@link
 * org.springframework.security.authentication.dao.DaoAuthenticationProvider}에 등록됩니다. {@code
 * authenticationManager.authenticate()} 호출 시 사용됩니다.
 *
 * <p>실제 JWT 인증 흐름 (매 요청)에서는 이 서비스가 호출되지 않습니다. JWT 기반 인증은 {@link
 * com.kdb.it.common.system.security.JwtAuthenticationFilter}가 JWT 클레임에서 직접 {@link
 * com.kdb.it.common.system.security.CustomUserDetails}를 생성하여 처리합니다.
 *
 * <p>RBAC(역할 기반 접근 제어)는 로그인 시 {@code AuthService}가 DB에서 자격등급을 조회하여 JWT 클레임({@code athIds})에 포함하고, 매
 * 요청마다 {@code JwtAuthenticationFilter}가 클레임에서 {@code CustomUserDetails}를 생성하는 방식으로 구현되어 있습니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class CustomUserDetailsService implements UserDetailsService {

    /** 사용자 정보 조회를 위한 리포지토리 */
    private final UserRepository userRepository;

    /**
     * 사번(username)으로 사용자 정보를 로드합니다.
     *
     * <p>Spring Security의 {@code DaoAuthenticationProvider}에서 호출됩니다. 실제 JWT 인증 흐름에서는 호출되지 않으며,
     * 권한(authorities)은 빈 목록으로 반환합니다. 실제 RBAC 권한은 JWT 클레임의 {@code athIds}를 기반으로 {@link
     * com.kdb.it.common.system.security.CustomUserDetails}에서 처리됩니다.
     *
     * @param eno 로드할 사용자의 사번 (Spring Security에서 username으로 전달)
     * @return 로드된 사용자 정보 ({@link UserDetails})
     * @throws UsernameNotFoundException 해당 사번의 사용자가 DB에 없을 경우
     */
    @Override
    public UserDetails loadUserByUsername(String eno) throws UsernameNotFoundException {
        // DB에서 사번으로 사용자 조회 (없으면 UsernameNotFoundException 발생)
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + eno));

        // Spring Security의 UserDetails 구현체 생성
        // 권한은 빈 목록: 실제 RBAC은 JWT 클레임 기반 CustomUserDetails에서 처리
        return User.builder()
                .username(user.getEno())
                .password(user.getUsrEcyPwd())
                .authorities(Collections.emptyList())
                .build();
    }
}
