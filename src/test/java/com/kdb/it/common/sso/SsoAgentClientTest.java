package com.kdb.it.common.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoAgentClientTest {

    private static final String HOST = "https://esso-host.test";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SsoProperties props(String requestData) {
        return new SsoProperties(false, "K140024", "https://esso-browser.test", HOST, "AGENT-1",
                requestData, 5000, 5000);
    }

    private static JsonNode json(String source) throws Exception {
        return MAPPER.readTree(source);
    }

    @Test
    @DisplayName("isServerAlive: 성공 resultCode이면 true를 반환한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void isServerAlive_successCode_true() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(request);
        when(request.uri(HOST + "/openapi/checkserver")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(JsonNode.class)).thenReturn(json("{\"resultCode\":\"000000\"}"));

        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        assertThat(client.isServerAlive()).isTrue();
    }

    @Test
    @DisplayName("isServerAlive: 비성공 resultCode, null 본문, 통신 실패이면 false를 반환한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void isServerAlive_failure_false() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(request);
        when(request.uri(HOST + "/openapi/checkserver")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(JsonNode.class))
                .thenReturn(json("{\"resultCode\":\"999999\"}"))
                .thenReturn(null)
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")));
        SsoAgentClient client = new SsoAgentClient(restClient, props("id"));

        assertThat(client.isServerAlive()).isFalse();
        assertThat(client.isServerAlive()).isFalse();
        assertThat(client.isServerAlive()).isFalse();
    }

    @Test
    @DisplayName("authorize: 성공 응답이면 requestData 키를 쉼표로 추출한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void authorize_success_extractsRequestData() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(JsonNode.class)).thenReturn(json("""
                {
                  "resultCode": "000000",
                  "resultMessage": "OK",
                  "returnUrl": "/return",
                  "useCSMode": true,
                  "user": { "id": "K150024", "name": "홍길동" }
                }
                """));
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
                .contains("requestData=id,%20name,%20missing")
                .contains("agentId=AGENT-1")
                .contains("clientIP=127.0.0.1");
    }

    @Test
    @DisplayName("authorize: 실패 코드, null 본문, 통신 예외이면 실패 결과를 반환한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void authorize_failurePaths_failureResult() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri(any(URI.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(JsonNode.class))
                .thenReturn(json("{\"resultCode\":\"310017\",\"resultMessage\":\"권한없음\"}"))
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
