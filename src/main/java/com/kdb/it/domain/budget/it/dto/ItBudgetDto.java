package com.kdb.it.domain.budget.it.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 정보기술부문 예산 조회/비교 DTO 클래스 모음
 *
 * <p>
 * IT/정보보호 구분 비목별 편성요청액·편성액 집계 및 전년 대비 증감 비교 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 */
public class ItBudgetDto {

    /**
     * 비목별 IT/정보보호 구분 집계 행
     *
     * @param ioeCode    비목코드값 (TPRMPP_CCODEM.CDVA, cId='IOE')
     * @param codeNm     비목코드값명 (CDVA_NM)
     * @param itReqAmt   정보기술 편성요청액 (천원)
     * @param itAdjAmt   정보기술 편성액 (천원)
     * @param secReqAmt  정보보호 편성요청액 (천원)
     * @param secAdjAmt  정보보호 편성액 (천원)
     * @param totalReqAmt 합계 편성요청액 (천원)
     * @param totalAdjAmt 합계 편성액 (천원)
     */
    @Schema(name = "ItBudgetCategoryRow", description = "비목별 IT/정보보호 구분 집계 행")
    public record CategoryRow(
            @Schema(description = "비목코드값", example = "001") String ioeCode,
            @Schema(description = "비목코드값명", example = "개발비") String codeNm,
            @Schema(description = "정보기술 편성요청액 (천원)") long itReqAmt,
            @Schema(description = "정보기술 편성액 (천원)") long itAdjAmt,
            @Schema(description = "정보보호 편성요청액 (천원)") long secReqAmt,
            @Schema(description = "정보보호 편성액 (천원)") long secAdjAmt,
            @Schema(description = "합계 편성요청액 (천원)") long totalReqAmt,
            @Schema(description = "합계 편성액 (천원)") long totalAdjAmt
    ) {}

    /**
     * 정보기술부문 예산 조회 응답
     *
     * @param bgYy 예산년도
     * @param rows 비목별 집계 행 목록
     */
    @Schema(name = "ItBudgetSummaryResponse", description = "정보기술부문 예산 조회 응답")
    public record SummaryResponse(
            @Schema(description = "예산년도", example = "2026") String bgYy,
            @Schema(description = "비목별 집계 행 목록") List<CategoryRow> rows
    ) {}

    /**
     * 전년 대비 증감 행
     *
     * @param ioeCode    비목코드값
     * @param categoryNm 비목코드값명
     * @param prevAmt    전년도 편성요청액 합계 (천원)
     * @param currAmt    금년도 편성요청액 합계 (천원)
     * @param diff       증감액 (천원)
     * @param diffRate   증감률 (%), 전년도 0이면 null
     */
    @Schema(name = "ItBudgetYoyRow", description = "전년 대비 증감 행")
    public record YoyRow(
            @Schema(description = "비목코드값") String ioeCode,
            @Schema(description = "비목코드값명") String categoryNm,
            @Schema(description = "전년도 편성요청액 합계 (천원)") long prevAmt,
            @Schema(description = "금년도 편성요청액 합계 (천원)") long currAmt,
            @Schema(description = "증감액 (천원)") long diff,
            @Schema(description = "증감률 (%), 전년도 0이면 null") Double diffRate
    ) {}

    /**
     * 정보기술부문 예산 비교 응답
     *
     * @param currYy        금년도
     * @param prevYy        전년도
     * @param fssMapping    금감원 비목 매핑표 (추후 구현 예정, 현재 빈 목록)
     * @param yoyComparison 전년 대비 증감 목록
     */
    @Schema(name = "ItBudgetComparisonResponse", description = "정보기술부문 예산 비교 응답")
    public record ComparisonResponse(
            @Schema(description = "금년도") String currYy,
            @Schema(description = "전년도") String prevYy,
            @Schema(description = "금감원 비목 매핑표 (추후 구현 예정)") List<Object> fssMapping,
            @Schema(description = "전년 대비 증감 목록") List<YoyRow> yoyComparison
    ) {}
}
