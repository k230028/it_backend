package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전자결재 대시보드와 배지 집계를 인증 사용자의 부서 범위로 제공합니다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDashboardService {

    /** 결재 대기 긴급도: 신청일자가 3일을 초과해 지난 건. */
    static final String URGENCY_URGENT = "urgent";

    /** 결재 대기 긴급도: 3일 이내 신청 건. */
    static final String URGENCY_NORMAL = "normal";

    /** 결재 대기 긴급도: 신청일자가 없어 판정할 수 없는 건. 정상으로 숨기지 않고 드러낸다. */
    static final String URGENCY_UNKNOWN = "unknown";

    private final ApplicationRepository applicationRepository;

    /**
     * 부서 통계와 본인 결재 대기 목록을 반환합니다. 일반 사용자의 요청 조건은 인증 주체로 덮어씁니다.
     *
     * <p>결재 대기 건의 신청일자({@code RQS_DT})가 비어 있으면 오늘로 간주하지 않고 {@code requestedAt=null}, {@code
     * urgency=unknown}으로 내리며 WARN 로그에 신청서번호를 남깁니다.
     */
    public ApplicationDto.DashboardResponse getDashboard(
            String requestedBbrC, String requestedEno, CustomUserDetails user) {
        DashboardScope scope = dashboardScope(requestedBbrC, requestedEno, user);
        boolean departmentLimited = !user.isAdmin();
        int pendingCount =
                departmentLimited
                        ? applicationRepository.countPendingByEnoAndBbrC(scope.eno(), scope.bbrC())
                        : applicationRepository.countPendingByEno(scope.eno());
        int inProgressCount =
                departmentLimited
                        ? applicationRepository.countInProgressByEnoAndBbrC(
                                scope.eno(), scope.bbrC())
                        : applicationRepository.countInProgressByEno(scope.eno());
        int rejectedCount =
                departmentLimited
                        ? applicationRepository.countRejectedByEnoAndBbrC(scope.eno(), scope.bbrC())
                        : applicationRepository.countRejectedByEno(scope.eno());

        List<ApplicationDto.MonthlyCount> monthlyTrend =
                applicationRepository.findMonthlyTrendRowsByBbrC(scope.bbrC()).stream()
                        .map(
                                row ->
                                        ApplicationDto.MonthlyCount.builder()
                                                .month(row.label())
                                                .count(Math.toIntExact(row.count()))
                                                .build())
                        .toList();

        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        List<ApplicationDto.PendingItem> pendingList =
                (departmentLimited
                                ? applicationRepository.findPendingRowsByEnoAndBbrC(
                                        scope.eno(), scope.bbrC())
                                : applicationRepository.findPendingRowsByEno(scope.eno()))
                        .stream()
                                .map(
                                        row ->
                                                ApplicationDto.PendingItem.builder()
                                                        .apfMngNo(row.apfDcmNo())
                                                        .title(row.title())
                                                        .requesterName(row.usrNm())
                                                        .requestedAt(row.rqsDt())
                                                        .urgency(
                                                                urgency(
                                                                        row.apfDcmNo(),
                                                                        row.rqsDt(),
                                                                        threeDaysAgo))
                                                        .build())
                                .toList();

        return ApplicationDto.DashboardResponse.builder()
                .pendingCount(pendingCount)
                .inProgressCount(inProgressCount)
                .monthlyCompletedCount(
                        applicationRepository.countMonthlyCompletedByBbrC(scope.bbrC()))
                .rejectedCount(rejectedCount)
                .monthlyTrend(monthlyTrend)
                .pendingList(pendingList)
                .build();
    }

    /**
     * 결재 대기 건의 긴급도를 판정합니다.
     *
     * @param applicationId 신청서관리번호 (경고 로그 식별용)
     * @param requestedAt 신청일자 (YYYY-MM-DD), 누락 가능
     * @param threeDaysAgo 긴급 판정 기준일
     * @return {@code urgent}·{@code normal}, 신청일자가 없으면 {@code unknown}
     */
    private static String urgency(
            String applicationId, String requestedAt, LocalDate threeDaysAgo) {
        if (requestedAt == null) {
            log.warn("결재 대기 신청서의 신청일자(RQS_DT)가 비어 있어 긴급도를 판정할 수 없습니다: apfMngNo={}", applicationId);
            return URGENCY_UNKNOWN;
        }
        return LocalDate.parse(requestedAt).isBefore(threeDaysAgo)
                ? URGENCY_URGENT
                : URGENCY_NORMAL;
    }

    /** 결재 대기와 기안 진행 중 건수를 반환합니다. 일반 사용자의 요청 조건은 인증 주체로 덮어씁니다. */
    public ApplicationDto.ApprovalBadgeCountResponse getApprovalBadgeCount(
            String requestedBbrC, String requestedEno, CustomUserDetails user) {
        DashboardScope scope = dashboardScope(requestedBbrC, requestedEno, user);
        boolean departmentLimited = !user.isAdmin();
        return ApplicationDto.ApprovalBadgeCountResponse.builder()
                .pendingCount(
                        departmentLimited
                                ? applicationRepository.countPendingByEnoAndBbrC(
                                        scope.eno(), scope.bbrC())
                                : applicationRepository.countPendingByEno(scope.eno()))
                .inProgressCount(
                        departmentLimited
                                ? applicationRepository.countInProgressByEnoAndBbrC(
                                        scope.eno(), scope.bbrC())
                                : applicationRepository.countInProgressByEno(scope.eno()))
                .build();
    }

    private static DashboardScope dashboardScope(
            String requestedBbrC, String requestedEno, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 필요합니다.");
        }
        if (user.isAdmin()) {
            return new DashboardScope(requestedBbrC, requestedEno);
        }
        if (user.getBbrC() == null
                || user.getBbrC().isBlank()
                || user.getEno() == null
                || user.getEno().isBlank()) {
            throw new AccessDeniedException("사용자 부서와 사번을 확인할 수 없습니다.");
        }
        return new DashboardScope(user.getBbrC(), user.getEno());
    }

    private record DashboardScope(String bbrC, String eno) {}
}
