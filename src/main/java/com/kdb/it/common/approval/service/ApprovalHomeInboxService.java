package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApprovalHomeInboxDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전자결재 Home 결재함·기안함 조회 서비스
 *
 * <p>단일 조회 쿼리 한 건을 상태별 목록으로 분류하는 읽기 전용 책임만 담당합니다. 신청서 등록·결재·회수 등 상태를 바꾸는 로직은 {@link
 * ApplicationService}가 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalHomeInboxService {

    /** 신청서 마스터 데이터 접근 리포지토리 (TPRMPP_CAPPLM) */
    private final ApplicationRepository applicationRepository;

    /** 기안함 분류값 — 결재 진행 중 */
    private static final String DRAFT_IN_PROGRESS = "IN_PROGRESS";

    /** 기안함 분류값 — 결재 완료 */
    private static final String DRAFT_COMPLETED = "COMPLETED";

    /** 기안함 분류값 — 반려 */
    private static final String DRAFT_REJECTED = "REJECTED";

    /**
     * 인증 사용자의 전자결재 Home 결재함·기안함 전체 목록을 상태별로 분류합니다.
     *
     * <p>한 신청서가 결재함과 기안함에 동시에 속할 수 있으므로 같은 항목이 여러 목록에 들어갈 수 있습니다.
     *
     * @param user 인증 사용자
     * @return 결재함·기안함 상태별 목록
     * @throws IllegalArgumentException 사번이 null이거나 공백일 때
     */
    public ApprovalHomeInboxDto.Response getHomeInbox(CustomUserDetails user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("인증 정보가 필요합니다.");
        }
        String eno = user.getEno();
        if (eno == null || eno.isBlank()) {
            throw new IllegalArgumentException("사용자 사번이 필요합니다.");
        }
        String departmentCode = user.getBbrC() == null ? "" : user.getBbrC().trim();
        if (departmentCode.isBlank()) {
            return new ApprovalHomeInboxDto.Response(
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<ApprovalHomeInboxDto.Item> approvalPending = new ArrayList<>();
        List<ApprovalHomeInboxDto.Item> approvalCompleted = new ArrayList<>();
        List<ApprovalHomeInboxDto.Item> draftInProgress = new ArrayList<>();
        List<ApprovalHomeInboxDto.Item> draftCompleted = new ArrayList<>();
        List<ApprovalHomeInboxDto.Item> draftRejected = new ArrayList<>();

        for (ApplicationRepository.HomeInboxRow row :
                applicationRepository.findHomeInboxRowsByEnoAndBbrC(eno, departmentCode)) {
            ApprovalHomeInboxDto.Item item = toItem(row);
            if (row.getApprovalPending() == 1) approvalPending.add(item);
            if (row.getApprovalCompleted() == 1) approvalCompleted.add(item);
            if (DRAFT_IN_PROGRESS.equals(row.getDraftCategory())) draftInProgress.add(item);
            if (DRAFT_COMPLETED.equals(row.getDraftCategory())) draftCompleted.add(item);
            if (DRAFT_REJECTED.equals(row.getDraftCategory())) draftRejected.add(item);
        }

        return new ApprovalHomeInboxDto.Response(
                approvalPending, approvalCompleted, draftInProgress, draftCompleted, draftRejected);
    }

    /** 조회 행을 Home 목록 항목으로 변환합니다. 신청일시가 없으면 신청일자를 null로 둡니다. */
    private ApprovalHomeInboxDto.Item toItem(ApplicationRepository.HomeInboxRow row) {
        return new ApprovalHomeInboxDto.Item(
                row.getApfMngNo(),
                row.getTitle(),
                row.getRequestNote(),
                row.getRequesterName(),
                row.getRequestedAt() == null ? null : row.getRequestedAt().toLocalDate(),
                row.getStatusCode(),
                ApprovalStatus.ofCode(row.getStatusCode()).label(),
                row.getActionable() == 1);
    }
}
