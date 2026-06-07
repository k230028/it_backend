package com.kdb.it.infra.eai.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EaiResultTest {

    @Test
    @DisplayName("success: success=true, skipped=false, 응답 보관")
    void success_setsFlags() {
        EaiResult r = EaiResult.success("OK-RAW");
        assertThat(r.success()).isTrue();
        assertThat(r.skipped()).isFalse();
        assertThat(r.responseRaw()).isEqualTo("OK-RAW");
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    @DisplayName("skipped: 전송 스킵 상태 (success=false, skipped=true)")
    void skipped_setsFlags() {
        EaiResult r = EaiResult.skip();
        assertThat(r.success()).isFalse();
        assertThat(r.skipped()).isTrue();
        assertThat(r.responseRaw()).isNull();
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    @DisplayName("failure: success=false, 오류메시지 보관")
    void failure_setsMessage() {
        EaiResult r = EaiResult.failure("전송 실패: timeout");
        assertThat(r.success()).isFalse();
        assertThat(r.skipped()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("전송 실패: timeout");
    }
}
