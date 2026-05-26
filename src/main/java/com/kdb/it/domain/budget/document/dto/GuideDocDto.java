package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 가이드 문서(TPRMPP_BGDOCM) 관련 DTO 클래스 모음
 *
 * <p>
 * 가이드 문서 엔티티의 생성, 수정, 조회에 사용되는 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 */
public class GuideDocDto {

    /**
     * 가이드 문서 생성 요청 DTO
     *
     * <p>
     * {@code docMngNo}가 null 또는 빈 문자열이면 서비스에서 자동 채번합니다.
     * 형식: {@code GDOC-{연도}-{seq:04d}} (예: GDOC-2026-0001)
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GuideDocCreateRequest", description = "가이드 문서 생성 요청")
    public static class CreateRequest {

        /** 문서관리번호: null이면 자동 채번 */
        @Schema(description = "문서관리번호 (미입력 시 자동 채번, 예: GDOC-2026-0001)")
        private String docMngNo;

        /** 문서명 */
        @Schema(description = "문서명")
        private String docNm;

        /** 문서내용 (HTML 포함 가능) */
        @Schema(description = "문서내용")
        private String docInf;

        /**
         * CreateRequest를 {@link Bgdocm} 엔티티로 변환합니다.
         *
         * @return 변환된 Bgdocm 엔티티
         */
        public Bgdocm toEntity() {
            return Bgdocm.builder()
                    .docMngNo(this.docMngNo)
                    .docNm(this.docNm)
                    .docInf(this.docInf)
                    .build();
        }
    }

    /**
     * 가이드 문서 수정 요청 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GuideDocUpdateRequest", description = "가이드 문서 수정 요청")
    public static class UpdateRequest {

        /** 문서명 */
        @Schema(description = "문서명")
        private String docNm;

        /** 문서내용 (HTML 포함 가능) */
        @Schema(description = "문서내용")
        private String docInf;
    }

    /**
     * 가이드 문서 조회 응답 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GuideDocResponse", description = "가이드 문서 조회 응답")
    public static class Response {

        /** 문서관리번호 */
        @Schema(description = "문서관리번호")
        private String docMngNo;

        /** 문서명 */
        @Schema(description = "문서명")
        private String docNm;

        /** 문서정보 (CLOB, HTML 포함 가능) */
        @Schema(description = "문서정보")
        private String docInf;

        /** 삭제여부 */
        @Schema(description = "삭제여부")
        private String delYn;

        /** 최초생성시간 */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초생성자 사번 */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /** 마지막수정시간 */
        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        /** 마지막수정자 사번 */
        @Schema(description = "마지막수정자")
        private String lstChgUsid;

        /**
         * {@link Bgdocm} 엔티티를 Response DTO로 변환합니다.
         *
         * @param entity 변환할 Bgdocm 엔티티
         * @return 변환된 Response DTO
         */
        public static Response fromEntity(Bgdocm entity) {
            return Response.builder()
                    .docMngNo(entity.getDocMngNo())
                    .docNm(entity.getDocNm())
                    .docInf(entity.getDocInf())
                    .delYn(entity.getDelYn())
                    .fstEnrDtm(entity.getFstEnrDtm())
                    .fstEnrUsid(entity.getFstEnrUsid())
                    .lstChgDtm(entity.getLstChgDtm())
                    .lstChgUsid(entity.getLstChgUsid())
                    .build();
        }
    }
}
