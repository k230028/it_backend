package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link OrgNameResolver} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class OrgNameResolverTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrgNameResolver orgNameResolver;

    @Test
    @DisplayName("조직코드로 CORGNI를 조회해 조직명을 반환한다")
    void resolveName_returnsBbrNm() {
        CorgnI organization = mock(CorgnI.class);
        given(organization.getBbrNm()).willReturn("정보기술부");
        given(organizationRepository.findById("BBR001")).willReturn(Optional.of(organization));

        assertThat(orgNameResolver.resolveName("BBR001")).isEqualTo("정보기술부");
    }

    @Test
    @DisplayName("코드가 null 또는 공백이면 조회 없이 null을 반환한다")
    void resolveName_nullOrBlank_returnsNull() {
        assertThat(orgNameResolver.resolveName(null)).isNull();
        assertThat(orgNameResolver.resolveName("")).isNull();
        assertThat(orgNameResolver.resolveName("  ")).isNull();
        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("CORGNI에 등록되지 않은 코드는 null을 반환한다")
    void resolveName_unknownCode_returnsNull() {
        given(organizationRepository.findById("XXXXX")).willReturn(Optional.empty());

        assertThat(orgNameResolver.resolveName("XXXXX")).isNull();
    }
}
