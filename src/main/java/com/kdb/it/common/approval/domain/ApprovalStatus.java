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

    /** 라벨(한글명)로 enum 조회. (예: "결재중" → IN_PROGRESS) */
    public static ApprovalStatus ofLabel(String label) {
        for (ApprovalStatus s : values()) if (s.label.equals(label)) return s;
        throw new IllegalArgumentException("Unknown APF_STS label: " + label);
    }

    /** 라벨이 유효한지 여부 (없으면 false). */
    public static boolean hasLabel(String label) {
        for (ApprovalStatus s : values()) if (s.label.equals(label)) return true;
        return false;
    }

    public boolean isTerminated() {
        return this == COMPLETED || this == REJECTED || this == RECALLED;
    }
}
