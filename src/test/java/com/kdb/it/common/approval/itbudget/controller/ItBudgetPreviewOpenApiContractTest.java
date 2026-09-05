package com.kdb.it.common.approval.itbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.service.ItBudgetApprovalFacade;
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

@SpringBootTest(classes = ItBudgetPreviewOpenApiContractTest.App.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ItBudgetPreviewOpenApiContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean ItBudgetApprovalFacade facade;

    @Test
    void previewPathUsesStableRequestResponseAndErrorSchemas() throws Exception {
        var json =
                new ObjectMapper()
                        .readTree(
                                mvc.perform(get("/v3/api-docs"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
        var operation = json.at("/paths/~1api~1applications~1it-budget~1previews/post");
        assertThat(json.at("/components/schemas/ItBudgetSourceDigest/required").toString())
                .contains("displayName", "sourceDigest", "order");
        assertThat(
                        json.at(
                                        "/components/schemas/ItBudgetChangedSource/properties/modifiedAt/type")
                                .toString())
                .contains("string", "null");
        assertThat(
                        json.at("/components/schemas/ItBudgetChangedSource/properties/no")
                                .isMissingNode())
                .isTrue();
        var submission = json.at("/paths/~1api~1applications~1it-budget~1submissions/post");
        assertThat(submission.isMissingNode()).isFalse();
        assertThat(submission.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetSubmissionRequest");
        assertThat(submission.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetSubmissionResponse");
        for (var code : new String[] {"400", "409"})
            assertThat(
                            submission
                                    .at(
                                            "/responses/"
                                                    + code
                                                    + "/content/application~1json/schema/$ref")
                                    .asText())
                    .isEqualTo("#/components/schemas/ItBudgetApprovalErrorResponse");
        assertThat(
                        json.at("/components/schemas/ItBudgetSubmissionRequest/properties/snapshot")
                                .isMissingNode())
                .isTrue();
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetPreviewRequest");
        assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetPreviewResponse");
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        for (var status : new String[] {"400", "404"})
            assertThat(
                            operation
                                    .at(
                                            "/responses/"
                                                    + status
                                                    + "/content/application~1json/schema/$ref")
                                    .asText())
                    .isEqualTo("#/components/schemas/ItBudgetApprovalErrorResponse");
        assertThat(
                        json.at(
                                        "/components/schemas/ItBudgetSnapshotProject/properties/currentRequestAmount/type")
                                .toString())
                .contains("string");
        assertThat(
                        json.at("/components/schemas/ItBudgetSnapshotCost/properties/baseYear/type")
                                .toString())
                .contains("string");
        assertThat(
                        json.at(
                                        "/components/schemas/ItBudgetSnapshotApprovalPerson/properties/date/type")
                                .toString())
                .contains("string", "null");
        assertThat(json.at("/components/schemas/ItBudgetSnapshotRequester/required").toString())
                .contains("eno", "name");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({ItBudgetApplicationController.class, SwaggerConfig.class})
    static class App {}
}
