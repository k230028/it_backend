package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전자결재 대시보드와 배지 집계를 인증 사용자의 부서 범위로 제공합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDashboardService {

    private final ApplicationRepository applicationRepository;

    /** 부서 통계와 본인 결재 대기 목록을 반환합니다. 일반 사용자의 요청 조건은 인증 주체로 덮어씁니다. */
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
                                        row -> {
                                            String requestedAt = row.rqsDt();
                                            LocalDate requestedDate =
                                                    requestedAt == null
                                                            ? LocalDate.now()
                                                            : LocalDate.parse(requestedAt);
                                            return ApplicationDto.PendingItem.builder()
                                                    .apfMngNo(row.apfDcmNo())
                                                    .title(row.title())
                                                    .requesterName(row.usrNm())
                                                    .requestedAt(requestedAt)
                                                    .urgency(
                                                            requestedDate.isBefore(threeDaysAgo)
                                                                    ? "urgent"
                                                                    : "normal")
                                                    .build();
                                        })
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
