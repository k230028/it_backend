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

/**
 * 지정맥 BioAgent 결과 공급자이다.
 *
 * <p>{@code mfa.md}의 「지정맥인증 연계 보안방안」을 따른다. 서버가 거래마다 6자리 랜덤키를 발급하고, 지정맥인증 서버가 {@code 년월일 + 사번 + 랜덤키
 * + 검증값 + 고정키}를 SHA-256으로 3회 해시한 값을 에이전트를 통해 돌려준다. 이 공급자는 같은 값을 직접 만들어 비교하므로, 클라이언트가 보낸 결과 코드를 그대로
 * 믿지 않는다.
 *
 * <p>검증값은 인증 성공이 {@code SUCC}, 실패가 {@code FAIL}이다. 성공 해시와 일치할 때만 통과시키므로 실패 해시나 결과 코드 문자열은 거부된다.
 *
 * <p>년월일은 서버 기준 날짜다. 랜덤키는 인스턴스 로컬 메모리에 두지 않고 {@link MfaChallengeData#providerTransactionId()}로 돌려주어
 * {@code MfaService}가 거래 저장소({@code APN_CER_SVC_TR_NO})에 남기며, 검증 때 {@link
 * MfaVerifyContext#providerTransactionId()}로 다시 받는다. 따라서 시작과 검증 요청이 다른 WAS 인스턴스에 떨어져도 동작한다. 랜덤키 1회
 * 사용과 재전송 차단은 거래 상태 전이로 {@code MfaService}가 보장한다.
 */
public final class FingerVeinMfaProvider implements MfaProvider {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SUCCESS_VERDICT = "SUCC";
    private static final int RANDOM_KEY_DIGITS = 6;
    private static final int HASH_ROUNDS = 3;

    private final String fixedKey;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 지정맥 공급자를 생성한다.
     *
     * @param fixedKey 지정맥인증 서버와 공유하는 고정키
     * @param clock 년월일을 만들 서버 시계
     * @throws IllegalArgumentException 고정키가 비어 있는 경우
     */
    public FingerVeinMfaProvider(String fixedKey, Clock clock) {
        if (fixedKey == null || fixedKey.isBlank()) {
            throw new IllegalArgumentException("지정맥 고정키는 필수입니다.");
        }
        this.fixedKey = fixedKey;
        this.clock = clock;
    }

    /**
     * 거래용 랜덤키를 발급한다.
     *
     * @param context 서버가 만든 MFA 거래 컨텍스트
     * @return challenge 식별자는 거래 식별자, {@code randomKey}와 {@code providerTransactionId}는 같은 랜덤키
     */
    @Override
    public MfaChallengeData start(MfaStartContext context) {
        String randomKey = newRandomKey();
        return new MfaChallengeData(
                context.transactionId(), null, randomKey, context.expiresAt(), randomKey);
    }

    /**
     * 에이전트가 돌려준 결과 해시를 서버가 재계산한 성공 해시와 비교한다.
     *
     * @param context 거래 컨텍스트, challenge 식별자, 결과 해시, 저장된 랜덤키
     * @return 랜덤키가 없거나 거래가 만료됐거나 challenge 식별자가 거래와 다르거나 해시가 다르면 실패
     */
    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        String randomKey = context.providerTransactionId();
        MfaStartContext start = context.startContext();
        if (randomKey == null
                || randomKey.isBlank()
                || !start.transactionId().equals(context.challengeId())
                || !start.expiresAt().isAfter(Instant.now(clock))) {
            return MfaVerificationResult.failure();
        }

        String expected = successHash(start.eno(), randomKey);
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
