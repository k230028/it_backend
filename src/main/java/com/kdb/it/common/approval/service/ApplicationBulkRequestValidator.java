package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 일괄 조회·결재 입력의 서비스 경계 불변식을 검증합니다. */
final class ApplicationBulkRequestValidator {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_APPLICATION_ID_LENGTH = 64;
    private static final int MAX_OPINION_LENGTH = 2000;
    private static final Set<String> ALLOWED_DECISIONS = Set.of("2", "3", "승인", "반려");

    private ApplicationBulkRequestValidator() {}

    /** 일괄 조회 ID를 검증하고 첫 등장 순서로 중복을 제거합니다. */
    static List<String> validateRead(ApplicationDto.BulkGetRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("일괄 조회 요청은 필수입니다.");
        }
        List<String> ids = requireBatch(request.getApfMngNos(), "조회할 신청관리번호");
        for (String id : ids) {
            requireApplicationId(id);
        }
        return ids.stream().distinct().toList();
    }

    /** 일괄 결재 항목을 검증하며 같은 신청서의 중복 처리를 거부합니다. */
    static List<ApplicationDto.ApprovalItem> validateApproval(
            ApplicationDto.BulkApproveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("일괄 결재 요청은 필수입니다.");
        }
        List<ApplicationDto.ApprovalItem> approvals =
                requireBatch(request.getApprovals(), "결재할 신청서");
        Set<String> uniqueIds = new HashSet<>();
        for (ApplicationDto.ApprovalItem item : approvals) {
            if (item == null) {
                throw new IllegalArgumentException("결재 항목은 null일 수 없습니다.");
            }
            String id = item.getApfMngNo();
            requireApplicationId(id);
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("중복 신청관리번호는 일괄 결재할 수 없습니다: " + id);
            }
            if (item.getDcdOpnn() != null && item.getDcdOpnn().length() > MAX_OPINION_LENGTH) {
                throw new IllegalArgumentException("결재의견은 2000자 이하여야 합니다.");
            }
            if (item.getDcdSts() == null
                    || item.getDcdSts().isBlank()
                    || !ALLOWED_DECISIONS.contains(item.getDcdSts())) {
                throw new IllegalArgumentException("결재상태는 승인 또는 반려여야 합니다.");
            }
        }
        return List.copyOf(approvals);
    }

    private static <T> List<T> requireBatch(List<T> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 목록은 1건 이상이어야 합니다.");
        }
        if (values.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(fieldName + " 목록은 100건 이하여야 합니다.");
        }
        return values;
    }

    private static void requireApplicationId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("신청관리번호는 필수입니다.");
        }
        if (id.length() > MAX_APPLICATION_ID_LENGTH) {
            throw new IllegalArgumentException("신청관리번호는 64자 이하여야 합니다.");
        }
    }
}
