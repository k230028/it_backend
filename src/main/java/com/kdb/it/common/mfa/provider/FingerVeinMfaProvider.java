package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지정맥 BioAgent 결과 공급자이다.
 *
 * <p>브라우저가 전달한 결과는 거래 nonce와 {@code FE00} 코드만 대조한다. 웹 애플리케이션은 BioAgent 클라이언트의 기기 증명이나 서명 자체를 검증할 수
 * 없으므로, 이 결과만으로 기기 소유를 암호학적으로 보장하지는 못한다.
 */
public final class FingerVeinMfaProvider implements MfaProvider {

    private static final String SUCCESS_CODE = "FE00";

    private final ConcurrentHashMap<String, Instant> activeNonces = new ConcurrentHashMap<>();

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        activeNonces.put(context.transactionId(), context.expiresAt());
        return new MfaChallengeData(context.transactionId(), null, context.expiresAt());
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        String nonce = context.startContext().transactionId();
        Instant expiresAt = activeNonces.get(nonce);
        boolean matchingNonce = nonce.equals(context.challengeId()) && expiresAt != null;
        boolean successfulCode = SUCCESS_CODE.equals(normalize(context.verificationValue()));
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            activeNonces.remove(nonce, expiresAt);
            return MfaVerificationResult.failure();
        }
        if (matchingNonce && successfulCode && activeNonces.remove(nonce, expiresAt)) {
            return MfaVerificationResult.success();
        }
        return MfaVerificationResult.failure();
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
