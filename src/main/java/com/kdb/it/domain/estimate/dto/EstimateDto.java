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

    @Schema(
            name = "EstimateListItem",
            description = "소요예산 산정 목록 항목",
            requiredProperties = {
                "rqmBgReqDocNo",
                "docVrsSno",
                "ioeC",
                "cncdRfrNo",
                "abusNm",
                "totalBudget",
                "sttDtm",
                "endDtm",
                "svnDpmC",
                "svnDpmNm",
                "stsTc",
                "reqUsid",
                "reqDtm"
            })
    public record ListItem(
            @Schema(nullable = true) String rqmBgReqDocNo,
            @Schema(nullable = true) Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            @Schema(nullable = true) String abusNm,
            @Schema(nullable = true) BigDecimal totalBudget,
            @Schema(nullable = true) LocalDate sttDtm,
            @Schema(nullable = true) LocalDate endDtm,
            @Schema(nullable = true) String svnDpmC,
            @Schema(nullable = true) String svnDpmNm,
            @Schema(nullable = true) String stsTc,
            @Schema(nullable = true) String reqUsid,
            @Schema(nullable = true) LocalDateTime reqDtm) {}

    @Schema(
            name = "EstimateLine",
            description = "팀별 산정 명세 응답",
            requiredProperties = {"svnTemC", "ioeC", "rqmBgAmt", "opnnCone"})
    public record Line(
            String svnTemC,
            String ioeC,
            @Schema(nullable = true) BigDecimal rqmBgAmt,
            @Schema(nullable = true) String opnnCone) {}

    @Schema(
            name = "EstimateDetail",
            description = "소요예산 산정 상세",
            requiredProperties = {
                "rqmBgReqDocNo",
                "docVrsSno",
                "ioeC",
                "cncdRfrNo",
                "abusNm",
                "stsTc",
                "reqCone",
                "reqUsid",
                "reqDtm",
                "lines"
            })
    public record Detail(
            String rqmBgReqDocNo,
            Integer docVrsSno,
            String ioeC,
            String cncdRfrNo,
            @Schema(nullable = true) String abusNm,
            String stsTc,
            @Schema(nullable = true) String reqCone,
            @Schema(nullable = true) String reqUsid,
            @Schema(nullable = true) LocalDateTime reqDtm,
            List<Line> lines) {

        /** 대상구분: 소요예산 산정은 정보화사업(100) 전용이므로 고정값을 사용합니다. */
        private static final String TGT_PROJECT = "100";

        /**
         * 상세 응답 전용 프로젝션({@link
         * com.kdb.it.domain.estimate.repository.EstimateRepository.EstimateDetailView})으로부터 상세 DTO를
         * 조립합니다. 엔티티 전체를 적재하는 기존 생성 방식과 동일한 필드 구성을 유지합니다.
         *
         * @param view 상세 조회용 마스터 프로젝션 (7개 필드)
         * @param abusNm 대상 사업명 (미존재 시 null)
         * @param lines 팀별 산정 명세 목록
         * @return 상세 응답 DTO
         */
        public static Detail fromView(
                com.kdb.it.domain.estimate.repository.EstimateRepository.EstimateDetailView view,
                String abusNm,
                List<Line> lines) {
            return new Detail(
                    view.getRqmBgReqDocNo(),
                    view.getDocVrsSno(),
                    TGT_PROJECT,
                    view.getCncdRfrNo(),
                    abusNm,
                    view.getStsTc(),
                    view.getReqCone(),
                    view.getFstEnrUsid(),
                    view.getFstEnrDtm(),
                    lines);
        }
    }
}
