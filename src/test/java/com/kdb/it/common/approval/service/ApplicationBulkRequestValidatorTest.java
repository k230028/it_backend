package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.approval.dto.ApplicationDto;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 일괄 요청 서비스 경계의 입력 상한과 null 방어 규칙을 검증합니다. */
class ApplicationBulkRequestValidatorTest {

    @Test
    @DisplayName("조회 ID는 검증 후 첫 등장 순서로 중복 제거한다")
    void validateRead_deduplicatesInEncounterOrder() {
        var request = readRequest(List.of("APF-2", "APF-1", "APF-2"));

        assertThat(ApplicationBulkRequestValidator.validateRead(request))
                .containsExactly("APF-2", "APF-1");
    }

    @Test
    @DisplayName("조회 요청이나 목록이 null·빈 값·100건 초과이면 거부한다")
    void validateRead_rejectsInvalidContainers() {
        var nullList = new ApplicationDto.BulkGetRequest();

        assertThatThrownBy(() -> ApplicationBulkRequestValidator.validateRead(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationBulkRequestValidator.validateRead(nullList))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ApplicationBulkRequestValidator.validateRead(readRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateRead(
                                        readRequest(Collections.nCopies(101, "APF-1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조회 ID가 null·공백·64자 초과이면 거부한다")
    void validateRead_rejectsInvalidIds() {
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateRead(
                                        readRequest(Collections.singletonList(null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateRead(
                                        readRequest(List.of("   "))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateRead(
                                        readRequest(List.of("A".repeat(65)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "3", "승인", "반려"})
    @DisplayName("일괄 결재는 네 가지 승인·반려 표현만 허용한다")
    void validateApproval_acceptsAllowedDecisions(String status) {
        var request = approvalRequest(List.of(approval("APF-1", status, null)));

        assertThat(ApplicationBulkRequestValidator.validateApproval(request)).hasSize(1);
    }

    @Test
    @DisplayName("결재 요청이나 목록이 null·빈 값·100건 초과이면 거부한다")
    void validateApproval_rejectsInvalidContainers() {
        var nullList = new ApplicationDto.BulkApproveRequest();

        assertThatThrownBy(() -> ApplicationBulkRequestValidator.validateApproval(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationBulkRequestValidator.validateApproval(nullList))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(
                                                Collections.nCopies(
                                                        101, approval("APF-1", "2", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결재 항목의 null·ID·의견 상한을 검증한다")
    void validateApproval_rejectsInvalidItemsAndLengths() {
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(Collections.singletonList(null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(List.of(approval(" ", "2", null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(
                                                List.of(approval("A".repeat(65), "2", null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(
                                                List.of(approval("APF-1", "2", "가".repeat(2001))))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결재 상태의 null·공백·허용 외 값과 중복 ID를 거부한다")
    void validateApproval_rejectsInvalidDecisionsAndDuplicateIds() {
        for (String status : new String[] {null, " ", "완료"}) {
            assertThatThrownBy(
                            () ->
                                    ApplicationBulkRequestValidator.validateApproval(
                                            approvalRequest(
                                                    List.of(approval("APF-1", status, null)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(
                        () ->
                                ApplicationBulkRequestValidator.validateApproval(
                                        approvalRequest(
                                                List.of(
                                                        approval("APF-1", "2", null),
                                                        approval("APF-1", "3", null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    private static ApplicationDto.BulkGetRequest readRequest(List<String> ids) {
        var request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(ids);
        return request;
    }

    private static ApplicationDto.BulkApproveRequest approvalRequest(
            List<ApplicationDto.ApprovalItem> approvals) {
        var request = new ApplicationDto.BulkApproveRequest();
        request.setApprovals(approvals);
        return request;
    }

    private static ApplicationDto.ApprovalItem approval(
            String applicationId, String status, String opinion) {
        var item = new ApplicationDto.ApprovalItem();
        item.setApfMngNo(applicationId);
        item.setDcdSts(status);
        item.setDcdOpnn(opinion);
        return item;
    }
}
