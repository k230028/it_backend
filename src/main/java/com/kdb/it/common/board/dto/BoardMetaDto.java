package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Cblbmm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 게시판 메타 DTO 모음
 */
public class BoardMetaDto {

    private BoardMetaDto() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardMetaResponse", description = "게시판 메타 응답")
    public static class Response {
        @Schema(description = "게시판관리번호") private String  blbMngNo;
        @Schema(description = "게시판명")      private String  blbNm;
        @Schema(description = "게시판구분코드 (공통코드 BLB_TC: 001=공지사항, 002=자료실)", example = "001") private String  blbTp;
        @Schema(description = "답변사용여부")  private String  repUseYn;
        @Schema(description = "댓글사용여부")  private String  cmmtUseYn;
        @Schema(description = "첨부필수여부")  private String  flEsnYn;
        @Schema(description = "머리말태그사용여부") private String hedTagUseYn;
        @Schema(description = "화면순서번호")  private Integer sreSqnNo;
        @Schema(description = "사용여부")      private String  useYn;
        @Schema(description = "비고")          private String  rmk;

        public static Response from(Cblbmm e) {
            return Response.builder()
                .blbMngNo(e.getBlbMngNo()).blbNm(e.getBlbNm()).blbTp(e.getBlbTp())
                .repUseYn(e.getRepUseYn()).cmmtUseYn(e.getCmmtUseYn())
                .flEsnYn(e.getFlEsnYn())
                .hedTagUseYn(e.getHedTagUseYn())
                .sreSqnNo(e.getSreSqnNo()).useYn(e.getUseYn()).rmk(e.getRmk())
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
        @Schema(description = "게시판명", requiredMode = Schema.RequiredMode.REQUIRED)         private String  blbNm;
        @Schema(description = "게시판구분코드 (공통코드 BLB_TC: 001=공지사항, 002=자료실)", example = "001", requiredMode = Schema.RequiredMode.REQUIRED) private String  blbTp;
        @Schema(description = "답변사용여부", example = "N")       private String  repUseYn;
        @Schema(description = "댓글사용여부", example = "N")       private String  cmmtUseYn;
        @Schema(description = "첨부필수여부", example = "N")       private String  flEsnYn;
        @Schema(description = "머리말태그사용여부", example = "N")  private String  hedTagUseYn;
        @Schema(description = "화면순서번호", example = "0")       private Integer sreSqnNo;
        @Schema(description = "비고")                              private String  rmk;

        public Cblbmm.UpdateCommand toUpdateCommand() {
            return new Cblbmm.UpdateCommand(
                blbNm,
                nvl(repUseYn,     "N"), nvl(cmmtUseYn,    "N"),
                nvl(flEsnYn,      "N"),
                nvl(hedTagUseYn,  "N"),
                sreSqnNo == null ? 0 : sreSqnNo, "Y", rmk
            );
        }

        private String nvl(String v, String def) { return v != null ? v : def; }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardMetaUpdateRequest", description = "게시판 메타 수정 요청")
    public static class UpdateRequest {
        @Schema(description = "게시판명")          private String  blbNm;
        @Schema(description = "답변사용여부")      private String  repUseYn;
        @Schema(description = "댓글사용여부")      private String  cmmtUseYn;
        @Schema(description = "첨부필수여부")      private String  flEsnYn;
        @Schema(description = "머리말태그사용여부") private String  hedTagUseYn;
        @Schema(description = "화면순서번호")      private Integer sreSqnNo;
        @Schema(description = "사용여부")          private String  useYn;
        @Schema(description = "비고")              private String  rmk;

        public Cblbmm.UpdateCommand toUpdateCommand() {
            return new Cblbmm.UpdateCommand(
                blbNm, repUseYn, cmmtUseYn, flEsnYn,
                hedTagUseYn,
                sreSqnNo, useYn, rmk
            );
        }
    }
}
