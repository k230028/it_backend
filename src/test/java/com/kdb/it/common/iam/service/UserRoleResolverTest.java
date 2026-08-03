package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

/** 사용자 자격등급 공통 해석기의 조회 조건·순서·불변성·폴백·예외 전파 계약 검증. */
@ExtendWith(MockitoExtension.class)
class UserRoleResolverTest {

    @Mock private RoleRepository roleRepository;
    @InjectMocks private UserRoleResolver resolver;

    @Test
    @DisplayName("resolveAthIds - 활성 자격등급 다건을 조회 순서 그대로 변경 불가 목록으로 반환한다")
    void resolveAthIds_활성역할다건_순서보존불변목록반환() {
        CroleI admin = mock(CroleI.class);
        CroleI manager = mock(CroleI.class);
        given(admin.getAthId()).willReturn("ITPAD001");
        given(manager.getAthId()).willReturn("ITPZZ002");
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willReturn(List.of(admin, manager));

        List<String> result = resolver.resolveAthIds("10001");

        assertThat(result).containsExactly("ITPAD001", "ITPZZ002");
        assertThatThrownBy(() -> result.add("ITPZZ001"))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(roleRepository).findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N");
    }

    @Test
    @DisplayName("resolveAthIds - 활성 자격등급이 없으면 일반 사용자 자격등급으로 폴백한다")
    void resolveAthIds_활성역할없음_일반사용자폴백() {
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willReturn(List.of());

        assertThat(resolver.resolveAthIds("10001")).containsExactly(CustomUserDetails.ATH_USER);
    }

    @Test
    @DisplayName("resolveAthIds - 자격등급 조회 실패는 Spring Data 원본 예외를 그대로 전파한다")
    void resolveAthIds_조회실패_SpringData원본예외전파() {
        DataRetrievalFailureException repositoryError =
                new DataRetrievalFailureException("역할 조회 실패");
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willThrow(repositoryError);

        assertThatThrownBy(() -> resolver.resolveAthIds("10001")).isSameAs(repositoryError);
    }
}
