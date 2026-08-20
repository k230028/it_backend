package com.kdb.it.common.mfa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 영속 계층 매핑 전용 restore() 팩토리와 확장된 상태값의 계약을 고정한다. */
class MfaTransactionRestoreTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-20T00:01:30Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-08-20T00:00:30Z");

    @Test
    void restore는_저장된_필드를_그대로_복원한다() {
        MfaTransaction restored =
                MfaTransaction.restore(
                        "token-hash",
                        "E10001",
                        MfaPurpose.APPROVAL,
                        MfaMethod.FIDO,
                        EXPIRES_AT,
                        "try-hash",
                        "12345678901234567890",
                        "proof-hash",
                        MfaTransactionStatus.CONSUMED,
                        VERIFIED_AT,
                        2);

        assertThat(restored.tokenHash()).isEqualTo("token-hash");
        assertThat(restored.eno()).isEqualTo("E10001");
        assertThat(restored.purpose()).isEqualTo(MfaPurpose.APPROVAL);
        assertThat(restored.method()).isEqualTo(MfaMethod.FIDO);
        assertThat(restored.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(restored.providerChallengeHash()).isEqualTo("try-hash");
        assertThat(restored.svcTrId()).isEqualTo("12345678901234567890");
        assertThat(restored.proofHash()).isEqualTo("proof-hash");
        assertThat(restored.status()).isEqualTo(MfaTransactionStatus.CONSUMED);
        assertThat(restored.verifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(restored.failureCount()).isEqualTo(2);
    }

    @Test
    void restore는_취소_상태를_검증시각_없이_복원할_수_있다() {
        MfaTransaction restored =
                MfaTransaction.restore(
                        "token-hash-2",
                        "E10001",
                        MfaPurpose.LOGIN,
                        MfaMethod.MOTP,
                        EXPIRES_AT,
                        null,
                        null,
                        null,
                        MfaTransactionStatus.CANCELLED,
                        null,
                        0);

        assertThat(restored.status()).isEqualTo(MfaTransactionStatus.CANCELLED);
        assertThat(restored.verifiedAt()).isNull();
        assertThat(restored.svcTrId()).isNull();
    }
}
