package com.kdb.it.common.approval.itbudget.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApprovalPerson;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRef;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ChangedSource;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.CodeLabel;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.DocumentRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ErrorResponse;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Form;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Integrity;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ItBudgetSnapshot;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Payload;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Person;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.PreviewDocument;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.PreviewRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.PreviewResponse;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ProjectItem;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Requester;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SnapshotApprovalLine;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SnapshotSource;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SubmissionDocument;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SubmissionRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Summary;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Terminal;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ItBudgetApprovalDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final String DIGEST = "a".repeat(64);

    @Test
    void requesterRequiresIdentityAndNameWhileOptionalPersonRemainsNullable() throws Exception {
        for (String requester :
                List.of("{}", "{\"eno\":null,\"name\":null}", "{\"eno\":\" \",\"name\":\"\"}")) {
            var line =
                    objectMapper.readValue(
                            "{\"requester\":"
                                    + requester
                                    + ",\"approvers\":[{\"role\":\"DEPT_HEAD\",\"eno\":\"E2\",\"name\":\"결재자\",\"rank\":\"부장\",\"date\":\"2026-09-06\"}]}",
                            SnapshotApprovalLine.class);
            assertThat(validator.validate(line))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("requester.eno", "requester.name");
        }
        var valid =
                objectMapper.readValue(
                        "{\"requester\":{\"eno\":\"E1\",\"name\":\"신청자\"},\"approvers\":[{\"role\":\"DEPT_HEAD\",\"eno\":\"E2\",\"name\":\"결재자\",\"rank\":\"부장\",\"date\":\"2026-09-06\"}]}",
                        SnapshotApprovalLine.class);
        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(new Person(null, null, null))).isEmpty();
    }

    @Test
    void previewRequest_acceptsTypedApproversAndSourceRefs_andSerializesTheirStableJsonNames()
            throws Exception {
        PreviewRequest request =
                new PreviewRequest(
                        List.of(new ApproverRef(ApproverRole.TEAM_LEAD, "E20001")),
                        List.of(
                                new DocumentRequest(
                                        "doc-1",
                                        List.of(
                                                new SourceRef(
                                                        SourceKind.PROJECT, "P-001", 1, 1)))));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(objectMapper.writeValueAsString(request))
                .isEqualTo(
                        "{\"approvers\":[{\"role\":\"TEAM_LEAD\",\"eno\":\"E20001\"}],"
                                + "\"documents\":[{\"clientDocumentKey\":\"doc-1\",\"sourceRefs\":[{\"kind\":\"PROJECT\","
                                + "\"id\":\"P-001\",\"revision\":1,\"order\":1}]}]}");
    }

    @Test
    void requestCollections_rejectNullElements() {
        DocumentRequest validDocument =
                new DocumentRequest(
                        "doc-1", List.of(new SourceRef(SourceKind.PROJECT, "P-001", 1, 1)));
        PreviewRequest requestWithNullApprover =
                new PreviewRequest(Collections.singletonList(null), List.of(validDocument));
        PreviewRequest requestWithNullDocument =
                new PreviewRequest(
                        List.of(new ApproverRef(ApproverRole.TEAM_LEAD, "E20001")),
                        Collections.singletonList(null));
        DocumentRequest requestWithNullSource =
                new DocumentRequest("doc-1", Collections.singletonList(null));

        assertThat(validator.validate(requestWithNullApprover))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("approvers[0].<list element>");
        assertThat(validator.validate(requestWithNullDocument))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("documents[0].<list element>");
        assertThat(validator.validate(requestWithNullSource))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("sourceRefs[0].<list element>");
    }

    @Test
    void submissionRequestCollections_rejectNullElements() {
        SubmissionDocument validDocument =
                new SubmissionDocument(
                        "doc-1",
                        DIGEST,
                        List.of(
                                new ItBudgetApprovalDto.SourceDigest(
                                        SourceKind.PROJECT, "P-001", 1, 1, DIGEST, "사업")));
        SubmissionRequest requestWithNullApprover =
                new SubmissionRequest(
                        DIGEST,
                        "preview-token",
                        Collections.singletonList(null),
                        List.of(validDocument));
        SubmissionRequest requestWithNullDocument =
                new SubmissionRequest(
                        DIGEST,
                        "preview-token",
                        List.of(new ApproverRef(ApproverRole.TEAM_LEAD, "E20001")),
                        Collections.singletonList(null));
        SubmissionDocument requestWithNullSource =
                new SubmissionDocument("doc-1", DIGEST, Collections.singletonList(null));

        assertThat(validator.validate(requestWithNullApprover))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("approvers[0].<list element>");
        assertThat(validator.validate(requestWithNullDocument))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("documents[0].<list element>");
        assertThat(validator.validate(requestWithNullSource))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("sources[0].<list element>");
    }

    @Test
    void submissionDocument_serializesDigestReferencesAsSources() throws Exception {
        SubmissionDocument document =
                new SubmissionDocument(
                        "doc-1",
                        DIGEST,
                        List.of(
                                new ItBudgetApprovalDto.SourceDigest(
                                        SourceKind.PROJECT, "P-001", 1, 1, DIGEST, "사업")));

        assertThat(objectMapper.writeValueAsString(document))
                .isEqualTo(
                        "{\"clientDocumentKey\":\"doc-1\",\"payloadDigest\":\""
                                + DIGEST
                                + "\",\"sources\":[{\"kind\":\"PROJECT\",\"id\":\"P-001\",\"revision\":1,"
                                + "\"order\":1,\"sourceDigest\":\""
                                + DIGEST
                                + "\",\"displayName\":\"사업\"}]}");
    }

    @Test
    void previewResponse_serializesExplicitV2SnapshotAndSourceDigestContract() throws Exception {
        ItBudgetSnapshot snapshot =
                new ItBudgetSnapshot(
                        new Form("it-budget", 2),
                        new Payload(List.of(), List.of(), new Summary("1.000", "2.000", "3.000")),
                        new SnapshotApprovalLine(new Requester("E10001", "신청자", "과장"), List.of()),
                        new Integrity(
                                "SHA-256",
                                "IT_BUDGET_V2",
                                DIGEST,
                                Instant.parse("2026-09-06T05:00:00Z"),
                                List.of(
                                        new SnapshotSource(
                                                SourceKind.PROJECT, "P-001", 1, 1, DIGEST))));
        PreviewResponse response =
                new PreviewResponse(
                        DIGEST,
                        "preview-token",
                        Instant.parse("2026-09-06T06:00:00Z"),
                        List.of(
                                new PreviewDocument(
                                        "doc-1",
                                        snapshot,
                                        DIGEST,
                                        List.of(
                                                new ItBudgetApprovalDto.SourceDigest(
                                                        SourceKind.PROJECT,
                                                        "P-001",
                                                        1,
                                                        1,
                                                        DIGEST,
                                                        "사업")))));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(validator.validate(response)).isEmpty();
        assertThat(json.path("previewDigest").asText()).isEqualTo(DIGEST);
        assertThat(json.at("/documents/0/snapshot/form/id").asText()).isEqualTo("it-budget");
        assertThat(json.at("/documents/0/sources/0/sourceDigest").asText()).isEqualTo(DIGEST);
        assertThat(json.at("/documents/0/snapshot/payload/summary/total").isTextual()).isTrue();
        assertThat(json.at("/documents/0/snapshot/payload/summary/total").asText())
                .isEqualTo("1.000");
        assertThat(json.at("/documents/0/snapshot/integrity/capturedAt").isTextual()).isTrue();
        assertThat(json.at("/documents/0/snapshot/integrity/capturedAt").asText())
                .isEqualTo("2026-09-06T05:00:00Z");
    }

    @Test
    void snapshotAmountsExchangeRatesAndQuantities_serializeAsScaledStrings() {
        ProjectItem item =
                new ProjectItem(
                        "I1", 1, 1, new CodeLabel("BT", "예산유형"), "서버", "3", "KRW", "4.000", "산정근거");
        Terminal terminal =
                new Terminal(
                        "T1",
                        1,
                        1,
                        new CodeLabel("CL", "분류"),
                        new CodeLabel("KD", "종류"),
                        "업무용",
                        "사양",
                        "USD",
                        "1.2345",
                        "5.000",
                        "6.000");

        var itemJson = objectMapper.valueToTree(item);
        var terminalJson = objectMapper.valueToTree(terminal);

        assertThat(itemJson.path("id").asText()).isEqualTo("I1");
        assertThat(terminalJson.path("id").asText()).isEqualTo("T1");

        assertThat(itemJson.path("quantity").isTextual()).isTrue();
        assertThat(itemJson.path("quantity").asText()).isEqualTo("3");
        assertThat(itemJson.path("amount").isTextual()).isTrue();
        assertThat(itemJson.path("amount").asText()).isEqualTo("4.000");
        assertThat(terminalJson.path("exchangeRate").isTextual()).isTrue();
        assertThat(terminalJson.path("exchangeRate").asText()).isEqualTo("1.2345");
        assertThat(terminalJson.path("foreignAmount").asText()).isEqualTo("5.000");
        assertThat(terminalJson.path("budgetAmount").asText()).isEqualTo("6.000");
    }

    @Test
    void snapshotNumericStrings_requireExactScaleAndAllowNegativeValues() {
        for (String invalid : Arrays.asList(null, "", " ", "1", "1.00", "1.0000", "1e3")) {
            assertThat(
                            validator.validateValue(
                                    ItBudgetApprovalDto.Project.class,
                                    "currentRequestAmount",
                                    invalid))
                    .isNotEmpty();
        }
        for (String valid : List.of("0.000", "-1.000", "123456789.123")) {
            assertThat(
                            validator.validateValue(
                                    ItBudgetApprovalDto.Project.class,
                                    "currentRequestAmount",
                                    valid))
                    .isEmpty();
        }
        Summary validSummary = new Summary("-1.000", "0.000", "2.000");
        Summary invalidMoneyScale = new Summary("1.00", "0.000", "2.000");
        ProjectItem invalidQuantity =
                new ProjectItem(
                        "I1",
                        1,
                        1,
                        new CodeLabel("BT", "예산유형"),
                        "서버",
                        "1.0",
                        "KRW",
                        "1.000",
                        "산정근거");
        Terminal invalidExchangeRate =
                new Terminal(
                        "T1",
                        1,
                        1,
                        new CodeLabel("CL", "분류"),
                        new CodeLabel("KD", "종류"),
                        "업무용",
                        "사양",
                        "USD",
                        "1.234",
                        "-5.000",
                        "6.000");

        assertThat(validator.validate(validSummary)).isEmpty();
        assertThat(validator.validate(invalidMoneyScale))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("total");
        assertThat(validator.validate(invalidQuantity))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("quantity");
        assertThat(validator.validate(invalidExchangeRate))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("exchangeRate");
    }

    @Test
    void everySnapshotNumericStringField_usesTheSharedWireFormatPatterns() {
        assertPattern(Summary.class, "^-?\\d+\\.\\d{3}$", "total", "asset", "cost");
        assertPattern(ProjectItem.class, "^-?\\d+$", "quantity");
        assertPattern(ProjectItem.class, "^-?\\d+\\.\\d{3}$", "amount");
        assertPattern(
                ItBudgetApprovalDto.Project.class,
                "^-?\\d+\\.\\d{3}$",
                "projectBudget",
                "currentRequestAmount",
                "assetBudget",
                "costBudget");
        assertPattern(ItBudgetApprovalDto.Terminal.class, "^-?\\d+\\.\\d{4}$", "exchangeRate");
        assertPattern(
                ItBudgetApprovalDto.Terminal.class,
                "^-?\\d+\\.\\d{3}$",
                "foreignAmount",
                "budgetAmount");
        assertPattern(
                ItBudgetApprovalDto.Cost.class,
                "^-?\\d+\\.\\d{3}$",
                "totalAmount",
                "assetBudget",
                "costBudget");
        assertPattern(ItBudgetApprovalDto.Cost.class, "^-?\\d+\\.\\d{4}$", "exchangeRate");
    }

    @Test
    void snapshotDates_deserializeAsIsoDatesAndRejectInvalidValues() throws Exception {
        ApprovalPerson person =
                objectMapper.readValue(
                        "{\"eno\":\"E20001\",\"name\":\"결재자\",\"rank\":\"부장\",\"date\":\"2026-09-06\"}",
                        ApprovalPerson.class);

        assertThat(person.date()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(objectMapper.writeValueAsString(person)).contains("\"date\":\"2026-09-06\"");
        assertThatThrownBy(
                        () ->
                                objectMapper.readValue(
                                        "{\"eno\":\"E20001\",\"name\":\"결재자\",\"rank\":\"부장\",\"date\":\"2026-09-31\"}",
                                        ApprovalPerson.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void everySnapshotDateField_usesLocalDateRuntimeType() {
        assertDateType(ApprovalPerson.class, "date");
        assertDateType(
                ItBudgetApprovalDto.Project.class, "startDate", "endDate", "feasibilityDate");
        assertDateType(ItBudgetApprovalDto.Cost.class, "exchangeRateBaseDate", "firstDeferralDate");
    }

    @Test
    void previewRequest_allowsEmptyApproversButRejectsMissingDocuments() {
        PreviewRequest request = new PreviewRequest(List.of(), List.of());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("documents");
    }

    @Test
    void submissionRequest_stillRequiresAtLeastOneApprover() {
        SubmissionDocument document =
                new SubmissionDocument(
                        "doc-1",
                        DIGEST,
                        List.of(
                                new ItBudgetApprovalDto.SourceDigest(
                                        SourceKind.PROJECT, "P-001", 1, 1, DIGEST, "사업")));
        SubmissionRequest request =
                new SubmissionRequest(DIGEST, "preview-token", List.of(), List.of(document));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("approvers");
    }

    @Test
    void previewRequest_rejectsMoreThanOneHundredDocuments() {
        DocumentRequest document =
                new DocumentRequest(
                        "doc-1", List.of(new SourceRef(SourceKind.PROJECT, "P-001", 1, 1)));
        PreviewRequest request =
                new PreviewRequest(
                        List.of(new ApproverRef(ApproverRole.TEAM_LEAD, "E20001")),
                        Collections.nCopies(101, document));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("documents");
    }

    @Test
    void itBudgetApprovalException_handlerPreservesTypedConflictDetails() {
        ChangedSource changedSource =
                new ChangedSource(
                        SourceKind.PROJECT,
                        "P-001",
                        1,
                        "차세대 시스템 구축",
                        "E20001",
                        LocalDateTime.of(2026, 9, 6, 14, 25));
        ItBudgetApprovalException exception =
                new ItBudgetApprovalException(
                        HttpStatus.CONFLICT,
                        "IT_BUDGET_SOURCE_CHANGED",
                        "신청 대상이 중간에 변경되었습니다.",
                        List.of(changedSource));

        var response = new GlobalExceptionHandler().handleItBudgetApproval(exception);

        ErrorResponse body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("IT_BUDGET_SOURCE_CHANGED");
        assertThat(body.message()).isEqualTo("신청 대상이 중간에 변경되었습니다.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.changedSources()).containsExactly(changedSource);

        var json = objectMapper.valueToTree(body);
        assertThat(json.at("/changedSources/0/displayName").asText()).isEqualTo("차세대 시스템 구축");
        assertThat(json.at("/changedSources/0/modifiedBy").asText()).isEqualTo("E20001");
        assertThat(json.at("/changedSources/0/no").isMissingNode()).isTrue();
    }

    @Test
    void itBudgetApprovalException_handlerOmitsEmptyChangedSources() {
        ItBudgetApprovalException exception =
                new ItBudgetApprovalException(
                        HttpStatus.CONFLICT,
                        "IT_BUDGET_PREVIEW_STALE",
                        "미리보기가 오래되었습니다.",
                        List.of());

        var body = new GlobalExceptionHandler().handleItBudgetApproval(exception).getBody();

        assertThat(objectMapper.valueToTree(body).has("changedSources")).isFalse();
    }

    private static void assertPattern(
            Class<?> type, String expectedPattern, String... componentNames) {
        for (String componentName : componentNames) {
            Pattern pattern =
                    Arrays.stream(type.getRecordComponents())
                            .filter(component -> component.getName().equals(componentName))
                            .findFirst()
                            .orElseThrow()
                            .getAccessor()
                            .getAnnotation(Pattern.class);

            assertThat(pattern)
                    .as("%s.%s validation pattern", type.getSimpleName(), componentName)
                    .isNotNull();
            assertThat(pattern.regexp()).isEqualTo(expectedPattern);
        }
    }

    private static void assertDateType(Class<?> type, String... componentNames) {
        for (String componentName : componentNames) {
            Class<?> componentType =
                    Arrays.stream(type.getRecordComponents())
                            .filter(component -> component.getName().equals(componentName))
                            .findFirst()
                            .orElseThrow()
                            .getType();

            assertThat(componentType)
                    .as("%s.%s runtime type", type.getSimpleName(), componentName)
                    .isEqualTo(LocalDate.class);
        }
    }
}
