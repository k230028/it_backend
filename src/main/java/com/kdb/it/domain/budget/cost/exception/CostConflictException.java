package com.kdb.it.domain.budget.cost.exception;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

/**
 * 전산업무비 저장의 동시성 위반을 HTTP 상태와 현재 원장 상태로 전달한다.
 *
 * <p>{@code COST_STAMP_REQUIRED}(400)는 현재 상태 없이, {@code COST_SOURCE_CHANGED}·{@code
 * COST_CONCURRENT_UPDATE}(409)는 사용자가 병합할 수 있도록 현재 상태와 최신 스탬프를 함께 담는다.
 */
public final class CostConflictException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String changedBy;
    private final LocalDateTime changedAt;
    private final String currentStamp;
    private final transient CostDto.Response current;

    public CostConflictException(
            HttpStatus status,
            String code,
            String message,
            String changedBy,
            LocalDateTime changedAt,
            String currentStamp,
            CostDto.Response current) {
        super(message);
        this.status = status;
        this.code = code;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.currentStamp = currentStamp;
        this.current = current;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String changedBy() {
        return changedBy;
    }

    public LocalDateTime changedAt() {
        return changedAt;
    }

    public String currentStamp() {
        return currentStamp;
    }

    public CostDto.Response current() {
        return current;
    }
}
