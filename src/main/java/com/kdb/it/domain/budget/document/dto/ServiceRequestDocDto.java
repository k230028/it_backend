package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.domain.budget.document.entity.Brdocm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 요구사항 정의서(TAAABB_BRDOCM) 관련 DTO 클래스 모음
 *
 * <p>
 * 요구사항 정의서 엔티티의 생성, 수정, 조회에 사용되는 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 */
public class ServiceRequestDocDto {

    /**
     * 요구사항 정의서 생성 요청 DTO
     *
     * <p>
     * {@code docMngNo}가 null 또는 빈 문자열이면 서비스에서 자동 채번합니다.
     * 형식: {@code DOC-{연도}-{seq:04d}} (예: DOC-2026-0001)
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ServiceRequestDocCreateRequest", description = "요구사항 정의서 생성 요청")
    public static class CreateRequest {

        /** 문서관리번호: null이면 자동 채번 */
        @Schema(description = "문서관리번호 (미입력 시 자동 채번)")
        private String docMngNo;

        /** 요구사항명 */
        @Schema(description = "요구사항명")
        private String reqNm;

        /** 요구사항내용 (HTML 포함 가능) */
        @Schema(description = "요구사항내용")
        private String reqCone;

        /** 요구사항구분 코드 */
        @Schema(description = "요구사항구분")
        private String reqDtt;

        /** 업무구분 코드 */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 완료기한 */
        @Schema(description = "완료기한 (yyyy-MM-dd)")
        private LocalDate fsgTlm;

        /**
         * CreateRequest를 {@link Brdocm} 엔티티로 변환합니다.
         *
         * <p>
         * 복합 기본키({@code docMngNo}, {@code docVrs})를 구성하기 위해
         * 서비스 레이어에서 채번된 문서관리번호와 최초 문서버전을 파라미터로 전달받습니다.
         * </p>
         *
         * @param docMngNo 채번된 문서관리번호 (예: DOC-2026-0001)
         * @param docVrs   문서버전 (최초 생성 시 일반적으로 0.01)
         * @return 변환된 Brdocm 엔티티
         */
        public Brdocm toEntity(String docMngNo, BigDecimal docVrs) {
            return Brdocm.builder()
                    .docMngNo(docMngNo)
                    .docVrs(docVrs)
                    .reqNm(this.reqNm)
                    .reqCone(this.reqCone != null ? this.reqCone.getBytes(StandardCharsets.UTF_8) : null)
                    .reqDtt(this.reqDtt)
                    .bzDtt(this.bzDtt)
                    .fsgTlm(this.fsgTlm)
                    .build();
        }
    }

    /**
     * 요구사항 정의서 수정 요청 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ServiceRequestDocUpdateRequest", description = "요구사항 정의서 수정 요청")
    public static class UpdateRequest {

        /** 요구사항명 */
        @Schema(description = "요구사항명")
        private String reqNm;

        /** 요구사항내용 (HTML 포함 가능) */
        @Schema(description = "요구사항내용")
        private String reqCone;

        /** 요구사항구분 코드 */
        @Schema(description = "요구사항구분")
        private String reqDtt;

        /** 업무구분 코드 */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 완료기한 */
        @Schema(description = "완료기한 (yyyy-MM-dd)")
        private LocalDate fsgTlm;
    }

    /**
     * 요구사항 정의서 조회 응답 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ServiceRequestDocResponse", description = "요구사항 정의서 조회 응답")
    public static class Response {

        /** 문서관리번호 */
        @Schema(description = "문서관리번호")
        private String docMngNo;

        /** 문서버전 */
        @Schema(description = "문서버전")
        private BigDecimal docVrs;

        /** 요구사항명 */
        @Schema(description = "요구사항명")
        private String reqNm;

        /** 요구사항내용 (BLOB → UTF-8 문자열 변환) */
        @Schema(description = "요구사항내용")
        private String reqCone;

        /** 요구사항구분 */
        @Schema(description = "요구사항구분")
        private String reqDtt;

        /** 업무구분 */
        @Schema(description = "업무구분")
        private String bzDtt;

        /** 완료기한 */
        @Schema(description = "완료기한")
        private LocalDate fsgTlm;

        /** 삭제여부 */
        @Schema(description = "삭제여부")
        private String delYn;

        /** 최초생성시간 */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초생성자 사번 */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /** 최초생성자 이름 (TAAABB_CUSERI JOIN) */
        @Schema(description = "최초생성자 이름")
        private String fstEnrUsNm;

        /** 마지막수정시간 */
        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        /** 마지막수정자 사번 */
        @Schema(description = "마지막수정자")
        private String lstChgUsid;

        /**
         * {@link Brdocm} 엔티티를 Response DTO로 변환합니다.
         *
         * @param entity 변환할 Brdocm 엔티티
         * @return 변환된 Response DTO
         */
        public static Response fromEntity(Brdocm entity) {
            // BLOB → UTF-8 문자열 변환
            String reqConeStr = null;
            if (entity.getReqCone() != null) {
                reqConeStr = new String(entity.getReqCone(), StandardCharsets.UTF_8);
            }

            return Response.builder()
                    .docMngNo(entity.getDocMngNo())
                    .docVrs(entity.getDocVrs())
                    .reqNm(entity.getReqNm())
                    .reqCone(reqConeStr)
                    .reqDtt(entity.getReqDtt())
                    .bzDtt(entity.getBzDtt())
                    .fsgTlm(entity.getFsgTlm())
                    .delYn(entity.getDelYn())
                    .fstEnrDtm(entity.getFstEnrDtm())
                    .fstEnrUsid(entity.getFstEnrUsid())
                    .lstChgDtm(entity.getLstChgDtm())
                    .lstChgUsid(entity.getLstChgUsid())
                    .build();
        }
    }

    /**
     * 요구사항 정의서 버전 히스토리 응답 DTO
     *
     * <p>
     * 동일 {@code docMngNo}에 대한 모든 버전 목록을 반환할 때 사용합니다.
     * 본문(BLOB)은 제외하고 메타 정보만 포함합니다.
     * </p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ServiceRequestDocVersionResponse", description = "요구사항 정의서 버전 히스토리 응답")
    public static class VersionResponse {

        /** 문서관리번호 */
        @Schema(description = "문서관리번호")
        private String docMngNo;

        /** 문서버전 */
        @Schema(description = "문서버전")
        private BigDecimal docVrs;

        /** 최초생성시간 */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 마지막수정시간 */
        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        /** 삭제여부 */
        @Schema(description = "삭제여부")
        private String delYn;

        /**
         * {@link Brdocm} 엔티티를 VersionResponse DTO로 변환합니다.
         *
         * @param entity 변환할 Brdocm 엔티티
         * @return 변환된 VersionResponse DTO
         */
        public static VersionResponse fromEntity(Brdocm entity) {
            return VersionResponse.builder()
                    .docMngNo(entity.getDocMngNo())
                    .docVrs(entity.getDocVrs())
                    .fstEnrDtm(entity.getFstEnrDtm())
                    .lstChgDtm(entity.getLstChgDtm())
                    .delYn(entity.getDelYn())
                    .build();
        }
    }

    /**
     * 요구사항 정의서 대시보드 응답 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DocumentDashboardResponse", description = "요구사항 정의서 대시보드 응답")
    public static class DashboardResponse {
        @Schema(description = "전체 문서 수") private int totalCount;
        @Schema(description = "검토 진행 중 문서 수") private int reviewingCount;
        @Schema(description = "협의 완료 문서 수") private int completedCount;
        @Schema(description = "완료기한 초과 문서 수") private int overdueCount;
        @Schema(description = "최근 6개월 월별 등록 추이") private java.util.List<MonthlyCount> monthlyTrend;
        @Schema(description = "검토 중인 최근 요청 목록 (최대 3건)") private java.util.List<ReviewingItem> recentReviewing;
    }

    /**
     * 월별 건수 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DocumentMonthlyCount", description = "월별 등록 건수")
    public static class MonthlyCount {
        @Schema(description = "년월 (YYYY-MM)") private String month;
        @Schema(description = "건수") private int count;
    }

    /**
     * 검토 중인 문서 항목 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DocumentReviewingItem", description = "검토 중인 문서 항목")
    public static class ReviewingItem {
        @Schema(description = "문서관리번호") private String docMngNo;
        @Schema(description = "요구사항명") private String title;
        @Schema(description = "작성자명") private String authorName;
        @Schema(description = "최초 등록 일시 (YYYY-MM-DD)") private String createdAt;
        @Schema(description = "상태: reviewing(검토중) | delayed(지연)") private String status;
    }

    /**
     * 사이드바 배지 건수 응답 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DocumentBadgeCountResponse", description = "사이드바 배지 건수 응답")
    public static class BadgeCountResponse {
        @Schema(description = "검토 진행 중 문서 수") private int reviewingCount;
    }
}
