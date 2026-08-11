package com.kdb.it.common.mfa.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * 지정맥 BioAgent 결과 공급자이다.
 *
 * <p>{@code mfa.md}의 「지정맥인증 연계 보안방안」을 따른다. 서버가 거래마다 6자리 랜덤키를 발급하고, 지정맥인증 서버가 {@code 년월일 + 사번 + 랜덤키
 * + 검증값 + 고정키}를 SHA-256으로 3회 해시한 값을 에이전트를 통해 돌려준다. 이 공급자는 같은 값을 직접 만들어 비교하므로, 클라이언트가 보낸 결과 코드를 그대로
 * 믿지 않는다.
 *
 * <p>검증값은 인증 성공이 {@code SUCC}, 실패가 {@code FAIL}이다. 성공 해시와 일치할 때만 통과시키므로 실패 해시나 결과 코드 문자열은 거부된다.
 *
 * <p>년월일은 서버 기준 날짜다. 랜덤키는 검증 성공·실패와 무관하게 1회 사용 후 폐기해 재전송을 막는다.
 */
public final class FingerVeinMfaProvider implements MfaProvider {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SUCCESS_VERDICT = "SUCC";
    private static final int RANDOM_KEY_DIGITS = 6;
    private static final int HASH_ROUNDS = 3;
    private static final int DEFAULT_MAX_PENDING_NONCES = 1_024;

    private final String fixedKey;
    private final Clock clock;
    private final int maxPendingNonces;
    private final SecureRandom secureRandom = new SecureRandom();
    private final LinkedHashMap<String, PendingScan> activeScans = new LinkedHashMap<>();

    /**
     * 기본 최대 대기 거래 수를 사용하는 지정맥 공급자를 생성한다.
     *
     * @param fixedKey 지정맥인증 서버와 공유하는 고정키
     * @param clock 년월일을 만들 서버 시계
     */
    public FingerVeinMfaProvider(String fixedKey, Clock clock) {
        this(fixedKey, clock, DEFAULT_MAX_PENDING_NONCES);
    }

    FingerVeinMfaProvider(String fixedKey, Clock clock, int maxPendingNonces) {
        if (fixedKey == null || fixedKey.isBlank()) {
            throw new IllegalArgumentException("지정맥 고정키는 필수입니다.");
        }
        if (maxPendingNonces < 1) {
            throw new IllegalArgumentException("대기 거래 최대 수는 1 이상이어야 합니다.");
        }
        this.fixedKey = fixedKey;
        this.clock = clock;
        this.maxPendingNonces = maxPendingNonces;
    }

    @Override
    public synchronized MfaChallengeData start(MfaStartContext context) {
        removeExpiredScans();
        if (!activeScans.containsKey(context.transactionId())
                && activeScans.size() >= maxPendingNonces) {
            Iterator<String> iterator = activeScans.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        PendingScan scan = new PendingScan(newRandomKey(), context.eno(), context.expiresAt());
        activeScans.put(context.transactionId(), scan);
        return new MfaChallengeData(
                context.transactionId(), null, scan.randomKey(), context.expiresAt());
    }

    @Override
    public synchronized MfaVerificationResult verify(MfaVerifyContext context) {
        removeExpiredScans();
        String transactionId = context.startContext().transactionId();
        PendingScan scan = activeScans.get(transactionId);
        if (scan == null || !transactionId.equals(context.challengeId())) {
            return MfaVerificationResult.failure();
        }
        // 성공·실패와 무관하게 랜덤키를 즉시 폐기해 같은 해시를 다시 쓸 수 없게 한다.
        activeScans.remove(transactionId);

        String expected = successHash(scan.eno(), scan.randomKey());
        return MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        normalize(context.verificationValue()).getBytes(StandardCharsets.US_ASCII))
                ? MfaVerificationResult.success()
                : MfaVerificationResult.failure();
    }

    /** 규격의 성공 검증값으로 기대 해시를 만든다. */
    private String successHash(String eno, String randomKey) {
        String value =
                LocalDate.now(clock).format(DAY) + eno + randomKey + SUCCESS_VERDICT + fixedKey;
        String hashed = value;
        for (int round = 0; round < HASH_ROUNDS; round++) {
            hashed = sha256(hashed);
        }
        return hashed;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String newRandomKey() {
        StringBuilder value = new StringBuilder(RANDOM_KEY_DIGITS);
        for (int index = 0; index < RANDOM_KEY_DIGITS; index++) {
            value.append(secureRandom.nextInt(10));
        }
        return value.toString();
    }

    private void removeExpiredScans() {
        Instant now = Instant.now(clock);
        activeScans.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 거래별로 보관하는 지정맥 스캔 대기 정보다. */
    private record PendingScan(String randomKey, String eno, Instant expiresAt) {}
}
