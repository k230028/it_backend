package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Cblbcm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 게시물 DTO 모음
 */
public class BoardPostDto {

    private BoardPostDto() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostListItem", description = "게시물 목록 아이템")
    public static class ListItem {
        @Schema(description = "게시물관리번호")  private String        nacMngNo;
        @Schema(description = "게시판관리번호")  private String        blbMngNo;
        @Schema(description = "제목")           private String        nacNm;
        @Schema(description = "조회수")         private Integer       nacInqNbr;
        @Schema(description = "게시물고유ID") private String        nacUnqId;
        @Schema(description = "공지여부")   private String        ancYn;
        @Schema(description = "화면여부")       private String        sreYn;
        @Schema(description = "파일첨부여부")   private String        flApgYn;
        @Schema(description = "파일수")         private Integer       flNbr;
        @Schema(description = "그룹레벨 (들여쓰기 계산용)") private Integer nacGrpLev;
        @Schema(description = "공개시작일")     private LocalDate     sttYmd;
        @Schema(description = "공개종료일")     private LocalDate     endYmd;
        @Schema(description = "작성자사번")     private String        fstEnrUsid;
        @Schema(description = "등록일시")       private LocalDateTime fstEnrDtm;

        public static ListItem from(Cblbcm e) {
            return ListItem.builder()
                .nacMngNo(e.getNacMngNo()).blbMngNo(e.getBlbMngNo())
                .nacNm(e.getNacNm()).nacInqNbr(e.getNacInqNbr())
                .nacUnqId(e.getNacId())
                .ancYn(e.getAncYn()).sreYn(e.getSreYn())
                .flApgYn(e.getFlApgYn()).flNbr(e.getFlNbr())
                .nacGrpLev(e.getNacGrpLev())
                .sttYmd(e.getSttDt()).endYmd(e.getEndDt())
                .fstEnrUsid(e.getFstEnrUsid()).fstEnrDtm(e.getFstEnrDtm())
                .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostDetail", description = "게시물 상세")
    public static class Detail {
        @Schema(description = "게시물관리번호")  private String        nacMngNo;
        @Schema(description = "게시판관리번호")  private String        blbMngNo;
        @Schema(description = "제목")           private String        nacNm;
        @Schema(description = "본문 HTML")      private String        nacCone;
        @Schema(description = "조회수")         private Integer       nacInqNbr;
        @Schema(description = "게시물고유ID") private String        nacUnqId;
        @Schema(description = "공지여부")   private String        ancYn;
        @Schema(description = "화면여부")       private String        sreYn;
        @Schema(description = "담당부서코드")   private String        bbrC;
        @Schema(description = "공개시작일")     private LocalDate     sttYmd;
        @Schema(description = "공개종료일")     private LocalDate     endYmd;
        @Schema(description = "파일첨부여부")   private String        flApgYn;
        @Schema(description = "파일수")         private Integer       flNbr;
        @Schema(description = "그룹순서")       private Integer       nacGrpSqn;
        @Schema(description = "그룹레벨")       private Integer       nacGrpLev;
        @Schema(description = "상위게시물번호") private String        hrkNacNo;
        @Schema(description = "작성자사번")     private String        fstEnrUsid;
        @Schema(description = "등록일시")       private LocalDateTime fstEnrDtm;
        @Schema(description = "수정일시")       private LocalDateTime lstChgDtm;
        @Schema(description = "수정 가능 여부") private boolean       canModify;

        public static Detail from(Cblbcm e, boolean canModify) {
            return Detail.builder()
                .nacMngNo(e.getNacMngNo()).blbMngNo(e.getBlbMngNo())
                .nacNm(e.getNacNm()).nacCone(e.getNacCone())
                .nacInqNbr(e.getNacInqNbr()).nacUnqId(e.getNacId())
                .ancYn(e.getAncYn()).sreYn(e.getSreYn())
                .bbrC(e.getBbrC()).sttYmd(e.getSttDt()).endYmd(e.getEndDt())
                .flApgYn(e.getFlApgYn()).flNbr(e.getFlNbr())
                .nacGrpSqn(e.getNacGrpSqn()).nacGrpLev(e.getNacGrpLev())
                .hrkNacNo(e.getHrkNacNo())
                .fstEnrUsid(e.getFstEnrUsid()).fstEnrDtm(e.getFstEnrDtm())
                .lstChgDtm(e.getLstChgDtm()).canModify(canModify)
                .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostCreateRequest", description = "게시물 등록 요청")
    public static class CreateRequest {
        @Schema(description = "제목 (최대 300자)", requiredMode = Schema.RequiredMode.REQUIRED) private String    nacNm;
        @Schema(description = "본문 HTML")                         private String    nacCone;
        @Schema(description = "공지여부", example = "N")        private String   ancYn;
        @Schema(description = "화면여부", example = "Y")            private String   sreYn;
        @Schema(description = "담당부서코드")                       private String   bbrC;
        @Schema(description = "공개시작일")                         private LocalDate sttYmd;
        @Schema(description = "공개종료일")                         private LocalDate endYmd;
        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostUpdateRequest", description = "게시물 수정 요청")
    public static class UpdateRequest {
        @Schema(description = "제목")          private String    nacNm;
        @Schema(description = "본문 HTML")     private String    nacCone;
        @Schema(description = "공지여부")  private String    ancYn;
        @Schema(description = "화면여부")      private String    sreYn;
        @Schema(description = "담당부서코드")  private String    bbrC;
        @Schema(description = "공개시작일")    private LocalDate sttYmd;
        @Schema(description = "공개종료일")    private LocalDate endYmd;
        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;

        public Cblbcm.UpdateCommand toUpdateCommand(String sanitizedCone) {
            return new Cblbcm.UpdateCommand(
                nacNm, sanitizedCone,
                ancYn == null ? "N" : ancYn,
                sreYn == null ? "Y" : sreYn,
                bbrC, sttYmd, endYmd
            );
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostReplyCreateRequest", description = "답변글 등록 요청")
    public static class ReplyCreateRequest {
        @Schema(description = "제목", requiredMode = Schema.RequiredMode.REQUIRED) private String    nacNm;
        @Schema(description = "본문 HTML")             private String    nacCone;
        @Schema(description = "담당부서코드")           private String    bbrC;
        @Schema(description = "공개시작일")             private LocalDate sttYmd;
        @Schema(description = "공개종료일")             private LocalDate endYmd;
        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "BoardPostSearchCondition", description = "게시물 목록 검색 조건")
    public static class SearchCondition {
        @Schema(description = "키워드 (제목/본문/작성자 LIKE)") private String keyword;
        @Schema(description = "등록일 시작 (yyyy-MM-dd)")       private String enrDtmFrom;
        @Schema(description = "등록일 종료 (yyyy-MM-dd)")       private String enrDtmTo;
        @Schema(description = "담당부서코드")                   private String bbrC;
        @Schema(description = "페이지 번호 (0-based)", example = "0") private int page;
        @Schema(description = "페이지 크기", example = "20")    private int size = 20;
    }
}
