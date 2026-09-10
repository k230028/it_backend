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

    @Test
    @DisplayName("단건 조회도 결재선·신청자·부서 정보를 같은 조립기로 해석한다")
    void assembleOne_resolvesNamesAndApprovers() {
        ApproverRepository approverRepository = mock(ApproverRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        ApplicationRepository.ApplicationReadView view =
                mock(ApplicationRepository.ApplicationReadView.class);
        ApproverRepository.ApproverReadView approver =
                mock(ApproverRepository.ApproverReadView.class);
        UserRepository.UserNameView user = mock(UserRepository.UserNameView.class);
        OrganizationRepository.OrganizationNameView organization =
                mock(OrganizationRepository.OrganizationNameView.class);
        given(view.getApfMngNo()).willReturn("APF-1");
        given(view.getDcdReqUsid()).willReturn("E-1");
        given(view.getDcdReqBbrC()).willReturn("D-1");
        given(approver.getDcdMngNo()).willReturn("APF-1");
        given(user.getEno()).willReturn("E-1");
        given(user.getUsrNm()).willReturn("홍길동");
        given(organization.getPrlmOgzCCone()).willReturn("D-1");
        given(organization.getBbrNm()).willReturn("정보기획부");
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-1"))
                .willReturn(List.of(approver));
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(List.of(user));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(organization));

        ApplicationDto.Response result =
                ApplicationBulkReadSupport.assembleOne(
                        view, approverRepository, userRepository, organizationRepository);

        assertThat(result.getApfMngNo()).isEqualTo("APF-1");
        assertThat(result.getRqsNm()).isEqualTo("홍길동");
        assertThat(result.getRqsBbrNm()).isEqualTo("정보기획부");
        assertThat(result.getApprovers()).hasSize(1);
    }

    @Test
    @DisplayName("신청자 직위명과 기안자 요청 행(순번 0)의 결재의견을 응답에 채운다")
    void assembleOne_resolvesRequesterRankAndDecisionOpinion() {
        ApproverRepository approverRepository = mock(ApproverRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        ApplicationRepository.ApplicationReadView view =
                mock(ApplicationRepository.ApplicationReadView.class);
        UserRepository.UserNameView user = mock(UserRepository.UserNameView.class);
        ApproverRepository.RequesterDecisionView requesterDecision =
                mock(ApproverRepository.RequesterDecisionView.class);
        given(view.getApfMngNo()).willReturn("APF-1");
        given(view.getDcdReqUsid()).willReturn("E-1");
        given(view.getRgprDcdReqCone()).willReturn("정보화사업 3건 상신");
        given(user.getEno()).willReturn("E-1");
        given(user.getUsrNm()).willReturn("홍길동");
        given(user.getPtCNm()).willReturn("차장");
        given(requesterDecision.getDcdMngNo()).willReturn("APF-1");
        given(requesterDecision.getDcrOpnnCone()).willReturn("검토 부탁드립니다.");
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(List.of(user));
        given(approverRepository.findRequesterDecisionViewsByDcdMngNoIn(List.of("APF-1")))
                .willReturn(List.of(requesterDecision));

        ApplicationDto.Response result =
                ApplicationBulkReadSupport.assembleOne(
                        view, approverRepository, userRepository, organizationRepository);

        assertThat(result.getRqsPtCNm()).isEqualTo("차장");
        assertThat(result.getRqsDcdOpnn()).isEqualTo("검토 부탁드립니다.");
    }

    @Test
    @DisplayName("기안자 요청 행이 없으면 기안자 결재의견은 신청의견으로 대체하지 않고 null이다")
    void assembleOne_keepsDecisionOpinionNullWithoutRequesterRow() {
        ApproverRepository approverRepository = mock(ApproverRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        ApplicationRepository.ApplicationReadView view =
                mock(ApplicationRepository.ApplicationReadView.class);
        given(view.getApfMngNo()).willReturn("APF-1");
        given(view.getDcdReqUsid()).willReturn("E-1");
        given(view.getRgprDcdReqCone()).willReturn("신청의견입니다.");
        given(approverRepository.findRequesterDecisionViewsByDcdMngNoIn(List.of("APF-1")))
                .willReturn(List.of());

        ApplicationDto.Response result =
                ApplicationBulkReadSupport.assembleOne(
                        view, approverRepository, userRepository, organizationRepository);

        assertThat(result.getRqsOpnn()).isEqualTo("신청의견입니다.");
        assertThat(result.getRqsDcdOpnn()).isNull();
    }
}
