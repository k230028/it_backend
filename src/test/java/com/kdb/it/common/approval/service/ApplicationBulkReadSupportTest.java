package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신청서 일괄 조회가 단건 반복 없이 배치 응답을 조립하는지 검증합니다. */
class ApplicationBulkReadSupportTest {

    @Test
    @DisplayName("신청자·부서·결재선을 배치로 읽어 응답을 조립한다")
    void read_resolvesNamesAndApproversInBatch() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        ApproverRepository approverRepository = mock(ApproverRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        ApplicationRepository.ApplicationReadView view =
                mock(ApplicationRepository.ApplicationReadView.class);
        ApproverRepository.ApproverReadView approver =
                mock(ApproverRepository.ApproverReadView.class);
        UserRepository.UserNameView user = mock(UserRepository.UserNameView.class);
        UserRepository.UserNameView duplicateUser = mock(UserRepository.UserNameView.class);
        OrganizationRepository.OrganizationNameView organization =
                mock(OrganizationRepository.OrganizationNameView.class);
        OrganizationRepository.OrganizationNameView duplicateOrganization =
                mock(OrganizationRepository.OrganizationNameView.class);
        OrganizationRepository.OrganizationNameView unnamedOrganization =
                mock(OrganizationRepository.OrganizationNameView.class);
        given(view.getApfMngNo()).willReturn("APF-1");
        given(view.getDcdReqUsid()).willReturn("E-1");
        given(view.getDcdReqBbrC()).willReturn("D-1");
        given(approver.getDcdMngNo()).willReturn("APF-1");
        given(user.getEno()).willReturn("E-1");
        given(user.getUsrNm()).willReturn("홍길동");
        given(duplicateUser.getEno()).willReturn("E-1");
        given(duplicateUser.getUsrNm()).willReturn("홍길동(중복)");
        given(organization.getPrlmOgzCCone()).willReturn("D-1");
        given(organization.getBbrNm()).willReturn("정보기획부");
        given(duplicateOrganization.getPrlmOgzCCone()).willReturn("D-1");
        given(duplicateOrganization.getBbrNm()).willReturn("정보기획부(중복)");
        given(unnamedOrganization.getPrlmOgzCCone()).willReturn("D-2");
        given(unnamedOrganization.getBbrNm()).willReturn(null);
        given(applicationRepository.findReadViewsByApfMngNoIn(any())).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of(approver));
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(List.of(user, duplicateUser));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(organization, duplicateOrganization, unnamedOrganization));
        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of("APF-1"));

        ApplicationDto.BulkResponse result =
                ApplicationBulkReadSupport.read(
                        request,
                        applicationRepository,
                        approverRepository,
                        userRepository,
                        organizationRepository);

        assertThat(result.items()).hasSize(1);
        assertThat(result.failedIds()).isEmpty();
    }
}
