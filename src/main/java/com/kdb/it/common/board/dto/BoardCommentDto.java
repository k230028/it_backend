package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Ccmmtm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 게시판 댓글 DTO 모음
 */
public class BoardCommentDto {

    private BoardCommentDto() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardCommentResponse", description = "댓글 응답")
    public static class Response {
        @Schema(description = "댓글관리번호")   private String        cmmtMngNo;
        @Schema(description = "게시물관리번호") private String        nacMngNo;
        @Schema(description = "댓글내용")       private String        cmmtCone;
        @Schema(description = "화면여부")       private String        sreYn;
        @Schema(description = "그룹번호")       private String        cmmtGrpNo;
        @Schema(description = "그룹순서")       private Integer       cmmtGrpSqn;
        @Schema(description = "그룹레벨 (들여쓰기 계산용)") private Integer cmmtGrpLev;
        @Schema(description = "상위댓글번호")   private String        hrkCmmtMngNo;
        @Schema(description = "삭제여부")       private String        delYn;
        @Schema(description = "작성자사번")     private String        fstEnrUsid;
        @Schema(description = "등록일시")       private LocalDateTime fstEnrDtm;
        @Schema(description = "수정일시")       private LocalDateTime lstChgDtm;
        @Schema(description = "수정 가능 여부") private boolean       canModify;

        public static Response from(Ccmmtm e, boolean canModify) {
            // 삭제된 댓글은 본문을 마스킹한다
            String displayCone = "Y".equals(e.getDelYn())
                ? "삭제된 댓글입니다."
                : e.getCmmtCone();
            return Response.builder()
                .cmmtMngNo(e.getCmmtMngNo()).nacMngNo(e.getNacMngNo())
                .cmmtCone(displayCone).sreYn(e.getSreYn())
                .cmmtGrpNo(e.getCmmtGrpNo()).cmmtGrpSqn(e.getCmmtGrpSqn()).cmmtGrpLev(e.getCmmtGrpLev())
                .hrkCmmtMngNo(e.getHrkCmmtMngNo()).delYn(e.getDelYn())
                .fstEnrUsid(e.getFstEnrUsid()).fstEnrDtm(e.getFstEnrDtm())
                .lstChgDtm(e.getLstChgDtm()).canModify(canModify)
                .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardCommentCreateRequest", description = "댓글 등록 요청")
    public static class CreateRequest {
        @Schema(description = "댓글 내용 (최대 2000자)", required = true) private String cmmtCone;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardCommentUpdateRequest", description = "댓글 수정 요청")
    public static class UpdateRequest {
        @Schema(description = "댓글 내용 (최대 2000자)", required = true) private String cmmtCone;
    }
}
