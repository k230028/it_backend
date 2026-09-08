package com.kdb.it.domain.budget.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 정보화사업 저장 충돌 응답
 *
 * <p>{@code current}는 저장 직전 잠금 상태에서 읽은 상세 응답이며 품목 목록 전체를 포함한다. 400 응답에서는 {@code changedBy}·{@code
 * changedAt}·{@code currentStamp}·{@code current}가 모두 null이다.
 */
@Schema(name = "ProjectConflictResponse", description = "정보화사업 저장 충돌 응답")
public record ProjectConflictResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String changedBy,
        String changedByEno,
        LocalDateTime changedAt,
        String currentStamp,
        ProjectDto.Response current) {}
