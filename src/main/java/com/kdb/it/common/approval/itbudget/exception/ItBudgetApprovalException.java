package com.kdb.it.common.approval.itbudget.exception;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ChangedSource;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 전산예산 미리보기·상신에서 HTTP 상태와 구조화된 충돌 정보를 전달한다. */
public final class ItBudgetApprovalException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ChangedSource> changedSources;
    private final Reason reason;

    /** HTTP 오류 코드를 바꾸지 않고 서명 검증 실패만 운영 지표에서 구별한다. */
    public enum Reason {
        UNSPECIFIED,
        SIGNATURE_FAILURE
    }

    public ItBudgetApprovalException(
            HttpStatus status, String code, String message, List<ChangedSource> changedSources) {
        this(status, code, message, changedSources, Reason.UNSPECIFIED);
    }

    public ItBudgetApprovalException(
            HttpStatus status,
            String code,
            String message,
            List<ChangedSource> changedSources,
            Reason reason) {
        super(message);
        this.status = status;
        this.code = code;
        this.changedSources = List.copyOf(changedSources);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
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
