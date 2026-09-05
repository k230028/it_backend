package com.kdb.it.common.approval.itbudget.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRef;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ChangedSource;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.DocumentRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.PreviewRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ItBudgetApprovalDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void previewRequest_acceptsTypedApproversAndSources_andSerializesTheirStableJsonNames()
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
                                + "\"documents\":[{\"clientDocumentKey\":\"doc-1\",\"sources\":[{\"kind\":\"PROJECT\","
                                + "\"id\":\"P-001\",\"revision\":1,\"order\":1}]}]}");
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
                        1,
                        SourceKind.PROJECT,
                        "P-001",
                        1,
                        "차세대 시스템 구축",
                        "E20001",
                        java.time.LocalDateTime.of(2026, 9, 6, 14, 25));
        ItBudgetApprovalException exception =
                new ItBudgetApprovalException(
                        HttpStatus.CONFLICT,
                        "IT_BUDGET_SOURCE_CHANGED",
                        "신청 대상이 중간에 변경되었습니다.",
                        List.of(changedSource));

        var response = new GlobalExceptionHandler().handleItBudgetApproval(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("status", 409)
                .containsEntry("code", "IT_BUDGET_SOURCE_CHANGED")
                .containsEntry("message", "신청 대상이 중간에 변경되었습니다.")
                .containsKey("timestamp")
                .containsEntry("changedSources", List.of(changedSource));
    }
}
