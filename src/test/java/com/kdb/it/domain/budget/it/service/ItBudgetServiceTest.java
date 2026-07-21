package com.kdb.it.domain.budget.it.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.it.repository.ItBudgetQueryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItBudgetServiceTest {

    @Mock private ItBudgetQueryRepository itBudgetQueryRepository;

    @InjectMocks private ItBudgetService service;

    @Test
    @DisplayName("getSummary: 조회 연도와 집계 행을 그대로 응답한다")
    void getSummary_returnsRows() {
        var row =
                new ItBudgetDto.CategoryRow(
                        "001", "351-1100", "개발비(일반)", "개발비", "전산용역비", true, 10, 5, 3, 2, 13, 7);
        given(itBudgetQueryRepository.findSummary("2026")).willReturn(List.of(row));

        ItBudgetDto.SummaryResponse result = service.getSummary("2026");

        assertThat(result.bgYy()).isEqualTo("2026");
        assertThat(result.rows()).containsExactly(row);
    }

    @Test
    @DisplayName("getComparison: 전년도 금액이 있으면 증감액과 증감률을 계산한다")
    void getComparison_calculatesDiffRate() {
        given(itBudgetQueryRepository.findSummary("2026"))
                .willReturn(
                        List.of(
                                new ItBudgetDto.CategoryRow(
                                        "001",
                                        "351-1100",
                                        "개발비(일반)",
                                        "개발비",
                                        "전산용역비",
                                        true,
                                        150,
                                        0,
                                        50,
                                        0,
                                        200,
                                        0)));
        given(itBudgetQueryRepository.findSummary("2025"))
                .willReturn(
                        List.of(
                                new ItBudgetDto.CategoryRow(
                                        "001",
                                        "351-1100",
                                        "개발비(일반)",
                                        "개발비",
                                        "전산용역비",
                                        true,
                                        100,
                                        0,
                                        0,
                                        0,
                                        100,
                                        0)));

        ItBudgetDto.ComparisonResponse result = service.getComparison("2026");

        assertThat(result.currYy()).isEqualTo("2026");
        assertThat(result.prevYy()).isEqualTo("2025");
        assertThat(result.fssMapping())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.ioeCode()).isEqualTo("001");
                            /* 당행 비목코드 = 코드값상세코드, 당행 비목명 = 코드값약어명 */
                            assertThat(row.ioeDtlCode()).isEqualTo("351-1100");
                            assertThat(row.bankCategoryNm()).isEqualTo("개발비");
                            assertThat(row.categoryNm()).isEqualTo("개발비(일반)");
                            assertThat(row.fssCategory()).isEqualTo("개발비(일반)");
                            assertThat(row.currAmt()).isEqualTo(200);
                            assertThat(row.note()).contains("임시");
                        });
        assertThat(result.yoyComparison())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.prevAmt()).isEqualTo(100);
                            assertThat(row.currAmt()).isEqualTo(200);
                            assertThat(row.diff()).isEqualTo(100);
                            assertThat(row.diffRate()).isEqualTo(100.0);
                        });
    }

    @Test
    @DisplayName("getComparison: 코드값약어명이 없으면 당행 비목명은 코드값명으로 대체한다")
    void getComparison_blankAbbrNm_fallsBackToCodeNm() {
        given(itBudgetQueryRepository.findSummary("2026"))
                .willReturn(
                        List.of(
                                new ItBudgetDto.CategoryRow(
                                        "009",
                                        "239-0200",
                                        "외주용역(자문/심사)",
                                        null,
                                        "전산용역비",
                                        false,
                                        10,
                                        0,
                                        0,
                                        0,
                                        10,
                                        0)));
        given(itBudgetQueryRepository.findSummary("2025")).willReturn(List.of());

        ItBudgetDto.ComparisonResponse result = service.getComparison("2026");

        assertThat(result.fssMapping())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.ioeDtlCode()).isEqualTo("239-0200");
                            assertThat(row.bankCategoryNm()).isEqualTo("외주용역(자문/심사)");
                        });
    }

    @Test
    @DisplayName("getComparison: 전년도 금액이 없으면 증감률은 null이다")
    void getComparison_prevMissing_hasNullRate() {
        given(itBudgetQueryRepository.findSummary("2026"))
                .willReturn(
                        List.of(
                                new ItBudgetDto.CategoryRow(
                                        "002",
                                        "240-0200",
                                        "유지보수료",
                                        "유지보수료",
                                        "전산제비",
                                        false,
                                        30,
                                        0,
                                        0,
                                        0,
                                        30,
                                        0)));
        given(itBudgetQueryRepository.findSummary("2025")).willReturn(List.of());

        ItBudgetDto.ComparisonResponse result = service.getComparison("2026");

        assertThat(result.yoyComparison())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.prevAmt()).isZero();
                            assertThat(row.diff()).isEqualTo(30);
                            assertThat(row.diffRate()).isNull();
                        });
    }
}
