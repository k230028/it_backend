package com.kdb.it.domain.council.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;

/** 협의회의 결재·질의응답·생략 판정 API 계약을 분리한 기반 타입입니다. */
public class CouncilWorkflowDto {

    @Schema(name = "ApprovalRequest")
    public record ApprovalRequest(@NotBlank String approverEno, String rqsOpnn) {}

    @Schema(name = "ApprovalCallbackRequest")
    public record ApprovalCallbackRequest(boolean approved) {}

    @Schema(name = "ApprovalResponse", requiredProperties = "apfMngNo")
    public record ApprovalResponse(String apfMngNo) {}

    @Schema(name = "ResultApprovalRequest")
    public record ResultApprovalRequest(
            @NotBlank String teamLeadEno, @NotBlank String deptHeadEno, String rqsOpnn) {}

    @Schema(name = "QnaCreateRequest")
    public record QnaCreateRequest(@NotBlank String qtnCone) {}

    @Schema(name = "QnaReplyRequest")
    public record QnaReplyRequest(@NotBlank String repCone) {}

    @Schema(name = "QnaUpdateRequest")
    public record QnaUpdateRequest(@NotBlank String qtnCone) {}

    @Schema(
            name = "QnaResponse",
            requiredProperties = {
                "qtnId", "qtnEno", "qtnNm", "qtnCone", "repEno", "repNm", "repCone", "repYn"
            })
    public record QnaResponse(
            String qtnId,
            String qtnEno,
            @Schema(nullable = true) String qtnNm,
            String qtnCone,
            @Schema(nullable = true) String repEno,
            @Schema(nullable = true) String repNm,
            @Schema(nullable = true) String repCone,
            @Schema(allowableValues = {"Y", "N"}) String repYn) {}

    @Schema(
            name = "NotifyResponse",
            requiredProperties = {"eno", "usrNm", "bbrNm", "temNm"})
    public record NotifyResponse(
            String eno,
            @Schema(nullable = true) String usrNm,
            @Schema(nullable = true) String bbrNm,
            @Schema(nullable = true) String temNm) {}

    @Schema(name = "SkipRequestCreate")
    public record SkipRequestCreate(@NotBlank String rsn, @NotBlank String flMpnId) {}

    @Schema(name = "SkipDecisionRequest")
    public record SkipDecisionRequest(
            @NotBlank String omtYn,
            @NotBlank String cnfmCone,
            @NotEmpty List<String> approverEnos) {}

    @Schema(
            name = "SkipRequestResponse",
            requiredProperties = {
                "asctId",
                "rsn",
                "flMpnId",
                "rqsUsid",
                "rqsDtm",
                "decided",
                "omtYn",
                "cnfmCone",
                "cnfmUsid",
                "cnfmDtm",
                "apfMngNo"
            })
    public record SkipRequestResponse(
            String asctId,
            String rsn,
            String flMpnId,
            String rqsUsid,
            LocalDateTime rqsDtm,
            boolean decided,
            @Schema(
                            nullable = true,
                            allowableValues = {"Y", "N"})
                    String omtYn,
            @Schema(nullable = true) String cnfmCone,
            @Schema(nullable = true) String cnfmUsid,
            @Schema(nullable = true) LocalDateTime cnfmDtm,
            @Schema(nullable = true) String apfMngNo) {}
}
