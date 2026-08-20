package com.kdb.it.common.mfa.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MfaProviderRegistryTest {

    @Test
    void mockProvider_requiresExplicitVerifyBeforeItSucceeds() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.FINGER_VEIN, new MockMfaProvider()));
        MfaStartContext context =
                new MfaStartContext(
                        "transaction-1",
                        "10000001",
                        MfaPurpose.LOGIN,
                        Instant.parse("2026-08-10T12:00:00Z"));

        MfaChallengeData challenge = registry.start(MfaMethod.FINGER_VEIN, context);

        assertThat(challenge.challengeId()).isEqualTo("transaction-1");
        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(context, "transaction-1", "", null))
                                .verified())
                .isTrue();
    }
}
