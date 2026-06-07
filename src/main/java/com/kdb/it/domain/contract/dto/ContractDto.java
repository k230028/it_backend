package com.kdb.it.domain.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 입찰계약 API 요청/응답 DTO 모음. */
public final class ContractDto {
    private ContractDto() {}

    @Schema(name = "ContractCreateRequest", description = "입찰계약 신규 의뢰 요청")
    public record CreateRequest(
            @NotBlank @Size(max = 3) String bgPrnTc,
            @NotBlank @Size(max = 30) String cncdRfrNo,
            @Size(max = 300) String reqCone
    ) {}

    @Schema(name = "ContractUpdateRequest", description = "입찰계약 마스터 수정(작성중)")
    public record UpdateRequest(@Size(max = 300) String reqCone) {}

    @Schema(name = "ContractStatusRequest", description = "입찰계약 상태 전이")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}

    @Schema(name = "ContractWorkRequest", description = "계약 정보 입력(진행중)")
    public record WorkRequest(
            @Size(max = 2) String cttManrC,
            @Size(max = 1000) String cttManrRsn,
            @Size(max = 100) String cttNm,
            BigDecimal cttAmt,
            @Size(max = 100) String cttOppNm,
            @Size(max = 8) String cttDt
    ) {}

    @Schema(name = "ContractListItem", description = "입찰계약 목록 항목")
    public record ListItem(
            String docMngNo, Integer docVrsSno, String bgPrnTc, String cncdRfrNo,
            String stsTc, String cttNm, BigDecimal cttAmt, String reqUsid, java.time.LocalDateTime reqDtm
    ) {}

    @Schema(name = "ContractDetail", description = "입찰계약 상세")
    public record Detail(
            String docMngNo, Integer docVrsSno, String bgPrnTc, String cncdRfrNo, String tgtNm,
            String stsTc, String reqCone, String cttManrC, String cttManrRsn, String cttNm,
            BigDecimal cttAmt, String cttOppNm, String cttDt,
            String reqUsid, java.time.LocalDateTime reqDtm
    ) {}
}
