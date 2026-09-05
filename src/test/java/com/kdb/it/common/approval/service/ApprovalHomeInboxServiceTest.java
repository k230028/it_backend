package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.approval.dto.ApprovalHomeInboxDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 전자결재 Home 결재함·기안함 조회 서비스 단위 테스트 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalHomeInboxService 단위 테스트")
class ApprovalHomeInboxServiceTest {

    @Mock private ApplicationRepository applicationRepository;

    @InjectMocks private ApprovalHomeInboxService approvalHomeInboxService;

    @Test
    @DisplayName("getHomeInbox: 인증 사용자의 결재함과 기안함을 상태별 전체 목록으로 분류한다")
    void getHomeInbox_사용자별상태분류() {
        ApplicationRepository.HomeInboxRow approvalPending =
                homeRow("APF-005", "승인 대기", "김기안", "1", 1, 0, null, 1);
        ApplicationRepository.HomeInboxRow approvalCompleted =
                homeRow("APF-004", "승인 완료", "박기안", "2", 0, 1, null, 0);
        ApplicationRepository.HomeInboxRow draftInProgress =
                homeRow("APF-003", "내 진행 문서", "홍길동", "1", 0, 0, "IN_PROGRESS", 0);
        ApplicationRepository.HomeInboxRow draftCompleted =
                homeRow("APF-002", "내 완료 문서", "홍길동", "2", 0, 0, "COMPLETED", 0);
        ApplicationRepository.HomeInboxRow draftRejected =
                homeRow("APF-001", "내 반려 문서", "홍길동", "3", 0, 0, "REJECTED", 0);
        given(applicationRepository.findHomeInboxRowsByEno("E10001"))
                .willReturn(
                        List.of(
                                approvalPending,
                                approvalCompleted,
                                draftInProgress,
                                draftCompleted,
                                draftRejected));

        ApprovalHomeInboxDto.Response result = approvalHomeInboxService.getHomeInbox("E10001");

        assertThat(result.approvalPending())
                .extracting(ApprovalHomeInboxDto.Item::apfMngNo)
                .containsExactly("APF-005");
        assertThat(result.approvalPending().getFirst().actionable()).isTrue();
        assertThat(result.approvalCompleted())
                .extracting(ApprovalHomeInboxDto.Item::apfMngNo)
                .containsExactly("APF-004");
        assertThat(result.draftInProgress())
                .extracting(ApprovalHomeInboxDto.Item::apfMngNo)
                .containsExactly("APF-003");
        assertThat(result.draftCompleted())
                .extracting(ApprovalHomeInboxDto.Item::apfMngNo)
                .containsExactly("APF-002");
        assertThat(result.draftRejected())
                .extracting(ApprovalHomeInboxDto.Item::apfMngNo)
                .containsExactly("APF-001");
    }

    @Test
    @DisplayName("getHomeInbox: 인증 사번이 비어 있으면 빈 목록이 아닌 입력 오류로 구분한다")
    void getHomeInbox_사번없음_예외() {
        assertThatThrownBy(() -> approvalHomeInboxService.getHomeInbox(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사번");
    }

    private ApplicationRepository.HomeInboxRow homeRow(
            String id,
            String title,
            String requesterName,
            String statusCode,
            int approvalPending,
            int approvalCompleted,
            String draftCategory,
            int actionable) {
        ApplicationRepository.HomeInboxRow row = mock(ApplicationRepository.HomeInboxRow.class);
        given(row.getApfMngNo()).willReturn(id);
        given(row.getTitle()).willReturn(title);
        given(row.getRequesterName()).willReturn(requesterName);
        given(row.getRequestedAt()).willReturn(LocalDate.of(2026, 9, 1).atStartOfDay());
        given(row.getStatusCode()).willReturn(statusCode);
        given(row.getApprovalPending()).willReturn(approvalPending);
        given(row.getApprovalCompleted()).willReturn(approvalCompleted);
        given(row.getDraftCategory()).willReturn(draftCategory);
        given(row.getActionable()).willReturn(actionable);
        return row;
    }
}
