package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
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
    @Mock private ProjectRepository projectRepository;
    @Mock private CostRepository costRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApprovalLineDelegate approvalLineDelegate;
    @Mock private com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    @InjectMocks
    private ApplicationService applicationService;

    private static final String APF_MNG_NO = "APF_202600000001";

    /** Capplm Mock — getApfDtlCone() null로 updateApprovalLineInDetail 즉시 리턴 */
    private Capplm mockCapplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(null);
        return capplm;
    }

    /** 미결재(dcdStsC="1") 상태의 Cdecim 생성 */
    private Cdecim pendingApprover(String eno, int sqn, String lstDcdYn) {
        return Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcrSqnSno(sqn)
                .dcrEno(eno)
                .lstDcdYn(lstDcdYn)
                .dcdStsC(com.kdb.it.common.approval.domain.DecisionStatus.PENDING.code())
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

    /** JSON 결재선 갱신까지 검증하기 위한 실제 ObjectMapper 서비스 */
    private ApplicationService serviceWithRealObjectMapper() {
        return new ApplicationService(
                applicationRepository,
                approverRepository,
                applicationMapRepository,
                projectRepository,
                costRepository,
                eventPublisher,
                new ApprovalLineDelegate(new ObjectMapper()),
                bprojaSyncService);
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
                .dcdMngNo(APF_MNG_NO).dcrSqnSno(1).dcrEno("E10001")
                .lstDcdYn("Y")
                .dcdStsC(com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code()).build();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO)).willReturn(List.of(completed));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 현재 결재자가 아닌 사번으로 요청하면 IllegalArgumentException을 던진다")
    void approve_잘못된결재자_IllegalArgumentException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
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
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재 상태");
    }

    @Test
    @DisplayName("approve: 결재 상태가 빈 문자열이면 IllegalArgumentException을 던진다")
    void approve_결재상태빈문자열_IllegalArgumentException발생() {
        // Arrange: 결재 상태를 빈 문자열("")로 설정 — isEmpty() 분기를 별도 커버
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        // Act & Assert: 빈 문자열도 유효하지 않은 결재 상태
        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", "")))
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
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(capplm).updateStatus(ApprovalStatus.COMPLETED);
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 반려 처리 시 신청서 상태가 반려로 변경되고 이벤트가 발행된다")
    void approve_반려처리_반려이벤트발행() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "반려"));

        verify(capplm).updateStatus(ApprovalStatus.REJECTED);
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 중간 결재자 승인 시 신청서 상태 변경 없이 이벤트도 발행되지 않는다")
    void approve_중간결재자승인_상태변경없음() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim mid = pendingApprover("E10001", 1, "N");
        Cdecim last = pendingApprover("E10002", 2, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO)).willReturn(List.of(mid, last));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(capplm, never()).updateStatus(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("approve: 이전 결재가 승인 상태가 아니면 결재 차례 예외가 발생한다")
    void approve_이전결재미승인_IllegalStateException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim rejected = Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcrSqnSno(1)
                .dcrEno("E10001")
                .lstDcdYn("N")
                .dcdStsC(com.kdb.it.common.approval.domain.DecisionStatus.REJECTED.code())
                .build();
        Cdecim pending = pendingApprover("E10002", 2, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(rejected, pending));

        assertThatThrownBy(() -> applicationService.approve(APF_MNG_NO, approveRequest("E10002", "승인")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 동일 결재자가 연속이면 함께 승인되고 JSON 결재선 날짜도 반영된다")
    void approve_동일결재자연속_자동승인과Json갱신() throws Exception {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm = Capplm.builder()
                .apfMngNo(APF_MNG_NO)
                .dcdReqInf("{\"approvalLine\":{\"team\":{\"id\":\"E10001\"},\"dept\":{\"id\":\"E10001\"},\"ceo\":{\"id\":\"E10002\"}}}")
                .build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim first = pendingApprover("E10001", 1, "N");
        Cdecim second = pendingApprover("E10001", 2, "N");
        Cdecim last = pendingApprover("E10002", 3, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(first, second, last));

        realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        assertThat(first.getDcdStsC()).isEqualTo(com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code());
        assertThat(second.getDcdStsC()).isEqualTo(com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code());
        assertThat(capplm.getDcdReqInf()).contains("\"date\"");
        verify(approverRepository, times(2)).save(any(Cdecim.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("approve: 결재선 JSON이 없는 경우 결재 처리는 정상 완료된다")
    void approve_결재선Json없음_결재처리완료() {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm = Capplm.builder()
                .apfMngNo(APF_MNG_NO)
                .dcdReqInf(null)
                .build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        assertThat(capplm.getApfPrgStsC()).isEqualTo(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code());
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 결재선 JSON이 깨진 경우 CustomGeneralException으로 트랜잭션 롤백 — ERR-03")
    void approve_결재선Json파싱실패_CustomGeneralException() {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm = Capplm.builder()
                .apfMngNo(APF_MNG_NO)
                .dcdReqInf("{not-json")
                .build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(() -> realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(com.kdb.it.exception.CustomGeneralException.class);
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
        given(projectRepository.countBySearchCondition(any())).willReturn(3L);
        given(costRepository.countBySearchCondition(any())).willReturn(2L);

        ApplicationDto.PendingCountResponse result = applicationService.getPendingCount(null);

        assertThat(result.getProjectCount()).isEqualTo(3L);
        assertThat(result.getCostCount()).isEqualTo(2L);
        assertThat(result.getTotalCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getPendingCount: 미상신 건수가 없으면 총합 0을 반환한다")
    void getPendingCount_미상신없음_총합0반환() {
        given(projectRepository.countBySearchCondition(any())).willReturn(0L);
        given(costRepository.countBySearchCondition(any())).willReturn(0L);

        ApplicationDto.PendingCountResponse result = applicationService.getPendingCount(null);

        assertThat(result.getTotalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getPendingCount: 전체 적재 대신 countBySearchCondition COUNT 쿼리를 사용한다")
    void getPendingCount_usesCountQuery() {
        given(projectRepository.countBySearchCondition(any())).willReturn(3L);
        given(costRepository.countBySearchCondition(any())).willReturn(2L);

        ApplicationDto.PendingCountResponse res = applicationService.getPendingCount("2026");

        assertThat(res.getTotalCount()).isEqualTo(5L);
        verify(projectRepository, never()).searchByCondition(any());
        verify(costRepository, never()).searchByCondition(any());
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
        given(capplm.getDcdReqInf()).willReturn("{\"test\":\"value\"}");
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
        // 결재자 목록은 In-쿼리 1회 배치 조회 (빈 목록 반환)
        given(approverRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(any())).willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getApplications: 결재자 목록은 findByDcdMngNoIn 1회로 배치 조회한다")
    void getApplications_batchesApprovers() {
        Capplm a1 = mock(Capplm.class);
        Capplm a2 = mock(Capplm.class);
        given(a1.getApfMngNo()).willReturn("APF-1");
        given(a2.getApfMngNo()).willReturn("APF-2");
        given(applicationRepository.findAll()).willReturn(List.of(a1, a2));
        // APF-1 결재선 2건(순서 유지 검증), APF-2 결재선 없음
        Cdecim d1 = Cdecim.builder().dcdMngNo("APF-1").dcrSqnSno(1).dcrEno("E001").build();
        Cdecim d2 = Cdecim.builder().dcdMngNo("APF-1").dcrSqnSno(2).dcrEno("E002").build();
        given(approverRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of(d1, d2));

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(2);
        verify(approverRepository, times(1))
                .findByDcdMngNoInOrderByDcrSqnSnoAsc(any());
        verify(approverRepository, never())
                .findByDcdMngNoOrderByDcrSqnSnoAsc(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("getApplications: 레거시 1자리 미결재 코드가 있어도 목록을 반환한다")
    void getApplications_레거시미결재코드_목록반환() {
        Capplm capplm = Capplm.builder()
                .apfMngNo(APF_MNG_NO)
                .apfPrgStsC(ApprovalStatus.IN_PROGRESS.code())
                .build();
        Cdecim legacyPending = Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcrSqnSno(1)
                .dcrEno("E10001")
                .lstDcdYn("Y")
                .dcdStsC("0")
                .build();
        given(applicationRepository.findAll()).willReturn(List.of(capplm));
        given(approverRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of(legacyPending));

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getApprovers().getFirst().getDcdSts()).isNull();
        assertThat(result.getFirst().getApprovers().getFirst().getDcdTp()).isNull();
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
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO)).willReturn(List.of());

        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of(APF_MNG_NO, "APF_NONE"));

        ApplicationDto.BulkResponse result = applicationService.getApplicationsByIds(request);

        // 존재하는 1건만 반환
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("getApplicationsByIds: 일부 미존재 ID는 failedIds에 담기고 items는 정상분만 반환")
    void getApplicationsByIds_partialMissing_returnsItemsAndFailedIds() {
        Capplm found = mock(Capplm.class);
        given(found.getApfMngNo()).willReturn("APF-1");
        given(applicationRepository.findById("APF-1")).willReturn(Optional.of(found));
        given(applicationRepository.findById("APF-X")).willReturn(Optional.empty());
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc("APF-1")).willReturn(List.of());
        ApplicationDto.BulkGetRequest req = new ApplicationDto.BulkGetRequest();
        req.setApfMngNos(List.of("APF-1", "APF-X"));

        ApplicationDto.BulkResponse result = applicationService.getApplicationsByIds(req);

        assertThat(result.items()).hasSize(1);
        assertThat(result.failedIds()).containsExactly("APF-X");
    }

    @Test
    @DisplayName("getApplicationsByIds: 모두 존재하지 않으면 빈 목록을 반환한다")
    void getApplicationsByIds_모두없음_빈목록반환() {
        given(applicationRepository.findById(any())).willReturn(Optional.empty());

        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of("APF_NONE1", "APF_NONE2"));

        ApplicationDto.BulkResponse result = applicationService.getApplicationsByIds(request);

        assertThat(result.items()).isEmpty();
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
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
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

    // ───────────────────────────────────────────────────────
    // submit — 신청서 생성
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submit: 결재자 1명으로 신청서를 생성하면 APF_ 형식의 관리번호를 반환한다")
    void submit_신청서생성_관리번호반환() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("10001");
        request.setApproverEnos(List.of("10002"));

        String result = applicationService.submit(request);

        assertThat(result).startsWith("APF-");
        verify(applicationRepository).save(any());
    }

    @Test
    @DisplayName("submit: 원본 항목을 연결하고 기안자가 1차 결재자여도 자동 승인하지 않는다")
    void submit_원본항목연결_기안자1차결재자도_자동승인없음() {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.OrcItem project = new ApplicationDto.OrcItem();
        project.setFntTbNm("BPROJM");
        project.setPkColNm("PRJ-001");
        project.setFntTbCrySno("3");
        ApplicationDto.OrcItem cost = new ApplicationDto.OrcItem();
        cost.setFntTbNm("BCOSTM");
        cost.setPkColNm("COST-001");

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("10001");
        request.setApfDtlCone("{\"approvalLine\":{\"team\":{\"id\":\"10001\"},\"dept\":{\"id\":\"10002\"}}}");
        request.setOrcItems(List.of(project, cost));
        request.setApproverEnos(List.of("10001", "10002"));

        String result = realMapperService.submit(request);

        assertThat(result).startsWith("APF-");
        ArgumentCaptor<Cappla> capplaCaptor = ArgumentCaptor.forClass(Cappla.class);
        verify(applicationMapRepository, times(2)).save(capplaCaptor.capture());
        assertThat(capplaCaptor.getAllValues()).extracting(value -> value.getFntTbCrySno())
                .containsExactly(3, null);
        // 결재선 2건 초기 저장만 발생 (자동 승인 분기 제거됨)
        verify(approverRepository, times(2)).save(any(Cdecim.class));
    }

    @Test
    @DisplayName("submit: APF_STS_C='01'로 저장된다")
    void submit_setsApfStsCToInProgressCode() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("E001");
        request.setApproverEnos(List.of("E002"));

        applicationService.submit(request);

        ArgumentCaptor<Capplm> captor = ArgumentCaptor.forClass(Capplm.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getApfPrgStsC()).isEqualTo("1");
    }

    @Test
    @DisplayName("submit: orcItems N건이면 Cappla N건 저장 (pkColNm 매핑 확인)")
    void submit_savesCapplaPerOrcItem() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        java.util.List<ApplicationDto.OrcItem> items = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ApplicationDto.OrcItem item = new ApplicationDto.OrcItem();
            item.setFntTbNm("BPROJM");
            item.setPkColNm("PRJ-2026-000" + i);
            items.add(item);
        }
        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("E001");
        request.setApproverEnos(List.of("E002"));
        request.setOrcItems(items);

        applicationService.submit(request);

        ArgumentCaptor<Cappla> captor = ArgumentCaptor.forClass(Cappla.class);
        verify(applicationMapRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(value -> value.getPkColNm())
                .containsExactly("PRJ-2026-0001", "PRJ-2026-0002", "PRJ-2026-0003");
    }

    @Test
    @DisplayName("submit: BPROJM 원천은 BPROJA 결재중('05')으로 적재하고, BCOSTM 원천은 적재하지 않는다")
    void submit_BPROJM원천_BPROJA결재중02_적재() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.OrcItem project = new ApplicationDto.OrcItem();
        project.setFntTbNm("BPROJM");
        project.setPkColNm("PRJ-2026-0001");
        ApplicationDto.OrcItem cost = new ApplicationDto.OrcItem();
        cost.setFntTbNm("BCOSTM");
        cost.setPkColNm("COST-001");

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("E001");
        request.setApproverEnos(List.of("E002"));
        request.setOrcItems(List.of(project, cost));

        applicationService.submit(request);

        // BPROJM 프로젝트만 (pk, pk, '05')로 upsert, BCOSTM은 미적재
        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PRJ-2026-0001", "05");
        verify(bprojaSyncService, never()).upsert(eq("COST-001"), any(), any());
    }

    @Test
    @DisplayName("approve: 최종 승인 완료 시 연결된 BPROJM의 BPROJA를 결재완료('09')로 적재한다")
    void approve_최종승인_BPROJA결재완료09_적재() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));
        Cappla bprojm = Cappla.builder().apfDcmNo(APF_MNG_NO).fntTbNm("BPROJM").pkColNm("PRJ-2026-0001").build();
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, "BPROJM"))
                .willReturn(List.of(bprojm));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PRJ-2026-0001", "09");
    }

    @Test
    @DisplayName("bulkApprove: 승인 목록이 비어 있으면 0건 성공으로 반환한다")
    void bulkApprove_빈목록_0건반환() {
        ApplicationDto.BulkApproveRequest request = new ApplicationDto.BulkApproveRequest();
        request.setApprovals(List.of());

        ApplicationDto.BulkApproveResponse response = applicationService.bulkApprove(request);

        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getResults()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getDashboard — 대시보드 집계
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDashboard: 정상 호출 시 집계 결과를 반환한다")
    void getDashboard_정상호출_집계결과반환() {
        given(applicationRepository.countPendingByEno("10001")).willReturn(2);
        given(applicationRepository.countInProgressByEno("10001")).willReturn(1);
        given(applicationRepository.countMonthlyCompletedByBbrC("BBR001")).willReturn(3);
        given(applicationRepository.countRejectedByEno("10001")).willReturn(0);
        given(applicationRepository.findMonthlyTrendRowsByBbrC("BBR001")).willReturn(List.of());
        given(applicationRepository.findPendingRowsByEno("10001")).willReturn(List.of());

        ApplicationDto.DashboardResponse result =
                applicationService.getDashboard("BBR001", "10001");

        assertThat(result.getPendingCount()).isEqualTo(2);
        assertThat(result.getInProgressCount()).isEqualTo(1);
        assertThat(result.getMonthlyCompletedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getDashboard: 월별 추이와 오래된 결재 대기 건은 긴급으로 반환한다")
    void getDashboard_월별추이와긴급상태반환() {
        given(applicationRepository.countPendingByEno("10001")).willReturn(2);
        given(applicationRepository.countInProgressByEno("10001")).willReturn(1);
        given(applicationRepository.countMonthlyCompletedByBbrC("BBR001")).willReturn(4);
        given(applicationRepository.countRejectedByEno("10001")).willReturn(1);
        given(applicationRepository.findMonthlyTrendRowsByBbrC("BBR001"))
                .willReturn(java.util.Collections.singletonList(
                        LabeledCountRow.fromRow(new Object[]{"2026-05", 4})));
        given(applicationRepository.findPendingRowsByEno("10001")).willReturn(List.of(
                PendingApprovalRow.fromRow(new Object[]{"APF-OLD", "오래된 신청", "홍길동", LocalDate.now().minusDays(4).toString()}),
                PendingApprovalRow.fromRow(new Object[]{"APF-NULL", "날짜 없음", "김길동", null})
        ));

        ApplicationDto.DashboardResponse result = applicationService.getDashboard("BBR001", "10001");

        assertThat(result.getMonthlyTrend()).extracting(value -> value.getCount())
                .containsExactly(4);
        assertThat(result.getPendingList()).extracting(value -> value.getUrgency())
                .containsExactly("urgent", "normal");
    }
}
