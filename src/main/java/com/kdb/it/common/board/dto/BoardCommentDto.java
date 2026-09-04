package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentListRow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

/** 게시판 댓글 DTO 모음 */
public class BoardCommentDto {

    private BoardCommentDto() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "BoardCommentResponse",
            description = "댓글 응답",
            requiredProperties = {
                "cmmtMngNo", "nacMngNo", "cmmtCone", "cmmtGrpNo", "cmmtGrpSqn", "cmmtGrpLev",
                "hrkCmmtMngNo", "delYn", "fstEnrUsid", "fstEnrUsNm", "fstEnrDtm", "lstChgDtm",
                "canModify"
            })
    public static class Response {
        @Schema(description = "댓글관리번호")
        private Long cmmtMngNo;

        @Schema(description = "게시물관리번호")
        private String nacMngNo;

        @Schema(description = "댓글내용")
        private String cmmtCone;

        @Schema(description = "그룹번호")
        private Long cmmtGrpNo;

        @Schema(description = "그룹순서")
        private Integer cmmtGrpSqn;

        @Schema(description = "그룹레벨 (들여쓰기 계산용)")
        private Integer cmmtGrpLev;

        @Schema(description = "상위댓글번호", nullable = true)
        private Long hrkCmmtMngNo;

        @Schema(
                description = "삭제여부",
                allowableValues = {"Y", "N"})
        private String delYn;

        @Schema(description = "작성자사번")
        private String fstEnrUsid;

        @Schema(description = "작성자명", nullable = true)
        private String fstEnrUsNm;

        @Schema(description = "등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "수정일시")
        private LocalDateTime lstChgDtm;

        @Schema(description = "수정 가능 여부")
        private boolean canModify;

        public static Response from(Ccmmtm e, boolean canModify) {
            // 삭제된 댓글은 본문을 마스킹한다
            String displayCone = "Y".equals(e.getDelYn()) ? "삭제된 댓글입니다." : e.getCmmtCone();
            return Response.builder()
                    .cmmtMngNo(e.getCmmtMngNo())
                    .nacMngNo(e.getNacMngNo())
                    .cmmtCone(displayCone)
                    .cmmtGrpNo(e.getCmmtGrpNo())
                    .cmmtGrpSqn(e.getCmmtGrpSqn())
                    .cmmtGrpLev(e.getCmmtGrpLev())
                    .hrkCmmtMngNo(e.getHrkCmmtMngNo())
                    .delYn(e.getDelYn())
                    .fstEnrUsid(e.getFstEnrUsid())
                    .fstEnrDtm(e.getFstEnrDtm())
                    .lstChgDtm(e.getLstChgDtm())
                    .canModify(canModify)
                    .build();
        }

        /**
         * REST 응답 전용 경량 프로젝션({@link BoardCommentListRow})으로부터 응답 DTO를 생성합니다. {@link #from(Ccmmtm,
         * boolean)}와 동일한 삭제 댓글 마스킹 규칙을 적용합니다.
         *
         * @param row 댓글 목록 프로젝션 행
         * @param canModify 현재 사용자의 수정 가능 여부
         * @return 응답 DTO
         */
        public static Response from(BoardCommentListRow row, boolean canModify, String fstEnrUsNm) {
            // 삭제된 댓글은 본문을 마스킹한다
            String displayCone = "Y".equals(row.delYn()) ? "삭제된 댓글입니다." : row.cmmtCone();
            return Response.builder()
                    .cmmtMngNo(row.cmmtMngNo())
                    .nacMngNo(row.nacMngNo())
                    .cmmtCone(displayCone)
                    .cmmtGrpNo(row.cmmtGrpNo())
                    .cmmtGrpSqn(row.cmmtGrpSqn())
                    .cmmtGrpLev(row.cmmtGrpLev())
                    .hrkCmmtMngNo(row.hrkCmmtMngNo())
                    .delYn(row.delYn())
                    .fstEnrUsid(row.fstEnrUsid())
                    .fstEnrUsNm(fstEnrUsNm)
                    .fstEnrDtm(row.fstEnrDtm())
                    .lstChgDtm(row.lstChgDtm())
                    .canModify(canModify)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardCommentCreateRequest", description = "댓글 등록 요청")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "댓글 내용 (최대 2000자)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String cmmtCone;

        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;

        // 멘션 정보가 없는 단순 댓글 생성 요청용 편의 생성자
        public CreateRequest(String cmmtCone) {
            this.cmmtCone = cmmtCone;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardCommentUpdateRequest", description = "댓글 수정 요청")
    public static class UpdateRequest {
        @NotBlank
        @Schema(description = "댓글 내용 (최대 2000자)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String cmmtCone;

        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;

        // 멘션 정보가 없는 단순 댓글 수정 요청용 편의 생성자
        public UpdateRequest(String cmmtCone) {
            this.cmmtCone = cmmtCone;
        }
    }
}
