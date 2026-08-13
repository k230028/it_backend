package com.kdb.it.common.mfa.provider;

import com.kdb.it.common.mfa.config.MfaProperties;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** OnePass mOTP/FIDO HTTP 요청을 안전한 MFA 공급자 데이터로 변환하는 클라이언트이다. */
public final class OnePassClient {

    private static final String SUCCESS_CODE = "100000";
    private static final String OTP_AUTH_TYPE = "OTP01";
    private static final int MAX_QR_LENGTH = 16_384;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final MfaProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 설정된 endpoint와 timeout으로 OnePass HTTP 클라이언트를 생성한다.
     *
     * @param properties OnePass endpoint, 기관/서비스 식별자 및 timeout
     */
    public OnePassClient(MfaProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** mOTP challenge를 시작한다. */
    public MfaChallengeData requestMotpChallenge(MfaStartContext context) {
        String svcTrId = newServiceTransactionId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("command", "requestServiceAuth");
        request.put("svcTrId", svcTrId);
        request.put("siteId", properties.siteId());
        request.put("svcId", properties.svcId());
        request.put("loginId", context.eno());
        request.put("crossDomain", true);
        request.put("authType", OTP_AUTH_TYPE);
        return challenge(request(request), context);
    }

    /** FIDO challenge를 시작한다. */
    public MfaChallengeData requestFidoChallenge(MfaStartContext context) {
        return startFido(context).challenge();
    }

    /** 명시적으로 제출된 mOTP만 OnePass에 검증 요청한다. */
    public MfaVerificationResult verifyMotp(
            MfaStartContext context, String challengeId, String otp) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("command", "requestVerifyOtp");
        request.put("trId", challengeId);
        request.put("otpValue", otp);
        request.put("crossDomain", true);
        request.put("authType", OTP_AUTH_TYPE);
        return success(request(request))
                ? MfaVerificationResult.success()
                : MfaVerificationResult.failure();
    }

    /** FIDO 시작 요청에 사용한 OnePass 서비스 거래 식별자를 보존한 내부 결과이다. */
    FidoStart startFido(MfaStartContext context) {
        String svcTrId = newServiceTransactionId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("command", "requestServiceAuth");
        request.put("svcTrId", svcTrId);
        request.put("siteId", properties.siteId());
        request.put("svcId", properties.svcId());
        request.put("loginId", context.eno());
        request.put("bizAlarmType", "1");
        request.put("crossDomain", true);
        return new FidoStart(challenge(request(request), context), svcTrId);
    }

    /**
     * FIDO 시작 시 발급한 동일 서비스 거래 식별자로 OnePass 결과를 확인한다.
     *
     * <p>거래 조회 자체가 성공({@code resultCode=100000})했는데 {@code trStatus}가 승인(1)이 아니면 사용자가 아직 기기에서 처리하지
     * 않은 상태로 보고 {@link MfaVerificationResult#undecided()}를 반환한다. 제공된 연동 규격에 사용자 거부를 뜻하는 별도 {@code
     * trStatus} 값이 정의되어 있지 않아, 거부와 대기를 구분하지 못하고 모두 미결정으로 처리한다. 거부한 거래도 challenge 만료 시각까지 미결정으로 남을 뿐
     * 승인되지는 않는다. 규격에 거부 상태값이 추가되면 이 분기에서 {@link MfaVerificationResult#failure()}로 분리한다.
     *
     * @param svcTrId FIDO 시작에서 발급한 서비스 거래 식별자
     * @return 승인이면 성공, 거래 조회는 됐으나 미승인이면 미결정, 그 밖의 응답이면 실패
     */
    MfaVerificationResult confirmFido(String svcTrId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("command", "trResultConfirm");
        request.put("svcTrId", svcTrId);
        request.put("crossDomain", true);
        Map<String, Object> response = request(request);
        if (!success(response)) {
            return MfaVerificationResult.failure();
        }
        return "1".equals(text(resultData(response), "trStatus"))
                ? MfaVerificationResult.success()
                : MfaVerificationResult.undecided();
    }

    private MfaChallengeData challenge(Map<String, Object> response, MfaStartContext context) {
        if (!success(response)) {
            throw new OnePassProviderException(
                    text(response, "resultCode"), text(response, "resultMsg"));
        }
        Map<String, Object> resultData = resultData(response);
        String challengeId = text(resultData, "trId");
        if (challengeId.isBlank()) {
            throw new IllegalStateException("OnePass MFA 응답에 challenge 식별자가 없습니다.");
        }
        String qrData = optionalText(resultData, "qrImage");
        if (qrData != null && qrData.length() > MAX_QR_LENGTH) {
            throw new IllegalArgumentException("OnePass QR 데이터가 허용 길이를 초과했습니다.");
        }
        return new MfaChallengeData(challengeId, qrData, null, context.expiresAt());
    }

    private Map<String, Object> request(Map<String, Object> request) {
        try {
            Map<String, Object> response =
                    restClient
                            .post()
                            .uri(properties.endpoint())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(MAP_TYPE);
            if (response == null) {
                throw new IllegalStateException("OnePass MFA 통신 또는 응답 처리에 실패했습니다.");
            }
            return response;
        } catch (Exception ignored) {
            throw new IllegalStateException("OnePass MFA 통신 또는 응답 처리에 실패했습니다.");
        }
    }

    private String newServiceTransactionId() {
        StringBuilder value = new StringBuilder(20);
        for (int index = 0; index < 20; index++) {
            value.append(secureRandom.nextInt(10));
        }
        return value.toString();
    }

    private static boolean success(Map<String, Object> response) {
        return SUCCESS_CODE.equals(text(response, "resultCode"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultData(Map<String, Object> response) {
        Object value = response.get("resultData");
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String text(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null ? "" : value.toString();
    }

    private static String optionalText(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null ? null : value.toString();
    }

    record FidoStart(MfaChallengeData challenge, String svcTrId) {}
}
