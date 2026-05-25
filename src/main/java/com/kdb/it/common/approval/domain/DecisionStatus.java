package com.kdb.it.common.approval.domain;

/**
 * 결재선 결재상태 (Ccodem cId='DCD_STS').
 */
public enum DecisionStatus {
    PENDING    ("001", "미결재"),
    APPROVED   ("002", "승인"),
    REJECTED   ("003", "반려"),
    INVALIDATED("004", "회수무효");

    private final String code;
    private final String label;

    DecisionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code()  { return code; }
    public String label() { return label; }

    public static DecisionStatus ofCode(String code) {
        for (DecisionStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown DCD_STS code: " + code);
    }

    /** 라벨(한글명)로 enum 조회. (예: "승인" → APPROVED) */
    public static DecisionStatus ofLabel(String label) {
        for (DecisionStatus s : values()) if (s.label.equals(label)) return s;
        throw new IllegalArgumentException("Unknown DCD_STS label: " + label);
    }
}
