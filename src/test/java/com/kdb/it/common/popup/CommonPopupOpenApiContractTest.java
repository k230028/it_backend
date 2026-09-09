package com.kdb.it.common.popup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.config.SwaggerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 공통 안내 팝업의 실제 생성 OpenAPI 상태 코드 계약을 검증합니다. */
@SpringBootTest(classes = CommonPopupOpenApiContractTest.OpenApiTestApp.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CommonPopupOpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private CommonPopupService service;

    @Test
    @DisplayName("사용자 조회는 활성 문서가 없는 204 응답을 OpenAPI에 공개한다")
    void activePopupDocumentsNoContentResponse() throws Exception {
        JsonNode responses = responsesAt("/paths/~1api~1common-popup/get/responses");

        assertThat(responses.has("204")).isTrue();
    }

    @Test
    @DisplayName("관리자 게시 중지는 204 응답만 OpenAPI에 공개한다")
    void stopPublishingDocumentsNoContentResponse() throws Exception {
        JsonNode responses = responsesAt("/paths/~1api~1admin~1common-popup/delete/responses");

        assertThat(responses.has("204")).isTrue();
        assertThat(responses.has("200")).isFalse();
    }

    @Test
    @DisplayName("화면별 사용자 조회는 204 응답을 OpenAPI에 공개한다")
    void routePopupDocumentsNoContentResponse() throws Exception {
        JsonNode responses = responsesAt("/paths/~1api~1common-popup~1{type}/get/responses");

        assertThat(responses.has("204")).isTrue();
    }

    @Test
    @DisplayName("화면별 관리자 게시 중지는 204 응답만 OpenAPI에 공개한다")
    void routePopupStopDocumentsNoContentResponse() throws Exception {
        JsonNode responses =
                responsesAt("/paths/~1api~1admin~1common-popup~1{type}/delete/responses");

        assertThat(responses.has("204")).isTrue();
        assertThat(responses.has("200")).isFalse();
    }

    private JsonNode responsesAt(String pointer) throws Exception {
        String document =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(document).at(pointer);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({CommonPopupController.class, AdminCommonPopupController.class, SwaggerConfig.class})
    static class OpenApiTestApp {}
}
