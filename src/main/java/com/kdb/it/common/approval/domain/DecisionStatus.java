package com.kdb.it.common.approval.domain;

/**
 * 결재선 결재상태 (Ccodem cId='IT_PTL_DCD_STS_C').
 *
 * <p>코드값은 현행 CCODEM(IT_PTL_DCD_STS_C) 및 업무 컬럼(VARCHAR2(1))과 일치하는 1자리 체계다:
 * {@code 1=미결재, 2=승인, 3=반려, 4=회수무효}.</p>
 */
public enum DecisionStatus {
    PENDING    ("1", "미결재"),
    APPROVED   ("2", "승인"),
    REJECTED   ("3", "반려"),
    INVALIDATED("4", "회수무효");

    private final String code;
    private final String label;

    DecisionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code()  { return code; }
    public String label() { return label; }

    /**
     * IT_PTL_DCD_STS_C 코드값으로 enum 상수를 조회합니다.
     *
     * @param code IT_PTL_DCD_STS_C 코드값 (예: "1"=미결재, "2"=승인, "3"=반려, "4"=회수무효). 레거시 값도 정규화 후 조회.
     * @return 해당 코드의 {@link DecisionStatus}
     * @throws IllegalArgumentException 등록되지 않은 코드값이 입력된 경우 (null 포함)
     */
    public static DecisionStatus ofCode(String code) {
        String normalizedCode = normalizeCode(code);
        for (DecisionStatus s : values()) if (s.code.equals(normalizedCode)) return s;
        throw new IllegalArgumentException("Unknown IT_PTL_DCD_STS_C code: " + code);
    }

    /** 미결재 코드 여부를 현행값(1)과 레거시값(0, 001) 모두 기준으로 판단합니다. */
    public static boolean isPendingCode(String code) {
        return PENDING.code.equals(normalizeCode(code));
    }

    /** 승인 코드 여부를 현행값(2)과 레거시값(002) 모두 기준으로 판단합니다. */
    public static boolean isApprovedCode(String code) {
        return APPROVED.code.equals(normalizeCode(code));
    }

    /** 라벨(한글명)로 enum 조회. (예: "승인" → APPROVED) */
    public static DecisionStatus ofLabel(String label) {
        for (DecisionStatus s : values()) if (s.label.equals(label)) return s;
        throw new IllegalArgumentException("Unknown IT_PTL_DCD_STS_C label: " + label);
    }

    /**
     * 레거시 코드값을 현행 1자리 체계로 정규화합니다.
     *
     * <ul>
     *   <li>구 3자리 표준값: 001→1, 002→2, 003→3, 004→4</li>
     *   <li>구 0-based 미결재값: 0→1</li>
     *   <li>현행 1자리값(1~4): 그대로 통과</li>
     * </ul>
     */
    private static String normalizeCode(String code) {
        if (code == null) return null;
        return switch (code.trim()) {
            case "001" -> PENDING.code;
            case "002" -> APPROVED.code;
            case "003" -> REJECTED.code;
            case "004" -> INVALIDATED.code;
            case "0"   -> PENDING.code;
            default -> code.trim();
        };
    }
}
