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
                                        new MfaVerifyContext(context, "transaction-1", ""))
                                .verified())
                .isTrue();
    }

    @Test
    void fingerVein_acceptsOnlyNormalizedFe00ForTheMatchingTransactionNonce() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.FINGER_VEIN, new FingerVeinMfaProvider()));
        MfaStartContext context =
                new MfaStartContext(
                        "nonce-1",
                        "10000001",
                        MfaPurpose.LOGIN,
                        Instant.parse("2099-01-01T00:00:00Z"));
        registry.start(MfaMethod.FINGER_VEIN, context);

        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(context, "nonce-1", " fe00 "))
                                .verified())
                .isTrue();
        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(context, "different", "FE00"))
                                .verified())
                .isFalse();
    }

    @Test
    void fingerVein_rejectsExpiredAndAlreadyConsumedNonces() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.FINGER_VEIN, new FingerVeinMfaProvider()));
        MfaStartContext expired =
                new MfaStartContext("expired", "10000001", MfaPurpose.LOGIN, Instant.EPOCH);
        MfaStartContext active =
                new MfaStartContext(
                        "active",
                        "10000001",
                        MfaPurpose.LOGIN,
                        Instant.parse("2099-01-01T00:00:00Z"));
        registry.start(MfaMethod.FINGER_VEIN, expired);
        registry.start(MfaMethod.FINGER_VEIN, active);

        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(expired, "expired", "FE00"))
                                .verified())
                .isFalse();
        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(active, "active", "FE00"))
                                .verified())
                .isTrue();
        assertThat(
                        registry.verify(
                                        MfaMethod.FINGER_VEIN,
                                        new MfaVerifyContext(active, "active", "FE00"))
                                .verified())
                .isFalse();
    }
}
