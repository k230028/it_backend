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
        return requestChallenge("requestServiceAuth", "MOTP", context);
    }

    /** FIDO challenge를 시작한다. */
    public MfaChallengeData requestFidoChallenge(MfaStartContext context) {
        return requestChallenge("requestServiceAuth", "FIDO", context);
    }

    /** 명시적으로 제출된 mOTP만 OnePass에 검증 요청한다. */
    public MfaVerificationResult verifyMotp(
            MfaStartContext context, String challengeId, String otp) {
        Map<String, Object> response =
                request("requestVerifyOtp", "MOTP", context, challengeId, otp);
        return success(response)
                ? MfaVerificationResult.success()
                : MfaVerificationResult.failure();
    }

    /** FIDO challenge 결과를 OnePass에 확인한다. */
    public MfaVerificationResult confirmFido(MfaStartContext context, String challengeId) {
        Map<String, Object> response =
                request("trResultConfirm", "FIDO", context, challengeId, null);
        return success(response) && "1".equals(text(response, "trStatus"))
                ? MfaVerificationResult.success()
                : MfaVerificationResult.failure();
    }

    private MfaChallengeData requestChallenge(
            String operation, String method, MfaStartContext context) {
        Map<String, Object> response = request(operation, method, context, null, null);
        if (!success(response)) {
            throw new IllegalStateException("OnePass MFA 요청에 실패했습니다.");
        }
        String challengeId = text(response, "challengeId");
        if (challengeId.isBlank()) {
            throw new IllegalStateException("OnePass MFA 응답에 challenge 식별자가 없습니다.");
        }
        String qrData = optionalText(response, "qrData");
        if (qrData != null && qrData.length() > MAX_QR_LENGTH) {
            throw new IllegalArgumentException("OnePass QR 데이터가 허용 길이를 초과했습니다.");
        }
        return new MfaChallengeData(challengeId, qrData, context.expiresAt());
    }

    private Map<String, Object> request(
            String operation,
            String method,
            MfaStartContext context,
            String challengeId,
            String otp) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("siteId", properties.siteId());
            request.put("svcId", properties.svcId());
            request.put("svcTrId", newServiceTransactionId());
            request.put("operation", operation);
            request.put("method", method);
            request.put("eno", context.eno());
            request.put("purpose", context.purpose().name());
            if (challengeId != null) {
                request.put("challengeId", challengeId);
            }
            if (otp != null) {
                request.put("otp", otp);
            }
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

    private static String text(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null ? "" : value.toString();
    }

    private static String optionalText(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null ? null : value.toString();
    }
}
