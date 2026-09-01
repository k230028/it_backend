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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.access.AccessDeniedException;

/**
 * ApplicationService 단위 테스트
 *
 * <p>결재 처리(approve), 신청서 조회(getApplication, getApplications, getApplicationsByIds), 세부내용
 * 조회(getApfDtlCone), 일괄결재(bulkApprove), 배지수(getApprovalBadgeCount), 미상신 건수(getPendingCount)를 검증합니다.
 * Capplm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다. Cdecim 엔티티는 @SuperBuilder로 직접 구성합니다.
 * Oracle DB 없이 실행됩니다.
 *
 * <p>커버리지 60% 달성을 위해 미커버 메서드 테스트 추가 (2026-04-29)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationServiceTest {

    private record NameView(String eno, String usrNm, String ptCNm)
            implements UserRepository.UserNameView {
        /** 직위명이 검증 대상이 아닌 기존 케이스용 축약 생성자. */
        private NameView(String eno, String usrNm) {
            this(eno, usrNm, null);
        }

        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return ptCNm;
        }
    }

    private record OrgNameView(String prlmOgzCCone, String bbrNm)
            implements OrganizationRepository.OrganizationNameView {
        @Override
        public String getPrlmOgzCCone() {
            return prlmOgzCCone;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }
    }

    private record ApplicationReadView(
            String apfMngNo,
            String itPtlApfPrgStsC,
            String dcdReqTtl,
            String dcdReqInf,
            String dcdReqUsid,
            LocalDate dcdReqDtm,
            String rgprDcdReqCone,
            String dcdReqBbrC)
            implements ApplicationRepository.ApplicationReadView {
        @Override
        public String getApfMngNo() {
            return apfMngNo;
        }

        @Override
        public String getItPtlApfPrgStsC() {
            return itPtlApfPrgStsC;
        }

        @Override
        public String getDcdReqTtl() {
            return dcdReqTtl;
        }

        @Override
        public String getDcdReqInf() {
            return dcdReqInf;
        }

        @Override
        public String getDcdReqUsid() {
            return dcdReqUsid;
        }

        @Override
        public LocalDate getDcdReqDtm() {
            return dcdReqDtm;
        }

        @Override
        public String getRgprDcdReqCone() {
            return rgprDcdReqCone;
        }

        @Override
        public String getDcdReqBbrC() {
            return dcdReqBbrC;
        }
    }

    private record ApproverReadView(
            String dcdMngNo,
            Integer dcrSqnSno,
            String dcrEno,
            String itPtlDcdStsC,
            LocalDate dcdDtm,
            String dcrOpnnCone,
            String lstDcdYn)
            implements ApproverRepository.ApproverReadView {
        @Override
        public String getDcdMngNo() {
            return dcdMngNo;
        }

        @Override
        public Integer getDcrSqnSno() {
            return dcrSqnSno;
        }

        @Override
        public String getDcrEno() {
            return dcrEno;
        }

        @Override
        public String getItPtlDcdStsC() {
            return itPtlDcdStsC;
        }

        @Override
        public LocalDate getDcdDtm() {
            return dcdDtm;
        }

        @Override
        public String getDcrOpnnCone() {
            return dcrOpnnCone;
        }

        @Override
        public String getLstDcdYn() {
            return lstDcdYn;
        }
    }

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApproverRepository approverRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CostRepository costRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApprovalLineDelegate approvalLineDelegate;
    @Mock private com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;
    @Mock private ApprovalRequestNotifier approvalRequestNotifier;

    @InjectMocks private ApplicationService applicationService;
    @InjectMocks private PendingApproverService pendingApproverService;

    private static final String APF_MNG_NO = "APF_202600000001";

    @BeforeEach
    void setUp() {
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(List.of());
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any())).willReturn(List.of());
    }

    /** Capplm Mock — getApfDtlCone() null로 updateApprovalLineInDetail 즉시 리턴 */
    private Capplm mockCapplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(null);
        return capplm;
    }

    /** 미결재(itPtlDcdStsC="1") 상태의 Cdecim 생성 */
    private Cdecim pendingApprover(String eno, int sqn, String lstDcdYn) {
        return Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcrSqnSno(sqn)
                .dcrEno(eno)
                .lstDcdYn(lstDcdYn)
                .itPtlDcdStsC(com.kdb.it.common.approval.domain.DecisionStatus.PENDING.code())
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
                userRepository,
                organizationRepository,
                eventPublisher,
                new ApprovalLineDelegate(new ObjectMapper()),
                bprojaSyncService,
                approvalRequestNotifier);
    }

    // ───────────────────────────────────────────────────────
    // approve — 예외 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("approve: 신청서가 없으면 IllegalArgumentException을 던진다")
    void approve_신청서없음_IllegalArgumentException발생() {
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                applicationService.approve(
                                        APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(APF_MNG_NO);
    }

    @Test
    @DisplayName("approve: 모든 결재가 완료된 경우 IllegalStateException을 던진다")
    void approve_모든결재완료_IllegalStateException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim completed =
                Cdecim.builder()
                        .dcdMngNo(APF_MNG_NO)
                        .dcrSqnSno(1)
                        .dcrEno("E10001")
                        .lstDcdYn("Y")
                        .itPtlDcdStsC(
                                com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code())
                        .build();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(completed));

        assertThatThrownBy(
                        () ->
                                applicationService.approve(
                                        APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 현재 결재자가 아닌 사번으로 요청하면 IllegalArgumentException을 던진다")
    void approve_잘못된결재자_IllegalArgumentException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(
                        () ->
                                applicationService.approve(
                                        APF_MNG_NO, approveRequest("E99999", "승인")))
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

        assertThatThrownBy(
                        () ->
                                applicationService.approve(
                                        APF_MNG_NO, approveRequest("E10001", null)))
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
        assertThatThrownBy(
                        () -> applicationService.approve(APF_MNG_NO, approveRequest("E10001", "")))
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
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(mid, last));

        applicationService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        verify(capplm, never()).updateStatus(any());
        verify(eventPublisher, never()).publishEvent(any());
        // 중간 승인은 결재완료 이벤트 대신 다음 결재자 알림 발행을 위임한다
        verify(approvalRequestNotifier).notifyApprovalRequest(capplm);
    }

    @Test
    @DisplayName("approve: 이전 결재가 승인 상태가 아니면 결재 차례 예외가 발생한다")
    void approve_이전결재미승인_IllegalStateException발생() {
        Capplm capplm = mockCapplm();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim rejected =
                Cdecim.builder()
                        .dcdMngNo(APF_MNG_NO)
                        .dcrSqnSno(1)
                        .dcrEno("E10001")
                        .lstDcdYn("N")
                        .itPtlDcdStsC(
                                com.kdb.it.common.approval.domain.DecisionStatus.REJECTED.code())
                        .build();
        Cdecim pending = pendingApprover("E10002", 2, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(rejected, pending));

        assertThatThrownBy(
                        () ->
                                applicationService.approve(
                                        APF_MNG_NO, approveRequest("E10002", "승인")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 동일 결재자가 연속이면 함께 승인되고 JSON 결재선 날짜도 반영된다")
    void approve_동일결재자연속_자동승인과Json갱신() throws Exception {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm =
                Capplm.builder()
                        .apfMngNo(APF_MNG_NO)
                        .dcdReqInf(
                                "{\"approvalLine\":{\"team\":{\"id\":\"E10001\"},\"dept\":{\"id\":\"E10001\"},\"ceo\":{\"id\":\"E10002\"}}}")
                        .build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));

        Cdecim first = pendingApprover("E10001", 1, "N");
        Cdecim second = pendingApprover("E10001", 2, "N");
        Cdecim last = pendingApprover("E10002", 3, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(first, second, last));

        realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        assertThat(first.getItPtlDcdStsC())
                .isEqualTo(com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code());
        assertThat(second.getItPtlDcdStsC())
                .isEqualTo(com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code());
        assertThat(capplm.getDcdReqInf()).contains("\"date\"");
        verify(approverRepository, times(2)).save(any(Cdecim.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("approve: 결재선 JSON이 없는 경우 결재 처리는 정상 완료된다")
    void approve_결재선Json없음_결재처리완료() {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm = Capplm.builder().apfMngNo(APF_MNG_NO).dcdReqInf(null).build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인"));

        assertThat(capplm.getItPtlApfPrgStsC())
                .isEqualTo(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code());
        verify(eventPublisher).publishEvent(any(ApprovalCompletedEvent.class));
    }

    @Test
    @DisplayName("approve: 결재선 JSON이 깨진 경우 CustomGeneralException으로 트랜잭션 롤백 — ERR-03")
    void approve_결재선Json파싱실패_CustomGeneralException() {
        ApplicationService realMapperService = serviceWithRealObjectMapper();
        Capplm capplm = Capplm.builder().apfMngNo(APF_MNG_NO).dcdReqInf("{not-json").build();
        given(applicationRepository.findById(APF_MNG_NO)).willReturn(Optional.of(capplm));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("E10001", 1, "Y")));

        assertThatThrownBy(
                        () -> realMapperService.approve(APF_MNG_NO, approveRequest("E10001", "승인")))
                .isInstanceOf(com.kdb.it.exception.CustomGeneralException.class);
    }

    // ───────────────────────────────────────────────────────
    // getApplication
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplication: 존재하지 않는 신청서 번호이면 IllegalArgumentException을 던진다")
    void getApplication_신청서없음_IllegalArgumentException발생() {
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.empty());

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
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApfDtlCone(APF_MNG_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(APF_MNG_NO);
    }

    @Test
    @DisplayName("getApfDtlCone: 존재하는 신청서이면 세부내용 응답 DTO를 반환한다")
    void getApfDtlCone_존재하는신청서_DTO반환() {
        // ApfDtlConeResponse.fromReadView() 가 호출되므로 필요한 필드만 설정
        ApplicationReadView view =
                new ApplicationReadView(
                        APF_MNG_NO, null, null, "{\"test\":\"value\"}", null, null, null, null);
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.of(view));

        ApplicationDto.ApfDtlConeResponse result = applicationService.getApfDtlCone(APF_MNG_NO);

        assertThat(result).isNotNull();
        assertThat(result.getApfMngNo()).isEqualTo(APF_MNG_NO);
        assertThat(result.getApfDtlCone()).isEqualTo("{\"test\":\"value\"}");
    }

    // ───────────────────────────────────────────────────────
    // getApplications — 전체 목록 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplications: 전체 신청서 목록을 반환한다")
    void getApplications_전체목록반환() {
        // given: 두 개의 신청서 read view 준비
        ApplicationReadView v1 =
                new ApplicationReadView(APF_MNG_NO, null, null, null, null, null, null, null);
        ApplicationReadView v2 =
                new ApplicationReadView(
                        "APF_202600000002", null, null, null, null, null, null, null);
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of(v1, v2));
        // 결재자 목록은 In-쿼리 1회 배치 조회 (빈 목록 반환)
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getApplications: 결재자 목록은 findByDcdMngNoIn 1회로 배치 조회한다")
    void getApplications_batchesApprovers() {
        ApplicationReadView a1 =
                new ApplicationReadView("APF-1", null, null, null, null, null, null, null);
        ApplicationReadView a2 =
                new ApplicationReadView("APF-2", null, null, null, null, null, null, null);
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of(a1, a2));
        // APF-1 결재선 2건(순서 유지 검증), APF-2 결재선 없음
        ApproverReadView d1 = new ApproverReadView("APF-1", 1, "E001", "1", null, null, "N");
        ApproverReadView d2 =
                new ApproverReadView("APF-1", 2, "E002", "2", LocalDate.now(), "승인", "Y");
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of(d1, d2));

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getApprovers())
                .extracting(approver -> approver.getDcdSqn())
                .containsExactly(1, 2);
        verify(approverRepository, times(1)).findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any());
        verify(approverRepository, never())
                .findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("getApplications: 결재자 표시 정보를 한 번에 해석한다")
    void getApplications_결재자표시정보_배치해석() {
        ApplicationReadView view =
                new ApplicationReadView("APF-1", null, null, null, null, null, null, null);
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(
                        List.of(
                                new ApproverReadView("APF-1", 1, "E001", "1", null, null, "N"),
                                new ApproverReadView("APF-1", 2, "E002", "1", null, null, "Y")));
        given(userRepository.findByEnoIn(Set.of("E001", "E002")))
                .willReturn(
                        List.of(
                                CuserI.builder()
                                        .eno("E001")
                                        .usrNm("김기획부장")
                                        .ptCNm("부장")
                                        .organization(CorgnI.builder().bbrNm("기획부").build())
                                        .build(),
                                CuserI.builder()
                                        .eno("E002")
                                        .usrNm("김기획팀장")
                                        .ptCNm("팀장")
                                        .organization(CorgnI.builder().bbrNm("기획팀").build())
                                        .build()));

        ApplicationDto.Response response = applicationService.getApplications().getFirst();

        assertThat(response.getApprovers())
                .extracting(ApplicationDto.ApproverResponse::getUsrNm)
                .containsExactly("김기획부장", "김기획팀장");
        assertThat(response.getApprovers())
                .extracting(ApplicationDto.ApproverResponse::getPtCNm)
                .containsExactly("부장", "팀장");
        assertThat(response.getApprovers())
                .extracting(ApplicationDto.ApproverResponse::getBbrNm)
                .containsExactly("기획부", "기획팀");
        verify(userRepository).findByEnoIn(Set.of("E001", "E002"));
    }

    @Test
    @DisplayName("getApplications: 레거시 1자리 미결재 코드가 있어도 목록을 반환한다")
    void getApplications_레거시미결재코드_목록반환() {
        ApplicationReadView view =
                new ApplicationReadView(
                        APF_MNG_NO,
                        ApprovalStatus.IN_PROGRESS.code(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        ApproverReadView legacyPending =
                new ApproverReadView(APF_MNG_NO, 1, "E10001", "0", null, null, "Y");
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of(legacyPending));

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getApprovers().getFirst().getDcdSts()).isNull();
        assertThat(result.getFirst().getApprovers().getFirst().getDcdTp()).isNull();
    }

    @Test
    @DisplayName("getApplications: 신청서가 없으면 빈 목록을 반환한다")
    void getApplications_신청서없음_빈목록반환() {
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getPendingApplications — 본인 결재 대기 목록 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingApplications: 결재 대기 신청서를 조회 순서(최신순) 그대로 반환한다")
    void getPendingApplications_최신순반환() {
        ApplicationReadView older =
                new ApplicationReadView("APF-1", null, null, null, null, null, null, null);
        ApplicationReadView newer =
                new ApplicationReadView("APF-2", null, null, null, null, null, null, null);
        given(applicationRepository.findPendingApfMngNosByEno("E10001"))
                .willReturn(List.of("APF-2", "APF-1"));
        // findReadViewsByApfMngNoIn은 순서를 보장하지 않으므로 뒤섞인 순서로 돌려준다
        given(applicationRepository.findReadViewsByApfMngNoIn(List.of("APF-2", "APF-1")))
                .willReturn(List.of(older, newer));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getPendingApplications("E10001");

        assertThat(result)
                .extracting(ApplicationDto.Response::getApfMngNo)
                .containsExactly("APF-2", "APF-1");
    }

    @Test
    @DisplayName("getPendingApplications: 결재 대기 건이 없으면 빈 목록을 반환하고 상세를 조회하지 않는다")
    void getPendingApplications_대기없음_빈목록반환() {
        given(applicationRepository.findPendingApfMngNosByEno("E10001")).willReturn(List.of());

        List<ApplicationDto.Response> result = applicationService.getPendingApplications("E10001");

        assertThat(result).isEmpty();
        verify(applicationRepository, never()).findReadViewsByApfMngNoIn(any());
    }

    @Test
    @DisplayName("getPendingApplications: 사번이 비어 있으면 빈 목록이 아니라 예외로 구분한다")
    void getPendingApplications_사번없음_예외() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> applicationService.getPendingApplications("  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(applicationRepository, never()).findPendingApfMngNosByEno(any());
    }

    // ───────────────────────────────────────────────────────
    // getApplicationsByIds — 일괄 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplicationsByIds: 존재하는 신청서만 반환하고 없는 항목은 제외된다")
    void getApplicationsByIds_존재하는것만반환() {
        // given: APF_MNG_NO는 존재, "APF_NONE"은 없음
        ApplicationReadView view =
                new ApplicationReadView(APF_MNG_NO, null, null, null, null, null, null, null);
        given(applicationRepository.findReadViewsByApfMngNoIn(any())).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of());

        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of(APF_MNG_NO, "APF_NONE"));

        ApplicationDto.BulkResponse result = applicationService.getApplicationsByIds(request);

        // 존재하는 1건만 반환
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("getApplicationsByIds: 일부 미존재 ID는 failedIds에 담기고 items는 정상분만 반환")
    void getApplicationsByIds_partialMissing_returnsItemsAndFailedIds() {
        ApplicationReadView found =
                new ApplicationReadView("APF-1", null, null, null, null, null, null, null);
        given(applicationRepository.findReadViewsByApfMngNoIn(any())).willReturn(List.of(found));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of());
        ApplicationDto.BulkGetRequest req = new ApplicationDto.BulkGetRequest();
        req.setApfMngNos(List.of("APF-1", "APF-X"));

        ApplicationDto.BulkResponse result = applicationService.getApplicationsByIds(req);

        assertThat(result.items()).hasSize(1);
        assertThat(result.failedIds()).containsExactly("APF-X");
    }

    @Test
    @DisplayName("getApplicationsByIds: 결재자 표시 정보를 배치 해석한다")
    void getApplicationsByIds_결재자표시정보_배치해석() {
        ApplicationReadView view =
                new ApplicationReadView("APF-1", null, null, null, null, null, null, null);
        given(applicationRepository.findReadViewsByApfMngNoIn(any())).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(
                        List.of(new ApproverReadView("APF-1", 1, "E001", "1", null, null, "Y")));
        given(userRepository.findByEnoIn(Set.of("E001")))
                .willReturn(
                        List.of(
                                CuserI.builder()
                                        .eno("E001")
                                        .usrNm("김기획부장")
                                        .ptCNm("부장")
                                        .organization(CorgnI.builder().bbrNm("기획부").build())
                                        .build()));
        ApplicationDto.BulkGetRequest request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of("APF-1"));

        ApplicationDto.ApproverResponse approver =
                applicationService
                        .getApplicationsByIds(request)
                        .items()
                        .getFirst()
                        .getApprovers()
                        .getFirst();

        assertThat(approver.getUsrNm()).isEqualTo("김기획부장");
        assertThat(approver.getPtCNm()).isEqualTo("부장");
        assertThat(approver.getBbrNm()).isEqualTo("기획부");
        verify(userRepository).findByEnoIn(Set.of("E001"));
    }

    @Test
    @DisplayName("getApplicationsByIds: 모두 존재하지 않으면 빈 목록을 반환한다")
    void getApplicationsByIds_모두없음_빈목록반환() {
        given(applicationRepository.findReadViewByApfMngNo(any())).willReturn(Optional.empty());

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
    @DisplayName("submit: 결재선 생성 시 결재유형코드(DCD_TP_C)가 요청('10')으로 채워진다")
    void submit_결재선생성_결재유형코드요청기본값() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("10001");
        request.setApproverEnos(List.of("10002", "10003"));

        applicationService.submit(request);

        ArgumentCaptor<Cdecim> captor = ArgumentCaptor.forClass(Cdecim.class);
        verify(approverRepository, times(2)).save(captor.capture());
        List<Cdecim> savedDecisions = captor.getAllValues();

        assertThat(savedDecisions)
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.getDcdTpC()).isEqualTo(Cdecim.DECISION_TYPE_REQUEST));
    }

    @Test
    @DisplayName("submit: 신청서 등록 후 결재요청 알림 발행을 ApprovalRequestNotifier에 위임한다")
    void submit_결재요청알림_알림발행위임() {
        given(applicationRepository.getNextVal()).willReturn(1L);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("10001");
        request.setApproverEnos(List.of("10002"));

        applicationService.submit(request);

        // 알림 발행의 상세(수신자 선정, 채널, 페이로드 렌더링 실패 방어)는
        // ApprovalRequestNotifierTest가 검증하고, 여기서는 위임 자체만 확인한다.
        ArgumentCaptor<Capplm> captor = ArgumentCaptor.forClass(Capplm.class);
        verify(approvalRequestNotifier).notifyApprovalRequest(captor.capture());
        assertThat(captor.getValue().getApfMngNo())
                .isEqualTo("APF-" + LocalDate.now().getYear() + "-00000001");
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
        cost.setFntTbCrySno("1");
        givenActiveVersion("PRJ-001", 3);
        givenActiveCostVersion("COST-001", 1);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("테스트 신청서");
        request.setRqsEno("10001");
        request.setApfDtlCone(
                "{\"approvalLine\":{\"team\":{\"id\":\"10001\"},\"dept\":{\"id\":\"10002\"}}}");
        request.setOrcItems(List.of(project, cost));
        request.setApproverEnos(List.of("10001", "10002"));

        String result = realMapperService.submit(request);

        assertThat(result).startsWith("APF-");
        ArgumentCaptor<Cappla> capplaCaptor = ArgumentCaptor.forClass(Cappla.class);
        verify(applicationMapRepository, times(2)).save(capplaCaptor.capture());
        assertThat(capplaCaptor.getAllValues())
                .extracting(value -> value.getFntTbCrySno())
                .containsExactly(3, 1);
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
        assertThat(captor.getValue().getItPtlApfPrgStsC()).isEqualTo("1");
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
            item.setFntTbCrySno("1");
            givenActiveVersion("PRJ-2026-000" + i, 1);
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
        project.setFntTbCrySno("1");
        ApplicationDto.OrcItem cost = new ApplicationDto.OrcItem();
        cost.setFntTbNm("BCOSTM");
        cost.setPkColNm("COST-001");
        cost.setFntTbCrySno("1");
        givenActiveVersion("PRJ-2026-0001", 1);
        givenActiveCostVersion("COST-001", 1);

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
        Cappla bprojm =
                Cappla.builder()
                        .apfDcmNo(APF_MNG_NO)
                        .fntTbNm("BPROJM")
                        .pkColNm("PRJ-2026-0001")
                        .build();
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
                .willReturn(
                        java.util.Collections.singletonList(
                                LabeledCountRow.fromRow(new Object[] {"2026-05", 4})));
        given(applicationRepository.findPendingRowsByEno("10001"))
                .willReturn(
                        List.of(
                                PendingApprovalRow.fromRow(
                                        new Object[] {
                                            "APF-OLD",
                                            "오래된 신청",
                                            "홍길동",
                                            LocalDate.now().minusDays(4).toString()
                                        }),
                                PendingApprovalRow.fromRow(
                                        new Object[] {"APF-NULL", "날짜 없음", "김길동", null})));

        ApplicationDto.DashboardResponse result =
                applicationService.getDashboard("BBR001", "10001");

        assertThat(result.getMonthlyTrend())
                .extracting(value -> value.getCount())
                .containsExactly(4);
        assertThat(result.getPendingList())
                .extracting(value -> value.getUrgency())
                .containsExactly("urgent", "normal");
    }

    // ───────────────────────────────────────────────────────
    // 신청자명·부서명 해석 및 방어 분기 보강
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplication: 신청자 사번·부점코드를 이름/부서명으로 해석해 채운다")
    void getApplication_신청자명_부서명_해석() {
        ApplicationReadView view =
                new ApplicationReadView(APF_MNG_NO, null, null, null, "10001", null, null, "18001");
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.of(view));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10002", 1, "Y")));
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new NameView("10001", "홍길동")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(new OrgNameView("18001", "정보기술부")));

        ApplicationDto.Response result = applicationService.getApplication(APF_MNG_NO);

        assertThat(result.getApfMngNo()).isEqualTo(APF_MNG_NO);
        assertThat(result.getRqsNm()).isEqualTo("홍길동");
        assertThat(result.getRqsBbrNm()).isEqualTo("정보기술부");
    }

    @Test
    @DisplayName("getApplication: 결재자 표시 정보를 배치 해석한다")
    void getApplication_결재자표시정보_배치해석() {
        ApplicationReadView view =
                new ApplicationReadView(APF_MNG_NO, null, null, null, null, null, null, null);
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.of(view));
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(
                        List.of(new ApproverReadView(APF_MNG_NO, 1, "E001", "1", null, null, "Y")));
        given(userRepository.findByEnoIn(Set.of("E001")))
                .willReturn(
                        List.of(
                                CuserI.builder()
                                        .eno("E001")
                                        .usrNm("김기획부장")
                                        .ptCNm("부장")
                                        .organization(CorgnI.builder().bbrNm("기획부").build())
                                        .build()));

        ApplicationDto.ApproverResponse approver =
                applicationService.getApplication(APF_MNG_NO).getApprovers().getFirst();

        assertThat(approver.getUsrNm()).isEqualTo("김기획부장");
        assertThat(approver.getPtCNm()).isEqualTo("부장");
        assertThat(approver.getBbrNm()).isEqualTo("기획부");
        verify(userRepository).findByEnoIn(Set.of("E001"));
    }

    @Test
    @DisplayName("getApplication: 신청자 사번·부점코드가 없으면 이름 해석 없이 반환한다")
    void getApplication_신청자정보없음_null유지() {
        ApplicationReadView view =
                new ApplicationReadView(APF_MNG_NO, null, null, null, null, null, null, null);
        given(applicationRepository.findReadViewByApfMngNo(APF_MNG_NO))
                .willReturn(Optional.of(view));
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of());

        ApplicationDto.Response result = applicationService.getApplication(APF_MNG_NO);

        assertThat(result.getApfMngNo()).isEqualTo(APF_MNG_NO);
    }

    @Test
    @DisplayName("getApplications: 부서명이 null인 조직은 매핑에서 제외된다")
    void getApplications_부서명null조직_제외() {
        ApplicationReadView view =
                new ApplicationReadView(APF_MNG_NO, null, null, null, "10001", null, null, "18001");
        given(applicationRepository.findTop500ByOrderByApfMngNoDesc()).willReturn(List.of(view));
        given(approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(any()))
                .willReturn(List.of());
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new NameView("10001", "홍길동")));
        // bbrNm이 null인 조직은 filter(bbrNm != null)에서 제외되는 분기 커버
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(new OrgNameView("18001", null)));

        List<ApplicationDto.Response> result = applicationService.getApplications();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("submit: 신청자 사번으로 소속 부점코드를 조회해 저장한다")
    void submit_신청자부점코드_해석저장() {
        given(applicationRepository.getNextVal()).willReturn(11L);
        given(userRepository.findById("10001"))
                .willReturn(Optional.of(CuserI.builder().eno("10001").bbrC("18001").build()));

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("부서 해석 신청");
        request.setRqsEno("10001");
        request.setApproverEnos(List.of("10002"));

        applicationService.submit(request);

        ArgumentCaptor<Capplm> captor = ArgumentCaptor.forClass(Capplm.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getDcdReqBbrC()).isEqualTo("18001");
    }

    @Test
    @DisplayName("submit: 신청자 사번이 없으면 신청부점코드는 null로 저장된다")
    void submit_신청자사번없음_부점코드null() {
        given(applicationRepository.getNextVal()).willReturn(12L);

        ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
        request.setApfNm("사번 없는 신청");
        request.setRqsEno(null);
        request.setApproverEnos(List.of("10002"));

        String result = applicationService.submit(request);

        assertThat(result).startsWith("APF-");
        ArgumentCaptor<Capplm> captor = ArgumentCaptor.forClass(Capplm.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getDcdReqBbrC()).isNull();
    }

    // 참고: 다음 결재자 사번이 공백일 때 알림을 생략하는 수신자 선정 로직은
    // ApprovalRequestNotifier로 이동했으므로 ApprovalRequestNotifierTest에서 검증한다.

    @Test
    @DisplayName("getPendingCount: bgYy가 공백이면 연도 필터 없이 집계한다")
    void getPendingCount_공백연도_필터없이집계() {
        given(projectRepository.countBySearchCondition(any())).willReturn(1L);
        given(costRepository.countBySearchCondition(any())).willReturn(1L);

        ApplicationDto.PendingCountResponse res = applicationService.getPendingCount("   ");

        assertThat(res.getTotalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("changePendingApprover: 결재선 직원은 미결재 결재자를 변경할 수 있다")
    void changePendingApprover_결재선직원_변경성공() {
        Cdecim requester = pendingApprover("10001", 1, "N");
        Cdecim target = pendingApprover("10002", 2, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(requester, target));
        given(userRepository.findById("20002"))
                .willReturn(Optional.of(CuserI.builder().eno("20002").build()));

        pendingApproverService.changePendingApprover(APF_MNG_NO, 2, "20002", "10001", false);

        assertThat(target.getDcrEno()).isEqualTo("20002");
    }

    @Test
    @DisplayName("changePendingApprover: 결재선 밖의 일반 사용자는 변경할 수 없다")
    void changePendingApprover_결재선외직원_권한거부() {
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10001", 1, "Y")));

        assertThatThrownBy(
                        () ->
                                pendingApproverService.changePendingApprover(
                                        APF_MNG_NO, 1, "20001", "99999", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("changePendingApprover: 관리자는 결재선 밖에서도 변경할 수 있다")
    void changePendingApprover_관리자_변경성공() {
        Cdecim target = pendingApprover("10001", 1, "Y");
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(target));
        given(userRepository.findById("20001"))
                .willReturn(Optional.of(CuserI.builder().eno("20001").build()));

        pendingApproverService.changePendingApprover(APF_MNG_NO, 1, "20001", "99999", true);

        assertThat(target.getDcrEno()).isEqualTo("20001");
    }

    @Test
    @DisplayName("changePendingApprover: 처리된 결재선은 변경할 수 없다")
    void changePendingApprover_승인완료_상태거부() {
        Cdecim approved = pendingApprover("10001", 1, "Y");
        approved.approve("승인", com.kdb.it.common.approval.domain.DecisionStatus.APPROVED);
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(approved));

        assertThatThrownBy(
                        () ->
                                pendingApproverService.changePendingApprover(
                                        APF_MNG_NO, 1, "20001", "10001", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("changePendingApprover: 결재선이 없으면 변경할 수 없다")
    void changePendingApprover_결재선없음_거부() {
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of());

        assertThatThrownBy(
                        () ->
                                pendingApproverService.changePendingApprover(
                                        APF_MNG_NO, 1, "20001", "10001", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changePendingApprover: 존재하지 않는 결재 순번은 변경할 수 없다")
    void changePendingApprover_결재순번없음_거부() {
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10001", 1, "Y")));

        assertThatThrownBy(
                        () ->
                                pendingApproverService.changePendingApprover(
                                        APF_MNG_NO, 2, "20001", "10001", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changePendingApprover: 존재하지 않는 직원으로 변경할 수 없다")
    void changePendingApprover_직원없음_거부() {
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10001", 1, "Y")));
        given(userRepository.findById("20001")).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                pendingApproverService.changePendingApprover(
                                        APF_MNG_NO, 1, "20001", "10001", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("상신 시 원천 개정본 순번 검증")
    class OrcItemVersionValidationTests {

        private ApplicationDto.CreateRequest requestWith(String fntTbNm, String sno) {
            ApplicationDto.CreateRequest request = new ApplicationDto.CreateRequest();
            request.setApfNm("예산 상신");
            request.setRqsEno("10001");
            request.setApproverEnos(List.of("20001"));
            ApplicationDto.OrcItem item = new ApplicationDto.OrcItem();
            item.setFntTbNm(fntTbNm);
            item.setPkColNm("PRJ-2026-0001");
            item.setFntTbCrySno(sno);
            request.setOrcItems(List.of(item));
            return request;
        }

        @Test
        @DisplayName("존재하지 않는 개정 순번으로 상신하면 거절한다 — 승인 시점 구버전 재승격을 막는다")
        void 존재하지않는_순번은_거절한다() {
            given(projectRepository.findByAbusMngNoAndSnoAndDelYn("PRJ-2026-0001", 1, "N"))
                    .willReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> applicationService.submit(requestWith("BPROJM", "1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("개정본");

            verify(applicationMapRepository, never()).save(any());
        }

        @Test
        @DisplayName("개정 순번이 비어 있으면 거절한다 — 승인 리스너가 null 순번으로 예외를 던진다")
        void 순번이_비어있으면_거절한다() {
            assertThatThrownBy(() -> applicationService.submit(requestWith("BPROJM", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("개정본");

            verify(applicationMapRepository, never()).save(any());
        }
    }

    /** 상신 검증이 통과하도록 해당 사업 개정본이 활성 상태라고 설정합니다. */
    private void givenActiveVersion(String abusMngNo, int sno) {
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn(abusMngNo, sno, "N"))
                .willReturn(
                        Optional.of(
                                com.kdb.it.domain.budget.project.entity.Bprojm.builder()
                                        .abusMngNo(abusMngNo)
                                        .sno(sno)
                                        .delYn("N")
                                        .build()));
    }

    /** 상신 검증이 통과하도록 해당 전산업무비 개정본이 활성 상태라고 설정합니다. */
    private void givenActiveCostVersion(String costBgNo, int bgSno) {
        given(costRepository.findByCostBgNoAndBgSnoAndDelYn(costBgNo, bgSno, "N"))
                .willReturn(
                        Optional.of(
                                com.kdb.it.domain.budget.cost.entity.Bcostm.builder()
                                        .costBgNo(costBgNo)
                                        .bgSno(bgSno)
                                        .delYn("N")
                                        .build()));
    }
}
