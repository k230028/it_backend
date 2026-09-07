package com.kdb.it.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostConflictResponse;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 전산업무비 저장 충돌 예외가 코드·현재 상태를 담은 응답으로 변환되는지 검증합니다. */
class CostConflictExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("충돌 예외는 409와 현재 상태·스탬프를 반환한다")
    void conflictCarriesCurrentState() {
        CostDto.Response current = CostDto.Response.builder().costBgNo("COST_2026_0001").build();
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 8, 14, 25);
        CostConflictException exception =
                new CostConflictException(
                        HttpStatus.CONFLICT,
                        "COST_SOURCE_CHANGED",
                        "다른 사용자가 이 전산업무비를 수정했습니다.",
                        "홍길동",
                        changedAt,
                        "b".repeat(64),
                        current);

        ResponseEntity<CostConflictResponse> response = handler.handleCostConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("COST_SOURCE_CHANGED");
        assertThat(response.getBody().changedBy()).isEqualTo("홍길동");
        assertThat(response.getBody().changedAt()).isEqualTo(changedAt);
        assertThat(response.getBody().currentStamp()).isEqualTo("b".repeat(64));
        assertThat(response.getBody().current()).isSameAs(current);
    }

    @Test
    @DisplayName("스탬프 누락은 400과 빈 현재 상태를 반환한다")
    void missingStampIsBadRequest() {
        CostConflictException exception =
                new CostConflictException(
                        HttpStatus.BAD_REQUEST,
                        "COST_STAMP_REQUIRED",
                        "동시성 스탬프가 필요합니다.",
                        null,
                        null,
                        null,
                        null);

        ResponseEntity<CostConflictResponse> response = handler.handleCostConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().current()).isNull();
    }
}
