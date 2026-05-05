package com.kdb.it.domain.budget.work.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 예산 작업 관련 DTO 클래스 모음
 *
 * <p>
 * 예산 편성률 적용(TAAABB_BBUGTM)의 요청/응답 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <p>
 * 포함된 DTO:
 * </p>
 * <ul>
 * <li>{@link ApplyRequest}: 편성률 일괄 적용 요청</li>
 * <li>{@link RateItem}: 개별 비목 편성률</li>
 * <li>{@link IoeCategoryResponse}: 편성비목 목록 조회 응답</li>
 * <li>{@link SummaryResponse}: 편성 결과 요약 응답</li>
 * <li>{@link SummaryItem}: 비목별 요약 항목</li>
 * <li>{@link SummaryTotals}: 합계</li>
 * <li>{@link ApplyResponse}: 편성률 적용 결과 응답</li>
 * </ul>
 *
 * // Design Ref: §4.3 — BudgetWorkDto (record 기반 DTO)
 */
public class BudgetWorkDto {

    /**
     * 편성률 일괄 적용 요청 DTO
     *
     * @param bgYy  예산년도 (예: 2026)
     * @param rates 비목별 편성률 목록
     */
    @Schema(name = "BudgetWorkApplyRequest", description = "편성률 일괄 적용 요청")
    public record ApplyRequest(
            @Schema(description = "예산년도", example = "2026") String bgYy,
            @Schema(description = "비목별 편성률 목록") List<RateItem> rates
    ) {}

    /**
     * 개별 비목 편성률 DTO
     *
     * @param cdId  편성비목 코드ID (예: DUP-IOE-237)
     * @param dupRt 편성률 (0~100)
     */
    @Schema(name = "BudgetWorkRateItem", description = "개별 비목 편성률")
    public record RateItem(
            @Schema(description = "편성비목 코드ID", example = "DUP-IOE-237") String cdId,
            @Schema(description = "편성률 (0~100)", example = "80") Integer dupRt
    ) {}

    /**
     * 사업별 편성률 적용 요청 DTO (REQ-2: 비목별 → 사업별 전환)
     *
     * @param bgYy  예산년도 (예: 2026)
     * @param items 사업별 편성률 목록
     */
    @Schema(name = "BudgetWorkItemApplyRequest", description = "사업별 편성률 적용 요청")
    public record ItemApplyRequest(
            @Schema(description = "예산년도", example = "2026") String bgYy,
            @Schema(description = "사업별 편성률 목록") List<ItemRate> items
    ) {}

    /**
     * 개별 사업 편성률 DTO (자본예산/일반관리비 분리)
     *
     * @param orcTb      원본 테이블 (TAAABB_BPROJM / TAAABB_BCOSTM)
     * @param orcPkVl    원본 PK (prjMngNo / itMngcNo)
     * @param assetDupRt 자본예산 편성률 (0~100, null=해당없음)
     * @param costDupRt  일반관리비 편성률 (0~100)
     */
    @Schema(name = "BudgetWorkItemRate", description = "개별 사업 편성률 (자본예산/일반관리비 분리)")
    public record ItemRate(
            @Schema(description = "원본 테이블", example = "BPROJM") String orcTb,
            @Schema(description = "원본 PK", example = "PRJ-2026-0001") String orcPkVl,
            @Schema(description = "자본예산 편성률 (0~100, null=해당없음)") Integer assetDupRt,
            @Schema(description = "일반관리비 편성률 (0~100)") Integer costDupRt
    ) {}

    /**
     * 편성비목 목록 조회 응답 DTO
     *
     * @param cdId          편성비목 코드ID
     * @param cdNm          편성비목명
     * @param cdva          코드값
     * @param prefix        비목 접두어 (IOE_C/GCL_DTT 매칭용)
     * @param dupRt         기존 편성률 (미적용 시 null)
     * @param requestAmount 결재완료 요청금액 합계
     */
    @Schema(name = "BudgetWorkIoeCategoryResponse", description = "편성비목 목록 조회 응답")
    public record IoeCategoryResponse(
            @Schema(description = "편성비목 코드ID", example = "DUP-IOE-237") String cdId,
            @Schema(description = "편성비목명", example = "전산임차료(SW)") String cdNm,
            @Schema(description = "코드값") String cdva,
            @Schema(description = "비목 접두어", example = "237") String prefix,
            @Schema(description = "기존 편성률 (0~100)") Integer dupRt,
            @Schema(description = "결재완료 요청금액 합계") BigDecimal requestAmount
    ) {}

    /**
     * 편성 결과 요약 응답 DTO
     *
     * @param data   비목별 요약 항목 목록
     * @param totals 합계
     */
    @Schema(name = "BudgetWorkSummaryResponse", description = "편성 결과 요약 응답")
    public record SummaryResponse(
            @Schema(description = "비목별 요약 항목 목록") List<SummaryItem> data,
            @Schema(description = "합계") SummaryTotals totals
    ) {}

    /**
     * 비목별 요약 항목 DTO
     *
     * @param ioeCategory   세부 비목명 (예: 국외전산임차료)
     * @param ioeC          실제 IOE 코드 (예: IOE-237-0100)
     * @param ioePrefix     편성비목 접두어 (예: IOE-237)
     * @param groupName     편성비목 그룹명 (예: 전산임차료)
     * @param capital       자본예산 여부 (true: 자본예산, false: 일반관리비)
     * @param requestAmount 결재완료 요청금액 합계
     * @param dupAmount     편성금액 합계
     * @param dupRt         편성률
     */
    @Schema(name = "BudgetWorkSummaryItem", description = "비목별 요약 항목")
    public record SummaryItem(
            @Schema(description = "세부 비목명", example = "국외전산임차료") String ioeCategory,
            @Schema(description = "실제 IOE 코드", example = "IOE-237-0100") String ioeC,
            @Schema(description = "편성비목 접두어", example = "IOE-237") String ioePrefix,
            @Schema(description = "편성비목 그룹명", example = "전산임차료") String groupName,
            @Schema(description = "자본예산 여부") boolean capital,
            @Schema(description = "결재완료 요청금액 합계") BigDecimal requestAmount,
            @Schema(description = "편성금액 합계") BigDecimal dupAmount,
            @Schema(description = "편성률 (0~100)") Integer dupRt
    ) {}

    /**
     * 합계 DTO
     *
     * @param requestAmount 요청금액 총합계
     * @param dupAmount     편성금액 총합계
     */
    @Schema(name = "BudgetWorkSummaryTotals", description = "합계")
    public record SummaryTotals(
            @Schema(description = "요청금액 총합계") BigDecimal requestAmount,
            @Schema(description = "편성금액 총합계") BigDecimal dupAmount
    ) {}

    /**
     * 편성률 적용 결과 응답 DTO
     *
     * @param message      처리 결과 메시지
     * @param totalRecords 처리된 총 레코드 수
     * @param summary      편성 결과 요약
     */
    @Schema(name = "BudgetWorkApplyResponse", description = "편성률 적용 결과 응답")
    public record ApplyResponse(
            @Schema(description = "처리 결과 메시지") String message,
            @Schema(description = "처리된 총 레코드 수") int totalRecords,
            @Schema(description = "편성 결과 요약") SummaryResponse summary
    ) {}

    /**
     * 사업별 편성 결과 요약 응답 DTO
     *
     * @param categories 편성비목 목록 (컬럼 헤더용: 비목명 + 편성률)
     * @param data       사업별 요약 항목 목록
     * @param totals     합계
     */
    @Schema(name = "BudgetWorkProjectSummaryResponse", description = "사업별 편성 결과 요약 응답")
    public record ProjectSummaryResponse(
            @Schema(description = "편성비목 목록 (컬럼 헤더용)") List<ProjectSummaryCategory> categories,
            @Schema(description = "사업별 요약 항목 목록") List<ProjectSummaryItem> data,
            @Schema(description = "합계") SummaryTotals totals
    ) {}

    /**
     * 사업별 편성 결과의 비목 컬럼 정보 DTO
     *
     * @param ioePrefix 비목 접두어
     * @param cdNm      비목명
     * @param cdDes     코드 설명 (예: 전산임차료 편성 비율)
     * @param dupRt     편성률 (0~100)
     */
    @Schema(name = "BudgetWorkProjectSummaryCategory", description = "편성비목 컬럼 정보")
    public record ProjectSummaryCategory(
            @Schema(description = "비목 접두어", example = "IOE-237") String ioePrefix,
            @Schema(description = "비목명", example = "전산임차료") String cdNm,
            @Schema(description = "코드 설명", example = "전산임차료 편성 비율") String cdDes,
            @Schema(description = "편성률 (0~100)") Integer dupRt
    ) {}

    /**
     * 사업별 편성 결과 요약 항목 DTO
     *
     * @param orcPkVl        원본PK값 (프로젝트관리번호 또는 전산업무비관리번호)
     * @param orcTb          원본테이블 (BPROJM/BCOSTM)
     * @param name           사업명/계약명
     * @param requestAmount  요청금액 합계
     * @param dupAmount      편성금액 합계
     * @param categoryAmounts 비목별 금액 맵 (key: ioePrefix, value: [요청금액, 편성금액])
     */
    @Schema(name = "BudgetWorkProjectSummaryItem", description = "사업별 편성 결과 요약 항목")
    public record ProjectSummaryItem(
            @Schema(description = "원본PK값") String orcPkVl,
            @Schema(description = "원본테이블 (BPROJM/BCOSTM)") String orcTb,
            @Schema(description = "사업명/계약명") String name,
            @Schema(description = "요청금액 합계") BigDecimal requestAmount,
            @Schema(description = "편성금액 합계") BigDecimal dupAmount,
            @Schema(description = "비목별 금액 맵 (key: ioePrefix, value: {request, dup})") Map<String, CategoryAmount> categoryAmounts
    ) {}

    /**
     * 비목별 금액 DTO
     *
     * @param requestAmount 요청금액
     * @param dupAmount     편성금액
     */
    @Schema(name = "BudgetWorkCategoryAmount", description = "비목별 금액")
    public record CategoryAmount(
            @Schema(description = "요청금액") BigDecimal requestAmount,
            @Schema(description = "편성금액") BigDecimal dupAmount
    ) {}
}
