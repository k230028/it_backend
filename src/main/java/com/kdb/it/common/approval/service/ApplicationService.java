package com.kdb.it.common.approval.service;

import java.time.LocalDate;
import java.util.List;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.event.ApprovalRecalledEvent;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcherRouter;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.NotificationMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 신청서(결재) 관리 서비스
 *
 * <p>
 * 정보화사업·전산관리비 등 각종 신청서의 등록, 조회, 결재(승인/반려), 일괄 결재를
 * 처리하는 비즈니스 로직을 담당합니다.
 * </p>
 *
 * <p>
 * 신청서 상태 흐름:
 * </p>
 *
 * <pre>
 *   [신청서 등록] → 결재중
 *                      ↓ (모든 결재자 순차 승인)
 *                   결재완료
 *                      ↓ (중간 결재자 반려)
 *                    반려
 * </pre>
 *
 * <p>
 * 결재선(Approval Line):
 * </p>
 * <ul>
 * <li>등록 시 결재자 사번 목록({@code approverEnos})을 순서대로 받아 {@link Cdecim}으로 저장</li>
 * <li>순차 결재: 앞 순번이 승인해야 다음 순번이 결재 가능</li>
 * <li>동일 결재자 연속 등장 시 일괄 승인 처리</li>
 * </ul>
 *
 * <p>
 * 원본 데이터 연결:
 * </p>
 * <ul>
 * <li>신청서는 원본 테이블(예: BPROJM)과 {@link Cappla}로 연결</li>
 * <li>{@code fntTbNm}: 원천 테이블명 (예: "BPROJM")</li>
 * <li>{@code pkColNm}: 원천 테이블의 PK 값 (예: 프로젝트관리번호)</li>
 * <li>{@code fntTbCrySno}: 원천 테이블의 SNO 값 (예: 프로젝트순번)</li>
 * </ul>
 *
 * <p>
 * {@code @Transactional(readOnly = true)}: 조회 메서드의 기본값.
 * 쓰기 메서드는 {@code @Transactional}로 오버라이드합니다.
 * </p>
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

    /** 원천테이블명: 정보화사업 마스터(BPROJM). BPROJA 적재 대상 식별용 상수. */
    private static final String FNT_TB_BPROJM = "BPROJM";

    /**
     * 신청서 등록 (결재 요청)
     *
     * <p>
     * 신청서 마스터({@link Capplm})를 생성하고, 원본 데이터 연결({@link Cappla})
     * 및 결재선({@link Cdecim})을 함께 저장합니다.
     * </p>
     *
     * <p>
     * 신청관리번호 생성 규칙: {@code APF_{yyyy}{시퀀스8자리}}
     * </p>
     * <p>
     * 예: {@code APF_202600000001}
     * </p>
     *
     * <p>
     * 처리 순서:
     * </p>
     * <ol>
     * <li>Oracle 시퀀스로 신청관리번호 채번</li>
     * <li>신청서 마스터 저장 (상태: "결재중")</li>
     * <li>원본 데이터 연결 저장 (fntTbNm이 있는 경우)</li>
     * <li>결재선 생성 (승인자 순서대로, 마지막 승인자는 lstDcdYn='Y')</li>
     * </ol>
     *
     * @param request 신청서 생성 요청 DTO (신청서명, 세부내용, 신청자, 결재자 목록 등)
     * @return 생성된 신청관리번호 (예: "APF_202600000001")
     */
    @Transactional
    public String submit(ApplicationDto.CreateRequest request) {

        // Oracle 시퀀스로 채번하여 신청관리번호 생성 (APF-{yyyy}-{seq:08d})
        Long capplmSeq = applicationRepository.getNextVal();
        String apfMngNo = String.format("APF-%s-%08d",
                java.time.LocalDate.now().getYear(), capplmSeq);

        // 1. 신청서 마스터 생성 (초기 상태: "결재중")
        Capplm capplm = Capplm.builder()
                .apfMngNo(apfMngNo)                        // 신청관리번호 (PK)
                .dcdReqTtl(request.getApfNm())             // 결재요청제목
                .dcdReqInf(request.getApfDtlCone())        // 결재요청정보 (JSON)
                .itPtlApfPrgStsC(ApprovalStatus.IN_PROGRESS.code())
                .dcdReqUsid(request.getRqsEno())           // 결재요청사용자ID
                .dcdReqBbrC(resolveRequesterBbrC(request.getRqsEno())) // 결재요청부점코드
                .dcdReqDtm(LocalDate.now())                // 결재요청일시 = 오늘
                .rgprDcdReqCone(request.getRqsOpnn())      // 등록자결재요청내용
                .build();
        applicationRepository.save(capplm);

        // 1-1. 원천 데이터 연결 저장 (orcItems 각각에 대해 Cappla 생성)
        // 하나의 신청서가 복수의 원천 레코드(정보화사업, 전산관리비 등)를 연결할 수 있습니다.
        if (request.getOrcItems() != null && !request.getOrcItems().isEmpty()) {
            for (ApplicationDto.OrcItem item : request.getOrcItems()) {
                Cappla cappla = Cappla.builder()
                        .apfDcmNo(apfMngNo)
                        .fntTbNm(item.getFntTbNm())
                        .pkColNm(item.getPkColNm())
                        .fntTbCrySno(item.getFntTbCrySno() != null ? Integer.parseInt(item.getFntTbCrySno()) : null)
                        .build();
                applicationMapRepository.save(cappla);

                // 정보화사업(BPROJM) 결재 상신 → 정보화사업관계(BPROJA) 상태를 결재중('05')으로 갱신.
                // 단계 key(CNCD_RFR_NO)는 작성('01') 시와 동일하게 프로젝트관리번호(pkColNm) 자신을 사용해
                // 동일 행을 멱등 upsert 한다. 전산업무비(BCOSTM) 등 비-프로젝트 원천은 적재 대상이 아니다.
                if (FNT_TB_BPROJM.equals(item.getFntTbNm())) {
                    bprojaSyncService.upsert(item.getPkColNm(), item.getPkColNm(), "05");
                }
            }
        }

        // 2. 결재선 생성: 요청받은 결재자 사번 목록을 순번(dcdSqn)대로 저장
        List<String> approverEnos = request.getApproverEnos();
        List<Cdecim> savedApprovers = new java.util.ArrayList<>();

        for (int i = 0; i < approverEnos.size(); i++) {
            Cdecim cdecim = Cdecim.builder()
                    .dcdMngNo(apfMngNo) // 결재관리번호 (FK)
                    .dcrSqnSno(i + 1) // 결재순번 (1부터 시작)
                    .dcrEno(approverEnos.get(i)) // 결재자 사원번호
                    .itPtlDcdStsC(DecisionStatus.PENDING.code()) // 초기 결재상태: 미결재(1) — NOT NULL
                    .lstDcdYn(i == approverEnos.size() - 1 ? "Y" : "N") // 마지막 결재자 여부
                    .build();
            approverRepository.save(cdecim);
            savedApprovers.add(cdecim);
        }

        // 3. 다음 결재 차례인 결재자에게 알림 발행 (결재선의 가장 앞 순번 결재자)
        //    AFTER_COMMIT 리스너가 처리하므로 본 트랜잭션은 차단되지 않는다.
        //    참고: 기안자와 1차 결재자가 동일하더라도 자동 승인하지 않고 명시적 결재를 요구합니다.
        publishApprovalRequestNotification(capplm);

        return apfMngNo; // 생성된 신청관리번호 반환
    }

    /**
     * 결재선에서 다음 차례인 결재자에게 결재요청 알림을 발행한다.
     *
     * <p>{@code IT_PTL_DCD_STS_C = '1'(미결재)}인 결재 항목 중 가장 작은 {@code DCD_SQN}의 결재자가 대상.
     * 발견되지 않으면(=결재선 모두 처리됨) 알림을 발행하지 않는다.</p>
     */
    private void publishApprovalRequestNotification(Capplm capplm) {
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(capplm.getApfMngNo());
        Cdecim next = approvers.stream()
            .filter(a -> DecisionStatus.isPendingCode(a.getItPtlDcdStsC()))
            .findFirst()
            .orElse(null);
        if (next == null || next.getDcrEno() == null || next.getDcrEno().isBlank()) {
            log.info("[알림 진단] APPROVAL_REQUEST publishEvent 건너뜀: apfMngNo={}, approvers={}, nextNull={}, nextEnoBlank={}",
                capplm.getApfMngNo(),
                approvers.size(),
                next == null,
                next != null && (next.getDcrEno() == null || next.getDcrEno().isBlank()));
            return;
        }
        log.debug("[알림 진단] APPROVAL_REQUEST publishEvent: apfMngNo={}, recipientEno={}, dcrSqnSno={}",
            capplm.getApfMngNo(), next.getDcrEno(), next.getDcrSqnSno());
        eventPublisher.publishEvent(
            NotificationEvent.builder()
                .recipientEno(next.getDcrEno())
                .itPtlInfmSvcTc(NotificationEvent.TYPE_APPROVAL_REQUEST)
                .ttl(NotificationMessageFormatter.abbreviate("결재요청: " + safeText(capplm.getDcdReqTtl()), 100))
                .infmMsgCone(NotificationMessageFormatter.abbreviate(safeText(capplm.getDcdReqTtl()), 4000))
                // 결재 알림은 결재 대기 목록 화면으로 고정 (사용자 정책).
                // 상대 path 사용 — Nuxt navigateTo가 내부 라우팅으로 처리하며 운영 호스트와 무관.
                .infmRcdUrl("/approval/list?tab=pending")
                .itPtlSdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                .build()
        );
    }

    private static String safeText(String s) {
        return s == null ? "" : s;
    }

    /**
     * 결재 처리 (승인 또는 반려)
     *
     * <p>
     * 순차 결재 방식으로, 이전 결재자가 모두 승인한 경우에만 다음 결재자가 결재할 수 있습니다.
     * 동일 결재자가 연속으로 등장한 경우 한 번의 요청으로 연속 항목 모두 승인합니다.
     * </p>
     *
     * <p>
     * 처리 흐름:
     * </p>
     * <ol>
     * <li>신청서 존재 확인</li>
     * <li>전체 결재자 목록 조회 (순번 오름차순)</li>
     * <li>현재 결재 차례(미결재, 이전 모두 승인) 탐색</li>
     * <li>요청자가 현재 결재자인지 확인</li>
     * <li>결재 상태 저장 (승인/반려)</li>
     * <li>동일 결재자 연속 등장 시 일괄 승인</li>
     * <li>JSON 결재선 정보 업데이트</li>
     * <li>반려: 신청서 상태 → "반려" / 마지막 승인: 신청서 상태 → "결재완료"</li>
     * </ol>
     *
     * @param apfMngNo 결재할 신청관리번호
     * @param request  결재 요청 DTO (결재자 사번, 의견, 승인/반려 상태)
     * @throws IllegalArgumentException 신청서가 없거나 결재자가 아닌 경우
     * @throws IllegalStateException    결재 차례가 아닌 경우
     */
    @Transactional
    public void approve(String apfMngNo, ApplicationDto.ApproveRequest request) {
        // 신청서 마스터 조회 (없으면 예외)
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));

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

        // 현재 결재자의 결재 처리 및 저장
        currentApprover.approve(request.getDcdOpnn(), decision);
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
                    nextApprover.approve(request.getDcdOpnn(), decision);
                    approverRepository.save(nextApprover);
                    lastApproved = nextApprover;
                    approvedList.add(nextApprover);
                } else {
                    break; // 다른 결재자 만나면 일괄 승인 종료
                }
            }
        }

        // 신청서 상세 내용(JSON) 내 결재선 정보 업데이트 (결재 일자 기록)
        approvalLineDelegate.doUpdate(capplm, approvers, approvedList);

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
            // 신청서에 연결된 BPROJM 원천(Cappla)을 역조회해, 작성('01')/상신('02')과 동일 행(단계 key=프로젝트관리번호)을 멱등 upsert 한다.
            for (Cappla c : applicationMapRepository.findByApfDcmNoAndFntTbNm(apfMngNo, FNT_TB_BPROJM)) {
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
            publishApprovalRequestNotification(capplm);
        }
    }

    /**
     * 일괄 결재 (여러 신청서를 하나의 트랜잭션으로 처리)
     *
     * <p>
     * 복수의 신청서에 대해 순차적으로 {@link #approve(String, ApplicationDto.ApproveRequest)}를
     * 호출합니다. 하나라도 실패하면 전체 트랜잭션이 롤백됩니다.
     * </p>
     *
     * <p>
     * 주의: 예외 발생 시 {@link RuntimeException}을 다시 던져 트랜잭션 롤백을 유발합니다.
     * </p>
     *
     * @param request 일괄 결재 요청 DTO (처리할 신청서 목록)
     * @return 모든 항목이 성공한 경우의 일괄 결재 결과 DTO. 실패 항목이 있으면 반환되지 않는다.
     * @throws RuntimeException 개별 신청서 처리 실패 시 즉시 재발생하여 전체 롤백
     */
    @Transactional
    public ApplicationDto.BulkApproveResponse bulkApprove(ApplicationDto.BulkApproveRequest request) {
        List<ApplicationDto.ApprovalResult> results = new java.util.ArrayList<>(); // 개별 결과 목록
        int successCount = 0; // 성공 건수
        int failureCount = 0; // 실패 건수

        // 모든 신청서를 순회하며 승인 처리
        for (ApplicationDto.ApprovalItem item : request.getApprovals()) {
            try {
                // 개별 승인 요청 생성 (ApprovalItem → ApproveRequest 변환)
                ApplicationDto.ApproveRequest approveRequest = new ApplicationDto.ApproveRequest();
                approveRequest.setDcdEno(item.getDcdEno()); // 승인자 사원번호
                approveRequest.setDcdOpnn(item.getDcdOpnn()); // 승인 의견
                approveRequest.setDcdSts(item.getDcdSts()); // 승인 상태 (승인, 반려)

                // 개별 승인 처리
                approve(item.getApfMngNo(), approveRequest);

                // 성공 결과 추가
                results.add(ApplicationDto.ApprovalResult.builder()
                        .apfMngNo(item.getApfMngNo())
                        .success(true)
                        .message("처리 완료")
                        .build());
                successCount++;

            } catch (Exception e) {
                // 실패 시 RuntimeException을 던져 전체 트랜잭션 롤백
                throw new RuntimeException("신청서 " + item.getApfMngNo() + " 처리 실패: " + e.getMessage(), e);
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
     * <p>
     * 신청관리번호로 신청서 마스터를 조회하여 세부내용({@code APF_DTL_CONE}) 필드만 반환합니다.
     * </p>
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청관리번호와 세부내용을 담은 응답 DTO ({@link ApplicationDto.ApfDtlConeResponse})
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     */
    public ApplicationDto.ApfDtlConeResponse getApfDtlCone(String apfMngNo) {
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        return ApplicationDto.ApfDtlConeResponse.fromEntity(capplm);
    }

    /**
     * 단건 신청서 조회
     *
     * <p>
     * 신청관리번호로 신청서 마스터와 결재자 목록을 조회하여 DTO로 반환합니다.
     * </p>
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청서 상세 응답 DTO (결재자 목록 포함)
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     */
    public ApplicationDto.Response getApplication(String apfMngNo) {
        // 신청서 마스터 조회
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        // 결재자 목록 조회 (순번 오름차순)
        List<ApproverRepository.ApproverReadView> approvers =
                approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);
        String requesterNm = requesterName(resolveRequesterNames(List.of(capplm)), capplm.getDcdReqUsid());
        String requesterBbrNm = requesterDeptName(resolveRequesterDeptNames(List.of(capplm)), capplm.getDcdReqBbrC());
        return ApplicationDto.Response.fromReadViews(capplm, approvers, requesterNm, requesterBbrNm);
    }

    /**
     * 전체 신청서 목록 조회
     *
     * <p>
     * DB의 모든 신청서를 조회하고, 각 신청서의 결재자 목록을 포함하여 반환합니다.
     * </p>
     *
     * @return 전체 신청서 응답 DTO 목록 (각각 결재자 목록 포함)
     */
    public List<ApplicationDto.Response> getApplications() {
        List<Capplm> capplms = applicationRepository.findAll();
        List<String> apfMngNos = capplms.stream().map(value -> value.getApfMngNo()).toList();

        // 결재선 배치 조회 (N+1 제거): 신청번호별 결재자 목록 Map 선구성.
        // findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc가 DCR_SQN_SNO 오름차순으로 반환하므로
        // groupingBy가 각 신청번호 그룹 내 결재자 순서를 보존한다.
        java.util.Map<String, List<ApproverRepository.ApproverReadView>> approversByApf =
                approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(apfMngNos).stream()
                        .collect(java.util.stream.Collectors.groupingBy(value -> value.getDcdMngNo()));
        java.util.Map<String, String> requesterNamesByEno = resolveRequesterNames(capplms);
        java.util.Map<String, String> requesterDeptNamesByBbrC = resolveRequesterDeptNames(capplms);

        return capplms.stream()
                .map(capplm -> ApplicationDto.Response.fromReadViews(
                        capplm,
                        approversByApf.getOrDefault(capplm.getApfMngNo(), List.of()),
                        requesterName(requesterNamesByEno, capplm.getDcdReqUsid()),
                        requesterDeptName(requesterDeptNamesByBbrC, capplm.getDcdReqBbrC())))
                .toList();
    }

    /**
     * 신청자 사번으로 현재 소속 부점코드를 조회합니다.
     *
     * @param eno 신청자 사번
     * @return 신청자 소속 부점코드, 없으면 null
     */
    private String resolveRequesterBbrC(String eno) {
        if (eno == null || eno.isBlank()) {
            return null;
        }
        return userRepository.findById(eno)
                .map(user -> user.getBbrC())
                .orElse(null);
    }

    /**
     * 사번이 비어 있거나 맵 구현체가 null key를 허용하지 않는 경우를 방어하며 신청자명을 조회합니다.
     *
     * @param requesterNamesByEno 사번별 신청자명 맵
     * @param eno                 신청자 사번
     * @return 신청자명, 없으면 null
     */
    private String requesterName(java.util.Map<String, String> requesterNamesByEno, String eno) {
        if (eno == null || eno.isBlank()) {
            return null;
        }
        return requesterNamesByEno.get(eno);
    }

    /**
     * 부점코드가 비어 있거나 맵 구현체가 null key를 허용하지 않는 경우를 방어하며 신청부서명을 조회합니다.
     *
     * @param requesterDeptNamesByBbrC 부점코드별 신청부서명 맵
     * @param bbrC                     신청부서코드
     * @return 신청부서명, 없으면 null
     */
    private String requesterDeptName(java.util.Map<String, String> requesterDeptNamesByBbrC, String bbrC) {
        if (bbrC == null || bbrC.isBlank()) {
            return null;
        }
        return requesterDeptNamesByBbrC.get(bbrC);
    }

    /**
     * 신청서 목록의 신청자 사번을 사용자명으로 일괄 변환합니다.
     *
     * @param capplms 신청서 마스터 목록
     * @return 사번을 키로 하는 사용자명 맵
     */
    private java.util.Map<String, String> resolveRequesterNames(List<Capplm> capplms) {
        java.util.Set<String> requesterEnos = capplms.stream()
                .map(application -> application.getDcdReqUsid())
                .filter(eno -> eno != null && !eno.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (requesterEnos.isEmpty()) {
            return java.util.Map.of();
        }

        return userRepository.findNameViewsByEnoIn(requesterEnos).stream()
                .collect(java.util.stream.Collectors.toMap(
                        user -> user.getEno(),
                        user -> user.getUsrNm(),
                        (left, right) -> left));
    }

    /**
     * 신청서 목록의 신청부서코드를 부서명으로 일괄 변환합니다.
     *
     * @param capplms 신청서 마스터 목록
     * @return 부점코드를 키로 하는 부점명 맵
     */
    private java.util.Map<String, String> resolveRequesterDeptNames(List<Capplm> capplms) {
        java.util.Set<String> requesterBbrCs = capplms.stream()
                .map(application -> application.getDcdReqBbrC())
                .filter(bbrC -> bbrC != null && !bbrC.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (requesterBbrCs.isEmpty()) {
            return java.util.Map.of();
        }

        return organizationRepository.findNameViewsByPrlmOgzCConeIn(requesterBbrCs).stream()
                .filter(org -> org.getBbrNm() != null)
                .collect(java.util.stream.Collectors.toMap(
                        organization -> organization.getPrlmOgzCCone(),
                        organization -> organization.getBbrNm(),
                        (left, right) -> left));
    }

    /**
     * 일괄 조회 (여러 신청관리번호로 한 번에 조회)
     *
     * <p>
     * 요청 목록의 각 신청관리번호에 대해 {@link #getApplication(String)}을 호출합니다.
     * 존재하지 않는 신청서는 결과에서 제외합니다 (null 필터링).
     * </p>
     *
     * @param request 일괄 조회 요청 DTO (신청관리번호 목록)
     * @return 조회 성공 항목과 실패(미존재) ID 목록을 함께 담은 {@link ApplicationDto.BulkResponse}
     */
    public ApplicationDto.BulkResponse getApplicationsByIds(ApplicationDto.BulkGetRequest request) {
        List<ApplicationDto.Response> items = new java.util.ArrayList<>();
        List<String> failedIds = new java.util.ArrayList<>();
        for (String apfMngNo : request.getApfMngNos()) {
            try {
                items.add(getApplication(apfMngNo)); // 개별 신청서 조회
            } catch (IllegalArgumentException e) {
                // 미존재 ID는 조용히 버리지 않고 실패 목록에 수집해 호출자에게 노출한다.
                failedIds.add(apfMngNo);
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("bulk-get 누락: type=application, failedIds={}", failedIds);
        }
        return new ApplicationDto.BulkResponse(items, failedIds);
    }

    /**
     * 전자결재 대시보드 집계 조회
     *
     * <p>bbrC 기준 부서 통계와 eno 기준 본인 결재 대기 목록을 반환합니다.</p>
     *
     * @param bbrC 부서코드 (TPRMPP_CUSERI.BBR_C)
     * @param eno  사원번호 (본인 결재 대기 필터)
     * @return 대시보드 집계 응답 DTO
     * @throws org.springframework.dao.DataAccessException DB 조회 실패 시 (GlobalExceptionHandler에서 500 응답으로 처리)
     */
    public ApplicationDto.DashboardResponse getDashboard(String bbrC, String eno) {
        int pendingCount          = applicationRepository.countPendingByEno(eno);
        int inProgressCount       = applicationRepository.countInProgressByEno(eno);
        int monthlyCompletedCount = applicationRepository.countMonthlyCompletedByBbrC(bbrC);
        int rejectedCount         = applicationRepository.countRejectedByEno(eno);

        List<ApplicationDto.MonthlyCount> monthlyTrend =
            applicationRepository.findMonthlyTrendRowsByBbrC(bbrC).stream()
                .map(row -> ApplicationDto.MonthlyCount.builder()
                    .month(row.label())
                    .count(Math.toIntExact(row.count()))
                    .build())
                .toList();

        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        List<ApplicationDto.PendingItem> pendingList =
            applicationRepository.findPendingRowsByEno(eno).stream()
                .map(row -> {
                    String rqsDtStr = row.rqsDt();
                    LocalDate rqsDt = rqsDtStr != null ? LocalDate.parse(rqsDtStr) : LocalDate.now();
                    String urgency = rqsDt.isBefore(threeDaysAgo) ? "urgent" : "normal";
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
     * @param eno  사원번호
     * @return 배지 건수 응답 DTO
     */
    public ApplicationDto.ApprovalBadgeCountResponse getApprovalBadgeCount(String bbrC, String eno) {
        return ApplicationDto.ApprovalBadgeCountResponse.builder()
            .pendingCount(applicationRepository.countPendingByEno(eno))
            .inProgressCount(applicationRepository.countInProgressByEno(eno))
            .build();
    }

    /**
     * 미상신(결재 신청 이력 없음) 건수 집계
     *
     * <p>
     * 사이드바의 [결재 상신] 메뉴 옆 배지에서 사용됩니다.
     * 전체 목록 대신 건수만 반환하여 데이터 전송량을 최소화합니다.
     * </p>
     *
     * <p>
     * 집계 로직: {@code apfSts='none'} 조건으로 {@code ProjectRepository} 및
     * {@code CostRepository}의 {@code countBySearchCondition} 집계 쿼리를 호출해 각각의 건수를 계산합니다.
     * (CAPPLA 연결이 없는 BPROJM/BCOSTM 레코드 = 아직 결재 상신되지 않은 항목)
     * </p>
     *
     * @return 미상신 건수 응답 DTO (정보화사업/전산업무비 개별 건수 + 총합)
     */
    public ApplicationDto.PendingCountResponse getPendingCount(String bgYy) {
        // 미상신 정보화사업 건수: 활성/완료 신청서 없는 BPROJM (필요 시 연도 필터 적용).
        // 사이드바 배지가 [결재 상신] 화면(예산연도 필터링)과 동일한 카운트를 보이도록 bgYy 일치 필요.
        ProjectDto.SearchCondition projectCondition = new ProjectDto.SearchCondition();
        projectCondition.setApfSts("none");
        if (bgYy != null && !bgYy.isBlank()) projectCondition.setBseYy(bgYy);
        // 전체 엔티티 적재 대신 COUNT 쿼리로 건수만 산출 (동일 WHERE 조건 → 결과 동치)
        long projectCount = projectRepository.countBySearchCondition(projectCondition);

        // 미상신 전산업무비 건수
        CostDto.SearchCondition costCondition = new CostDto.SearchCondition();
        costCondition.setApfSts("none");
        if (bgYy != null && !bgYy.isBlank()) costCondition.setBseYy(bgYy);
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
     * @param apfMngNo   회수할 신청서 관리번호
     * @param request    회수 요청 (사유)
     * @param currentEno 회수 요청자 사번
     * @param isAdmin    관리자(ROLE_ADMIN) 여부
     * @throws IllegalArgumentException 신청서 없음
     * @throws IllegalStateException    회수 가능 상태 아님 / 최종승인 후
     * @throws AccessDeniedException    회수 권한 없음
     */
    @Transactional
    public void recall(String apfMngNo, ApplicationDto.RecallRequest request,
                       String currentEno, boolean isAdmin) {
        Capplm capplm = applicationRepository.findById(apfMngNo)
            .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));

        if (!ApprovalStatus.IN_PROGRESS.code().equals(capplm.getItPtlApfPrgStsC())) {
            throw new IllegalStateException("회수 가능한 상태가 아닙니다. 현재 상태: " + capplm.getItPtlApfPrgStsC());
        }

        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);
        boolean lastApproved = approvers.stream()
            .anyMatch(a -> "Y".equals(a.getLstDcdYn())
                        && DecisionStatus.isApprovedCode(a.getItPtlDcdStsC()));
        if (lastApproved) {
            throw new IllegalStateException("최종 결재자 승인 후에는 회수할 수 없습니다.");
        }

        if (!canRecall(capplm, approvers, currentEno, isAdmin)) {
            throw new AccessDeniedException("회수 권한이 없습니다.");
        }

        capplm.updateStatus(ApprovalStatus.RECALLED);
        approvalLineDelegate.applyRecallInfo(capplm, currentEno, request.getRecallOpnn());

        for (Cdecim a : approvers) {
            if (DecisionStatus.isPendingCode(a.getItPtlDcdStsC())) {
                a.invalidateByRecall();
                approverRepository.save(a);
            }
        }

        List<String> approvedMiddle = approvers.stream()
            .filter(a -> "N".equals(a.getLstDcdYn())
                      && DecisionStatus.isApprovedCode(a.getItPtlDcdStsC()))
            .map(value -> value.getDcrEno())
            .distinct()
            .toList();

        eventPublisher.publishEvent(new ApprovalRecalledEvent(apfMngNo, currentEno, approvedMiddle));
    }

    /**
     * 결재 회수 가능 여부 검증 헬퍼
     *
     * <p>
     * 결재중 상태인 신청서에 대해 관리자·신청자·중간결재자 세 가지 분기로 회수 권한을 판단합니다.
     * </p>
     *
     * <ul>
     * <li>결재중({@code APF_STS_C = IN_PROGRESS}) 상태가 아니면 무조건 false 반환</li>
     * <li>시스템관리자({@code isAdmin=true}): 항상 허용</li>
     * <li>신청자({@code currentEno == capplm.rqsEno}): 허용</li>
     * <li>중간결재자(최종결재자 아닌 결재선 중 현재 사용자): 허용</li>
     * </ul>
     *
     * @param capplm      대상 신청서 마스터 엔티티
     * @param approvers   결재선 목록 (중간결재자 여부 판단용, {@code LST_DCD_YN} 기준)
     * @param currentEno  현재 요청 사용자 사번
     * @param isAdmin     관리자 여부 플래그
     * @return 회수 가능 여부 (true=허용, false=거부)
     */
    private boolean canRecall(Capplm capplm, List<Cdecim> approvers, String currentEno, boolean isAdmin) {
        if (!ApprovalStatus.IN_PROGRESS.code().equals(capplm.getItPtlApfPrgStsC())) return false;
        if (isAdmin) return true;
        if (currentEno.equals(capplm.getDcdReqUsid())) return true;
        return approvers.stream()
            .filter(a -> !"Y".equals(a.getLstDcdYn()))
            .anyMatch(a -> currentEno.equals(a.getDcrEno()));
    }
}
