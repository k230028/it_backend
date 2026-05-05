package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;

/**
 * ApplicationService 단위 테스트
 *
 * <p>
 * 결재 처리(approve), 신청서 조회(getApplication, getApplications, getApplicationsByIds),
 * 세부내용 조회(getApfDtlCone), 일괄결재(bulkApprove), 배지수(getApprovalBadgeCount),
 * 미상신 건수(getPendingCount)를 검증합니다.
 * Capplm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Cdecim 엔티티는 @SuperBuilder로 직접 구성합니다. Oracle DB 없이 실행됩니다.
 * </p>
 * <p>커버리지 60% 달성을 위해 미커버 메서드 테스트 추가 (2026-04-29)</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApproverRepository approverRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ProjectRepository projectRepository;
    @Mock private CostRepository costRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApplicationService applicationService;

    private static final String APF_MNG_NO = "APF_202600000001";

    /** Capplm Mock — getApfDtlCone() null로 updateApprovalLineInDetail 즉시 리턴 */
    private Capplm mockCapplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfDtlCone()).willReturn(null);
        return capplm;
    }

    /** 미결재(dcdTp=null) 상태의 Cdecim 생성 */
    private Cdecim pendingApprover(String eno, int sqn, String lstDcdYn) {
        return Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcdSqn(sqn)
                .dcdEno(eno)
                .lstDcdYn(lstDcdYn)
                .build();
    }

    /** 결재 요청 DTO 생성 헬퍼 */
    private ApplicationDto.ApproveRequest approveRequest(String eno, String sts) {
        ApplicationDto.ApproveRequest req = new ApplicationDto.ApproveRequest();
        req.setDcdEno(eno);
        req.setDcdOpnn("테스트의견");
        req.setDcdSts(sts);
        return req;
    }

    // ───────────────────────────────────────────────────────
    // approve — 예외 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("approve: 신청서가 없으면 IllegalArgumentException을 던진다")
    void approve_신청서없음_IllegalArgumentException발생() {
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(APF_MNG_NO);
    }

    @Test
    @DisplayName("approve: 모든 결재가 완료된 경우 IllegalStateException을 던진다")
    void approve_모든결재완료_IllegalStateException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim completed = Cdecim.builder()
                .dcdMngNo(APF_MNG_NO).dcdSqn(1).dcdEno("E10001")
                .lstDcdYn("Y").dcdTp("결재").dcdSts("승인").build();
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO)).willReturn(List.of(completed));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 현재 결재자가 아닌 사번으로 요청하면 IllegalArgumentException을 던진다")
    void approve_잘못된결재자_IllegalArgumentException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E99999", "승인")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 결재자가 아닙니다");
    }

    @Test
    @DisplayName("approve: 결재 상태가 null이면 IllegalArgumentException을 던진다")
    void approve_결재상태미지정_IllegalArgumentException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재 상태");
    }

    // ───────────────────────────────────────────────────────
    // approve — 정상 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("approve: 마지막 결재자 승인 시 신청서 상태가 결재완료로 변경되고 이벤트가 발행된다")
    void approve_마지막결재자승인_결재완료이벤트발행() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(capplm).updateStatus("결재완료");
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 반려 처리 시 신청서 상태가 반려로 변경되고 이벤트가 발행된다")
    void approve_반려처리_반려이벤트발행() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "반려"));

        verify(capplm).updateStatus("반려");
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 중간 결재자 승인 시 신청서 상태 변경 없이 이벤트도 발행되지 않는다")
    void approve_중간결재자승인_상태변경없음() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim mid = pendingApprover("E10001", 1, "N");
        Cdecim last = pendingApprover("E10002", 2, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO)).willReturn(List.of(mid, last));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(capplm, never()).updateStatus(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ───────────────────────────────────────────────────────
    // getApplication
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplication: 존재하지 않는 신청서 번호이면 IllegalArgumentException을 던진다")
    void getApplication_신청서없음_IllegalArgumentException발생() {
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(APF_MNG_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(APF_MNG_NO);
    }

    // ───────────────────────────────────────────────────────
    // getPendingCount
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingCount: 프로젝트 3건 전산업무비 2건이면 총 5건을 반환한다")
    void getPendingCount_프로젝트3전산업무비2_총5반환() {
        given(projectRepository.searchByCondition(any())).willReturn(List.of(
                mock(Bprojm.class), mock(Bprojm.class), mock(Bprojm.class)));
        given(costRepository.searchByCondition(any())).willReturn(List.of(
                mock(Bcostm.class), mock(Bcostm.class)));

        ApplicationDto.PendingCountResponse result = applicationService.getPendingCount();

        assertThat(result.getProjectCount()).isEqualTo(3L);
        assertThat(result.getCostCount()).isEqualTo(2L);
        assertThat(result.getTotalCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getPendingCount: 미상신 건수가 없으면 총합 0을 반환한다")
    void getPendingCount_미상신없음_총합0반환() {
        given(projectRepository.searchByCondition(any())).willReturn(List.of());
        given(costRepository.searchByCondition(any())).willReturn(List.of());

        ApplicationDto.PendingCountResponse result = applicationService.getPendingCount();

        assertThat(result.getTotalCount()).isEqualTo(0L);
    }

    // ───────────────────────────────────────────────────────
    // getApfDtlCone — 커버리지 60% 달성을 위해 추가 (2026-04-29)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApfDtlCone: 존재하지 않는 신청서이면 IllegalArgumentException을 던진다")
    void getApfDtlCone_신청서없음_IllegalArgumentException발생() {
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApfDtlCone(APF_MNG_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(APF_MNG_NO);
    }

    @Test
    @DisplayName("getApfDtlCone: 존재하는 신청서이면 세부내용 응답 DTO를 반환한다")
    void getApfDtlCone_존재하는신청서_DTO반환() {
        // Capplm.ApfDtlConeResponse.fromEntity() 가 호출되므로 필요한 필드만 설정
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn(APF_MNG_NO);
        given(capplm.getApfDtlCone()).willReturn("{\"test\":\"value\"}");
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        ApplicationDto.ApfDtlConeResponse result = applicationService.getApfDtlCone(APF_MNG_NO);

        assertThat(result).isNotNull();
        assertThat(result.getApfMngNo()).isEqualTo(APF_MNG_NO);
    }

    // ───────────────────────────────────────────────────────
    // getApplications — 전체 목록 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplications: 전체 신청서 목록을 반환한다")
    void getApplications_전체목록반환() {
        // given: 두 개의 Capplm Mock 준비
        Capplm c1 = mock(Capplm.class);
        Capplm c2 = mock(Capplm.class);
        given(c1.getApfMngNo()).willReturn(APF_MNG_NO);
        given(c2.getApfMngNo()).willReturn("APF_202600000002");
        given(applicationRepository.findAll()).willReturn(List.of(c1, c2));
        // 각 신청서의 결재자 목록은 빈 목록으로 반환
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(any())).willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getApplications: 신청서가 없으면 빈 목록을 반환한다")
    void getApplications_신청서없음_빈목록반환() {
        given(applicationRepository.findAll()).willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getApplicationsByIds — 일괄 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplicationsByIds: 존재하는 신청서만 반환하고 없는 항목은 제외된다")
    void getApplicationsByIds_존재하는것만반환() {
        // given: APF_MNG_NO는 존재, "APF_NONE"은 없음
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn(APF_MNG_NO);
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(applicationRepository.findById("APF_NONE")).willReturn(Optional.empty());
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO)).willReturn(List.of());

        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of(APF_MNG_NO, "APF_NONE"));

        List<ApplicationDto.Response> result = applicationService.getApplicationsByIds(request);

        // 존재하는 1건만 반환
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getApplicationsByIds: 모두 존재하지 않으면 빈 목록을 반환한다")
    void getApplicationsByIds_모두없음_빈목록반환() {
        given(applicationRepository.findById(any())).willReturn(Optional.empty());

        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of("APF_NONE1", "APF_NONE2"));

        List<ApplicationDto.Response> result = applicationService.getApplicationsByIds(request);

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getApprovalBadgeCount — 배지 건수 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApprovalBadgeCount: 결재 대기/진행 건수를 정상 반환한다")
    void getApprovalBadgeCount_정상반환() {
        given(applicationRepository.countPendingByEno("E10001")).willReturn(3);
        given(applicationRepository.countInProgressByEno("E10001")).willReturn(2);

        ApplicationDto.ApprovalBadgeCountResponse result =
                applicationService.getApprovalBadgeCount("BBR001", "E10001");

        assertThat(result.getPendingCount()).isEqualTo(3);
        assertThat(result.getInProgressCount()).isEqualTo(2);
    }

    // ───────────────────────────────────────────────────────
    // bulkApprove — 일괄 결재
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("bulkApprove: 단건 승인 처리 후 성공 결과를 반환한다")
    void bulkApprove_단건승인_성공결과반환() {
        // given: approve 처리를 위한 Mock 설정
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcdSqnAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        // bulkApprove 요청 생성
        ApplicationDto.ApprovalItem item = new ApplicationDto.ApprovalItem();
        item.setApfMngNo(APF_MNG_NO);
        item.setDcdEno("E10001");
        item.setDcdOpnn("일괄결재테스트");
        item.setDcdSts("승인");

        ApplicationDto.BulkApproveRequest request = new ApplicationDto.BulkApproveRequest();
        request.setApprovals(List.of(item));

        // when
        ApplicationDto.BulkApproveResponse response = applicationService.bulkApprove(request);

        // then
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("bulkApprove: 신청서가 없으면 RuntimeException을 던진다")
    void bulkApprove_신청서없음_RuntimeException발생() {
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.empty());

        ApplicationDto.ApprovalItem item = new ApplicationDto.ApprovalItem();
        item.setApfMngNo(APF_MNG_NO);
        item.setDcdEno("E10001");
        item.setDcdOpnn("일괄결재테스트");
        item.setDcdSts("승인");

        ApplicationDto.BulkApproveRequest request = new ApplicationDto.BulkApproveRequest();
        request.setApprovals(List.of(item));

        assertThatThrownBy(() -> applicationService.bulkApprove(request))
                .isInstanceOf(RuntimeException.class);
    }
}
