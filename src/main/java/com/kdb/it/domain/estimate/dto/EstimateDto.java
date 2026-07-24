package com.kdb.it.domain.estimate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 소요예산 산정 API 요청/응답 DTO 모음. */
public final class EstimateDto {

    private EstimateDto() {}

    @Schema(name = "EstimateCreateRequest", description = "소요예산 산정 신규 신청 요청")
    public record CreateRequest(
            @NotBlank @Size(max = 30) String cncdRfrNo, @Size(max = 300) String reqCone) {}

    @Schema(name = "EstimateUpdateRequest", description = "소요예산 산정 마스터 수정 요청(작성중)")
    public record UpdateRequest(@Size(max = 300) String reqCone) {}

    @Schema(name = "EstimateStatusRequest", description = "소요예산 산정 상태 전이 요청")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}

    @Schema(name = "EstimateLineRequest", description = "팀별 산정 명세 1행")
    public record LineRequest(
            @NotBlank @Size(max = 5) String svnTemC,
            @NotBlank @Size(max = 7) String ioeC,
            @NotNull @DecimalMin(value = "0", message = "산정 예산액은 0 이상이어야 합니다.") BigDecimal rqmBgAmt,
            @Size(max = 6000) String opnnCone) {}

    @Schema(name = "EstimateLinesRequest", description = "팀별 산정 명세 일괄 저장 요청(진행중)")
    public record LinesRequest(@NotNull List<LineRequest> lines) {}

    @Schema(name = "EstimateListItem", description = "소요예산 산정 목록 항목")
    public record ListItem(
            String rqmBgReqDocNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            String abusNm,
            BigDecimal totalBudget,
            LocalDate sttDtm,
            LocalDate endDtm,
            String svnDpmC,
            String svnDpmNm,
            String stsTc,
            String reqUsid,
            LocalDateTime reqDtm) {}

    @Schema(name = "EstimateLine", description = "팀별 산정 명세 응답")
    public record Line(String svnTemC, String ioeC, BigDecimal rqmBgAmt, String opnnCone) {}

    @Schema(name = "EstimateDetail", description = "소요예산 산정 상세")
    public record Detail(
            String rqmBgReqDocNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            String abusNm,
            String stsTc,
            String reqCone,
            String reqUsid,
            LocalDateTime reqDtm,
            List<Line> lines) {}
}
