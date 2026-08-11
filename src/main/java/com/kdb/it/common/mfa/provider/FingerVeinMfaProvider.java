package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * 지정맥 BioAgent 결과 공급자이다.
 *
 * <p>현재 구현은 브라우저가 전달한 결과를 거래 nonce와 {@code FE00} 코드로만 대조한다. 이 대조만으로는 기기 소유를 암호학적으로 보장하지 못한다.
 *
 * <p><b>미구현 보강</b>: {@code mfa.md}의 「지정맥인증 연계 보안방안」은 서버가 독립 검증할 수 있는 해시 규격을 정의한다. 서버가 6자리 랜덤키를
 * 발급하고, 지정맥인증 서버가 {@code 년월일 + 사번 + 랜덤키 + 검증값(SUCC|FAIL) + 고정키}를 SHA-256으로 3회 해시해 돌려주면 업무 서버가 같은 값을
 * 만들어 비교하는 방식이다. 이를 도입하면 클라이언트 결과 코드에만 의존하지 않게 되므로, 적용 전까지만 위 한계가 유효하다. 상세와 미확정 사항은 {@code
 * ../TASK.md}의 SEC-11을 따른다.
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
