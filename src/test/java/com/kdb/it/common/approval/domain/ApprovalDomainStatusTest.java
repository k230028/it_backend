package com.kdb.it.common.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결재 상태 enum 단위 테스트
 *
 * <p>코드·라벨 변환, 유효성 검사와 종료 상태 분기를 검증합니다.
 */
class ApprovalDomainStatusTest {

    @Test
    @DisplayName("DecisionStatus: 코드와 라벨로 상태를 조회하고 미지정 값은 거부한다")
    void decisionStatus_코드및라벨조회() {
        assertThat(DecisionStatus.ofCode("002")).isEqualTo(DecisionStatus.APPROVED);
        assertThat(DecisionStatus.ofCode("0")).isEqualTo(DecisionStatus.PENDING);
        assertThat(DecisionStatus.ofCode("1")).isEqualTo(DecisionStatus.PENDING);
        assertThat(DecisionStatus.ofLabel("회수무효")).isEqualTo(DecisionStatus.INVALIDATED);
        assertThat(DecisionStatus.REJECTED.code()).isEqualTo("3");
        assertThat(DecisionStatus.REJECTED.label()).isEqualTo("반려");
        assertThat(DecisionStatus.isPendingCode("0")).isTrue();
        assertThat(DecisionStatus.isApprovedCode("2")).isTrue();

        assertThatThrownBy(() -> DecisionStatus.ofCode("999"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionStatus.ofLabel("미지정"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ApprovalStatus: 변환과 라벨 존재 여부를 판단한다")
    void approvalStatus_변환및라벨검증() {
        assertThat(ApprovalStatus.ofCode("1")).isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(ApprovalStatus.ofLabel("반려")).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(ApprovalStatus.RECALLED.code()).isEqualTo("4");
        assertThat(ApprovalStatus.RECALLED.label()).isEqualTo("회수");
        assertThat(ApprovalStatus.hasLabel("결재완료")).isTrue();
        assertThat(ApprovalStatus.hasLabel("미지정")).isFalse();

        assertThatThrownBy(() -> ApprovalStatus.ofCode("999"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalStatus.ofLabel("미지정"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalStatus.ofCode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> ApprovalStatus.ofLabel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThat(ApprovalStatus.hasLabel(null)).isFalse();
    }

    @Test
    @DisplayName("ApprovalStatus: 진행 중 상태만 종료 상태가 아니다")
    void approvalStatus_종료여부판단() {
        assertThat(ApprovalStatus.IN_PROGRESS.isTerminated()).isFalse();
        assertThat(ApprovalStatus.COMPLETED.isTerminated()).isTrue();
        assertThat(ApprovalStatus.REJECTED.isTerminated()).isTrue();
        assertThat(ApprovalStatus.RECALLED.isTerminated()).isTrue();
    }
}
