package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대기 중인 결재 순번의 담당자 변경을 처리합니다. */
@Service
@RequiredArgsConstructor
public class PendingApproverService {

    private final ApproverRepository approverRepository;
    private final UserRepository userRepository;

    /** 관리자 또는 해당 결재선 직원이 미결재 순번의 결재자를 변경합니다. */
    @Transactional
    public void changePendingApprover(
            String apfMngNo,
            int dcdSqn,
            String newApproverEno,
            String currentEno,
            boolean isAdmin) {
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(apfMngNo);
        if (approvers.isEmpty()) {
            throw new IllegalArgumentException("신청서 결재선을 찾을 수 없습니다: " + apfMngNo);
        }
        boolean isLineMember = approvers.stream().anyMatch(a -> currentEno.equals(a.getDcrEno()));
        if (!isAdmin && !isLineMember) {
            throw new AccessDeniedException("결재자 변경 권한이 없습니다.");
        }
        Cdecim target =
                approvers.stream()
                        .filter(a -> Integer.valueOf(dcdSqn).equals(a.getDcrSqnSno()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("결재 순번을 찾을 수 없습니다: " + dcdSqn));
        if (!DecisionStatus.isPendingCode(target.getItPtlDcdStsC())) {
            throw new IllegalStateException("미결재 상태인 결재자만 변경할 수 있습니다.");
        }
        if (newApproverEno == null
                || newApproverEno.isBlank()
                || userRepository.findById(newApproverEno).isEmpty()) {
            throw new IllegalArgumentException("변경할 직원을 찾을 수 없습니다.");
        }
        target.changeApprover(newApproverEno);
    }
}
