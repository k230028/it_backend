package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.iam.dto.OrganizationDto;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

/**
 * OrganizationService 단위 테스트
 *
 * <p>조직 목록 조회 서비스가 리포지토리에 정확히 위임하고 DTO로 변환하는지 검증합니다. 목록 응답 전용 프로젝션({@link
 * OrganizationRepository.OrganizationListView})은 테스트 전용 record로 구현하여 Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private record OrganizationListView(String prlmOgzCCone, String prlmHrkOgzCCone, String bbrNm)
            implements OrganizationRepository.OrganizationListView {
        @Override
        public String getPrlmOgzCCone() {
            return prlmOgzCCone;
        }

        @Override
        public String getPrlmHrkOgzCCone() {
            return prlmHrkOgzCCone;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }
    }

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private OrganizationService organizationService;

    @Captor private ArgumentCaptor<Sort> sortCaptor;

    @Test
    @DisplayName("getOrganizations: 전체 조직 목록을 DTO로 변환하여 반환한다")
    void getOrganizations_전체조직_DTO목록반환() {
        // given
        OrganizationListView org1 = new OrganizationListView("001", null, "경영지원본부");
        OrganizationListView org2 = new OrganizationListView("002", "001", "IT본부");
        given(organizationRepository.findListViewsBy(any(Sort.class)))
                .willReturn(List.of(org1, org2));

        // when
        List<OrganizationDto.Response> result = organizationService.getOrganizations();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrlmOgzCCone()).isEqualTo("001");
        assertThat(result.get(0).getBbrNm()).isEqualTo("경영지원본부");
        assertThat(result.get(1).getPrlmOgzCCone()).isEqualTo("002");
        assertThat(result.get(1).getPrlmHrkOgzCCone()).isEqualTo("001");
        verify(organizationRepository).findListViewsBy(any(Sort.class));
    }

    @Test
    @DisplayName("getOrganizations: 조직 트리 표시용으로 항목순서일련번호 오름차순 정렬을 요청한다")
    void getOrganizations_정렬기준_항목순서일련번호오름차순() {
        // given
        given(organizationRepository.findListViewsBy(any(Sort.class))).willReturn(List.of());

        // when
        organizationService.getOrganizations();

        // then: 1순위 itmSqnSno 오름차순(미지정은 뒤로), 2순위 조직코드 오름차순으로 순서를 고정한다
        verify(organizationRepository).findListViewsBy(sortCaptor.capture());
        assertThat(sortCaptor.getValue())
                .containsExactly(
                        Sort.Order.asc("itmSqnSno").nullsLast(), Sort.Order.asc("prlmOgzCCone"));
    }

    @Test
    @DisplayName("getOrganizations: 조직이 없으면 빈 목록을 반환한다")
    void getOrganizations_조직없음_빈목록반환() {
        // given
        given(organizationRepository.findListViewsBy(any(Sort.class))).willReturn(List.of());

        // when
        List<OrganizationDto.Response> result = organizationService.getOrganizations();

        // then
        assertThat(result).isEmpty();
    }
}
