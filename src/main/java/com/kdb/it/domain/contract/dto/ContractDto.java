package com.kdb.it.domain.contract.dto;

import com.kdb.it.domain.contract.repository.ContractDetailRow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 입찰계약 API 요청/응답 DTO 모음. */
public final class ContractDto {
    private ContractDto() {}

    @Schema(name = "ContractCreateRequest", description = "입찰계약 신규 의뢰 요청")
    public record CreateRequest(
            @NotBlank @Size(max = 3) String ioeC,
            @NotBlank @Size(max = 30) String cncdRfrNo,
            @Size(max = 300) String reqCone) {}

    @Schema(name = "ContractUpdateRequest", description = "입찰계약 마스터 수정(작성중)")
    public record UpdateRequest(@Size(max = 300) String reqCone) {}

    @Schema(name = "ContractStatusRequest", description = "입찰계약 상태 전이")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}

    @Schema(name = "ContractWorkRequest", description = "계약 정보 입력(진행중)")
    public record WorkRequest(
            @Size(max = 2) String itPtlCttManrC,
            @Size(max = 1000) String cttManrRsn,
            @Size(max = 100) String cttNm,
            @DecimalMin(value = "0", message = "계약금액은 0 이상이어야 합니다.") BigDecimal cttAmt,
            @Size(max = 100) String cttOppNm,
            @Size(max = 8) String cttDt) {}

    @Schema(
            name = "ContractListItem",
            description = "입찰계약 목록 항목",
            requiredProperties = {
                "docMngNo",
                "docVrsSno",
                "ioeC",
                "cncdRfrNo",
                "stsTc",
                "cttNm",
                "cttAmt",
                "reqUsid",
                "reqDtm"
            })
    public record ListItem(
            String docMngNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            String stsTc,
            @Schema(nullable = true) String cttNm,
            @Schema(nullable = true) BigDecimal cttAmt,
            @Schema(nullable = true) String reqUsid,
            @Schema(nullable = true) java.time.LocalDateTime reqDtm) {}

    @Schema(
            name = "ContractDetail",
            description = "입찰계약 상세",
            requiredProperties = {
                "docMngNo",
                "docVrsSno",
                "ioeC",
                "cncdRfrNo",
                "tgtNm",
                "stsTc",
                "reqCone",
                "itPtlCttManrC",
                "cttManrRsn",
                "cttNm",
                "cttAmt",
                "cttOppNm",
                "cttDt",
                "reqUsid",
                "reqDtm"
            })
    public record Detail(
            String docMngNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            @Schema(nullable = true) String tgtNm,
            String stsTc,
            @Schema(nullable = true) String reqCone,
            @Schema(nullable = true) String itPtlCttManrC,
            @Schema(nullable = true) String cttManrRsn,
            @Schema(nullable = true) String cttNm,
            @Schema(nullable = true) BigDecimal cttAmt,
            @Schema(nullable = true) String cttOppNm,
            @Schema(nullable = true) String cttDt,
            @Schema(nullable = true) String reqUsid,
            @Schema(nullable = true) java.time.LocalDateTime reqDtm) {
        /**
         * 조회 프로젝션을 상세 응답으로 변환합니다.
         *
         * @param row 입찰계약 상세 조회 행
         * @return 상세 응답
         */
        public static Detail fromProjection(ContractDetailRow row) {
            return new Detail(
                    row.docMngNo(),
                    row.docVrsSno(),
                    row.ioeC(),
                    row.cncdRfrNo(),
                    row.tgtNm(),
                    row.stsTc(),
                    row.reqCone(),
                    row.itPtlCttManrC(),
                    row.cttManrRsn(),
                    row.cttNm(),
                    row.cttAmt(),
                    row.cttOppNm(),
                    row.cttDt(),
                    row.reqUsid(),
                    row.reqDtm());
        }
    }
}
