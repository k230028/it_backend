package com.kdb.it.domain.budget.it.controller;

import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.it.service.ItBudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ItBudgetControllerTest {

    @Mock
    private ItBudgetService itBudgetService;

    @InjectMocks
    private ItBudgetController controller;

    @Test
    @DisplayName("getSummary: 서비스 응답을 200 OK로 반환한다")
    void getSummary_returnsOk() {
        var response = new ItBudgetDto.SummaryResponse("2026", List.of());
        given(itBudgetService.getSummary("2026")).willReturn(response);

        ResponseEntity<ItBudgetDto.SummaryResponse> result = controller.getSummary("2026");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    @DisplayName("getComparison: 서비스 응답을 200 OK로 반환한다")
    void getComparison_returnsOk() {
        var mapping = new ItBudgetDto.FssMappingRow(
                "001",
                "개발비",
                "개발비",
                "개발비",
                200,
                "정식 금감원 매핑 테이블 도입 전 임시 동일 비목 매핑"
        );
        var response = new ItBudgetDto.ComparisonResponse("2026", "2025", List.of(mapping), List.of());
        given(itBudgetService.getComparison("2026")).willReturn(response);

        ResponseEntity<ItBudgetDto.ComparisonResponse> result = controller.getComparison("2026");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(response);
        assertThat(result.getBody().fssMapping()).containsExactly(mapping);
    }
}
