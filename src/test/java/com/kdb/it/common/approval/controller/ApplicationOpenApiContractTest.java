package com.kdb.it.common.approval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.service.ApplicationDashboardService;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.approval.service.ApprovalHomeInboxService;
import com.kdb.it.common.approval.service.ApprovalLineManagementService;
import com.kdb.it.common.approval.service.ApprovalLineSuggestionService;
import com.kdb.it.common.approval.service.PendingApproverService;
import com.kdb.it.config.SwaggerConfig;
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

/** 전자결재 공개 API에서 서버가 결정해야 하는 사번·상신 입력이 노출되지 않는지 검증합니다. */
@SpringBootTest(classes = ApplicationOpenApiContractTest.App.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ApplicationOpenApiContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApplicationDashboardService applicationDashboardService;
    @MockitoBean ApplicationService applicationService;
    @MockitoBean ApprovalHomeInboxService approvalHomeInboxService;
    @MockitoBean PendingApproverService pendingApproverService;
    @MockitoBean ApprovalLineManagementService approvalLineManagementService;
    @MockitoBean ApprovalLineSuggestionService approvalLineSuggestionService;

    @Test
    void approvalActorAndLegacySubmissionAreNotPublicInputs() throws Exception {
        String rawDocument =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var document = new ObjectMapper().readTree(rawDocument);

        assertThat(document.at("/paths/~1api~1applications/post").isMissingNode()).isTrue();
        assertThat(
                        document.at(
                                        "/components/schemas/ApplicationApproveRequest/properties/dcdEno")
                                .isMissingNode())
                .isTrue();
        assertThat(
                        document.at("/components/schemas/ApplicationApprovalItem/properties/dcdEno")
                                .isMissingNode())
                .isTrue();
    }

    @Test
    void bulkRequestsExposeValidationLimits() throws Exception {
        String rawDocument =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var document = new ObjectMapper().readTree(rawDocument);

        var approvalList =
                document.at(
                        "/components/schemas/ApplicationBulkApproveRequest/properties/approvals");
        assertThat(approvalList.path("minItems").asInt()).isEqualTo(1);
        assertThat(approvalList.path("maxItems").asInt()).isEqualTo(100);
        assertThat(
                        document.at(
                                        "/components/schemas/ApplicationApprovalItem/properties/apfMngNo/maxLength")
                                .asInt())
                .isEqualTo(64);
        assertThat(
                        document.at(
                                        "/components/schemas/ApplicationApprovalItem/properties/dcdOpnn/maxLength")
                                .asInt())
                .isEqualTo(2000);
        assertThat(
                        document.at(
                                "/components/schemas/ApplicationApprovalItem/properties/dcdSts/enum"))
                .hasToString("[\"2\",\"3\",\"승인\",\"반려\"]");

        var readList =
                document.at("/components/schemas/ApplicationBulkGetRequest/properties/apfMngNos");
        assertThat(readList.path("minItems").asInt()).isEqualTo(1);
        assertThat(readList.path("maxItems").asInt()).isEqualTo(100);
        assertThat(readList.path("items").path("maxLength").asInt()).isEqualTo(64);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({ApplicationController.class, SwaggerConfig.class})
    static class App {}
}
