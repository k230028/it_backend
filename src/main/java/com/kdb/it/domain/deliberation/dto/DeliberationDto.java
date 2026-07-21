package com.kdb.it.domain.deliberation.dto;

import com.kdb.it.domain.deliberation.repository.DeliberationDetailRow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 과업심의 API 요청/응답 DTO 모음. */
public final class DeliberationDto {
    private DeliberationDto() {}

    @Schema(name = "DeliberationCreateRequest", description = "과업심의 신규 신청 요청")
    public record CreateRequest(
            @NotBlank @Size(max = 3) String ioeC,
            @NotBlank @Size(max = 30) String cncdRfrNo,
            @Size(max = 300) String reqCone
    ) {}

    @Schema(name = "DeliberationUpdateRequest", description = "과업심의 마스터 수정(작성중)")
    public record UpdateRequest(@Size(max = 300) String reqCone) {}

    @Schema(name = "DeliberationStatusRequest", description = "과업심의 상태 전이")
    public record StatusRequest(@NotBlank @Size(max = 2) String stsTc) {}

    @Schema(name = "DeliberationResultRequest", description = "과업심의 결과 입력(진행중)")
    public record ResultRequest(
            @Size(max = 2) String taskDbrTc,
            @Size(max = 2) String taskDbrRltTc,
            @Size(max = 8) String taskDbrDt,
            @Size(max = 2) String taskDbrTod,
            @Pattern(regexp = "^[YN]$", message = "심의생략여부는 Y 또는 N이어야 합니다.") String taskDbrOmtYn,
            @Size(max = 200) String taskDbrOmtRsn,
            @Size(max = 1000) String opnnCone,
            @Size(max = 300) String apvTrdnRsnCone
    ) {}

    @Schema(name = "DeliberationListItem", description = "과업심의 목록 항목")
    public record ListItem(
            String docMngNo, Integer docVrsSno, String ioeC, String cncdRfrNo,
            String stsTc, String taskDbrRltTc, String reqUsid, java.time.LocalDateTime reqDtm
    ) {}

    @Schema(name = "DeliberationDetail", description = "과업심의 상세")
    public record Detail(
            String docMngNo, Integer docVrsSno, String ioeC, String cncdRfrNo, String tgtNm,
            String stsTc, String reqCone, String taskDbrTc, String taskDbrRltTc, String taskDbrDt,
            String taskDbrTod, String taskDbrOmtYn, String taskDbrOmtRsn, String opnnCone, String apvTrdnRsnCone,
            String reqUsid, java.time.LocalDateTime reqDtm
    ) {
        /**
         * 조회 프로젝션을 상세 응답으로 변환합니다.
         *
         * @param row 과업심의 상세 조회 행
         * @return 상세 응답
         */
        public static Detail fromProjection(DeliberationDetailRow row) {
            return new Detail(
                    row.docMngNo(), row.docVrsSno(), row.ioeC(), row.cncdRfrNo(), row.tgtNm(),
                    row.stsTc(), row.reqCone(), row.taskDbrTc(), row.taskDbrRltTc(), row.taskDbrDt(),
                    row.taskDbrTod(), row.taskDbrOmtYn(), row.taskDbrOmtRsn(), row.opnnCone(),
                    row.apvTrdnRsnCone(), row.reqUsid(), row.reqDtm());
        }
    }
}
