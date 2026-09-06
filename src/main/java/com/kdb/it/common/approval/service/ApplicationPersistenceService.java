package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.*;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.*;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 호출자의 트랜잭션에 참여해 신청서·원장 연결·결재선을 저장하고 기존 알림을 예약한다. */
@Service
@RequiredArgsConstructor
public class ApplicationPersistenceService {
    private final ApplicationRepository applicationRepository;
    private final ApproverRepository approverRepository;
    private final ApplicationMapRepository applicationMapRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;
    private final UserRepository userRepository;
    private final BprojaSyncService bprojaSyncService;
    private final ApprovalRequestNotifier approvalRequestNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private static final String FNT_TB_BPROJM = "BPROJM";
    private static final String FNT_TB_BCOSTM = "BCOSTM";

    /** 범용 제출의 개정 순번 파싱 규칙을 보존하는 원장 연결 입력이다. */
    public record SourceLink(String table, String id, String revision) {}

    /** 결재선 저장 규칙. 기존 범용 신청과 전산예산 v2를 명시적으로 분리한다. */
    public enum DecisionLinePolicy {
        LEGACY,
        IT_BUDGET_V2
    }

    public record ApplicationDraft(
            String applicationName,
            String detailJson,
            String requesterEno,
            String requesterOpinion,
            String requesterDecisionOpinion,
            List<SourceLink> sources,
            List<String> approverEnos,
            DecisionLinePolicy decisionLinePolicy,
            LocalDate requestDate) {
        /** 기존 호출부의 저장 규칙을 유지한다. */
        public ApplicationDraft(
                String applicationName,
                String detailJson,
                String requesterEno,
                String requesterOpinion,
                List<SourceLink> sources,
                List<String> approverEnos) {
            this(
                    applicationName,
                    detailJson,
                    requesterEno,
                    requesterOpinion,
                    null,
                    sources,
                    approverEnos,
                    DecisionLinePolicy.LEGACY,
                    null);
        }

        /** 전산예산 v2의 기안자 요청 행과 실제 결재자 행을 구분해 저장하는 입력을 만든다. */
        public static ApplicationDraft itBudgetV2(
                String applicationName,
                String detailJson,
                String requesterEno,
                String requesterSummary,
                String requesterDecisionOpinion,
                List<SourceLink> sources,
                List<String> approverEnos,
                LocalDate requestDate) {
            return new ApplicationDraft(
                    applicationName,
                    detailJson,
                    requesterEno,
                    requesterSummary,
                    requesterDecisionOpinion,
                    sources,
                    approverEnos,
                    DecisionLinePolicy.IT_BUDGET_V2,
                    requestDate);
        }

        /** 기존 범용 요청의 원문 JSON·결재선·원장 순서를 그대로 전달한다. */
        public static ApplicationDraft from(ApplicationDto.CreateRequest request) {
            return new ApplicationDraft(
                    request.getApfNm(),
                    request.getApfDtlCone(),
                    request.getRqsEno(),
                    request.getRqsOpnn(),
                    request.getOrcItems() == null
                            ? null
                            : request.getOrcItems().stream()
                                    .map(
                                            i ->
                                                    new SourceLink(
                                                            i.getFntTbNm(),
                                                            i.getPkColNm(),
                                                            i.getFntTbCrySno()))
                                    .toList(),
                    request.getApproverEnos());
        }
    }

    /**
     * 신청서를 저장하고 기존 알림 발행 순서를 유지한다. 새 트랜잭션을 생성하지 않는다.
     *
     * @param draft 호출자가 확정한 문서와 원장·결재자 입력
     * @return 생성한 신청관리번호
     * @throws IllegalArgumentException 원장 개정본 순번이 없거나 활성 개정본이 없을 때
     * @throws org.springframework.transaction.IllegalTransactionStateException 호출자 트랜잭션이 없을 때
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String persist(ApplicationDraft draft) {

        LocalDate requestDate = draft.requestDate() == null ? LocalDate.now() : draft.requestDate();
        List<String> approverEnos = draft.approverEnos();
        boolean itBudgetV2 = draft.decisionLinePolicy() == DecisionLinePolicy.IT_BUDGET_V2;
        int requesterApprovedPrefix =
                itBudgetV2 ? leadingRequesterApproverCount(approverEnos, draft.requesterEno()) : 0;
        boolean completedOnSubmission =
                itBudgetV2
                        && !approverEnos.isEmpty()
                        && requesterApprovedPrefix == approverEnos.size();

        // Oracle 시퀀스로 채번하여 신청관리번호 생성 (APF-{yyyy}-{seq:08d})
        Long capplmSeq = applicationRepository.getNextVal();
        String apfMngNo = String.format("APF-%s-%08d", requestDate.getYear(), capplmSeq);

        // 1. 신청서 마스터 생성. 기안자가 모든 결재 역할을 겸하면 상신과 동시에 결재 완료한다.
        Capplm capplm =
                Capplm.builder()
                        .apfMngNo(apfMngNo) // 신청관리번호 (PK)
                        .dcdReqTtl(draft.applicationName()) // 결재요청제목
                        .dcdReqInf(draft.detailJson()) // 결재요청정보 (JSON)
                        .itPtlApfPrgStsC(
                                (completedOnSubmission
                                                ? ApprovalStatus.COMPLETED
                                                : ApprovalStatus.IN_PROGRESS)
                                        .code())
                        .dcdReqUsid(draft.requesterEno()) // 결재요청사용자ID
                        .dcdReqBbrC(resolveRequesterBbrC(draft.requesterEno())) // 결재요청부점코드
                        .dcdReqDtm(requestDate) // 결재요청일시 = 오늘
                        .rgprDcdReqCone(draft.requesterOpinion()) // 등록자결재요청내용
                        .build();
        applicationRepository.save(capplm);

        // 1-1. 원천 데이터 연결 저장 (orcItems 각각에 대해 Cappla 생성)
        // 하나의 신청서가 복수의 원천 레코드(정보화사업, 전산관리비 등)를 연결할 수 있습니다.
        if (draft.sources() != null && !draft.sources().isEmpty()) {
            for (SourceLink item : draft.sources()) {
                Integer crySno = resolveSourceVersionSno(item);
                Cappla cappla =
                        Cappla.builder()
                                .apfDcmNo(apfMngNo)
                                .fntTbNm(item.table())
                                .pkColNm(item.id())
                                .fntTbCrySno(crySno)
                                .build();
                applicationMapRepository.save(cappla);

                // 정보화사업(BPROJM) 결재 상신 → 정보화사업관계(BPROJA) 상태를 결재중('05')으로 갱신.
                // 단계 key(CNCD_RFR_NO)는 작성('01') 시와 동일하게 프로젝트관리번호(pkColNm) 자신을 사용해
                // 동일 행을 멱등 upsert 한다. 전산업무비(BCOSTM) 등 비-프로젝트 원천은 적재 대상이 아니다.
                if (FNT_TB_BPROJM.equals(item.table())) {
                    bprojaSyncService.upsert(
                            item.id(), item.id(), completedOnSubmission ? "09" : "05");
                }
            }
        }

        // 2. 결재선 생성: 전산예산 v2는 기안자를 0번 요청 행으로 남기고 실제 결재자는 1번부터 저장한다.
        if (itBudgetV2) {
            approverRepository.save(
                    Cdecim.builder()
                            .dcdMngNo(apfMngNo)
                            .dcrSqnSno(0)
                            .dcrEno(draft.requesterEno())
                            .itPtlDcdStsC(DecisionStatus.APPROVED.code())
                            .dcdDtm(requestDate)
                            .dcrOpnnCone(draft.requesterDecisionOpinion())
                            .lstDcdYn("N")
                            .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                            .build());
        }

        for (int i = 0; i < approverEnos.size(); i++) {
            boolean autoApproved = i < requesterApprovedPrefix;
            Cdecim cdecim =
                    Cdecim.builder()
                            .dcdMngNo(apfMngNo) // 결재관리번호 (FK)
                            .dcrSqnSno(i + 1) // 결재순번 (1부터 시작)
                            .dcrEno(approverEnos.get(i)) // 결재자 사원번호
                            .itPtlDcdStsC(
                                    (autoApproved
                                                    ? DecisionStatus.APPROVED
                                                    : DecisionStatus.PENDING)
                                            .code())
                            .dcdDtm(autoApproved ? requestDate : null)
                            .dcrOpnnCone(autoApproved ? draft.requesterDecisionOpinion() : null)
                            .lstDcdYn(i == approverEnos.size() - 1 ? "Y" : "N") // 마지막 결재자 여부
                            .dcdTpC(
                                    itBudgetV2
                                            ? Cdecim.DECISION_TYPE_APPROVAL
                                            : Cdecim.DECISION_TYPE_REQUEST)
                            .build();
            approverRepository.save(cdecim);
        }

        // 현재 트랜잭션에서 다음 결재자 조회·메일 페이로드 준비·이벤트 발행을 수행한다.
        // 실제 알림 전달은 기존 AFTER_COMMIT 리스너가 처리하므로 전체 롤백 때 발송하지 않는다.
        if (completedOnSubmission) {
            eventPublisher.publishEvent(
                    new ApprovalCompletedEvent(apfMngNo, ApprovalStatus.COMPLETED.label()));
        } else {
            approvalRequestNotifier.notifyApprovalRequest(capplm);
        }

        return apfMngNo; // 생성된 신청관리번호 반환
    }

    /** 기안자와 동일한 결재자가 결재선 선두에 연속된 개수를 반환한다. */
    private int leadingRequesterApproverCount(List<String> approverEnos, String requesterEno) {
        int count = 0;
        for (String approverEno : approverEnos) {
            if (!requesterEno.equals(approverEno)) break;
            count++;
        }
        return count;
    }

    /**
     * 상신 대상 원천 개정본의 순번을 검증해 반환합니다.
     *
     * <p>결재 매핑의 순번은 승인 완료 시 어느 개정본을 최종본으로 승격할지 결정합니다. 잘못된 순번이 실리면 승인 시점에 폐기된 구버전이 다시 최종본이 되거나(내용
     * 롤백), 순번이 비어 있으면 승격 리스너가 예외를 던져 승인 트랜잭션 전체가 롤백됩니다. 두 경우 모두 결재자에게 원인을 알 수 없는 실패로 보이므로 상신 시점에
     * 거절합니다.
     *
     * <p>순번 개념이 없는 원천 테이블은 검증 대상이 아니며 입력값을 그대로 씁니다.
     *
     * @param item 상신 요청의 원천 데이터 연결 항목
     * @return 검증된 개정 순번 (검증 대상이 아니면 입력값 그대로, 없으면 null)
     * @throws IllegalArgumentException 순번이 없거나 활성 개정본이 존재하지 않는 경우
     */
    private Integer resolveSourceVersionSno(SourceLink item) {
        Integer sno =
                item.revision() != null && !item.revision().isBlank()
                        ? Integer.parseInt(item.revision().trim())
                        : null;
        boolean versioned =
                FNT_TB_BPROJM.equals(item.table()) || FNT_TB_BCOSTM.equals(item.table());
        if (!versioned) {
            return sno;
        }
        if (sno == null) {
            throw new IllegalArgumentException(
                    "상신 대상 개정본 순번이 없습니다: %s %s".formatted(item.table(), item.id()));
        }
        boolean exists =
                FNT_TB_BPROJM.equals(item.table())
                        ? projectRepository
                                .findByAbusMngNoAndSnoAndDelYn(item.id(), sno, "N")
                                .isPresent()
                        : costRepository
                                .findByCostBgNoAndBgSnoAndDelYn(item.id(), sno, "N")
                                .isPresent();
        if (!exists) {
            throw new IllegalArgumentException(
                    "상신 대상 개정본이 없습니다: %s %s #%d".formatted(item.table(), item.id(), sno));
        }
        return sno;
    }

    private String resolveRequesterBbrC(String eno) {
        if (eno == null || eno.isBlank()) return null;
        return userRepository.findById(eno).map(user -> user.getBbrC()).orElse(null);
    }
}
