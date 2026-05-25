package com.kdb.it.common.approval.domain;

/**
 * 신청서 결재상태 (Ccodem cId='APF_STS').
 */
public enum ApprovalStatus {
    IN_PROGRESS("001", "결재중"),
    COMPLETED  ("002", "결재완료"),
    REJECTED   ("003", "반려"),
    RECALLED   ("004", "회수");

    private final String code;
    private final String label;

    ApprovalStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code()  { return code; }
    public String label() { return label; }

    public static ApprovalStatus ofCode(String code) {
        for (ApprovalStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown APF_STS code: " + code);
    }

    public boolean isTerminated() {
        return this == COMPLETED || this == REJECTED || this == RECALLED;
    }
}
