package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthorOrgResolverTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private AuthorOrgResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveCurrent_인증정보없음_빈조직반환() {
        AuthorOrg result = resolver.resolveCurrent();

        assertThat(result).isEqualTo(AuthorOrg.empty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolveCurrent_미인증주체_빈조직반환() {
        Authentication authentication = authentication(false, "10001");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolveCurrent()).isEqualTo(AuthorOrg.empty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolveCurrent_사번공백_빈조직반환() {
        SecurityContextHolder.getContext().setAuthentication(authentication(true, " "));

        assertThat(resolver.resolveCurrent()).isEqualTo(AuthorOrg.empty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolveCurrent_사용자없음_빈조직반환() {
        SecurityContextHolder.getContext().setAuthentication(authentication(true, "10001"));
        given(userRepository.findByEno("10001")).willReturn(Optional.empty());

        assertThat(resolver.resolveCurrent()).isEqualTo(AuthorOrg.empty());
    }

    @Test
    void resolveCurrent_사용자존재_조직스냅샷반환() {
        SecurityContextHolder.getContext().setAuthentication(authentication(true, "10001"));
        CuserI user = mock(CuserI.class);
        given(user.getBbrC()).willReturn("D001");
        given(user.getTemC()).willReturn("T001");
        given(user.getPrlmHrkOgzCCone()).willReturn("H001");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));

        assertThat(resolver.resolveCurrent()).isEqualTo(new AuthorOrg("D001", "T001", "H001"));
    }

    private Authentication authentication(boolean authenticated, String name) {
        Authentication authentication = mock(Authentication.class);
        given(authentication.isAuthenticated()).willReturn(authenticated);
        if (authenticated) {
            given(authentication.getName()).willReturn(name);
        }
        return authentication;
    }
}
