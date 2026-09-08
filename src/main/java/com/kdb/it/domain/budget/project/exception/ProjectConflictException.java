package com.kdb.it.domain.budget.project.exception;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

/**
 * 정보화사업 저장의 동시성 위반을 HTTP 상태와 현재 원장 상태로 전달한다.
 *
 * <p>{@code PROJECT_STAMP_REQUIRED}(400)는 현재 상태 없이, {@code PROJECT_SOURCE_CHANGED}·{@code
 * PROJECT_CONCURRENT_UPDATE}(409)는 사용자가 병합할 수 있도록 현재 상태와 최신 스탬프를 함께 담는다. 전산업무비의 {@code
 * CostConflictException}과 같은 계약이다.
 */
public final class ProjectConflictException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String changedBy;
    private final String changedByEno;
    private final LocalDateTime changedAt;
    private final String currentStamp;
    private final transient ProjectDto.Response current;

    public ProjectConflictException(
            HttpStatus status,
            String code,
            String message,
            String changedBy,
            String changedByEno,
            LocalDateTime changedAt,
            String currentStamp,
            ProjectDto.Response current) {
        super(message);
        this.status = status;
        this.code = code;
        this.changedBy = changedBy;
        this.changedByEno = changedByEno;
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

    public String changedByEno() {
        return changedByEno;
    }

    public LocalDateTime changedAt() {
        return changedAt;
    }

    public String currentStamp() {
        return currentStamp;
    }

    public ProjectDto.Response current() {
        return current;
    }
}
