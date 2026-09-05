package com.kdb.it.common.approval.itbudget.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRef;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ChangedSource;
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
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SnapshotApprovalLine;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SnapshotSource;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SubmissionDocument;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SubmissionRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Summary;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ItBudgetApprovalDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final String DIGEST = "a".repeat(64);

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
                                        SourceKind.PROJECT, "P-001", 1, 1, DIGEST)));
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
                .containsExactly("sourceRefs[0].<list element>");
    }

    @Test
    void previewResponse_serializesExplicitV2SnapshotAndSourceDigestContract() throws Exception {
        ItBudgetSnapshot snapshot =
                new ItBudgetSnapshot(
                        new Form("it-budget", 2),
                        new Payload(
                                List.of(),
                                List.of(),
                                new Summary(
                                        new BigDecimal("0.000"),
                                        new BigDecimal("0.000"),
                                        new BigDecimal("0.000"))),
                        new SnapshotApprovalLine(new Person("E10001", "신청자", "과장"), List.of()),
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
                                                        DIGEST)))));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("previewDigest").asText()).isEqualTo(DIGEST);
        assertThat(json.at("/documents/0/snapshot/form/id").asText()).isEqualTo("it-budget");
        assertThat(json.at("/documents/0/sources/0/sourceDigest").asText()).isEqualTo(DIGEST);
    }

    @Test
    void previewRequest_rejectsMissingApproversAndDocuments() {
        PreviewRequest request = new PreviewRequest(List.of(), List.of());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("approvers", "documents");
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
}
