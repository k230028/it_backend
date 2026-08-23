package com.kdb.it.common.board.dto;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.iam.entity.CuserI;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** 게시물 DTO 모음 */
public class BoardPostDto {

    private BoardPostDto() {}

    /** 게시물 목록 응답에 필요한 필드만 담는 조회 전용 행입니다. */
    @Schema(name = "BoardPostListRow", description = "게시물 목록 조회 전용 행")
    public record ListRow(
            String nacMngNo,
            String blbMngNo,
            String nacNm,
            Integer nacInqNbr,
            String nacUnqId,
            String ancYn,
            String xpoYn,
            String flApgYn,
            Integer flNbr,
            Integer nacGrpLev,
            LocalDate sttYmd,
            LocalDate endYmd,
            String fstEnrUsid,
            String fstEnrUsNm,
            String fstEnrBbrNm,
            LocalDateTime fstEnrDtm) {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "BoardPostListItem",
            description = "게시물 목록 아이템",
            requiredProperties = {
                "nacMngNo",
                "blbMngNo",
                "nacNm",
                "nacInqNbr",
                "nacUnqId",
                "ancYn",
                "xpoYn",
                "flApgYn",
                "flNbr",
                "nacGrpLev",
                "sttYmd",
                "endYmd",
                "fstEnrUsid",
                "fstEnrUsNm",
                "fstEnrBbrNm",
                "fstEnrDtm"
            })
    public static class ListItem {
        @Schema(description = "게시물관리번호")
        private String nacMngNo;

        @Schema(description = "게시판관리번호")
        private String blbMngNo;

        @Schema(description = "제목")
        private String nacNm;

        @Schema(description = "조회수")
        private Integer nacInqNbr;

        @Schema(description = "게시물고유ID", nullable = true)
        private String nacUnqId;

        @Schema(
                description = "공지여부",
                allowableValues = {"Y", "N"})
        private String ancYn;

        @Schema(
                description = "노출여부",
                allowableValues = {"Y", "N"})
        private String xpoYn;

        @Schema(
                description = "파일첨부여부",
                allowableValues = {"Y", "N"})
        private String flApgYn;

        @Schema(description = "파일수")
        private Integer flNbr;

        @Schema(description = "그룹레벨 (들여쓰기 계산용)")
        private Integer nacGrpLev;

        @Schema(description = "공개시작일", nullable = true)
        private LocalDate sttYmd;

        @Schema(description = "공개종료일", nullable = true)
        private LocalDate endYmd;

        @Schema(description = "작성자사번")
        private String fstEnrUsid;

        @Schema(description = "작성자명", nullable = true)
        private String fstEnrUsNm;

        @Schema(description = "작성자 소속부서명", nullable = true)
        private String fstEnrBbrNm;

        @Schema(description = "등록일시")
        private LocalDateTime fstEnrDtm;

        public static ListItem from(Cblbcm e) {
            return ListItem.builder()
                    .nacMngNo(e.getNacMngNo())
                    .blbMngNo(e.getBlbMngNo())
                    .nacNm(e.getNacNm())
                    .nacInqNbr(e.getNacInqNbr())
                    .nacUnqId(e.getNacUnqId())
                    .ancYn(e.getAncYn())
                    .xpoYn(e.getXpoYn())
                    .flApgYn(e.getFlApgYn())
                    .flNbr(e.getFlNbr())
                    .nacGrpLev(e.getNacGrpLev())
                    .sttYmd(e.getSttDt())
                    .endYmd(e.getEndDt())
                    .fstEnrUsid(e.getFstEnrUsid())
                    // 엔티티 단독 변환 경로에는 작성자 조인 정보가 없다. 이름·부서명은
                    // searchPostRows 프로젝션 경로에서만 채우므로 여기서 조회를 추가하지 않는다.
                    .fstEnrUsNm(null)
                    .fstEnrBbrNm(null)
                    .fstEnrDtm(e.getFstEnrDtm())
                    .build();
        }

        /**
         * 경량 조회 행을 목록 응답으로 변환합니다.
         *
         * @param row 게시물 목록 조회 행
         * @return 게시물 목록 응답
         */
        public static ListItem from(ListRow row) {
            return ListItem.builder()
                    .nacMngNo(row.nacMngNo())
                    .blbMngNo(row.blbMngNo())
                    .nacNm(row.nacNm())
                    .nacInqNbr(row.nacInqNbr())
                    .nacUnqId(row.nacUnqId())
                    .ancYn(row.ancYn())
                    .xpoYn(row.xpoYn())
                    .flApgYn(row.flApgYn())
                    .flNbr(row.flNbr())
                    .nacGrpLev(row.nacGrpLev())
                    .sttYmd(row.sttYmd())
                    .endYmd(row.endYmd())
                    .fstEnrUsid(row.fstEnrUsid())
                    .fstEnrUsNm(row.fstEnrUsNm())
                    .fstEnrBbrNm(row.fstEnrBbrNm())
                    .fstEnrDtm(row.fstEnrDtm())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "BoardPostDetail",
            description = "게시물 상세",
            requiredProperties = {
                "nacMngNo",
                "blbMngNo",
                "nacNm",
                "nacCone",
                "nacInqNbr",
                "nacUnqId",
                "ancYn",
                "xpoYn",
                "bbrC",
                "sttYmd",
                "endYmd",
                "flApgYn",
                "flNbr",
                "nacGrpSqn",
                "nacGrpLev",
                "hrkNacNo",
                "fstEnrUsid",
                "fstEnrUsNm",
                "fstEnrBbrNm",
                "fstEnrDtm",
                "lstChgDtm",
                "canModify"
            })
    public static class Detail {
        @Schema(description = "게시물관리번호")
        private String nacMngNo;

        @Schema(description = "게시판관리번호")
        private String blbMngNo;

        @Schema(description = "제목")
        private String nacNm;

        @Schema(description = "본문 HTML")
        private String nacCone;

        @Schema(description = "조회수")
        private Integer nacInqNbr;

        @Schema(description = "게시물고유ID", nullable = true)
        private String nacUnqId;

        @Schema(
                description = "공지여부",
                allowableValues = {"Y", "N"})
        private String ancYn;

        @Schema(
                description = "노출여부",
                allowableValues = {"Y", "N"})
        private String xpoYn;

        @Schema(description = "담당부서코드", nullable = true)
        private String bbrC;

        @Schema(description = "공개시작일", nullable = true)
        private LocalDate sttYmd;

        @Schema(description = "공개종료일", nullable = true)
        private LocalDate endYmd;

        @Schema(
                description = "파일첨부여부",
                allowableValues = {"Y", "N"})
        private String flApgYn;

        @Schema(description = "파일수")
        private Integer flNbr;

        @Schema(description = "그룹순서")
        private Integer nacGrpSqn;

        @Schema(description = "그룹레벨")
        private Integer nacGrpLev;

        @Schema(description = "상위게시물번호", nullable = true)
        private String hrkNacNo;

        @Schema(description = "작성자사번")
        private String fstEnrUsid;

        @Schema(description = "작성자명", nullable = true)
        private String fstEnrUsNm;

        @Schema(description = "작성자 소속부서명", nullable = true)
        private String fstEnrBbrNm;

        @Schema(description = "등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "수정일시")
        private LocalDateTime lstChgDtm;

        @Schema(description = "수정 가능 여부")
        private boolean canModify;

        /**
         * 게시물 엔티티와 작성자 정보를 상세 응답으로 변환합니다.
         *
         * @param e 게시물 엔티티
         * @param canModify 수정 가능 여부
         * @param writer 작성자 사용자. 퇴직·삭제 등으로 조회되지 않으면 null이며 이름·부서명은 비웁니다.
         * @return 게시물 상세 DTO
         */
        public static Detail from(Cblbcm e, boolean canModify, CuserI writer) {
            return Detail.builder()
                    .nacMngNo(e.getNacMngNo())
                    .blbMngNo(e.getBlbMngNo())
                    .nacNm(e.getNacNm())
                    .nacCone(e.getNacCone())
                    .nacInqNbr(e.getNacInqNbr())
                    .nacUnqId(e.getNacUnqId())
                    .ancYn(e.getAncYn())
                    .xpoYn(e.getXpoYn())
                    .bbrC(e.getBbrC())
                    .sttYmd(e.getSttDt())
                    .endYmd(e.getEndDt())
                    .flApgYn(e.getFlApgYn())
                    .flNbr(e.getFlNbr())
                    .nacGrpSqn(e.getNacGrpSqn())
                    .nacGrpLev(e.getNacGrpLev())
                    .hrkNacNo(e.getHrkNacNo())
                    .fstEnrUsid(e.getFstEnrUsid())
                    .fstEnrUsNm(writer == null ? null : writer.getUsrNm())
                    .fstEnrBbrNm(writer == null ? null : writer.getBbrNm())
                    .fstEnrDtm(e.getFstEnrDtm())
                    .lstChgDtm(e.getLstChgDtm())
                    .canModify(canModify)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostCreateRequest", description = "게시물 등록 요청")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "제목 (최대 300자)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String nacNm;

        @Size(max = 4000)
        @Schema(description = "본문 HTML (최대 4000자)")
        private String nacCone;

        @Schema(description = "공지여부", example = "N")
        private String ancYn;

        @Schema(description = "노출여부", example = "Y")
        private String xpoYn;

        @Schema(description = "담당부서코드")
        private String bbrC;

        @Schema(description = "공개시작일")
        private LocalDate sttYmd;

        @Schema(description = "공개종료일")
        private LocalDate endYmd;

        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostUpdateRequest", description = "게시물 수정 요청")
    public static class UpdateRequest {
        @NotBlank
        @Schema(description = "제목")
        private String nacNm;

        @Size(max = 4000)
        @Schema(description = "본문 HTML (최대 4000자)")
        private String nacCone;

        @Schema(description = "공지여부")
        private String ancYn;

        @Schema(description = "노출여부")
        private String xpoYn;

        @Schema(description = "담당부서코드")
        private String bbrC;

        @Schema(description = "공개시작일")
        private LocalDate sttYmd;

        @Schema(description = "공개종료일")
        private LocalDate endYmd;

        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;

        public Cblbcm.UpdateCommand toUpdateCommand(String sanitizedCone) {
            return new Cblbcm.UpdateCommand(
                    nacNm,
                    sanitizedCone,
                    ancYn == null ? "N" : ancYn,
                    xpoYn == null ? "Y" : xpoYn,
                    bbrC,
                    sttYmd,
                    endYmd);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BoardPostReplyCreateRequest", description = "답변글 등록 요청")
    public static class ReplyCreateRequest {
        @NotBlank
        @Schema(description = "제목", requiredMode = Schema.RequiredMode.REQUIRED)
        private String nacNm;

        @Size(max = 4000)
        @Schema(description = "본문 HTML (최대 4000자)")
        private String nacCone;

        @Schema(description = "담당부서코드")
        private String bbrC;

        @Schema(description = "공개시작일")
        private LocalDate sttYmd;

        @Schema(description = "공개종료일")
        private LocalDate endYmd;

        @Schema(description = "프론트 자동완성에서 선택한 멘션 사용자 사번 목록")
        private java.util.List<String> mentionedEnos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "BoardPostSearchCondition", description = "게시물 목록 검색 조건")
    public static class SearchCondition {
        /**
         * 공개기간 조건을 무시할지 여부. 서버 전용 플래그이므로 setter를 만들지 않는다 — 컨트롤러가 {@code @ModelAttribute}로 바인딩하는
         * 대상이라 setter가 있으면 쿼리 파라미터로 주입돼 미공개·기간만료 게시물이 노출된다. 서비스가 {@link
         * #ignorePublicationPeriod()}로만 켠다.
         */
        @Setter(AccessLevel.NONE)
        @Schema(hidden = true)
        private boolean ignorePublicationPeriod;

        /** 공개기간 조건을 무시하도록 켠다. 관리자 조회처럼 서버가 스스로 판단한 경로에서만 호출한다. */
        public void ignorePublicationPeriod() {
            this.ignorePublicationPeriod = true;
        }

        @Schema(description = "키워드 (제목/본문/작성자 LIKE)")
        private String keyword;

        @Schema(description = "등록일 시작 (yyyy-MM-dd)")
        private String enrDtmFrom;

        @Schema(description = "등록일 종료 (yyyy-MM-dd)")
        private String enrDtmTo;

        @Schema(description = "담당부서코드")
        private String bbrC;

        @Schema(description = "관리자도 공개 게시물과 공개기간 내 게시물만 조회할지 여부", example = "false")
        private boolean publicOnly;

        @Schema(description = "페이지 번호 (0-based)", example = "0")
        private int page;

        @Schema(description = "페이지 크기", example = "20")
        private int size = 20;
    }
}
