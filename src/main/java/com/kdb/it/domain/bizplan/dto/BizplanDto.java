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

    @Schema(name = "BizplanListItem", description = "사업계획 목록 항목 (BPLANA 포함 사업 기준)")
    public record ListItem(
            String abusMngNo,
            String abusNm,
            String svnDpmC,
            String svnDpmNm,
            String bseYy,
            BigDecimal totRqmAmt,
            String stsTc, // 21/29, null=미작성
            LocalDateTime lstChgDtm) {}

    @Schema(name = "BizplanSchedule", description = "사업일정 행 응답")
    public record Schedule(Integer sno, String dsdCone, String sttDt, String endDt) {}

    @Schema(name = "BizplanItem", description = "사업품목 행 응답")
    public record Item(
            Integer sno,
            String gclNm,
            String ioeC,
            Long qty,
            BigDecimal amt,
            BigDecimal fcAmt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            Integer cttSno) {}

    @Schema(name = "BizplanContract", description = "사업계약 행 응답")
    public record Contract(Integer sno, String cttNm, String nowCttManrC, Integer cttTrmMmNbr) {}

    @Schema(name = "BizplanDetail", description = "사업계획 상세")
    public record Detail(
            String abusMngNo,
            String abusNm,
            String bgNo,
            BigDecimal totRqmAmt,
            String itPtlEdrtTc,
            String redtConeInf,
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
            @Size(max = 2) String nowCttManrC,
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
