package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kdb.it.common.iam.dto.OrganizationDto;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.repository.OrganizationRepository;

/**
 * OrganizationService 단위 테스트
 *
 * <p>
 * 조직 목록 조회 서비스가 리포지토리에 정확히 위임하고 DTO로 변환하는지 검증합니다.
 * CorgnI 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private CorgnI mockCorgnI(String orgCode, String parentCode, String name) {
        CorgnI org = mock(CorgnI.class);
        given(org.getPrlmOgzCCone()).willReturn(orgCode);
        given(org.getPrlmHrkOgzCCone()).willReturn(parentCode);
        given(org.getBbrNm()).willReturn(name);
        return org;
    }

    @Test
    @DisplayName("getOrganizations: 전체 조직 목록을 DTO로 변환하여 반환한다")
    void getOrganizations_전체조직_DTO목록반환() {
        // given
        CorgnI org1 = mockCorgnI("001", null, "경영지원본부");
        CorgnI org2 = mockCorgnI("002", "001", "IT본부");
        given(organizationRepository.findAll()).willReturn(List.of(org1, org2));

        // when
        List<OrganizationDto.Response> result = organizationService.getOrganizations();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrlmOgzCCone()).isEqualTo("001");
        assertThat(result.get(0).getBbrNm()).isEqualTo("경영지원본부");
        assertThat(result.get(1).getPrlmOgzCCone()).isEqualTo("002");
        assertThat(result.get(1).getPrlmHrkOgzCCone()).isEqualTo("001");
        verify(organizationRepository).findAll();
    }

    @Test
    @DisplayName("getOrganizations: 조직이 없으면 빈 목록을 반환한다")
    void getOrganizations_조직없음_빈목록반환() {
        // given
        given(organizationRepository.findAll()).willReturn(List.of());

        // when
        List<OrganizationDto.Response> result = organizationService.getOrganizations();

        // then
        assertThat(result).isEmpty();
    }
}
