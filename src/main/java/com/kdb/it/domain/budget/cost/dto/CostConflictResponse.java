package com.kdb.it.domain.budget.cost.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 전산업무비 저장 충돌 응답
 *
 * <p>{@code current}는 저장 직전 잠금 상태에서 읽은 상세 응답이며 단말 목록 전체를 포함한다. 400 응답에서는 {@code changedBy}·{@code
 * changedAt}·{@code currentStamp}·{@code current}가 모두 null이다.
 */
@Schema(description = "전산업무비 저장 충돌 응답")
public record CostConflictResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String changedBy,
        LocalDateTime changedAt,
        String currentStamp,
        CostDto.Response current) {}
