package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaListRow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 게시판 메타 DTO 모음 */
public class BoardMetaDto {

    private BoardMetaDto() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "BoardMetaResponse",
            description = "게시판 메타 응답",
            requiredProperties = {
                "blbMngNo",
                "blbNm",
                "itPtlBlbTc",
                "repUseYn",
                "cmmtUseYn",
                "flEsnYn",
                "hedTagUseYn",
                "sreSqnNo",
                "useYn",
                "rmk"
            })
    public static class Response {
        @Schema(description = "게시판관리번호")
        private String blbMngNo;

        @Schema(description = "게시판명")
        private String blbNm;

        @Schema(
                description =
                        "게시판구분코드 (공통코드 IT_PTL_BLB_TC: 001=공지사항, 002=자료실, 003=일정, 004=FAQ, 005=Q&A)",
                example = "001")
        private String itPtlBlbTc;

        @Schema(
                description = "답변사용여부",
                allowableValues = {"Y", "N"})
        private String repUseYn;

        @Schema(
                description = "댓글사용여부",
                allowableValues = {"Y", "N"})
        private String cmmtUseYn;

        @Schema(
                description = "첨부필수여부",
                allowableValues = {"Y", "N"})
        private String flEsnYn;

        @Schema(
                description = "머리말태그사용여부",
                allowableValues = {"Y", "N"})
        private String hedTagUseYn;

        @Schema(description = "화면순서번호")
        private Integer sreSqnNo;

        @Schema(
                description = "사용여부",
                allowableValues = {"Y", "N"})
        private String useYn;

        @Schema(description = "비고", nullable = true)
        private String rmk;

        public static Response from(Cblbmm e) {
            return Response.builder()
                    .blbMngNo(e.getBlbMngNo())
                    .blbNm(e.getBlbNm())
                    .itPtlBlbTc(e.getItPtlBlbTc())
                    .repUseYn(e.getRepUseYn())
                    .cmmtUseYn(e.getCmmtUseYn())
                    .flEsnYn(e.getFlEsnYn())
                    .hedTagUseYn(e.getHedTagUseYn())
                    .sreSqnNo(e.getSreSqnNo())
                    .useYn(e.getUseYn())
                    .rmk(e.getRmk())
                    .build();
        }

        /**
         * REST 응답 전용 경량 프로젝션({@link BoardMetaListRow})으로부터 응답 DTO를 생성합니다. {@link #from(Cblbmm)}와
         * 동일한 10개 필드를 동일한 순서로 매핑합니다.
         *
         * @param row 게시판 메타 목록 프로젝션 행
         * @return 응답 DTO
         */
        public static Response from(BoardMetaListRow row) {
            return Response.builder()
                    .blbMngNo(row.blbMngNo())
                    .blbNm(row.blbNm())
                    .itPtlBlbTc(row.itPtlBlbTc())
                    .repUseYn(row.repUseYn())
                    .cmmtUseYn(row.cmmtUseYn())
                    .flEsnYn(row.flEsnYn())
                    .hedTagUseYn(row.hedTagUseYn())
                    .sreSqnNo(row.sreSqnNo())
                    .useYn(row.useYn())
                    .rmk(row.rmk())
                    .build();
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardMetaCreateRequest", description = "게시판 메타 등록 요청")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "게시판명", requiredMode = Schema.RequiredMode.REQUIRED)
        private String blbNm;

        @NotBlank
        @Schema(
                description =
                        "게시판구분코드 (공통코드 IT_PTL_BLB_TC: 001=공지사항, 002=자료실, 003=일정, 004=FAQ, 005=Q&A)",
                example = "001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String itPtlBlbTc;

        @Schema(description = "답변사용여부", example = "N")
        private String repUseYn;

        @Schema(description = "댓글사용여부", example = "N")
        private String cmmtUseYn;

        @Schema(description = "첨부필수여부", example = "N")
        private String flEsnYn;

        @Schema(description = "머리말태그사용여부", example = "N")
        private String hedTagUseYn;

        @Schema(description = "화면순서번호", example = "0")
        private Integer sreSqnNo;

        @Schema(description = "비고")
        private String rmk;

        public Cblbmm.UpdateCommand toUpdateCommand() {
            return new Cblbmm.UpdateCommand(
                    blbNm,
                    nvl(repUseYn, "N"),
                    nvl(cmmtUseYn, "N"),
                    nvl(flEsnYn, "N"),
                    nvl(hedTagUseYn, "N"),
                    sreSqnNo == null ? 0 : sreSqnNo,
                    "Y",
                    rmk);
        }

        private String nvl(String v, String def) {
            return v != null ? v : def;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardMetaUpdateRequest", description = "게시판 메타 수정 요청")
    public static class UpdateRequest {
        @NotBlank
        @Schema(description = "게시판명")
        private String blbNm;

        @Schema(description = "답변사용여부")
        private String repUseYn;

        @Schema(description = "댓글사용여부")
        private String cmmtUseYn;

        @Schema(description = "첨부필수여부")
        private String flEsnYn;

        @Schema(description = "머리말태그사용여부")
        private String hedTagUseYn;

        @Schema(description = "화면순서번호")
        private Integer sreSqnNo;

        @Schema(description = "사용여부")
        private String useYn;

        @Schema(description = "비고")
        private String rmk;

        public Cblbmm.UpdateCommand toUpdateCommand() {
            return new Cblbmm.UpdateCommand(
                    blbNm, repUseYn, cmmtUseYn, flEsnYn, hedTagUseYn, sreSqnNo, useYn, rmk);
        }
    }
}
