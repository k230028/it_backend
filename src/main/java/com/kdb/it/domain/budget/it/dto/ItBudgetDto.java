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
     * @param ioeDtlCode 비목 코드값상세코드 (CO_CDVA_NM, 예: 237-0700). 비목 간 중복될 수 있음
     * @param codeNm     비목코드값명 (CDVA_NM, 예: 외주용역(외주운영/관제 등))
     * @param codeAbbrNm 비목 코드값약어명 (CO_CDVA_ABV_NM, 예: 외주용역). 미등록이면 null
     * @param groupName  비목 중분류 그룹명 (예: 전산임차료). 해석 근거가 없으면 null
     * @param capital    자본예산 여부 (true: 자본예산, false: 일반관리비)
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
            @Schema(description = "비목 코드값상세코드", example = "237-0700") String ioeDtlCode,
            @Schema(description = "비목코드값명", example = "개발비") String codeNm,
            @Schema(description = "비목 코드값약어명", example = "외주용역") String codeAbbrNm,
            @Schema(description = "비목 중분류 그룹명", example = "전산임차료") String groupName,
            @Schema(description = "자본예산 여부") boolean capital,
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
     * 금감원 비목 매핑 행
     *
     * @param ioeCode        비목코드값 (CDVA_ID, 예: 008). 행 식별용
     * @param ioeDtlCode     당행 비목코드 — 코드값상세코드 (CO_CDVA_NM, 예: 239-0200)
     * @param bankCategoryNm 당행 비목명 — 코드값약어명 (CO_CDVA_ABV_NM, 예: 외주용역).
     *                       약어명이 없으면 코드값명으로 대체됩니다.
     * @param categoryNm     당행 비목코드값명 (CDVA_NM, 예: 외주용역(외주운영/관제 등))
     * @param fssCategory    금감원 분류명
     * @param currAmt        금년도 편성요청액 합계 (천원)
     * @param note           매핑 산출 기준 비고
     */
    @Schema(name = "ItBudgetFssMappingRow", description = "금감원 비목 매핑 행")
    public record FssMappingRow(
            @Schema(description = "비목코드값", example = "008") String ioeCode,
            @Schema(description = "당행 비목코드 (코드값상세코드)", example = "239-0200") String ioeDtlCode,
            @Schema(description = "당행 비목명 (코드값약어명)", example = "외주용역") String bankCategoryNm,
            @Schema(description = "당행 비목코드값명") String categoryNm,
            @Schema(description = "금감원 분류명") String fssCategory,
            @Schema(description = "금년도 편성요청액 합계 (천원)") long currAmt,
            @Schema(description = "매핑 산출 기준 비고") String note
    ) {}

    /**
     * 정보기술부문 예산 비교 응답
     *
     * @param currYy        금년도
     * @param prevYy        전년도
     * @param fssMapping    금감원 비목 매핑표
     * @param yoyComparison 전년 대비 증감 목록
     */
    @Schema(name = "ItBudgetComparisonResponse", description = "정보기술부문 예산 비교 응답")
    public record ComparisonResponse(
            @Schema(description = "금년도") String currYy,
            @Schema(description = "전년도") String prevYy,
            @Schema(description = "금감원 비목 매핑표") List<FssMappingRow> fssMapping,
            @Schema(description = "전년 대비 증감 목록") List<YoyRow> yoyComparison
    ) {}
}
