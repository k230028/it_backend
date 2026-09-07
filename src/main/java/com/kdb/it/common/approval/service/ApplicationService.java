package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.event.ApprovalRecalledEvent;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청서(결재) 관리 서비스
 *
 * <p>정보화사업·전산관리비 등 각종 신청서의 등록, 조회, 결재(승인/반려), 일괄 결재를 처리하는 비즈니스 로직을 담당합니다.
 *
 * <p>신청서 상태 흐름:
 *
 * <pre>
 *   [신청서 등록] → 결재중
 *                      ↓ (모든 결재자 순차 승인)
 *                   결재완료
 *                      ↓ (중간 결재자 반려)
 *                    반려
 * </pre>
 *
 * <p>결재선(Approval Line):
 *
 * <ul>
 *   <li>등록 시 결재자 사번 목록({@code approverEnos})을 순서대로 받아 {@link Cdecim}으로 저장
 *   <li>순차 결재: 앞 순번이 승인해야 다음 순번이 결재 가능
 *   <li>동일 결재자 연속 등장 시 일괄 승인 처리
 * </ul>
 *
 * <p>원본 데이터 연결:
 *
 * <ul>
 *   <li>신청서는 원본 테이블(예: BPROJM)과 {@link Cappla}로 연결
 *   <li>{@code fntTbNm}: 원천 테이블명 (예: "BPROJM")
 *   <li>{@code pkColNm}: 원천 테이블의 PK 값 (예: 프로젝트관리번호)
 *   <li>{@code fntTbCrySno}: 원천 테이블의 SNO 값 (예: 프로젝트순번)
 * </ul>
 *
 * <p>{@code @Transactional(readOnly = true)}: 조회 메서드의 기본값. 쓰기 메서드는 {@code @Transactional}로
 * 오버라이드합니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 기본 읽기 전용 트랜잭션
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    /** 신청서 마스터 데이터 접근 리포지토리 (TPRMPP_CAPPLM) */
    private final ApplicationRepository applicationRepository;

    /** 결재(승인) 데이터 접근 리포지토리 (TPRMPP_CDECIM) */
    private final ApproverRepository approverRepository;

    /** 신청서-원본 데이터 연결 리포지토리 (TPRMPP_CAPPLA) */
    private final ApplicationMapRepository applicationMapRepository;

    /** 정보화사업(Bprojm) 리포지토리: 미상신 건수 집계용 */
    private final ProjectRepository projectRepository;

    /** 전산업무비(Bcostm) 리포지토리: 미상신 건수 집계용 */
    private final CostRepository costRepository;

    /** 사용자(TPRMPP_CUSERI) 리포지토리: 신청자명 조회용 */
    private final UserRepository userRepository;

    /** 조직(TPRMPP_CORGNI) 리포지토리: 신청부서명 조회용 */
    private final OrganizationRepository organizationRepository;

    /** 결재 완료/반려 시 도메인 이벤트 발행 (도메인 간 직접 의존 제거) */
    private final ApplicationEventPublisher eventPublisher;

    /** 결재선 JSON 업데이트 위임 — ERR-03/04: public @Transactional로 AOP 프록시 우회 방지 */
    private final ApprovalLineDelegate approvalLineDelegate;

    /** 정보화사업관계(TPRMPP_BPROJA) 동기화 서비스: 예산편성 결재 상신(02)/완료(09) 적재용 */
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    /** 결재요청 알림 발행 전담 컴포넌트 — 결재선의 다음 결재자 조회, 메일 페이로드 렌더링, 이벤트 발행을 위임한다. */
    private final ApprovalRequestNotifier approvalRequestNotifier;

    private final ApplicationPersistenceService persistence;
    private final ApprovalDetailPolicy detailPolicy;
    private final com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader snapshotReader;

    /** 원천테이블명: 정보화사업 마스터(BPROJM). BPROJA 적재 대상 식별용 상수. */
    private static final String FNT_TB_BPROJM = "BPROJM";

    /**
     * 신청서 등록 (결재 요청)
     *
     * <p>신청서 마스터({@link Capplm})를 생성하고, 원본 데이터 연결({@link Cappla}) 및 결재선({@link Cdecim})을 함께 저장합니다.
     *
     * <p>신청관리번호 생성 규칙: {@code APF-{yyyy}-{시퀀스8자리}}
     *
     * <p>예: {@code APF-2026-00000001}
     *
     * <p>처리 순서:
     *
     * <ol>
     *   <li>Oracle 시퀀스로 신청관리번호 채번
     *   <li>신청서 마스터 저장 (상태: "결재중")
     *   <li>원본 데이터 연결 저장 (fntTbNm이 있는 경우)
     *   <li>결재선 생성 (승인자 순서대로, 마지막 승인자는 lstDcdYn='Y')
     * </ol>
     *
     * @param request 신청서 생성 요청 DTO (신청서명, 세부내용, 신청자, 결재자 목록 등)
     * @return 생성된 신청관리번호 (예: "APF-2026-00000001")
     */
    @Transactional
    public String submit(ApplicationDto.CreateRequest request) {
        return persistence.persist(ApplicationPersistenceService.ApplicationDraft.from(request));
    }

    /**
     * 결재 처리 (승인 또는 반려)
     *
     * <p>순차 결재 방식으로, 이전 결재자가 모두 승인한 경우에만 다음 결재자가 결재할 수 있습니다. 동일 결재자가 연속으로 등장한 경우 한 번의 요청으로 연속 항목 모두
     * 승인합니다.
     *
     * <p>처리 흐름:
     *
     * <ol>
     *   <li>신청서 존재 확인
     *   <li>전체 결재자 목록 조회 (순번 오름차순)
     *   <li>현재 결재 차례(미결재, 이전 모두 승인) 탐색
     *   <li>요청자가 현재 결재자인지 확인
     *   <li>결재 상태 저장 (승인/반려)
     *   <li>동일 결재자 연속 등장 시 일괄 승인
     *   <li>JSON 결재선 정보 업데이트
     *   <li>반려: 신청서 상태 → "반려" / 마지막 승인: 신청서 상태 → "결재완료"
     * </ol>
     *
     * @param apfMngNo 결재할 신청관리번호
     * @param request 결재 요청 DTO (결재자 사번, 의견, 승인/반려 상태)
     * @throws IllegalArgumentException 신청서가 없거나 결재자가 아닌 경우
     * @throws IllegalStateException 결재 차례가 아닌 경우
     */
    @Transactional
    public void approve(String apfMngNo, ApplicationDto.ApproveRequest request) {
        approve(apfMngNo, request, null);
    }

    /** 일괄 결재에서는 원본 양식 분류 결과를 재사용한다. 마스터 잠금은 각 명령에서 유지한다. */
    private void approve(
            String apfMngNo,
            ApplicationDto.ApproveRequest request,
            java.util.Set<String> jsonlessCouncilIds) {
        // 신청서 마스터 조회 (없으면 예외)
        Capplm capplm =
                applicationRepository
                        .findByIdForUpdate(apfMngNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));

        // 해당 신청서의 전체 결재자 목록 조회 (순번 오름차순)
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);

        // ===== 현재 결재 차례 탐색 =====
        Cdecim currentApprover = null; // 현재 결재해야 할 결재자
        boolean isPreviousApproved = true; // 이전 결재자가 모두 승인했는지 여부

        for (Cdecim approver : approvers) {
            String stsC = approver.getItPtlDcdStsC(); // 결재상태 코드 (IT_PTL_DCD_STS_C)

            if (DecisionStatus.isPendingCode(stsC)) {
                // 미결재 항목: 이전이 모두 승인되었을 때만 현재 차례
                if (isPreviousApproved) {
                    currentApprover = approver;
                }
                break;
            } else if (!DecisionStatus.isApprovedCode(stsC)) {
                // 반려/회수/무효 등 — 이전 결재자가 승인하지 않은 상태
                isPreviousApproved = false;
                break;
            }
        }

        // 결재 차례가 아닌 경우 (이미 완료되었거나 순서 불일치)
        if (currentApprover == null) {
            throw new IllegalStateException("결재할 차례가 아니거나 이미 모든 결재가 완료되었습니다.");
        }

        // 요청한 결재자가 현재 차례의 결재자인지 확인
        if (!currentApprover.getDcrEno().equals(request.getDcdEno())) {
            throw new IllegalArgumentException("현재 결재자가 아닙니다.");
        }

        // 결재 상태 유효성 검증 (승인 또는 반려만 허용)
        String status = request.getDcdSts();
        if (status == null || status.isEmpty()) {
            throw new IllegalArgumentException("결재 상태(승인/반려)는 필수입니다.");
        }

        // 상태 문자열(label 또는 code)을 DecisionStatus enum으로 매핑
        DecisionStatus decision;
        if ("승인".equals(status) || DecisionStatus.APPROVED.code().equals(status)) {
            decision = DecisionStatus.APPROVED;
        } else if ("반려".equals(status) || DecisionStatus.REJECTED.code().equals(status)) {
            decision = DecisionStatus.REJECTED;
        } else {
            throw new IllegalArgumentException("결재 상태(승인/반려)는 필수입니다.");
        }

        // 현재 명령의 결재 시각을 한 번만 확정해 연속 동일 결재자와 스냅샷에 재사용한다.
        LocalDateTime decisionAt = LocalDateTime.now();
        currentApprover.approve(request.getDcdOpnn(), decision, decisionAt.toLocalDate());
        approverRepository.save(currentApprover);

        // ===== 연속된 동일 결재자 일괄 승인 처리 =====
        // 예: [A, A, B] 결재선에서 A가 승인하면 두 A 항목 모두 승인
        Cdecim lastApproved = currentApprover;
        List<Cdecim> approvedList = new java.util.ArrayList<>();
        approvedList.add(currentApprover);

        if (decision == DecisionStatus.APPROVED) {
            int currentIndex = approvers.indexOf(currentApprover);
            for (int i = currentIndex + 1; i < approvers.size(); i++) {
                Cdecim nextApprover = approvers.get(i);
                if (nextApprover.getDcrEno().equals(currentApprover.getDcrEno())) {
                    // 같은 결재자가 연속으로 등장하면 자동 승인
                    nextApprover.approve(request.getDcdOpnn(), decision, decisionAt.toLocalDate());
                    approverRepository.save(nextApprover);
                    lastApproved = nextApprover;
                    approvedList.add(nextApprover);
                } else {
                    break; // 다른 결재자 만나면 일괄 승인 종료
                }
            }
        }

        // 신청서 상세 내용(JSON) 내 결재선 정보 업데이트 (결재 일자 기록)
        var detailMode =
                jsonlessCouncilIds == null
                        ? detailPolicy.resolve(capplm)
                        : detailPolicy.resolve(capplm, jsonlessCouncilIds);
        if (detailMode != ApprovalDetailPolicy.DetailMode.JSONLESS_COUNCIL)
            approvalLineDelegate.doUpdate(capplm, approvers, approvedList, decisionAt);

        // 신청서 전체 상태 업데이트
        String newApfSts = null;
        if (decision == DecisionStatus.REJECTED) {
            // 반려인 경우 신청서 상태도 "반려"로 변경
            capplm.updateStatus(ApprovalStatus.REJECTED);
            newApfSts = ApprovalStatus.REJECTED.label();
        } else if (decision == DecisionStatus.APPROVED && "Y".equals(lastApproved.getLstDcdYn())) {
            // 마지막 결재자(lstDcdYn='Y')가 승인한 경우 "결재완료"로 변경
            capplm.updateStatus(ApprovalStatus.COMPLETED);
            newApfSts = ApprovalStatus.COMPLETED.label();

            // 정보화사업(BPROJM) 결재 완료 → 연결된 각 프로젝트의 정보화사업관계(BPROJA)를 결재완료('09')로 갱신.
            // 신청서에 연결된 BPROJM 원천(Cappla)을 역조회해, 작성('01')/상신('02')과 동일 행(단계 key=프로젝트관리번호)을 멱등 upsert
            // 한다.
            for (Cappla c :
                    applicationMapRepository.findByApfDcmNoAndFntTbNm(apfMngNo, FNT_TB_BPROJM)) {
                bprojaSyncService.upsert(c.getPkColNm(), c.getPkColNm(), "09");
            }
        }

        // 신청서 상태가 종결(결재완료/반려)된 경우, 도메인 이벤트 발행
        // 구독 리스너(예: CouncilApprovalEventListener)가 도메인별 후처리를 담당합니다.
        // 알림 측면: NotificationEventListener.onApprovalCompleted가 신청자에게 결재결과 알림을 발행합니다.
        if (newApfSts != null) {
            eventPublisher.publishEvent(new ApprovalCompletedEvent(apfMngNo, newApfSts));
        } else if (decision == DecisionStatus.APPROVED) {
            // 중간 승인 → 다음 결재자에게 결재요청 알림 발행
            approvalRequestNotifier.notifyApprovalRequest(capplm);
        }
    }

    /**
     * 일괄 결재 (여러 신청서를 하나의 트랜잭션으로 처리)
     *
     * <p>복수의 신청서에 대해 순차적으로 {@link #approve(String, ApplicationDto.ApproveRequest)}를 호출합니다. 하나라도
     * 실패하면 전체 트랜잭션이 롤백됩니다.
     *
     * <p>주의: 예외 발생 시 {@link RuntimeException}을 다시 던져 트랜잭션 롤백을 유발합니다.
     *
     * @param request 일괄 결재 요청 DTO (처리할 신청서 목록)
     * @return 모든 항목이 성공한 경우의 일괄 결재 결과 DTO. 실패 항목이 있으면 반환되지 않는다.
     * @throws RuntimeException 개별 신청서 처리 실패 시 즉시 재발생하여 전체 롤백
     */
    @Transactional
    public ApplicationDto.BulkApproveResponse bulkApprove(
            ApplicationDto.BulkApproveRequest request) {
        List<ApplicationDto.ApprovalResult> results = new java.util.ArrayList<>(); // 개별 결과 목록
        int successCount = 0; // 성공 건수
        int failureCount = 0; // 실패 건수
        var jsonlessCouncilIds =
                detailPolicy.findJsonlessCouncilIds(
                        request.getApprovals().stream()
                                .map(ApplicationDto.ApprovalItem::getApfMngNo)
                                .toList());

        // 모든 신청서를 순회하며 승인 처리
        for (ApplicationDto.ApprovalItem item : request.getApprovals()) {
            try {
                // 개별 승인 요청 생성 (ApprovalItem → ApproveRequest 변환)
                ApplicationDto.ApproveRequest approveRequest = new ApplicationDto.ApproveRequest();
                approveRequest.setDcdEno(item.getDcdEno()); // 승인자 사원번호
                approveRequest.setDcdOpnn(item.getDcdOpnn()); // 승인 의견
                approveRequest.setDcdSts(item.getDcdSts()); // 승인 상태 (승인, 반려)

                // 개별 승인 처리
                approve(item.getApfMngNo(), approveRequest, jsonlessCouncilIds);

                // 성공 결과 추가
                results.add(
                        ApplicationDto.ApprovalResult.builder()
                                .apfMngNo(item.getApfMngNo())
                                .success(true)
                                .message("처리 완료")
                                .build());
                successCount++;

            } catch (RuntimeException e) {
                // 실패 시 RuntimeException을 던져 전체 트랜잭션 롤백
                throw new RuntimeException(
                        "신청서 " + item.getApfMngNo() + " 처리 실패: " + e.getMessage(), e);
            }
        }

        // 최종 결과 응답 생성
        return ApplicationDto.BulkApproveResponse.builder()
                .totalCount(request.getApprovals().size()) // 전체 요청 건수
                .successCount(successCount) // 성공 건수
                .failureCount(failureCount) // 실패 건수 (롤백 시 항상 0)
                .results(results) // 개별 결과 목록
                .build();
    }

    /**
     * 신청서 세부내용(APF_DTL_CONE) 단건 조회
     *
     * <p>신청관리번호로 신청서 마스터를 조회하여 세부내용({@code APF_DTL_CONE}) 필드만 반환합니다.
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청관리번호와 세부내용을 담은 응답 DTO ({@link ApplicationDto.ApfDtlConeResponse})
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     * @throws com.kdb.it.exception.DataCorruptionException 저장 상세가 손상되었거나 필수 상세가 없는 경우
     */
    public ApplicationDto.ApfDtlConeResponse getApfDtlCone(String apfMngNo) {
        ApplicationRepository.ApplicationReadView view =
                applicationRepository
                        .findReadViewByApfMngNo(apfMngNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        validateDetails(
                List.of(
                        new DetailRead(
                                view.getApfMngNo(),
                                view.getItPtlApfPrgStsC(),
                                view.getDcdReqInf())));
        return ApplicationDto.ApfDtlConeResponse.fromReadView(view);
    }

    /**
     * 단건 신청서 조회
     *
     * <p>신청관리번호로 신청서 마스터와 결재자 목록을 조회하여 DTO로 반환합니다.
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청서 상세 응답 DTO (결재자 목록 포함)
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     * @throws com.kdb.it.exception.DataCorruptionException 저장 상세가 손상되었거나 필수 상세가 없는 경우
     */
    public ApplicationDto.Response getApplication(String apfMngNo) {
        // 신청서 마스터 read view 조회 (응답이 실제 사용하는 8컬럼만 조회)
        ApplicationRepository.ApplicationReadView view =
                applicationRepository
                        .findReadViewByApfMngNo(apfMngNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        validateDetails(
                List.of(
                        new DetailRead(
                                view.getApfMngNo(),
                                view.getItPtlApfPrgStsC(),
                                view.getDcdReqInf())));
        return ApplicationBulkReadSupport.assembleOne(
                view, approverRepository, userRepository, organizationRepository);
    }

    /** 결재함 목록에서 제외하는 신청서 상태: 결재선 없는 작성완료(0)와 편성요청서 반입 표식인 수기등록(9). */
    static final List<String> INBOX_EXCLUDED_STATUS_CODES =
            List.of(ApprovalStatus.DRAFTED.code(), ApprovalStatus.MANUAL.code());

    /**
     * 전체 신청서 목록 조회
     *
     * <p>결재선이 없는 작성완료({@code 0})와 결재를 거치지 않는 수기등록({@code 9}) 신청서는 결재함 대상이 아니므로 제외하고, 그 외 신청서를 각각의
     * 결재자 목록과 함께 반환합니다.
     *
     * @return 전체 신청서 응답 DTO 목록 (각각 결재자 목록 포함)
     * @throws com.kdb.it.exception.DataCorruptionException 반환 대상에 손상되거나 누락된 필수 상세가 있는 경우
     */
    public List<ApplicationDto.Response> getApplications(
            CustomUserDetails user, boolean allDepartments) {
        String departmentCode = resolveDepartmentScope(user, allDepartments);
        if (departmentCode != null && departmentCode.isBlank()) return List.of();

        List<ApplicationRepository.ApplicationReadView> views =
                departmentCode == null
                        ? applicationRepository.findTop500ByItPtlApfPrgStsCNotInOrderByApfMngNoDesc(
                                INBOX_EXCLUDED_STATUS_CODES)
                        : applicationRepository
                                .findTop500ByDcdReqBbrCAndItPtlApfPrgStsCNotInOrderByApfMngNoDesc(
                                        departmentCode, INBOX_EXCLUDED_STATUS_CODES);
        return assembleList(views);
    }

    /**
     * 특정 결재자의 결재 대기 신청서 목록을 DB에서 걸러 조회합니다(목록 상한에 밀려 누락되지 않고, 다른 사람의 결재 건도 실리지 않습니다).
     *
     * @param user 인증 사용자(사번·소속 부서·시스템관리자 여부)
     * @param allDepartments 시스템관리자의 전체 부서 조회 요청 여부
     * @return 결재 대기 신청서 응답 DTO 목록 (최신순, 각각 결재자 목록 포함)
     * @throws IllegalArgumentException 사번이 비어 있는 경우 (빈 결과와 구분한다)
     * @throws AccessDeniedException 인증 정보가 없는 경우
     * @throws com.kdb.it.exception.DataCorruptionException 반환 대상에 손상되거나 누락된 필수 상세가 있는 경우
     */
    public List<ApplicationDto.Response> getPendingApplications(
            CustomUserDetails user, boolean allDepartments) {
        if (user == null) throw new AccessDeniedException("인증 정보가 필요합니다.");
        String eno = user.getEno();
        if (eno == null || eno.isBlank()) {
            throw new IllegalArgumentException("결재자 사번이 필요합니다.");
        }
        String departmentCode = resolveDepartmentScope(user, allDepartments);
        if (departmentCode != null && departmentCode.isBlank()) return List.of();
        List<String> apfMngNos =
                departmentCode == null
                        ? applicationRepository.findPendingApfMngNosByEno(eno)
                        : applicationRepository.findPendingApfMngNosByEnoAndBbrC(
                                eno, departmentCode);
        if (apfMngNos.isEmpty()) {
            return List.of();
        }
        // findReadViewsByApfMngNoIn은 순서를 보장하지 않으므로 조회 순서(최신순)로 다시 정렬한다.
        java.util.Map<String, ApplicationRepository.ApplicationReadView> viewsById =
                applicationRepository.findReadViewsByApfMngNoIn(apfMngNos).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ApplicationRepository.ApplicationReadView::getApfMngNo,
                                        value -> value,
                                        (left, right) -> left));
        return assembleList(
                apfMngNos.stream().map(viewsById::get).filter(java.util.Objects::nonNull).toList());
    }

    /** 인증 사용자와 관리자 전체 조회 선택으로 적용할 작성 부서 범위를 결정합니다. */
    private static String resolveDepartmentScope(CustomUserDetails user, boolean allDepartments) {
        if (user == null) throw new AccessDeniedException("인증 정보가 필요합니다.");
        if (allDepartments && user.isAdmin()) return null;
        return user.getBbrC() == null ? "" : user.getBbrC().trim();
    }

    /** 목록 조회의 응답 조립을 배치 읽기 지원 클래스에 위임합니다. */
    private List<ApplicationDto.Response> assembleList(
            List<ApplicationRepository.ApplicationReadView> views) {
        validateDetails(
                views.stream()
                        .map(
                                v ->
                                        new DetailRead(
                                                v.getApfMngNo(),
                                                v.getItPtlApfPrgStsC(),
                                                v.getDcdReqInf()))
                        .toList());
        return ApplicationBulkReadSupport.assembleList(
                views, approverRepository, userRepository, organizationRepository);
    }

    /**
     * 여러 신청관리번호를 배치로 읽고 상세를 검증해 응답을 조립한다.
     *
     * @throws com.kdb.it.exception.DataCorruptionException 반환 대상에 손상되거나 누락된 필수 상세가 있는 경우
     */
    public ApplicationDto.BulkResponse getApplicationsByIds(ApplicationDto.BulkGetRequest request) {
        ApplicationDto.BulkResponse response =
                ApplicationBulkReadSupport.read(
                        request,
                        applicationRepository,
                        approverRepository,
                        userRepository,
                        organizationRepository);
        validateDetails(
                response.items().stream()
                        .map(
                                v ->
                                        new DetailRead(
                                                v.getApfMngNo(), v.getApfStsC(), v.getApfDtlCone()))
                        .toList());
        if (!response.failedIds().isEmpty()) {
            log.warn("bulk-get 누락: type=application, failedIds={}", response.failedIds());
        }
        return response;
    }

    /** 원문은 그대로 반환하되 본문이 없는 수기등록·협의회 신청서는 각 업무 계약에 따라 허용한다. */
    private void validateDetails(List<DetailRead> details) {
        var absentIds =
                details.stream()
                        .filter(d -> d.raw() == null && !d.isManual())
                        .map(DetailRead::id)
                        .toList();
        var jsonlessCouncilIds =
                absentIds.isEmpty()
                        ? java.util.Set.<String>of()
                        : detailPolicy.findJsonlessCouncilIds(absentIds);
        for (var detail : details) {
            if (detail.raw() == null && detail.isManual()) continue;
            if (detail.raw() == null && jsonlessCouncilIds.contains(detail.id())) continue;
            snapshotReader.read(detail.raw());
        }
    }

    private record DetailRead(String id, String statusCode, String raw) {
        private boolean isManual() {
            return ApprovalStatus.MANUAL.code().equals(statusCode);
        }
    }

    /**
     * 전자결재 대시보드 집계 조회
     *
     * <p>bbrC 기준 부서 통계와 eno 기준 본인 결재 대기 목록을 반환합니다.
     *
     * @param bbrC 부서코드 (TPRMPP_CUSERI.BBR_C)
     * @param eno 사원번호 (본인 결재 대기 필터)
     * @return 대시보드 집계 응답 DTO
     * @throws org.springframework.dao.DataAccessException DB 조회 실패 시 (GlobalExceptionHandler에서 500
     *     응답으로 처리)
     */
    public ApplicationDto.DashboardResponse getDashboard(String bbrC, String eno) {
        int pendingCount = applicationRepository.countPendingByEno(eno);
        int inProgressCount = applicationRepository.countInProgressByEno(eno);
        int monthlyCompletedCount = applicationRepository.countMonthlyCompletedByBbrC(bbrC);
        int rejectedCount = applicationRepository.countRejectedByEno(eno);

        List<ApplicationDto.MonthlyCount> monthlyTrend =
                applicationRepository.findMonthlyTrendRowsByBbrC(bbrC).stream()
                        .map(
                                row ->
                                        ApplicationDto.MonthlyCount.builder()
                                                .month(row.label())
                                                .count(Math.toIntExact(row.count()))
                                                .build())
                        .toList();

        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        List<ApplicationDto.PendingItem> pendingList =
                applicationRepository.findPendingRowsByEno(eno).stream()
                        .map(
                                row -> {
                                    String rqsDtStr = row.rqsDt();
                                    LocalDate rqsDt =
                                            rqsDtStr != null
                                                    ? LocalDate.parse(rqsDtStr)
                                                    : LocalDate.now();
                                    String urgency =
                                            rqsDt.isBefore(threeDaysAgo) ? "urgent" : "normal";
                                    return ApplicationDto.PendingItem.builder()
                                            .apfMngNo(row.apfDcmNo())
                                            .title(row.title())
                                            .requesterName(row.usrNm())
                                            .requestedAt(rqsDtStr)
                                            .urgency(urgency)
                                            .build();
                                })
                        .toList();

        return ApplicationDto.DashboardResponse.builder()
                .pendingCount(pendingCount)
                .inProgressCount(inProgressCount)
                .monthlyCompletedCount(monthlyCompletedCount)
                .rejectedCount(rejectedCount)
                .monthlyTrend(monthlyTrend)
                .pendingList(pendingList)
                .build();
    }

    /**
     * 사이드바 배지용 결재 현황 수 조회
     *
     * @param bbrC 부서코드 (향후 부서 기준 집계 확장용, 현재 미사용)
     * @param eno 사원번호
     * @return 배지 건수 응답 DTO
     */
    public ApplicationDto.ApprovalBadgeCountResponse getApprovalBadgeCount(
            String bbrC, String eno) {
        return ApplicationDto.ApprovalBadgeCountResponse.builder()
                .pendingCount(applicationRepository.countPendingByEno(eno))
                .inProgressCount(applicationRepository.countInProgressByEno(eno))
                .build();
    }

    /**
     * 상신 대상(최신 신청서가 작성완료) 건수 집계
     *
     * <p>사이드바의 [결재 상신] 메뉴 옆 배지에서 사용됩니다. 전체 목록 대신 건수만 반환하여 데이터 전송량을 최소화합니다.
     *
     * <p>집계 로직: 요청한 결재상태(기본값 작성완료 {@code 0} = 상신 대상) 조건으로 {@code ProjectRepository} 및 {@code
     * CostRepository}의 {@code countBySearchCondition} 집계 쿼리를 호출해 각각의 건수를 계산합니다.
     *
     * <p>일반 사용자는 인증 주체의 소속 부서로 제한하고 시스템관리자만 전체 부서를 집계합니다.
     *
     * @param bgYy 기준연도 (공백이면 전체 연도)
     * @param apfSts 결재상태 (공백이면 작성완료(0) = 상신 대상)
     * @param user 인증 사용자
     * @return 결재상태별 건수 응답 DTO (정보화사업/전산업무비 개별 건수 + 총합)
     */
    public ApplicationDto.PendingCountResponse getPendingCount(
            String bgYy, String apfSts, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 필요합니다.");
        }

        // 상신 대상 = 최신 신청서가 작성완료(0)인 원천. 사이드바 배지는 apfSts 없이 호출하므로 이 기본값이 곧 배지 기준이다.
        String status = apfSts == null || apfSts.isBlank() ? ApprovalStatus.DRAFTED.code() : apfSts;
        String departmentCode = user.getBbrC();
        if (!user.isAdmin() && (departmentCode == null || departmentCode.isBlank())) {
            return ApplicationDto.PendingCountResponse.builder()
                    .projectCount(0L)
                    .costCount(0L)
                    .totalCount(0L)
                    .build();
        }

        ProjectDto.SearchCondition projectCondition = new ProjectDto.SearchCondition();
        projectCondition.setApfSts(status);
        if (bgYy != null && !bgYy.isBlank()) projectCondition.setBseYy(bgYy);
        if (!user.isAdmin()) projectCondition.setSvnDpmC(departmentCode);
        // 전체 엔티티 적재 대신 COUNT 쿼리로 건수만 산출 (동일 WHERE 조건 → 결과 동치)
        long projectCount = projectRepository.countBySearchCondition(projectCondition);

        CostDto.SearchCondition costCondition = new CostDto.SearchCondition();
        costCondition.setApfSts(status);
        if (bgYy != null && !bgYy.isBlank()) costCondition.setBseYy(bgYy);
        if (!user.isAdmin()) costCondition.setCostSvnDpmC(departmentCode);
        long costCount = costRepository.countBySearchCondition(costCondition);

        return ApplicationDto.PendingCountResponse.builder()
                .projectCount(projectCount)
                .costCount(costCount)
                .totalCount(projectCount + costCount)
                .build();
    }

    /**
     * 신청서 회수.
     *
     * @param apfMngNo 회수할 신청서 관리번호
     * @param request 회수 요청 (사유)
     * @param currentEno 회수 요청자 사번
     * @param isAdmin 관리자(ROLE_ADMIN) 여부
     * @throws IllegalArgumentException 신청서 없음
     * @throws IllegalStateException 회수 가능 상태 아님 / 최종승인 후
     * @throws AccessDeniedException 회수 권한 없음
     */
    @Transactional
    public void recall(
            String apfMngNo,
            ApplicationDto.RecallRequest request,
            String currentEno,
            boolean isAdmin) {
        Capplm capplm =
                applicationRepository
                        .findByIdForUpdate(apfMngNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));

        if (!ApprovalStatus.IN_PROGRESS.code().equals(capplm.getItPtlApfPrgStsC())) {
            throw new IllegalStateException(
                    "회수 가능한 상태가 아닙니다. 현재 상태: " + capplm.getItPtlApfPrgStsC());
        }

        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);
        boolean lastApproved =
                approvers.stream()
                        .anyMatch(
                                a ->
                                        "Y".equals(a.getLstDcdYn())
                                                && DecisionStatus.isApprovedCode(
                                                        a.getItPtlDcdStsC()));
        if (lastApproved) {
            throw new IllegalStateException("최종 결재자 승인 후에는 회수할 수 없습니다.");
        }

        if (!canRecall(capplm, approvers, currentEno, isAdmin)) {
            throw new AccessDeniedException("회수 권한이 없습니다.");
        }

        approvalLineDelegate.applyRecallInfo(
                capplm, currentEno, request.getRecallOpnn(), detailPolicy.resolve(capplm));
        capplm.updateStatus(ApprovalStatus.RECALLED);

        for (Cdecim a : approvers) {
            if (DecisionStatus.isPendingCode(a.getItPtlDcdStsC())) {
                a.invalidateByRecall();
                approverRepository.save(a);
            }
        }

        List<String> approvedMiddle =
                approvers.stream()
                        .filter(
                                a ->
                                        "N".equals(a.getLstDcdYn())
                                                && DecisionStatus.isApprovedCode(
                                                        a.getItPtlDcdStsC()))
                        .map(value -> value.getDcrEno())
                        .distinct()
                        .toList();

        eventPublisher.publishEvent(
                new ApprovalRecalledEvent(apfMngNo, currentEno, approvedMiddle));
    }

    /**
     * 결재 회수 가능 여부 검증 헬퍼
     *
     * <p>결재중 상태인 신청서에 대해 기안자 여부로 회수 권한을 판단합니다.
     *
     * <ul>
     *   <li>결재중({@code APF_STS_C = IN_PROGRESS}) 상태가 아니면 무조건 false 반환
     *   <li>기안자({@code currentEno == capplm.rqsEno}): 허용
     * </ul>
     *
     * @param capplm 대상 신청서 마스터 엔티티
     * @param approvers 결재선 목록 (호출부 호환성을 위해 유지하며 권한 판단에는 사용하지 않음)
     * @param currentEno 현재 요청 사용자 사번
     * @param isAdmin 관리자 여부 플래그 (호출부 호환성을 위해 유지하며 권한 판단에는 사용하지 않음)
     * @return 회수 가능 여부 (true=허용, false=거부)
     */
    private boolean canRecall(
            Capplm capplm, List<Cdecim> approvers, String currentEno, boolean isAdmin) {
        if (!ApprovalStatus.IN_PROGRESS.code().equals(capplm.getItPtlApfPrgStsC())) return false;
        return currentEno.equals(capplm.getDcdReqUsid());
    }
}
