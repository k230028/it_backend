package com.kdb.it.common.sso;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ESSO(ISign+) 인증서버와 통신하는 클라이언트.
 *
 * <p>JSP Web Agent의 {@code business.jsp}(인증서버 통신 점검)와 {@code checkauth.jsp} (토큰 검증·사용자 정보 조회) 로직을
 * {@link RestClient} 기반으로 옮긴 것입니다. 통신 실패는 예외를 전파하지 않고 "서버 불가"/"검증 실패"로 표현해 호출자가 수동 로그인으로 폴백하도록 합니다.
 */
@Component
public class SsoAgentClient {

    private static final Logger log = LoggerFactory.getLogger(SsoAgentClient.class);

    /** ISign+ 정상 응답 코드. */
    private static final String SUCCESS_CODE = "000000";

    /**
     * 인증서버 JSON 응답 역직렬화 타입.
     *
     * <p>Jackson 특정 버전의 {@code JsonNode}로 받지 않고 {@code Map}으로 받습니다. Spring Boot 4(Spring Framework
     * 7) 클래스패스에는 Jackson 2와 3이 공존하고 기본 JSON 컨버터가 Jackson 3로 동작하므로, Jackson 2의 {@code
     * com.fasterxml.jackson.databind.JsonNode}로 역직렬화를 요청하면 {@code HttpMessageConversionException:
     * Type definition error [JsonNode]}가 발생합니다. {@code Map}은 어떤 Jackson 버전 컨버터와도 호환됩니다.
     */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final SsoProperties props;

    public SsoAgentClient(@Qualifier("ssoRestClient") RestClient restClient, SsoProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /**
     * 인증서버 통신 가능 여부를 점검합니다 ({@code /openapi/checkserver}).
     *
     * <p>응답 {@code resultCode}가 성공 코드면 true, 통신 실패·비정상 응답이면 false를 반환합니다.
     *
     * @return 인증서버가 정상 응답하면 true
     */
    public boolean isServerAlive() {
        try {
            Map<String, Object> body =
                    restClient.get().uri(props.checkServerUrl()).retrieve().body(MAP_TYPE);
            return body != null && SUCCESS_CODE.equals(text(body, "resultCode"));
        } catch (RuntimeException e) {
            log.warn(
                    "SSO 인증서버 통신 점검 실패 - url: {}, 오류 유형: {}",
                    props.checkServerUrl(),
                    SsoLogSanitizer.exceptionType(e));
            return false;
        }
    }

    /**
     * 토큰을 검증하고 사용자 식별 데이터를 조회합니다 ({@code /token/authorization}).
     *
     * <p>원본 Web Agent와 동일하게 파라미터를 쿼리 스트링으로 전달합니다(POST + query string). 검증 성공 시 응답 {@code user} 객체에서
     * {@code requestData} 키(기본 {@code id})의 값을 추출해 {@link TokenAuthResult#resultData()}에 담습니다.
     *
     * @param secureToken ESSO가 콜백으로 전달한 보안 토큰
     * @param secureSessionId ESSO 보안 세션 ID (로그아웃 연계용)
     * @param clientIp 클라이언트 IP (인증서버 검증 항목)
     * @return 검증 결과 (통신 실패 시 resultCode는 비-성공값)
     */
    public TokenAuthResult authorize(String secureToken, String secureSessionId, String clientIp) {
        // 쿼리 값은 직접 퍼센트 인코딩한 뒤 build(true)로 조립한다(이미 인코딩됨으로 표시).
        // UriComponentsBuilder.encode()는 '+'를 쿼리에서 합법 문자로 보아 인코딩하지 않는데,
        // secureToken(base64)의 '+'가 그대로 전송되면 ISign+가 폼 디코딩 규칙으로 '+'를 공백으로
        // 해석해 토큰이 깨진다("토큰 복호화 실패", resultCode 310001). URLEncoder는 '+' → '%2B'로
        // 인코딩해 원본 base64가 서버에서 정확히 복원되게 한다(구 JSP Agent의 NameValuePair와 동일).
        URI uri =
                UriComponentsBuilder.fromUriString(props.tokenAuthorizationUrl())
                        .queryParam("secureToken", enc(secureToken))
                        .queryParam(
                                "secureSessionId",
                                enc(secureSessionId == null ? "" : secureSessionId))
                        .queryParam("requestData", enc(props.requestData()))
                        .queryParam("agentId", enc(props.agentId()))
                        .queryParam("clientIP", enc(clientIp))
                        .build(true)
                        .toUri();

        log.info(
                "SSO 토큰 검증 요청 - url: {}, agentId: {}, requestData: {}, secureSessionId: {}",
                props.tokenAuthorizationUrl(),
                props.agentId(),
                props.requestData(),
                SsoLogSanitizer.masked(secureSessionId));

        try {
            Map<String, Object> body = restClient.post().uri(uri).retrieve().body(MAP_TYPE);
            if (body == null) {
                log.warn("SSO 토큰 검증 응답 본문이 비어 있습니다.");
                return TokenAuthResult.failure("999999");
            }

            String resultCode = text(body, "resultCode");
            String resultMessage = text(body, "resultMessage");
            String returnUrl = text(body, "returnUrl");
            boolean useCSMode = bool(body.get("useCSMode"));

            String resultData = "";
            if (SUCCESS_CODE.equals(resultCode)) {
                resultData = extractRequestData(asMap(body.get("user")));
                log.info(
                        "SSO 토큰 검증 성공 - resultCode: {}, useCSMode: {}, 사용자 식별값 존재: {}",
                        SsoLogSanitizer.resultCode(resultCode),
                        useCSMode,
                        !resultData.isBlank());
            } else {
                log.warn("SSO 토큰 검증 거부 - resultCode: {}", SsoLogSanitizer.resultCode(resultCode));
            }
            return new TokenAuthResult(resultCode, resultMessage, resultData, returnUrl, useCSMode);
        } catch (RuntimeException e) {
            log.warn(
                    "SSO 토큰 검증 통신 실패 - url: {}, 오류 유형: {}",
                    props.tokenAuthorizationUrl(),
                    SsoLogSanitizer.exceptionType(e));
            return TokenAuthResult.failure("999999");
        }
    }

    /**
     * 인증 응답 {@code user} 객체에서 {@code requestData}가 지정한 키 값을 쉼표로 이어 붙입니다.
     *
     * @param user 인증서버가 반환한 사용자 정보 맵 (없으면 null)
     * @return 추출된 사용자 데이터 (없으면 빈 문자열)
     */
    private String extractRequestData(Map<String, Object> user) {
        if (user == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : props.requestData().split(",")) {
            Object value = user.get(key.trim());
            if (value == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(value);
        }
        return sb.toString();
    }

    /**
     * 응답 맵에서 문자열 필드를 안전하게 읽습니다.
     *
     * @param body 응답 맵
     * @param field 필드명
     * @return 필드 값 (없거나 null이면 빈 문자열)
     */
    private static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        return value == null ? "" : value.toString();
    }

    /**
     * 응답 값을 boolean으로 변환합니다. {@code Boolean}/{@code "true"} 문자열을 모두 처리합니다.
     *
     * @param value 응답 값
     * @return 변환된 boolean (해석 불가/null이면 false)
     */
    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    /**
     * 응답 값이 중첩 객체이면 {@code Map}으로, 아니면 null로 변환합니다.
     *
     * @param value 응답 값
     * @return 중첩 객체 맵 (객체가 아니면 null)
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /**
     * 쿼리 파라미터 값을 application/x-www-form-urlencoded 규칙으로 퍼센트 인코딩합니다.
     *
     * <p>특히 base64 secureToken의 {@code +}를 {@code %2B}로 인코딩해, 인증서버가 쿼리를 폼 디코딩할 때 {@code +}가 공백으로
     * 바뀌어 토큰이 깨지는 문제를 막습니다.
     *
     * @param value 인코딩할 원본 값
     * @return 퍼센트 인코딩된 값
     */
    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 토큰 검증 결과.
     *
     * @param resultCode ISign+ 결과 코드 ({@code 000000}=성공)
     * @param resultMessage 결과 메시지
     * @param resultData 추출된 사용자 식별 데이터(사번 등)
     * @param returnUrl 인증서버가 지정한 복귀 URL (사용하지 않을 수 있음)
     * @param useCSMode CS 모드 여부 (토큰 저장 페이지 경유 필요 시 true)
     */
    public record TokenAuthResult(
            String resultCode,
            String resultMessage,
            String resultData,
            String returnUrl,
            boolean useCSMode) {
        /** 통신/응답 실패 결과를 생성합니다. */
        static TokenAuthResult failure(String resultCode) {
            return new TokenAuthResult(resultCode, "", "", null, false);
        }
    }
}
