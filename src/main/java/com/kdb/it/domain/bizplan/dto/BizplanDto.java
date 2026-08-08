package com.kdb.it.domain.bizplan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 사업계획 API 요청/응답 DTO 모음. */
public final class BizplanDto {

    private BizplanDto() {}

    @Schema(
            name = "BizplanListItem",
            description = "사업계획 목록 항목 (BPLANA 포함 사업 기준)",
            requiredProperties = {
                "abusMngNo",
                "abusNm",
                "svnDpmC",
                "svnDpmNm",
                "bseYy",
                "totRqmAmt",
                "stsTc",
                "lstChgDtm"
            })
    public record ListItem(
            String abusMngNo,
            @Schema(nullable = true) String abusNm,
            @Schema(nullable = true) String svnDpmC,
            @Schema(nullable = true) String svnDpmNm,
            @Schema(nullable = true) String bseYy,
            @Schema(nullable = true) BigDecimal totRqmAmt,
            @Schema(nullable = true) String stsTc, // 21/29, null=미작성
            @Schema(nullable = true) LocalDateTime lstChgDtm) {}

    @Schema(
            name = "BizplanSchedule",
            description = "사업일정 행 응답",
            requiredProperties = {"sno", "dsdCone", "sttDt", "endDt"})
    public record Schedule(
            Integer sno,
            @Schema(nullable = true) String dsdCone,
            @Schema(nullable = true) String sttDt,
            @Schema(nullable = true) String endDt) {}

    @Schema(
            name = "BizplanItem",
            description = "사업품목 행 응답",
            requiredProperties = {
                "sno",
                "gclNm",
                "ioeC",
                "qty",
                "amt",
                "fcAmt",
                "curC",
                "xcr",
                "xcrBseDt",
                "cttSno"
            })
    public record Item(
            Integer sno,
            @Schema(nullable = true) String gclNm,
            @Schema(nullable = true) String ioeC,
            @Schema(nullable = true) Long qty,
            @Schema(nullable = true) BigDecimal amt,
            @Schema(nullable = true) BigDecimal fcAmt,
            @Schema(nullable = true) String curC,
            @Schema(nullable = true) BigDecimal xcr,
            @Schema(nullable = true) String xcrBseDt,
            @Schema(nullable = true) Integer cttSno) {}

    @Schema(
            name = "BizplanContract",
            description = "사업계약 행 응답",
            requiredProperties = {"sno", "cttNm", "nowCttManrC", "cttTrmMmNbr"})
    public record Contract(
            Integer sno,
            @Schema(nullable = true) String cttNm,
            @Schema(nullable = true) String nowCttManrC,
            @Schema(nullable = true) Integer cttTrmMmNbr) {}

    @Schema(
            name = "BizplanDetail",
            description = "사업계획 상세",
            requiredProperties = {
                "abusMngNo",
                "abusNm",
                "bgNo",
                "totRqmAmt",
                "itPtlEdrtTc",
                "redtConeInf",
                "stsTc",
                "schedules",
                "items",
                "contracts"
            })
    public record Detail(
            String abusMngNo,
            @Schema(nullable = true) String abusNm,
            @Schema(nullable = true) String bgNo,
            @Schema(nullable = true) BigDecimal totRqmAmt,
            @Schema(nullable = true) String itPtlEdrtTc,
            @Schema(nullable = true) String redtConeInf,
            String stsTc, // BPROJA의 사업계획 단계 상태(21/29)
            List<Schedule> schedules,
            List<Item> items,
            List<Contract> contracts) {}

    @Schema(name = "BizplanScheduleRequest", description = "사업일정 행 저장 요청")
    public record ScheduleRequest(
            @NotNull @Min(1) Integer sno,
            @Size(max = 1000) String dsdCone,
            @Pattern(regexp = "^\\d{8}$", message = "시작일자는 YYYYMMDD 형식이어야 합니다.") String sttDt,
            @Pattern(regexp = "^\\d{8}$", message = "종료일자는 YYYYMMDD 형식이어야 합니다.") String endDt) {}

    @Schema(name = "BizplanItemRequest", description = "사업품목 행 저장 요청")
    public record ItemRequest(
            @NotNull @Min(1) Integer sno,
            @Size(max = 100) String gclNm,
            @Size(max = 7) String ioeC,
            @Min(0) Long qty,
            @DecimalMin("0") BigDecimal amt,
            @DecimalMin("0") BigDecimal fcAmt,
            @Size(max = 3) String curC,
            @DecimalMin("0") BigDecimal xcr,
            @Pattern(regexp = "^\\d{8}$", message = "환율기준일자는 YYYYMMDD 형식이어야 합니다.") String xcrBseDt,
            Integer cttSno) {}

    @Schema(name = "BizplanContractRequest", description = "사업계약 행 저장 요청")
    public record ContractRequest(
            @NotNull @Min(1) Integer sno,
            @Size(max = 100) String cttNm,
            @NotBlank(message = "계약방법을 선택해야 합니다.") @Size(max = 2) String nowCttManrC,
            @Min(0) Integer cttTrmMmNbr) {}

    @Schema(name = "BizplanSaveRequest", description = "사업계획 전체 저장 요청 (보고서+일정/품목/계약)")
    public record SaveRequest(
            String redtConeInf,
            @Size(max = 2) String itPtlEdrtTc,
            @NotNull @Valid List<ScheduleRequest> schedules,
            @NotNull @Valid List<ItemRequest> items,
            @NotNull @Valid List<ContractRequest> contracts) {}

    @Schema(name = "BizplanStatusRequest", description = "사업계획 상태 전이 요청 (21→29 완료만 허용)")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}
}
