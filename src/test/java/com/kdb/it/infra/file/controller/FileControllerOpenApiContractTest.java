package com.kdb.it.infra.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.config.SwaggerConfig;
import com.kdb.it.domain.migration.request.service.RequestFormSourceArchiveService;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.authz.FileTargetWriteAuthorizerRegistry;
import com.kdb.it.infra.file.service.FileService;
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

/** 편성요청서 원본 ZIP의 실제 생성 OpenAPI 응답 계약을 검증합니다. */
@SpringBootTest(classes = FileControllerOpenApiContractTest.OpenApiTestApp.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FileControllerOpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private FileService fileService;
    @MockitoBean private FileOwnershipChecker fileOwnershipChecker;
    @MockitoBean private FileTargetWriteAuthorizerRegistry targetWriteAuthorizerRegistry;
    @MockitoBean private RequestFormSourceArchiveService requestFormSourceArchiveService;

    @Test
    @DisplayName("원본 ZIP 200 응답은 application/zip binary string으로 공개된다")
    void requestFormSourceArchiveResponseIsBinaryZip() throws Exception {
        String document =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode schema =
                objectMapper
                        .readTree(document)
                        .at(
                                "/paths/~1api~1files~1request-form-source~1archive/post/responses/200/content/application~1zip/schema");

        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(schema.path("format").asText()).isEqualTo("binary");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({FileController.class, SwaggerConfig.class})
    static class OpenApiTestApp {}
}
