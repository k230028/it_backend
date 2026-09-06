package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.itbudget.controller.ItBudgetApplicationController;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto;
import com.kdb.it.common.approval.itbudget.service.ItBudgetApprovalFacade;
import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.notification.dto.NotificationDto;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto;
import com.kdb.it.config.SwaggerConfig;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.payment.dto.PaymentDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

@SpringBootTest(classes = ApiResponseOpenApiContractTest.App.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ApiResponseOpenApiContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean ItBudgetApprovalFacade itBudgetApprovalFacade;

    @Test
    void itBudgetPreviewAndSubmissionPathsExposeTypedRequestsResponsesAndMfaErrors()
            throws Exception {
        JsonNode document =
                new ObjectMapper()
                        .readTree(
                                mvc.perform(get("/v3/api-docs"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
        JsonNode preview = document.at("/paths/~1api~1applications~1it-budget~1previews/post");
        JsonNode submission =
                document.at("/paths/~1api~1applications~1it-budget~1submissions/post");

        assertThat(preview.isMissingNode()).isFalse();
        assertThat(submission.isMissingNode()).isFalse();
        assertThat(preview.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetPreviewRequest");
        assertThat(preview.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetPreviewResponse");
        assertThat(submission.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetSubmissionRequest");
        assertThat(submission.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetSubmissionResponse");
        assertErrorResponse(preview, "400", "404");
        assertErrorResponse(submission, "400", "409");
        assertThat(preview.at("/responses/401/description").asText()).isEqualTo("미인증");
        assertThat(preview.at("/responses/403/description").asText()).isEqualTo("권한 없음");
        assertThat(submission.at("/responses/401/description").asText())
                .isEqualTo("미인증 또는 결재용 MFA 필요");
        assertThat(submission.at("/responses/403/description").asText()).isEqualTo("권한 없음");
        assertRequired(document, "ItBudgetPreviewRequest", "approvers", "documents");
        assertRequired(
                document,
                "ItBudgetPreviewResponse",
                "previewDigest",
                "previewToken",
                "expiresAt",
                "documents");
        assertRequired(
                document,
                "ItBudgetSubmissionRequest",
                "previewDigest",
                "previewToken",
                "approvers",
                "documents");
        assertRequired(document, "ItBudgetSubmissionResponse", "applicationNumbers");
        assertThat(
                        document.at(
                                        "/components/schemas/ItBudgetPreviewRequest/properties/approvers/maxItems")
                                .asInt())
                .isEqualTo(2);
        assertThat(
                        document.at(
                                        "/components/schemas/ItBudgetSubmissionRequest/properties/approvers/minItems")
                                .asInt())
                .isEqualTo(2);
        assertThat(
                        document.at(
                                        "/components/schemas/ItBudgetSubmissionRequest/properties/approvers/maxItems")
                                .asInt())
                .isEqualTo(2);
        assertRequired(
                document,
                "ItBudgetApprovalErrorResponse",
                "timestamp",
                "status",
                "code",
                "message");
        assertRequired(
                document,
                "ItBudgetChangedSource",
                "kind",
                "id",
                "revision",
                "displayName",
                "modifiedBy",
                "modifiedAt");
        JsonNode changedSources =
                document.at(
                        "/components/schemas/ItBudgetApprovalErrorResponse/properties/changedSources");
        assertThat(changedSources.path("type").asText()).isEqualTo("array");
        assertThat(changedSources.at("/items/$ref").asText())
                .isEqualTo("#/components/schemas/ItBudgetChangedSource");
        JsonNode modifiedAt =
                document.at("/components/schemas/ItBudgetChangedSource/properties/modifiedAt");
        assertThat(jsonStrings(modifiedAt.path("type")))
                .containsExactlyInAnyOrder("string", "null");
        assertThat(modifiedAt.path("format").asText()).isEqualTo("date-time");
    }

    private static void assertErrorResponse(JsonNode operation, String... statuses) {
        for (String responseStatus : statuses)
            assertThat(
                            operation
                                    .at(
                                            "/responses/"
                                                    + responseStatus
                                                    + "/content/application~1json/schema/$ref")
                                    .asText())
                    .isEqualTo("#/components/schemas/ItBudgetApprovalErrorResponse");
    }

    private static void assertRequired(JsonNode document, String schema, String... fields) {
        assertThat(jsonStrings(document.at("/components/schemas/" + schema + "/required")))
                .containsExactlyInAnyOrder(fields);
    }

    private static List<String> jsonStrings(JsonNode node) {
        assertThat(node.isArray()).isTrue();
        var values = new ArrayList<String>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    @Test
    void itBudgetApprovalSchemasExposeRequiredFieldsAndStableEnums() {
        Class<?> requester =
                java.util.Arrays.stream(
                                ItBudgetApprovalDto.SnapshotApprovalLine.class
                                        .getRecordComponents())
                        .filter(c -> c.getName().equals("requester"))
                        .findFirst()
                        .orElseThrow()
                        .getType();
        Schema<?> requesterSchema = resolve(requester);
        assertThat(requesterSchema.getProperties().keySet())
                .containsExactlyInAnyOrder("eno", "name", "rank", "date");
        assertThat(requesterSchema.getRequired()).containsExactlyInAnyOrder("eno", "name", "rank");
        assertThat(Boolean.TRUE.equals(property(requesterSchema, "rank").getNullable())).isTrue();
        assertThat(Boolean.TRUE.equals(property(requesterSchema, "date").getNullable())).isTrue();
        assertContract(
                ItBudgetApprovalDto.Person.class,
                fields("eno", "name", "rank"),
                fields("eno", "name", "rank"));
        assertContract(
                ItBudgetApprovalDto.ApprovalPerson.class,
                fields("role", "eno", "name", "rank", "date"),
                fields("date"));
        assertStringProperties(ItBudgetApprovalDto.Project.class, "currentRequestAmount");
        var projectSchema = resolve(ItBudgetApprovalDto.Project.class);
        assertThat(projectSchema.getRequired()).contains("currentRequestAmount");
        assertThat(
                        Boolean.TRUE.equals(
                                property(projectSchema, "currentRequestAmount").getNullable()))
                .isFalse();
        assertPatternProperties(
                ItBudgetApprovalDto.Project.class, "^-?\\d+\\.\\d{3}$", "currentRequestAmount");
        assertContract(
                ItBudgetApprovalDto.PreviewRequest.class,
                fields("approvers", "documents"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.SubmissionRequest.class,
                fields("previewDigest", "previewToken", "approvers", "documents"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.ChangedSource.class,
                fields("kind", "id", "revision", "displayName", "modifiedBy", "modifiedAt"),
                Set.of("modifiedAt"));
        assertContract(
                ItBudgetApprovalDto.PreviewResponse.class,
                fields("previewDigest", "previewToken", "expiresAt", "documents"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.PreviewDocument.class,
                fields("clientDocumentKey", "snapshot", "payloadDigest", "sources"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.ItBudgetSnapshot.class,
                fields("form", "payload", "approvalLine", "integrity"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.SourceDigest.class,
                fields("kind", "id", "revision", "order", "sourceDigest", "displayName"),
                Set.of());
        assertContract(
                ItBudgetApprovalDto.SubmissionDocument.class,
                fields("clientDocumentKey", "payloadDigest", "sources"),
                Set.of());
        assertStringProperties(ItBudgetApprovalDto.Summary.class, "total", "asset", "cost");
        assertStringProperties(ItBudgetApprovalDto.ProjectItem.class, "quantity", "amount");
        assertStringProperties(ItBudgetApprovalDto.ProjectItem.class, "id");
        assertStringProperties(ItBudgetApprovalDto.Terminal.class, "id");
        assertStringProperties(ItBudgetApprovalDto.Cost.class, "baseYear");
        assertThat(resolve(ItBudgetApprovalDto.ProjectItem.class).getRequired()).contains("id");
        assertThat(resolve(ItBudgetApprovalDto.Terminal.class).getRequired()).contains("id");
        assertThat(property(resolve(ItBudgetApprovalDto.Project.class), "startDate").getNullable())
                .isTrue();
        assertThat(property(resolve(ItBudgetApprovalDto.CodeLabel.class), "code").getNullable())
                .isTrue();
        assertThat(property(resolve(ItBudgetApprovalDto.Cost.class), "baseYear").getNullable())
                .isTrue();
        assertStringProperties(
                ItBudgetApprovalDto.Project.class, "projectBudget", "assetBudget", "costBudget");
        assertStringProperties(
                ItBudgetApprovalDto.Terminal.class,
                "exchangeRate",
                "foreignAmount",
                "budgetAmount");
        assertStringProperties(
                ItBudgetApprovalDto.Cost.class,
                "totalAmount",
                "exchangeRate",
                "assetBudget",
                "costBudget");
        assertPatternProperties(
                ItBudgetApprovalDto.Summary.class, "^-?\\d+\\.\\d{3}$", "total", "asset", "cost");
        assertPatternProperties(ItBudgetApprovalDto.ProjectItem.class, "^-?\\d+$", "quantity");
        assertPatternProperties(
                ItBudgetApprovalDto.ProjectItem.class, "^-?\\d+\\.\\d{3}$", "amount");
        assertPatternProperties(
                ItBudgetApprovalDto.Project.class,
                "^-?\\d+\\.\\d{3}$",
                "projectBudget",
                "assetBudget",
                "costBudget");
        assertPatternProperties(
                ItBudgetApprovalDto.Terminal.class, "^-?\\d+\\.\\d{4}$", "exchangeRate");
        assertPatternProperties(
                ItBudgetApprovalDto.Terminal.class,
                "^-?\\d+\\.\\d{3}$",
                "foreignAmount",
                "budgetAmount");
        assertPatternProperties(
                ItBudgetApprovalDto.Cost.class,
                "^-?\\d+\\.\\d{3}$",
                "totalAmount",
                "assetBudget",
                "costBudget");
        assertPatternProperties(
                ItBudgetApprovalDto.Cost.class, "^-?\\d+\\.\\d{4}$", "exchangeRate");
        assertFormat(ItBudgetApprovalDto.ApprovalPerson.class, "date", "date-time");
        assertFormat(ItBudgetApprovalDto.Requester.class, "date", "date-time");
        assertDateProperties(
                ItBudgetApprovalDto.Project.class, "startDate", "endDate", "feasibilityDate");
        assertDateProperties(
                ItBudgetApprovalDto.Cost.class, "exchangeRateBaseDate", "firstDeferralDate");
        assertFormat(ItBudgetApprovalDto.Integrity.class, "capturedAt", "date-time");
        assertPropertiesRequiredExcept(
                ItBudgetApprovalDto.ErrorResponse.class, fields("changedSources"));
        assertThat(
                        Boolean.TRUE.equals(
                                property(
                                                resolve(ItBudgetApprovalDto.ErrorResponse.class),
                                                "changedSources")
                                        .getNullable()))
                .isFalse();
        assertEnum(ItBudgetApprovalDto.SourceRef.class, "kind", "PROJECT", "COST");
        assertEnum(ItBudgetApprovalDto.SourceDigest.class, "kind", "PROJECT", "COST");
        assertEnum(ItBudgetApprovalDto.ApproverRef.class, "role", "TEAM_LEAD", "DEPT_HEAD");
        assertEnum(
                ItBudgetApprovalDto.ApprovalPerson.class,
                "role",
                "TEAM_LEAD",
                "DEPT_HEAD",
                "ADDITIONAL");
    }

    @Test
    void councilResponsesExposeRequiredNullableAndEnumContracts() {
        assertAllPropertiesRequired(
                CouncilDto.ListResponse.class,
                "asctId",
                "abusNm",
                "asctStsC",
                "dbrTc",
                "cnrcDt",
                "cnrcTm",
                "prjYy",
                "prjTp",
                "svnDpm",
                "prjBg",
                "sttDt",
                "endDt",
                "itDpm",
                "prjDes",
                "csfHeldYn");
        assertAllPropertiesRequired(
                CouncilDto.DetailResponse.class,
                "dbrTc",
                "cnrcDt",
                "cnrcTm",
                "cnrcPlc",
                "abusNm",
                "edrt",
                "sttDt",
                "endDt",
                "ncs",
                "prjBg",
                "prjDes",
                "xptEff",
                "csfHeldYn",
                "svnDpm");
        assertAllPropertiesRequired(
                CouncilDto.FeasibilityResponse.class, "prjBg", "lglRglNm", "flMngNo");
        assertAllPropertiesRequired(CouncilDto.PerformanceResponse.class);
        assertAllPropertiesRequired(CouncilDto.SelfCheckItemResponse.class, "ckgRcrd");
        assertAllPropertiesRequired(
                CouncilDto.CommitteeMemberResponse.class, "usrNm", "bbrNm", "ptCNm");
        assertAllPropertiesRequired(CouncilDto.CommitteeListResponse.class);
        assertAllPropertiesRequired(CouncilDto.ScheduleSlotResponse.class);
        assertAllPropertiesRequired(
                CouncilDto.MemberScheduleStatus.class, "usrNm", "bbrNm", "ptCNm", "csfHpYn");
        assertAllPropertiesRequired(CouncilDto.ScheduleStatusResponse.class);
        assertAllPropertiesRequired(
                CouncilDto.EvaluationItemResponse.class, "usrNm", "ckgRcrd", "ckgOpnn");
        assertAllPropertiesRequired(CouncilDto.CheckItemAvgScore.class);
        assertAllPropertiesRequired(CouncilDto.EvaluationSummaryResponse.class);
        assertAllPropertiesRequired(
                CouncilDto.PlanEvaluationItemResponse.class, "usrNm", "evalOpnn");
        assertAllPropertiesRequired(CouncilDto.PlanBusinessVerdict.class);
        assertAllPropertiesRequired(CouncilDto.PlanEvaluationSummaryResponse.class);
        assertAllPropertiesRequired(CouncilDto.PlanResultSummaryResponse.class);
        assertAllPropertiesRequired(CouncilDto.PlanTargetsResponse.class);
        assertAllPropertiesRequired(
                CouncilDto.PlanTargetBusiness.class,
                "abusNm",
                "pulDtt",
                "svnHdq",
                "svnDpmNm",
                "prjDes",
                "sttDt",
                "endDt",
                "prjBg",
                "assetBg",
                "costBg",
                "basePrjBg",
                "baseAssetBg",
                "baseCostBg");
        assertAllPropertiesRequired(
                CouncilDto.ResultResponse.class, "synOpnn", "ckgOpnn", "flMngNo");
        assertAllPropertiesRequired(CouncilDto.ApprovalResponse.class);
        assertAllPropertiesRequired(
                CouncilDto.QnaResponse.class, "qtnNm", "repEno", "repNm", "repCone");
        assertAllPropertiesRequired(CouncilDto.NotifyResponse.class, "usrNm", "bbrNm", "temNm");
        assertAllPropertiesRequired(
                CouncilDto.SkipRequestResponse.class,
                "omtYn",
                "cnfmCone",
                "cnfmUsid",
                "cnfmDtm",
                "apfMngNo");

        assertEnum(CouncilDto.ListResponse.class, "asctStsC", councilStatuses());
        assertEnum(CouncilDto.DetailResponse.class, "asctStsC", councilStatuses());
        assertEnum(CouncilDto.ListResponse.class, "dbrTc", hearingTypes());
        assertEnum(CouncilDto.DetailResponse.class, "dbrTc", hearingTypes());
        assertEnum(CouncilDto.ListResponse.class, "csfHeldYn", "Y", "N");
        assertEnum(CouncilDto.DetailResponse.class, "csfHeldYn", "Y", "N");
        assertEnum(CouncilDto.FeasibilityResponse.class, "lglRglYn", "Y", "N");
        assertEnum(CouncilDto.FeasibilityResponse.class, "kpnTc", "10", "20");
        assertEnum(CouncilDto.SelfCheckItemResponse.class, "ckgItmC", checkItemCodes());
        assertEnum(CouncilDto.CommitteeMemberResponse.class, "vlrTc", committeeTypes());
        assertEnum(CouncilDto.CommitteeMemberResponse.class, "cnfmYn", "Y", "N");
        assertEnum(CouncilDto.ScheduleSlotResponse.class, "psbYn", "Y", "N");
        assertEnum(CouncilDto.MemberScheduleStatus.class, "vlrTc", committeeTypes());
        assertEnum(CouncilDto.MemberScheduleStatus.class, "csfHpYn", "Y", "N");
        assertEnum(CouncilDto.EvaluationItemResponse.class, "ckgItmC", checkItemCodes());
        assertEnum(CouncilDto.CheckItemAvgScore.class, "ckgItmC", checkItemCodes());
        assertEnum(CouncilDto.PlanEvaluationItemResponse.class, "pprtYn", "Y", "N");
        assertEnum(CouncilDto.PlanBusinessVerdict.class, "finalPprtYn", "Y", "N");
        assertEnum(CouncilDto.QnaResponse.class, "repYn", "Y", "N");
        assertEnum(CouncilDto.SkipRequestResponse.class, "omtYn", "Y", "N");
    }

    @Test
    void costResponsesExposeRequiredNullableAndEnumContracts() {
        assertPropertiesRequiredExcept(
                CostDto.Response.class,
                fields("applicationInfo"),
                "costBgNo",
                "bgSno",
                "lstYn",
                "xcr",
                "fcAmt",
                "xcrBseDt",
                "bgUntAbusCNm",
                "ioeCNm",
                "dfrCleCNm",
                "tmnYnNm",
                "abusTcNm",
                "bseYy",
                "cncdRfrNo",
                "terminals",
                "costSvnDpmNm",
                "svnTemNm",
                "cgprNm",
                "cgprPtCNm",
                "assetBg",
                "dvcBg",
                "hwBg",
                "swBg",
                "costBg",
                "dupBgAmt",
                "assetDupBg",
                "costDupBg",
                "prevBgAmt",
                "prevDupBg",
                "delYn",
                "apfMngNo",
                "apfSts",
                "apfStsC",
                "lstChgDtm");
        assertAllPropertiesRequired(
                CostDto.TerminalDto.class,
                "tmnMngNo",
                "sno",
                "fcAmt",
                "xcr",
                "xcrBseDt",
                "cgprNm",
                "tmnClsfCNm",
                "tmnKdTcNm",
                "dfrCleCNm",
                "termSvnTemNm",
                "termSvnDpmNm");
        assertAllPropertiesRequired(CostDto.BulkResponse.class);
        assertEnum(CostDto.Response.class, "lstYn", "Y", "N");
        assertEnum(CostDto.Response.class, "sectSysUtzYn", "Y", "N");
        assertEnum(CostDto.Response.class, "tmnYn", "Y", "N");
        assertEnum(CostDto.Response.class, "delYn", "Y", "N");
    }

    @Test
    void documentAndApprovalResponsesExposeRequiredAndNullableContracts() {
        assertAllPropertiesRequired(
                ServiceRequestDocDto.Response.class, "svnDpmNm", "svnTemNm", "fstEnrUsNm");
        assertAllPropertiesRequired(ServiceRequestDocDto.VersionResponse.class);
        assertAllPropertiesRequired(ServiceRequestDocDto.DashboardResponse.class);
        assertAllPropertiesRequired(ServiceRequestDocDto.MonthlyCount.class);
        assertAllPropertiesRequired(ServiceRequestDocDto.ReviewingItem.class);
        assertAllPropertiesRequired(ServiceRequestDocDto.BadgeCountResponse.class);
        assertEnum(ServiceRequestDocDto.Response.class, "delYn", "Y", "N");
        assertEnum(ServiceRequestDocDto.VersionResponse.class, "delYn", "Y", "N");
        assertEnum(ServiceRequestDocDto.ReviewingItem.class, "status", "reviewing", "delayed");

        assertAllPropertiesRequired(
                ApplicationDto.Response.class,
                "apfDtlCone",
                "apfSts",
                "apfStsC",
                "rqsNm",
                "rqsBbrC",
                "rqsBbrNm",
                "rqsOpnn");
        assertPropertiesRequiredExcept(
                ApplicationDto.ApproverResponse.class,
                Set.of("usrNm", "ptCNm", "bbrNm"),
                "dcdTp",
                "dcdDt",
                "dcdOpnn",
                "dcdSts",
                "lstDcdYn");
        assertSchemaNullable(ApplicationDto.ApproverResponse.class, "usrNm", "ptCNm", "bbrNm");
        assertAllPropertiesRequired(ApplicationDto.DashboardResponse.class);
        assertAllPropertiesRequired(ApplicationDto.MonthlyCount.class);
        assertAllPropertiesRequired(ApplicationDto.PendingItem.class);
        assertAllPropertiesRequired(ApplicationDto.ApprovalBadgeCountResponse.class);
        assertEnum(ApplicationDto.PendingItem.class, "urgency", "urgent", "normal");
        assertEnum(ApplicationDto.ApproverResponse.class, "lstDcdYn", "Y", "N");

        assertAllPropertiesRequired(ApplicationInfoDto.class, "apfSts", "apfStsC", "rqsOpnn");
        assertAllPropertiesRequired(
                ApplicationInfoDto.ApproverDto.class, "dcdTp", "dcdSts", "dcdDt", "dcdOpnn");
    }

    @Test
    void boardResponsesExposeRequiredNullableAndEnumContracts() {
        assertAllPropertiesRequired(BoardMetaDto.Response.class, "rmk");
        // 작성자명·소속부서명은 목록 경량 조회에서 채우지 않는 경우가 있어 nullable 계약이다.
        assertAllPropertiesRequired(
                BoardPostDto.ListItem.class,
                "nacUnqId",
                "sttYmd",
                "endYmd",
                "fstEnrUsNm",
                "fstEnrBbrNm");
        // 상세도 작성자 원장을 찾지 못하면 작성자명·소속부서명을 null로 채운다(Detail.from).
        assertAllPropertiesRequired(
                BoardPostDto.Detail.class,
                "nacUnqId",
                "sttYmd",
                "endYmd",
                "bbrC",
                "hrkNacNo",
                "fstEnrUsNm",
                "fstEnrBbrNm");
        assertAllPropertiesRequired(BoardCommentDto.Response.class, "hrkCmmtMngNo", "fstEnrUsNm");
        assertEnum(BoardMetaDto.Response.class, "repUseYn", "Y", "N");
        assertEnum(BoardMetaDto.Response.class, "cmmtUseYn", "Y", "N");
        assertEnum(BoardMetaDto.Response.class, "flEsnYn", "Y", "N");
        assertEnum(BoardMetaDto.Response.class, "hedTagUseYn", "Y", "N");
        assertEnum(BoardMetaDto.Response.class, "useYn", "Y", "N");
        assertEnum(BoardPostDto.ListItem.class, "ancYn", "Y", "N");
        assertEnum(BoardPostDto.ListItem.class, "xpoYn", "Y", "N");
        assertEnum(BoardPostDto.ListItem.class, "flApgYn", "Y", "N");
        assertEnum(BoardCommentDto.Response.class, "delYn", "Y", "N");
    }

    @Test
    void budgetResponsesExposeRequiredAndNullableContracts() {
        assertAllPropertiesRequired(BudgetWorkDto.IoeCategoryResponse.class, "dupRt");
        assertAllPropertiesRequired(BudgetWorkDto.SummaryResponse.class);
        assertAllPropertiesRequired(BudgetWorkDto.SummaryItem.class, "dupRt");
        assertAllPropertiesRequired(BudgetWorkDto.SummaryTotals.class);
        assertAllPropertiesRequired(BudgetWorkDto.ApplyResponse.class);
        assertAllPropertiesRequired(BudgetWorkDto.ProjectSummaryResponse.class);
        assertAllPropertiesRequired(BudgetWorkDto.ProjectSummaryCategory.class, "cdDes", "dupRt");
        assertAllPropertiesRequired(BudgetWorkDto.ProjectSummaryItem.class);
        assertAllPropertiesRequired(BudgetWorkDto.CategoryAmount.class);

        assertAllPropertiesRequired(
                BudgetStatusDto.ProjectResponse.class,
                "svnDpmCNm",
                "tlrUsidNm",
                "usidNm",
                "dvmDpmCNm",
                "dvmTlrUsidNm",
                "dvmUsidNm",
                "exePttYn",
                "rprStsNm",
                "adjDevBg",
                "adjMachBg",
                "adjIntanBg",
                "adjAssetBg",
                "adjRentBg",
                "adjTravelBg",
                "adjServiceBg",
                "adjMiscBg",
                "adjCostBg",
                "adjTotalBg");
        assertAllPropertiesRequired(
                BudgetStatusDto.CostResponse.class,
                "ioeCNm",
                "costSvnDpmNm",
                "svnTemNm",
                "adjRentBg",
                "adjTravelBg",
                "adjServiceBg",
                "adjMiscBg",
                "adjTotalBg");
        assertAllPropertiesRequired(BudgetStatusDto.OrdinaryResponse.class, "machCur", "intanCur");

        assertAllPropertiesRequired(
                ItBudgetDto.CategoryRow.class, "ioeDtlCode", "codeAbbrNm", "groupName");
        assertAllPropertiesRequired(ItBudgetDto.SummaryResponse.class);
        assertAllPropertiesRequired(ItBudgetDto.YoyRow.class, "diffRate");
        assertAllPropertiesRequired(ItBudgetDto.FssMappingRow.class, "ioeDtlCode");
        assertAllPropertiesRequired(ItBudgetDto.ComparisonResponse.class);
    }

    @Test
    void authAndBizplanResponsesExposeRequiredAndNullableContracts() {
        assertContract(
                AuthDto.LoginResponse.class,
                fields("eno", "empNm", "athIds", "bbrC", "temC"),
                fields());

        assertContract(
                BizplanDto.ListItem.class,
                fields(
                        "abusMngNo",
                        "abusNm",
                        "svnDpmC",
                        "svnDpmNm",
                        "bseYy",
                        "totRqmAmt",
                        "stsTc",
                        "lstChgDtm"),
                fields(
                        "abusNm",
                        "svnDpmC",
                        "svnDpmNm",
                        "bseYy",
                        "totRqmAmt",
                        "stsTc",
                        "lstChgDtm"));
        assertContract(
                BizplanDto.Schedule.class,
                fields("sno", "dsdCone", "sttDt", "endDt"),
                fields("dsdCone", "sttDt", "endDt"));
        assertContract(
                BizplanDto.Item.class,
                fields(
                        "sno",
                        "gclNm",
                        "ioeC",
                        "qty",
                        "amt",
                        "fcAmt",
                        "curC",
                        "xcr",
                        "xcrBseDt",
                        "cttSno"),
                fields(
                        "gclNm",
                        "ioeC",
                        "qty",
                        "amt",
                        "fcAmt",
                        "curC",
                        "xcr",
                        "xcrBseDt",
                        "cttSno"));
        assertContract(
                BizplanDto.Contract.class,
                fields("sno", "cttNm", "nowCttManrC", "cttTrmMmNbr"),
                fields("cttNm", "nowCttManrC", "cttTrmMmNbr"));
        assertContract(
                BizplanDto.Detail.class,
                fields(
                        "abusMngNo",
                        "abusNm",
                        "bgNo",
                        "totRqmAmt",
                        "itPtlEdrtTc",
                        "redtConeInf",
                        "stsTc",
                        "schedules",
                        "items",
                        "contracts"),
                fields("abusNm", "bgNo", "totRqmAmt", "itPtlEdrtTc", "redtConeInf"));
    }

    @Test
    void executionDocumentResponsesExposeRequiredAndNullableContracts() {
        assertContract(
                ContractDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm"),
                fields("cttNm", "cttAmt", "reqUsid", "reqDtm"));
        assertContract(
                ContractDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "cttNm",
                        "cttOppNm",
                        "cttAmt",
                        "cttDt",
                        "itPtlCttManrC",
                        "cttManrRsn",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "tgtNm",
                        "reqCone",
                        "cttNm",
                        "cttOppNm",
                        "cttAmt",
                        "cttDt",
                        "itPtlCttManrC",
                        "cttManrRsn",
                        "reqUsid",
                        "reqDtm"));

        assertContract(
                DeliberationDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "taskDbrRltTc",
                        "reqUsid",
                        "reqDtm"),
                fields("taskDbrRltTc", "reqUsid", "reqDtm"));
        assertContract(
                DeliberationDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "taskDbrTc",
                        "taskDbrRltTc",
                        "taskDbrDt",
                        "taskDbrTod",
                        "taskDbrOmtYn",
                        "taskDbrOmtRsn",
                        "opnnCone",
                        "apvTrdnRsnCone",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "tgtNm",
                        "reqCone",
                        "taskDbrTc",
                        "taskDbrRltTc",
                        "taskDbrDt",
                        "taskDbrTod",
                        "taskDbrOmtYn",
                        "taskDbrOmtRsn",
                        "opnnCone",
                        "apvTrdnRsnCone",
                        "reqUsid",
                        "reqDtm"));

        assertContract(
                EstimateDto.ListItem.class,
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "abusNm",
                        "totalBudget",
                        "sttDtm",
                        "endDtm",
                        "svnDpmC",
                        "svnDpmNm",
                        "stsTc",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "abusNm",
                        "totalBudget",
                        "sttDtm",
                        "endDtm",
                        "svnDpmC",
                        "svnDpmNm",
                        "stsTc",
                        "reqUsid",
                        "reqDtm"));
        assertContract(
                EstimateDto.Line.class,
                fields("svnTemC", "ioeC", "rqmBgAmt", "opnnCone"),
                fields("rqmBgAmt", "opnnCone"));
        assertContract(
                EstimateDto.Detail.class,
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "abusNm",
                        "stsTc",
                        "reqCone",
                        "reqUsid",
                        "reqDtm",
                        "lines"),
                fields("abusNm", "reqCone", "reqUsid", "reqDtm"));

        assertContract(
                PaymentDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm"),
                fields("cttNm", "cttAmt", "reqUsid", "reqDtm"));
        assertContract(
                PaymentDto.Line.class,
                fields("dfrTod", "dfrAmt", "dfrDt", "dfrMplDt", "opnnCone"),
                fields("dfrAmt", "dfrDt", "dfrMplDt", "opnnCone"));
        assertContract(
                PaymentDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm",
                        "lines"),
                fields("tgtNm", "reqCone", "cttNm", "cttAmt", "reqUsid", "reqDtm"));
    }

    @Test
    void notificationAndTiptapResponsesExposeRequiredNullableAndEnumContracts() {
        assertContract(
                NotificationDto.Item.class,
                fields(
                        "infmMsgNo",
                        "itPtlInfmSvcTc",
                        "ttl",
                        "infmMsgCone",
                        "infmRcdUrl",
                        "inqYn",
                        "inqDtm",
                        "fstEnrDtm"),
                fields("ttl", "infmMsgCone", "infmRcdUrl", "inqDtm"));
        assertContract(NotificationDto.UnreadCount.class, fields("count"), fields());
        assertContract(NotificationDto.MarkAllReadResponse.class, fields("updated"), fields());
        assertEnum(
                NotificationDto.Item.class, "itPtlInfmSvcTc", "01", "02", "03", "04", "05", "06");
        assertEnum(NotificationDto.Item.class, "inqYn", "Y", "N");

        assertContract(TiptapVariableDto.MetadataResponse.class, fields("categories"), fields());
        assertContract(
                TiptapVariableDto.CategoryMetadata.class,
                fields("code", "label", "years", "projects", "items"),
                fields("projects"));
        assertContract(TiptapVariableDto.ProjectRef.class, fields("code", "name"), fields());
        assertContract(TiptapVariableDto.ItemRef.class, fields("key", "label"), fields());
        assertContract(TiptapVariableDto.ResolveResponse.class, fields("results"), fields());
        assertContract(TiptapVariableDto.ResolvedValue.class, fields("value", "status"), fields());
        assertEnum(
                TiptapVariableDto.ResolvedValue.class,
                "status",
                "OK",
                "MISSING",
                "FORBIDDEN",
                "INVALID");
    }

    /**
     * ProjectDto.Response는 필드 대부분이 requiredMode 미지정 상태라 {@link #assertAllPropertiesRequired}를 그대로 쓸
     * 수 없다(전체 필드가 required여야 한다는 전제와 충돌). Task 4에서 신규로 노출한 3개 파생 금액 필드(prjBgAmt, mplAmt, dfrAmt)와
     * 사업 응답의 금액 계약만 좁혀서 검증한다. 사업계획(Bizplan)의 동명 필드 `totRqmAmt`는 총소요금액이라 이름이 옳으므로 개명 대상이 아니다(BE-36).
     */
    @Test
    void projectResponseExposesAmountContracts() {
        Schema<?> schema = resolve(ProjectDto.Response.class);
        assertThat(schema.getRequired()).contains("prjBgAmt", "mplAmt", "dfrAmt", "tyyBgAmt");
        assertThat(Boolean.TRUE.equals(property(schema, "prjBgAmt").getNullable())).isFalse();
        assertThat(Boolean.TRUE.equals(property(schema, "mplAmt").getNullable())).isFalse();
        assertThat(Boolean.TRUE.equals(property(schema, "tyyBgAmt").getNullable())).isFalse();
        assertThat(Boolean.TRUE.equals(property(schema, "dfrAmt").getNullable())).isTrue();
        assertThat(property(schema, "tyyBgAmt").getDescription()).contains("당해 요청금액");
        assertThat(property(schema, "mplAmt").getDescription()).contains("원화 환산");
        assertThat(property(schema, "dfrAmt").getDescription()).contains("원화 지급금액");
        assertThat(property(schema, "prjBgAmt").getDescription()).contains("총소요금액");
    }

    private static void assertContract(Class<?> type, Set<String> required, Set<String> nullable) {
        Schema<?> schema = resolve(type);
        assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrderElementsOf(required);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(required);
        required.forEach(
                name ->
                        assertThat(Boolean.TRUE.equals(property(schema, name).getNullable()))
                                .as("%s.%s nullable", type.getSimpleName(), name)
                                .isEqualTo(nullable.contains(name)));
    }

    private static void assertAllPropertiesRequired(Class<?> type, String... nullableProperties) {
        Schema<?> schema = resolve(type);
        Set<String> properties = schema.getProperties().keySet();
        assertContract(type, properties, Set.of(nullableProperties));
    }

    private static void assertSchemaNullable(Class<?> type, String... properties) {
        Schema<?> schema = resolve(type);
        for (String name : properties) {
            assertThat(Boolean.TRUE.equals(property(schema, name).getNullable()))
                    .as("%s.%s nullable", type.getSimpleName(), name)
                    .isTrue();
        }
    }

    private static void assertPropertiesRequiredExcept(
            Class<?> type, Set<String> optionalProperties, String... nullableProperties) {
        Schema<?> schema = resolve(type);
        Set<String> properties = schema.getProperties().keySet();
        Set<String> required = new java.util.HashSet<>(properties);
        required.removeAll(optionalProperties);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(required);
        Set<String> nullable = Set.of(nullableProperties);
        required.forEach(
                name ->
                        assertThat(Boolean.TRUE.equals(property(schema, name).getNullable()))
                                .as("%s.%s nullable", type.getSimpleName(), name)
                                .isEqualTo(nullable.contains(name)));
    }

    private static void assertEnum(Class<?> type, String property, String... values) {
        assertThat(
                        property(resolve(type), property).getEnum().stream()
                                .map(String::valueOf)
                                .toList())
                .containsExactly(values);
    }

    private static void assertStringProperties(Class<?> type, String... properties) {
        Schema<?> schema = resolve(type);
        for (String property : properties) {
            assertThat(property(schema, property).getType())
                    .as("%s.%s type", type.getSimpleName(), property)
                    .isEqualTo("string");
        }
    }

    private static void assertPatternProperties(
            Class<?> type, String expectedPattern, String... properties) {
        Schema<?> schema = resolve(type);
        for (String property : properties) {
            assertThat(property(schema, property).getPattern())
                    .as("%s.%s pattern", type.getSimpleName(), property)
                    .isEqualTo(expectedPattern);
        }
    }

    private static void assertDateProperties(Class<?> type, String... properties) {
        Schema<?> schema = resolve(type);
        for (String property : properties) {
            assertThat(property(schema, property).getFormat())
                    .as("%s.%s format", type.getSimpleName(), property)
                    .isEqualTo("date");
        }
    }

    private static void assertFormat(Class<?> type, String propertyName, String expectedFormat) {
        assertThat(property(resolve(type), propertyName).getFormat())
                .as("%s.%s format", type.getSimpleName(), propertyName)
                .isEqualTo(expectedFormat);
    }

    private static Schema<?> resolve(Class<?> type) {
        ResolvedSchema resolved =
                ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(false));
        return resolved.schema;
    }

    private static Schema<?> property(Schema<?> schema, String name) {
        return (Schema<?>) schema.getProperties().get(name);
    }

    private static Set<String> fields(String... names) {
        return Set.of(names);
    }

    private static String[] councilStatuses() {
        return new String[] {
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "99"
        };
    }

    private static String[] hearingTypes() {
        return new String[] {"01", "02", "03", "04", "05"};
    }

    private static String[] committeeTypes() {
        return new String[] {"01", "02", "03", "04"};
    }

    private static String[] checkItemCodes() {
        return new String[] {"01", "02", "03", "04", "05", "06"};
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({ItBudgetApplicationController.class, SwaggerConfig.class})
    static class App {}
}
