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

    /**
     * DCD_STS 코드값으로 enum 상수를 조회합니다.
     *
     * @param code DCD_STS 코드값 (예: "001"=미결재, "002"=승인, "003"=반려, "004"=회수무효)
     * @return 해당 코드의 {@link DecisionStatus}
     * @throws IllegalArgumentException 등록되지 않은 코드값이 입력된 경우 (null 포함)
     */
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
