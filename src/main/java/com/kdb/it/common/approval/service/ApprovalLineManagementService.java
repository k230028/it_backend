package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결재중 신청서의 추가 결재선 등록·삭제를 처리합니다. */
@Service
@RequiredArgsConstructor
public class ApprovalLineManagementService {

    private static final int TEMPORARY_SEQUENCE_OFFSET = 1_000_000;

    private final ApplicationRepository applicationRepository;
    private final ApproverRepository approverRepository;
    private final UserRepository userRepository;
    private final ApprovalLineDelegate approvalLineDelegate;
    private final ApprovalDetailPolicy detailPolicy;

    /** 결재선 참여자 또는 관리자가 현재 결재선 뒤에 미결재 결재자를 추가합니다. */
    @Transactional
    public void addApprover(
            String apfMngNo, String newApproverEno, String currentEno, boolean isAdmin) {
        Capplm application = getInProgressApplication(apfMngNo);
        List<Cdecim> approvers = getApprovers(apfMngNo);
        assertCanManage(approvers, currentEno, isAdmin);
        CuserI user =
                userRepository
                        .findById(newApproverEno)
                        .orElseThrow(() -> new IllegalArgumentException("추가할 직원을 찾을 수 없습니다."));

        Cdecim last = approvers.get(approvers.size() - 1);
        last.markLast(false);
        approverRepository.save(last);
        int sequence = approvers.stream().mapToInt(Cdecim::getDcrSqnSno).max().orElse(0) + 1;
        Cdecim added =
                Cdecim.builder()
                        .dcdMngNo(apfMngNo)
                        .dcrSqnSno(sequence)
                        .dcrEno(newApproverEno)
                        .itPtlDcdStsC(DecisionStatus.PENDING.code())
                        .lstDcdYn("Y")
                        .dcdTpC(last.getDcdTpC())
                        .build();
        approverRepository.save(added);
        List<Cdecim> updatedOrder = new ArrayList<>(approvers);
        updatedOrder.add(added);
        if (detailPolicy.resolve(application) != ApprovalDetailPolicy.DetailMode.JSONLESS_COUNCIL) {
            approvalLineDelegate.addApproverToDetail(
                    application, newApproverEno, user.getUsrNm(), user.getPtCNm());
            approvalLineDelegate.updateApprovalOrder(application, updatedOrder);
        }
    }

    /** 결재선 참여자 또는 관리자가 미결재 상태인 결재자를 삭제합니다. */
    @Transactional
    public void deleteApprover(String apfMngNo, int dcdSqn, String currentEno, boolean isAdmin) {
        Capplm application = getInProgressApplication(apfMngNo);
        List<Cdecim> approvers = getApprovers(apfMngNo);
        assertCanManage(approvers, currentEno, isAdmin);
        Cdecim target =
                approvers.stream()
                        .filter(value -> Integer.valueOf(dcdSqn).equals(value.getDcrSqnSno()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("결재 순번을 찾을 수 없습니다: " + dcdSqn));
        if (!DecisionStatus.isPendingCode(target.getItPtlDcdStsC())) {
            throw new IllegalStateException("미결재 상태인 결재자만 삭제할 수 있습니다.");
        }

        int targetIndex = approvers.indexOf(target);
        approverRepository.delete(target);
        List<Cdecim> remaining = new ArrayList<>(approvers);
        remaining.remove(target);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).markLast(i == remaining.size() - 1);
        }
        if (!remaining.isEmpty()) approverRepository.save(remaining.get(remaining.size() - 1));
        if (detailPolicy.resolve(application) != ApprovalDetailPolicy.DetailMode.JSONLESS_COUNCIL) {
            approvalLineDelegate.removeApproverFromDetail(application, targetIndex);
            approvalLineDelegate.updateApprovalOrder(application, remaining);
        }
    }

    /** 승인 완료 결재자는 고정하고 미결재 결재자만 요청 순서로 재배치합니다. */
    @Transactional
    public void reorderPendingApprovers(
            String apfMngNo,
            List<Integer> orderedPendingSequences,
            String currentEno,
            boolean isAdmin) {
        Capplm application = getInProgressApplication(apfMngNo);
        List<Cdecim> approvers = getApprovers(apfMngNo);
        assertCanManage(approvers, currentEno, isAdmin);

        List<Cdecim> pendingApprovers = approvers.stream().filter(this::isPending).toList();
        List<Integer> pendingSequences =
                pendingApprovers.stream().map(Cdecim::getDcrSqnSno).toList();
        validatePendingPermutation(orderedPendingSequences, pendingSequences);

        if (!pendingSequences.equals(orderedPendingSequences)) {
            approverRepository.shiftPendingSequences(
                    apfMngNo, pendingSequences, TEMPORARY_SEQUENCE_OFFSET);
            for (int index = 0; index < pendingSequences.size(); index++) {
                approverRepository.updateSequence(
                        apfMngNo,
                        orderedPendingSequences.get(index) + TEMPORARY_SEQUENCE_OFFSET,
                        pendingSequences.get(index));
            }
        }

        Map<Integer, Cdecim> pendingBySequence = new HashMap<>();
        for (Cdecim pendingApprover : pendingApprovers) {
            pendingBySequence.put(pendingApprover.getDcrSqnSno(), pendingApprover);
        }
        List<Cdecim> reordered = new ArrayList<>();
        int pendingIndex = 0;
        for (Cdecim approver : approvers) {
            if (isPending(approver)) {
                reordered.add(pendingBySequence.get(orderedPendingSequences.get(pendingIndex++)));
            } else {
                reordered.add(approver);
            }
        }
        if (detailPolicy.resolve(application) != ApprovalDetailPolicy.DetailMode.JSONLESS_COUNCIL)
            approvalLineDelegate.updateApprovalOrder(application, reordered);
    }

    /**
     * 승인 완료 결재자는 유지하고 미결재 결재선을 요청한 전체 목록으로 원자적으로 교체합니다.
     *
     * @param apfMngNo 결재중인 신청서 관리번호
     * @param approverEnos 변경 후 미결재 결재자 사번 목록
     * @param currentEno 요청한 사용자 사번
     * @param isAdmin 관리자 권한 여부
     * @throws IllegalArgumentException 결재자 목록에 공백·미존재 사번이 있거나 목록이 비어 있으면 발생
     * @throws IllegalStateException 신청서가 결재중이 아니거나 교체할 미결재 결재자가 없으면 발생
     * @throws AccessDeniedException 결재선 참여자 또는 관리자가 아닌 사용자가 요청하면 발생
     */
    @Transactional
    public void replacePendingApprovers(
            String apfMngNo, List<String> approverEnos, String currentEno, boolean isAdmin) {
        Capplm application = getInProgressApplication(apfMngNo);
        List<Cdecim> approvers = getApprovers(apfMngNo);
        assertCanManage(approvers, currentEno, isAdmin);
        validateReplacementApprovers(approverEnos);

        List<Cdecim> completedApprovers = new ArrayList<>();
        List<Cdecim> pendingApprovers = new ArrayList<>();
        boolean pendingStarted = false;
        for (Cdecim approver : approvers) {
            if (isPending(approver)) {
                pendingStarted = true;
                pendingApprovers.add(approver);
            } else {
                if (pendingStarted) {
                    throw new IllegalStateException("승인 완료 결재자는 미결재 결재자 뒤에 있을 수 없습니다.");
                }
                completedApprovers.add(approver);
            }
        }
        if (pendingApprovers.isEmpty()) {
            throw new IllegalStateException("교체할 미결재 결재자가 없습니다.");
        }
        String replacementDecisionType = pendingApprovers.getFirst().getDcdTpC();

        List<CuserI> foundUsers = userRepository.findByEnoIn(approverEnos);
        Map<String, CuserI> usersByEno = new HashMap<>();
        for (CuserI user : foundUsers) {
            usersByEno.put(user.getEno(), user);
        }
        List<CuserI> replacementUsers = new ArrayList<>();
        for (String approverEno : approverEnos) {
            CuserI user = usersByEno.get(approverEno);
            if (user == null) {
                throw new IllegalArgumentException("결재자를 찾을 수 없습니다: " + approverEno);
            }
            if (!"N".equals(user.getDelYn())) {
                throw new IllegalArgumentException("활성 결재자가 아닙니다: " + approverEno);
            }
            replacementUsers.add(user);
        }

        int nextSequence =
                completedApprovers.isEmpty()
                        ? 1
                        : completedApprovers.get(completedApprovers.size() - 1).getDcrSqnSno() + 1;
        List<Cdecim> replacements = new ArrayList<>();
        for (int index = 0; index < approverEnos.size(); index++) {
            replacements.add(
                    Cdecim.builder()
                            .dcdMngNo(apfMngNo)
                            .dcrSqnSno(nextSequence + index)
                            .dcrEno(approverEnos.get(index))
                            .itPtlDcdStsC(DecisionStatus.PENDING.code())
                            .lstDcdYn(index == approverEnos.size() - 1 ? "Y" : "N")
                            .dcdTpC(replacementDecisionType)
                            .build());
        }

        List<Cdecim> completeOrder = new ArrayList<>(completedApprovers);
        completeOrder.addAll(replacements);
        for (int index = 0; index < completeOrder.size(); index++) {
            completeOrder.get(index).markLast(index == completeOrder.size() - 1);
        }

        approverRepository.deleteAll(pendingApprovers);
        approverRepository.flush();
        approverRepository.saveAll(replacements);

        if (detailPolicy.resolve(application) != ApprovalDetailPolicy.DetailMode.JSONLESS_COUNCIL) {
            approvalLineDelegate.replacePendingApproversInDetail(
                    application, completeOrder, replacementUsers);
            approvalLineDelegate.updateApprovalOrder(application, completeOrder);
        }
    }

    private boolean isPending(Cdecim approver) {
        return DecisionStatus.isPendingCode(approver.getItPtlDcdStsC());
    }

    private void validatePendingPermutation(List<Integer> requested, List<Integer> expected) {
        if (requested == null || requested.size() != expected.size()) {
            throw new IllegalArgumentException("미결재 결재자 전체 순서를 보내야 합니다.");
        }
        Set<Integer> requestedSet = new HashSet<>(requested);
        if (requestedSet.size() != requested.size()
                || !requestedSet.equals(new HashSet<>(expected))) {
            throw new IllegalArgumentException("미결재 결재자 순서가 현재 결재선과 일치하지 않습니다.");
        }
    }

    private void validateReplacementApprovers(List<String> approverEnos) {
        if (approverEnos == null || approverEnos.isEmpty()) {
            throw new IllegalArgumentException("미결재 결재자를 한 명 이상 지정해야 합니다.");
        }
        for (String approverEno : approverEnos) {
            if (approverEno == null || approverEno.isBlank()) {
                throw new IllegalArgumentException("결재자 사번은 비어 있을 수 없습니다.");
            }
        }
    }

    private Capplm getInProgressApplication(String apfMngNo) {
        Capplm application =
                applicationRepository
                        .findByIdForUpdate(apfMngNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        if (!ApprovalStatus.IN_PROGRESS.code().equals(application.getItPtlApfPrgStsC())) {
            throw new IllegalStateException("결재중인 신청서만 결재선을 변경할 수 있습니다.");
        }
        return application;
    }

    private List<Cdecim> getApprovers(String apfMngNo) {
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);
        if (approvers.isEmpty())
            throw new IllegalArgumentException("신청서 결재선을 찾을 수 없습니다: " + apfMngNo);
        return approvers;
    }

    private void assertCanManage(List<Cdecim> approvers, String currentEno, boolean isAdmin) {
        if (!isAdmin
                && approvers.stream().noneMatch(value -> currentEno.equals(value.getDcrEno()))) {
            throw new AccessDeniedException("결재선 변경 권한이 없습니다.");
        }
    }
}
