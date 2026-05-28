package com.kdb.it.domain.budget.it.service;

import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.it.repository.ItBudgetQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 정보기술부문 예산 서비스
 *
 * <p>
 * IT/정보보호 구분 비목별 편성요청액·편성액 조회 및 전년도 대비 비교 로직을 처리합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItBudgetService {

    private final ItBudgetQueryRepository itBudgetQueryRepository;

    /**
     * 정보기술부문 예산 요약 조회
     *
     * <p>비목별 IT/정보보호 편성요청액·편성액을 천원 단위로 반환합니다.</p>
     *
     * @param bgYy 예산년도
     * @return 요약 응답 (bgYy + 비목별 행 목록)
     */
    public ItBudgetDto.SummaryResponse getSummary(String bgYy) {
        List<ItBudgetDto.CategoryRow> rows = itBudgetQueryRepository.findSummary(bgYy);
        return new ItBudgetDto.SummaryResponse(bgYy, rows);
    }

    /**
     * 정보기술부문 예산 비교 (전년도 대비)
     *
     * <p>
     * 금년도와 전년도 편성요청액 합계(IT+정보보호)를 비교하여
     * 증감액·증감률을 반환합니다.
     * </p>
     *
     * @param bgYy 금년도 예산년도
     * @return 비교 응답 (금년/전년도 + 증감 목록)
     */
    public ItBudgetDto.ComparisonResponse getComparison(String bgYy) {
        String prevYy = String.valueOf(Integer.parseInt(bgYy) - 1);

        List<ItBudgetDto.CategoryRow> currRows = itBudgetQueryRepository.findSummary(bgYy);
        List<ItBudgetDto.CategoryRow> prevRows = itBudgetQueryRepository.findSummary(prevYy);

        Map<String, ItBudgetDto.CategoryRow> prevMap = prevRows.stream()
                .collect(Collectors.toMap(ItBudgetDto.CategoryRow::ioeCode, Function.identity()));

        // 금년도 비목 기준으로 증감 계산
        List<ItBudgetDto.YoyRow> yoyRows = new ArrayList<>();
        for (ItBudgetDto.CategoryRow curr : currRows) {
            ItBudgetDto.CategoryRow prev = prevMap.get(curr.ioeCode());
            long currAmt = curr.totalReqAmt();
            long prevAmt = prev != null ? prev.totalReqAmt() : 0L;
            long diff = currAmt - prevAmt;
            Double diffRate = prevAmt != 0 ? Math.round((double) diff / prevAmt * 1000.0) / 10.0 : null;
            yoyRows.add(new ItBudgetDto.YoyRow(curr.ioeCode(), curr.codeNm(), prevAmt, currAmt, diff, diffRate));
        }

        return new ItBudgetDto.ComparisonResponse(bgYy, prevYy, List.of(), yoyRows);
    }
}
