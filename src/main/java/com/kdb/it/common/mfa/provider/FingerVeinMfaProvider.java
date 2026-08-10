package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * 지정맥 BioAgent 결과 공급자이다.
 *
 * <p>브라우저가 전달한 결과는 거래 nonce와 {@code FE00} 코드만 대조한다. 웹 애플리케이션은 BioAgent 클라이언트의 기기 증명이나 서명 자체를 검증할 수
 * 없으므로, 이 결과만으로 기기 소유를 암호학적으로 보장하지는 못한다.
 */
public final class FingerVeinMfaProvider implements MfaProvider {

    private static final String SUCCESS_CODE = "FE00";
    private static final int DEFAULT_MAX_PENDING_NONCES = 1_024;

    private final int maxPendingNonces;
    private final LinkedHashMap<String, Instant> activeNonces = new LinkedHashMap<>();

    /** 기본 최대 대기 nonce 수를 사용하는 지정맥 공급자를 생성한다. */
    public FingerVeinMfaProvider() {
        this(DEFAULT_MAX_PENDING_NONCES);
    }

    FingerVeinMfaProvider(int maxPendingNonces) {
        if (maxPendingNonces < 1) {
            throw new IllegalArgumentException("대기 nonce 최대 수는 1 이상이어야 합니다.");
        }
        this.maxPendingNonces = maxPendingNonces;
    }

    @Override
    public synchronized MfaChallengeData start(MfaStartContext context) {
        removeExpiredNonces();
        if (!activeNonces.containsKey(context.transactionId())
                && activeNonces.size() >= maxPendingNonces) {
            Iterator<String> iterator = activeNonces.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        activeNonces.put(context.transactionId(), context.expiresAt());
        return new MfaChallengeData(context.transactionId(), null, context.expiresAt());
    }

    @Override
    public synchronized MfaVerificationResult verify(MfaVerifyContext context) {
        removeExpiredNonces();
        String nonce = context.startContext().transactionId();
        Instant expiresAt = activeNonces.get(nonce);
        boolean matchingNonce = nonce.equals(context.challengeId()) && expiresAt != null;
        boolean successfulCode = SUCCESS_CODE.equals(normalize(context.verificationValue()));
        if (matchingNonce && successfulCode) {
            activeNonces.remove(nonce);
            return MfaVerificationResult.success();
        }
        return MfaVerificationResult.failure();
    }

    private void removeExpiredNonces() {
        Instant now = Instant.now();
        activeNonces.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
