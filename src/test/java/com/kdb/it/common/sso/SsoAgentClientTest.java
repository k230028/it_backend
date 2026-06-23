package com.kdb.it.common.sso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoAgentClientTest {

    private static final String HOST = "https://esso-host.test";

    private SsoProperties props(String requestData) {
        return new SsoProperties(false, "K140024", "https://esso-browser.test", HOST, "AGENT-1",
                requestData, 5000, 5000);
    }

    /** 인증서버 JSON 응답을 모사하는 맵을 만듭니다 (RestClient 컨버터가 반환하는 형태). */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("isServerAlive: 성공 resultCode이면 true를 반환한다")
    @SuppressWarnings("unchecked")
    void isServerAlive_successCode_true() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(request);
        when(request.uri(HOST + "/openapi/checkserver")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class)))
                .thenReturn(map("resultCode", "000000"));

        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        assertThat(client.isServerAlive()).isTrue();
    }

    @Test
    @DisplayName("isServerAlive: 비성공 resultCode, null 본문, 통신 실패이면 false를 반환한다")
    @SuppressWarnings("unchecked")
    void isServerAlive_failure_false() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(request);
        when(request.uri(HOST + "/openapi/checkserver")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class)))
                .thenReturn(map("resultCode", "999999"))
                .thenReturn(null)
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        assertThat(client.isServerAlive()).isFalse();
        assertThat(client.isServerAlive()).isFalse();
        assertThat(client.isServerAlive()).isFalse();
    }

    @Test
    @DisplayName("authorize: 성공 응답이면 requestData 키를 쉼표로 추출한다")
    @SuppressWarnings("unchecked")
    void authorize_success_extractsRequestData() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class))).thenReturn(map(
                "resultCode", "000000",
                "resultMessage", "OK",
                "returnUrl", "/return",
                "useCSMode", true,
                "user", map("id", "K150024", "name", "홍길동")
        ));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id, name, missing"));

        SsoAgentClient.TokenAuthResult result = client.authorize("tok", "sess", "127.0.0.1");

        assertThat(result.resultCode()).isEqualTo("000000");
        assertThat(result.resultMessage()).isEqualTo("OK");
        assertThat(result.resultData()).isEqualTo("K150024,홍길동");
        assertThat(result.returnUrl()).isEqualTo("/return");
        assertThat(result.useCSMode()).isTrue();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(request).uri(uri.capture());
        assertThat(uri.getValue().toString())
                .contains("secureToken=tok")
                .contains("secureSessionId=sess")
                // requestData는 form-urlencoded 규칙으로 인코딩됨(comma→%2C, space→+)
                .contains("requestData=id%2C+name%2C+missing")
                .contains("agentId=AGENT-1")
                .contains("clientIP=127.0.0.1");
    }

    @Test
    @DisplayName("authorize: base64 secureToken의 +, /, = 가 %2B/%2F/%3D로 인코딩된다 (토큰 복호화 실패 방지)")
    @SuppressWarnings("unchecked")
    void authorize_base64Token_percentEncoded() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class)))
                .thenReturn(map("resultCode", "000000", "user", map("id", "K150024")));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        // 실제 ISign+ 토큰처럼 base64 특수문자(+, /, =)를 포함
        client.authorize("gF9s+rs6/rAb==", "sess", "127.0.0.1");

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(request).uri(uri.capture());
        String sent = uri.getValue().toString();
        // '+' 가 그대로 전송되면 서버가 공백으로 해석 → 토큰 복호화 실패. 반드시 %2B로 인코딩되어야 함.
        assertThat(sent).contains("secureToken=gF9s%2Brs6%2FrAb%3D%3D");
        assertThat(sent).doesNotContain("secureToken=gF9s+rs6");
    }

    @Test
    @DisplayName("authorize: useCSMode가 문자열 \"true\"여도 정상 해석한다")
    @SuppressWarnings("unchecked")
    void authorize_useCSModeAsString_parsed() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class))).thenReturn(map(
                "resultCode", "000000",
                "useCSMode", "true",
                "user", map("id", "K150024")
        ));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        SsoAgentClient.TokenAuthResult result = client.authorize("tok", "sess", "127.0.0.1");

        assertThat(result.useCSMode()).isTrue();
        assertThat(result.resultData()).isEqualTo("K150024");
    }

    @Test
    @DisplayName("authorize: 실패 코드, null 본문, 통신 예외이면 실패 결과를 반환한다")
    @SuppressWarnings("unchecked")
    void authorize_failurePaths_failureResult() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(any(ParameterizedTypeReference.class)))
                .thenReturn(map("resultCode", "310017", "resultMessage", "권한없음"))
                .thenReturn(null)
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        SsoAgentClient.TokenAuthResult failed = client.authorize("tok", null, "127.0.0.1");
        SsoAgentClient.TokenAuthResult empty = client.authorize("tok", null, "127.0.0.1");
        SsoAgentClient.TokenAuthResult exception = client.authorize("tok", null, "127.0.0.1");

        assertThat(failed.resultCode()).isEqualTo("310017");
        assertThat(failed.resultData()).isEmpty();
        assertThat(failed.useCSMode()).isFalse();
        assertThat(empty.resultCode()).isEqualTo("999999");
        assertThat(exception.resultCode()).isEqualTo("999999");
        assertThat(exception.returnUrl()).isNull();

        verify(request, org.mockito.Mockito.times(3)).uri(any(URI.class));
    }
}
