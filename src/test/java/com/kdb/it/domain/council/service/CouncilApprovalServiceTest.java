package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;

/**
 * CouncilApprovalService 단위 테스트
 *
 * <p>
 * 협의회 전자결재 연동 서비스의 결재요청·콜백 처리 메서드를 검증합니다.
 * Basctm·Bpovwm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CouncilService·ApplicationService는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilApprovalServiceTest {

    @Mock
    private CouncilService councilService;

    @Mock
    private ProjectOverviewRepository projectOverviewRepository;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private CouncilApprovalService councilApprovalService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // requestApproval
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("requestApproval: 협의회 상태가 SUBMITTED가 아니면 IllegalStateException을 던진다")
    void requestApproval_SUBMITTED아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("DRAFT");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E10001");

        // when & then
        assertThatThrownBy(() -> councilApprovalService.requestApproval(
                ASCT_ID,
                new CouncilDto.ApprovalRequest("E20001", "결재요청합니다"),
                userDetails))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    @DisplayName("requestApproval: SUBMITTED 상태이면 신청서를 등록하고 APPROVAL_PENDING으로 전이한다")
    void requestApproval_SUBMITTED상태_신청서등록후APPROVAL_PENDING전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("SUBMITTED");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getPrjNm()).willReturn("테스트 사업");
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(overview));

        given(applicationService.submit(any(ApplicationDto.CreateRequest.class)))
                .willReturn("APF_20260001");

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E10001");

        // when
        CouncilDto.ApprovalResponse response = councilApprovalService.requestApproval(
                ASCT_ID,
                new CouncilDto.ApprovalRequest("E20001", "결재요청합니다"),
                userDetails);

        // then
        assertThat(response.apfMngNo()).isEqualTo("APF_20260001");
        verify(applicationService).submit(any(ApplicationDto.CreateRequest.class));
        verify(councilService).changeStatus(ASCT_ID, "APPROVAL_PENDING");
    }

    // ───────────────────────────────────────────────────────
    // processApprovalCallback
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("processApprovalCallback: 협의회 상태가 APPROVAL_PENDING이 아니면 IllegalStateException을 던진다")
    void processApprovalCallback_APPROVAL_PENDING아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("APPROVED");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // when & then
        assertThatThrownBy(() -> councilApprovalService.processApprovalCallback(
                ASCT_ID, new CouncilDto.ApprovalCallbackRequest(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVAL_PENDING");
    }

    @Test
    @DisplayName("processApprovalCallback: 승인이면 APPROVED로 상태를 전이한다")
    void processApprovalCallback_승인_APPROVED전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("APPROVAL_PENDING");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // when
        councilApprovalService.processApprovalCallback(
                ASCT_ID, new CouncilDto.ApprovalCallbackRequest(true));

        // then
        verify(councilService).changeStatus(ASCT_ID, "APPROVED");
    }

    @Test
    @DisplayName("processApprovalCallback: 반려이면 DRAFT로 상태를 전이한다")
    void processApprovalCallback_반려_DRAFT전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("APPROVAL_PENDING");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // when
        councilApprovalService.processApprovalCallback(
                ASCT_ID, new CouncilDto.ApprovalCallbackRequest(false));

        // then
        verify(councilService).changeStatus(ASCT_ID, "DRAFT");
    }
}
