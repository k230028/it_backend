package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.domain.budget.document.entity.BgdocDocumentType;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 가이드 문서(TPRMPP_BGDOCM) 관련 DTO 클래스 모음
 *
 * <p>가이드 문서 엔티티의 생성, 수정, 조회에 사용되는 DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 */
public class GuideDocDto {

    /**
     * 가이드 문서 생성 요청 DTO
     *
     * <p>{@code docMngNo}가 null 또는 빈 문자열이면 서비스에서 자동 채번합니다. 형식: {@code GDOC-{연도}-{seq:04d}} (예:
     * GDOC-2026-0001)
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

        @NotBlank
        /** 문서명 */
        @Schema(description = "문서명")
        private String docTtlCone;

        /** 문서내용 (HTML 포함 가능) */
        @Schema(description = "문서내용")
        private String nacTxtInf;

        /**
         * CreateRequest를 {@link Bgdocm} 엔티티로 변환합니다.
         *
         * @return 변환된 Bgdocm 엔티티
         */
        public Bgdocm toEntity() {
            return Bgdocm.builder()
                    .docMngNo(this.docMngNo)
                    .docTtlCone(this.docTtlCone)
                    .docDtlItmC(BgdocDocumentType.BUSINESS_GUIDE.code())
                    .nacTxtInf(this.nacTxtInf)
                    .build();
        }
    }

    /** 가이드 문서 수정 요청 DTO */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GuideDocUpdateRequest", description = "가이드 문서 수정 요청")
    public static class UpdateRequest {

        /** 문서명 */
        @NotBlank
        @Schema(description = "문서명")
        private String docTtlCone;

        /** 문서내용 (HTML 포함 가능) */
        @Schema(description = "문서내용")
        private String nacTxtInf;
    }

    /** 가이드 문서 조회 응답 DTO */
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
        private String docTtlCone;

        /** 문서상세항목코드 */
        @Schema(description = "문서상세항목코드")
        private String docDtlItmC;

        /** 문서정보 (CLOB, HTML 포함 가능) */
        @Schema(description = "문서정보")
        private String nacTxtInf;

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
                    .docTtlCone(entity.getDocTtlCone())
                    .docDtlItmC(entity.getDocDtlItmC())
                    .nacTxtInf(entity.getNacTxtInf())
                    .delYn(entity.getDelYn())
                    .fstEnrDtm(entity.getFstEnrDtm())
                    .fstEnrUsid(entity.getFstEnrUsid())
                    .lstChgDtm(entity.getLstChgDtm())
                    .lstChgUsid(entity.getLstChgUsid())
                    .build();
        }
    }

    /**
     * 가이드 문서 목록 조회 응답 DTO
     *
     * <p>본문({@code nacTxtInf}, CLOB)을 제외한 경량 목록 응답입니다. 단건 상세 조회는 {@link Response}를 계속 사용합니다.
     *
     * @param docMngNo 문서관리번호
     * @param docTtlCone 문서명
     * @param docDtlItmC 문서상세항목코드
     * @param delYn 삭제여부
     * @param fstEnrDtm 최초생성시간
     * @param fstEnrUsid 최초생성자 사번
     * @param lstChgDtm 마지막수정시간
     * @param lstChgUsid 마지막수정자 사번
     * @param lstChgUsNm 마지막수정자 이름
     */
    @Schema(name = "GuideDocListResponse", description = "가이드 문서 목록 조회 응답 (본문 제외)")
    public record ListResponse(
            @Schema(description = "문서관리번호") String docMngNo,
            @Schema(description = "문서명") String docTtlCone,
            @Schema(description = "문서상세항목코드") String docDtlItmC,
            @Schema(description = "삭제여부") String delYn,
            @Schema(description = "최초생성시간") LocalDateTime fstEnrDtm,
            @Schema(description = "최초생성자") String fstEnrUsid,
            @Schema(description = "마지막수정시간") LocalDateTime lstChgDtm,
            @Schema(description = "마지막수정자 사번") String lstChgUsid,
            @Schema(description = "마지막수정자 이름") String lstChgUsNm) {

        /**
         * {@link GuideDocRepository.GuideDocListView} 프로젝션을 ListResponse로 변환합니다.
         *
         * @param view 변환할 목록 프로젝션
         * @param lstChgUsNm 마지막수정자 이름
         * @return 변환된 ListResponse
         */
        public static ListResponse fromView(
                GuideDocRepository.GuideDocListView view, String lstChgUsNm) {
            return new ListResponse(
                    view.getDocMngNo(),
                    view.getDocTtlCone(),
                    view.getDocDtlItmC(),
                    view.getDelYn(),
                    view.getFstEnrDtm(),
                    view.getFstEnrUsid(),
                    view.getLstChgDtm(),
                    view.getLstChgUsid(),
                    lstChgUsNm);
        }
    }
}
