package com.kdb.it.common.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalStatusTest {

    @Test
    @DisplayName("공통코드 0은 작성완료 상태로 해석한다")
    void resolvesDraftedCode() {
        ApprovalStatus status = ApprovalStatus.ofCode("0");

        assertThat(status).isEqualTo(ApprovalStatus.DRAFTED);
        assertThat(status.label()).isEqualTo("작성완료");
        assertThat(status.isTerminated()).isFalse();
    }

    @Test
    @DisplayName("수기등록은 코드 9로 옮겨졌다")
    void resolvesManualRegistrationCode() {
        ApprovalStatus status = ApprovalStatus.ofCode("9");

        assertThat(status).isEqualTo(ApprovalStatus.MANUAL);
        assertThat(status.label()).isEqualTo("수기등록");
        assertThat(ApprovalStatus.ofLabel("수기등록").code()).isEqualTo("9");
    }
}
