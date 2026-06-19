package com.kdb.it.common.sso;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * ESSO(ISign+) 인증서버와 통신하는 클라이언트.
 *
 * <p>JSP Web Agent의 {@code business.jsp}(인증서버 통신 점검)와 {@code checkauth.jsp}
 * (토큰 검증·사용자 정보 조회) 로직을 {@link RestClient} 기반으로 옮긴 것입니다. 통신 실패는
 * 예외를 전파하지 않고 "서버 불가"/"검증 실패"로 표현해 호출자가 수동 로그인으로 폴백하도록 합니다.</p>
 */
@Component
public class SsoAgentClient {

    private static final Logger log = LoggerFactory.getLogger(SsoAgentClient.class);

    /** ISign+ 정상 응답 코드. */
    private static final String SUCCESS_CODE = "000000";

    private final RestClient restClient;
    private final SsoProperties props;

    public SsoAgentClient(@Qualifier("ssoRestClient") RestClient restClient, SsoProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /**
     * 인증서버 통신 가능 여부를 점검합니다 ({@code /openapi/checkserver}).
     *
     * <p>응답 {@code resultCode}가 성공 코드면 true, 통신 실패·비정상 응답이면 false를 반환합니다.</p>
     *
     * @return 인증서버가 정상 응답하면 true
     */
    public boolean isServerAlive() {
        try {
            JsonNode body = restClient.get()
                    .uri(props.checkServerUrl())
                    .retrieve()
                    .body(JsonNode.class);
            return body != null && SUCCESS_CODE.equals(text(body, "resultCode"));
        } catch (Exception e) {
            log.warn("SSO 인증서버 통신 점검 실패 - url: {}, reason: {}", props.checkServerUrl(), e.toString());
            return false;
        }
    }

    /**
     * 토큰을 검증하고 사용자 식별 데이터를 조회합니다 ({@code /token/authorization}).
     *
     * <p>원본 Web Agent와 동일하게 파라미터를 쿼리 스트링으로 전달합니다(POST + query string).
     * 검증 성공 시 응답 {@code user} 객체에서 {@code requestData} 키(기본 {@code id})의 값을
     * 추출해 {@link TokenAuthResult#resultData()}에 담습니다.</p>
     *
     * @param secureToken     ESSO가 콜백으로 전달한 보안 토큰
     * @param secureSessionId ESSO 보안 세션 ID (로그아웃 연계용)
     * @param clientIp        클라이언트 IP (인증서버 검증 항목)
     * @return 검증 결과 (통신 실패 시 resultCode는 비-성공값)
     */
    public TokenAuthResult authorize(String secureToken, String secureSessionId, String clientIp) {
        URI uri = UriComponentsBuilder.fromUriString(props.tokenAuthorizationUrl())
                .queryParam("secureToken", secureToken)
                .queryParam("secureSessionId", secureSessionId == null ? "" : secureSessionId)
                .queryParam("requestData", props.requestData())
                .queryParam("agentId", props.agentId())
                .queryParam("clientIP", clientIp)
                .build()
                .encode()
                .toUri();

        try {
            JsonNode body = restClient.post()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                log.warn("SSO 토큰 검증 응답 본문이 비어 있습니다.");
                return TokenAuthResult.failure("999999");
            }

            String resultCode = text(body, "resultCode");
            String resultMessage = text(body, "resultMessage");
            String returnUrl = text(body, "returnUrl");
            boolean useCSMode = body.path("useCSMode").asBoolean(false);

            String resultData = "";
            if (SUCCESS_CODE.equals(resultCode)) {
                resultData = extractRequestData(body.path("user"));
            }
            return new TokenAuthResult(resultCode, resultMessage, resultData, returnUrl, useCSMode);
        } catch (Exception e) {
            log.warn("SSO 토큰 검증 통신 실패 - url: {}, reason: {}", props.tokenAuthorizationUrl(), e.toString());
            return TokenAuthResult.failure("999999");
        }
    }

    /**
     * 인증 응답 {@code user} 객체에서 {@code requestData}가 지정한 키 값을 쉼표로 이어 붙입니다.
     *
     * @param user 인증서버가 반환한 사용자 정보 노드
     * @return 추출된 사용자 데이터 (없으면 빈 문자열)
     */
    private String extractRequestData(JsonNode user) {
        if (user == null || user.isMissingNode() || user.isNull()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : props.requestData().split(",")) {
            JsonNode value = user.path(key.trim());
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(value.asText());
        }
        return sb.toString();
    }

    /**
     * JSON 노드에서 문자열 필드를 안전하게 읽습니다.
     *
     * @param node  대상 노드
     * @param field 필드명
     * @return 필드 값 (없거나 null이면 빈 문자열)
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    /**
     * 토큰 검증 결과.
     *
     * @param resultCode    ISign+ 결과 코드 ({@code 000000}=성공)
     * @param resultMessage 결과 메시지
     * @param resultData    추출된 사용자 식별 데이터(사번 등)
     * @param returnUrl     인증서버가 지정한 복귀 URL (사용하지 않을 수 있음)
     * @param useCSMode     CS 모드 여부 (토큰 저장 페이지 경유 필요 시 true)
     */
    public record TokenAuthResult(
            String resultCode,
            String resultMessage,
            String resultData,
            String returnUrl,
            boolean useCSMode
    ) {
        /** 통신/응답 실패 결과를 생성합니다. */
        static TokenAuthResult failure(String resultCode) {
            return new TokenAuthResult(resultCode, "", "", null, false);
        }
    }
}
