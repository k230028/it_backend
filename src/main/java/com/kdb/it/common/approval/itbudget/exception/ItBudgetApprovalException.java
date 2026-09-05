package com.kdb.it.common.approval.itbudget.exception;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ChangedSource;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 전산예산 미리보기·상신에서 HTTP 상태와 구조화된 충돌 정보를 전달한다. */
public final class ItBudgetApprovalException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ChangedSource> changedSources;

    public ItBudgetApprovalException(
            HttpStatus status, String code, String message, List<ChangedSource> changedSources) {
        super(message);
        this.status = status;
        this.code = code;
        this.changedSources = List.copyOf(changedSources);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ChangedSource> changedSources() {
        return changedSources;
    }
}
