package com.kdb.it.domain.payment.dto;

import com.kdb.it.domain.payment.repository.PaymentDetailRow;
import com.kdb.it.domain.payment.repository.PaymentLineView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 대금지급 API 요청/응답 DTO 모음. */
public final class PaymentDto {
    private PaymentDto() {}

    @Schema(name = "PaymentCreateRequest", description = "대금지급 신규 의뢰 요청")
    public record CreateRequest(
            @NotBlank @Size(max = 3) String ioeC,
            @NotBlank @Size(max = 30) String cncdRfrNo,
            @Size(max = 300) String reqCone,
            @Size(max = 100) String cttNm,
            @DecimalMin(value = "0", message = "계약금액은 0 이상이어야 합니다.") BigDecimal cttAmt) {}

    @Schema(name = "PaymentUpdateRequest", description = "대금지급 마스터 수정(작성중)")
    public record UpdateRequest(
            @Size(max = 300) String reqCone,
            @Size(max = 100) String cttNm,
            @DecimalMin(value = "0", message = "계약금액은 0 이상이어야 합니다.") BigDecimal cttAmt) {}

    @Schema(name = "PaymentStatusRequest", description = "대금지급 상태 전이")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}

    @Schema(name = "PaymentLineRequest", description = "회차별 지급 1행")
    public record LineRequest(
            @NotNull Integer dfrTod,
            @DecimalMin(value = "0", message = "지급금액은 0 이상이어야 합니다.") BigDecimal dfrAmt,
            @Size(max = 8) String dfrDt,
            @Size(max = 8) String dfrMplDt,
            @Size(max = 1000) String opnnCone) {}

    @Schema(name = "PaymentLinesRequest", description = "회차별 지급 일괄 저장(진행중)")
    public record LinesRequest(@NotNull List<LineRequest> lines) {}

    @Schema(
            name = "PaymentListItem",
            description = "대금지급 목록 항목",
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
            name = "PaymentLine",
            description = "회차별 지급 응답",
            requiredProperties = {"dfrTod", "dfrAmt", "dfrDt", "dfrMplDt", "opnnCone"})
    public record Line(
            Integer dfrTod,
            @Schema(nullable = true) BigDecimal dfrAmt,
            @Schema(nullable = true) String dfrDt,
            @Schema(nullable = true) String dfrMplDt,
            @Schema(nullable = true) String opnnCone) {
        /**
         * 지급 명세 조회 행을 응답으로 변환합니다.
         *
         * @param row 지급 명세 조회 행
         * @return 지급 명세 응답
         */
        public static Line fromProjection(PaymentLineView row) {
            return new Line(
                    row.dfrTod(), row.dfrAmt(), row.dfrDt(), row.dfrMplDt(), row.opnnCone());
        }
    }

    @Schema(
            name = "PaymentDetail",
            description = "대금지급 상세",
            requiredProperties = {
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
                "lines"
            })
    public record Detail(
            String docMngNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            @Schema(nullable = true) String tgtNm,
            String stsTc,
            @Schema(nullable = true) String reqCone,
            @Schema(nullable = true) String cttNm,
            @Schema(nullable = true) BigDecimal cttAmt,
            @Schema(nullable = true) String reqUsid,
            @Schema(nullable = true) java.time.LocalDateTime reqDtm,
            List<Line> lines) {
        /**
         * 상세 조회 행과 지급 명세를 상세 응답으로 변환합니다.
         *
         * @param row 대금지급 상세 조회 행
         * @param lines 지급 명세 응답 목록
         * @return 상세 응답
         */
        public static Detail fromProjection(PaymentDetailRow row, List<Line> lines) {
            return new Detail(
                    row.docMngNo(),
                    row.docVrsSno(),
                    row.ioeC(),
                    row.cncdRfrNo(),
                    row.tgtNm(),
                    row.stsTc(),
                    row.reqCone(),
                    row.cttNm(),
                    row.cttAmt(),
                    row.reqUsid(),
                    row.reqDtm(),
                    lines);
        }
    }
}
