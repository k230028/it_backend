package com.kdb.it.common.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalStatusTest {

    @Test
    @DisplayName("공통코드 0은 수기등록 상태로 해석한다")
    void resolvesManualRegistrationCode() {
        ApprovalStatus status = ApprovalStatus.ofCode("0");

        assertThat(status.code()).isEqualTo("0");
        assertThat(status.label()).isEqualTo("수기등록");
    }
}
