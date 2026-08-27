package com.kdb.it.common.approval.domain;

/** 신청서 결재상태 (Ccodem cId='APF_STS'). */
public enum ApprovalStatus {
    MANUAL("0", "수기등록"),
    IN_PROGRESS("1", "결재중"),
    COMPLETED("2", "결재완료"),
    REJECTED("3", "반려"),
    RECALLED("4", "회수");

    private final String code;
    private final String label;

    ApprovalStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * APF_STS 코드값으로 enum 상수를 조회합니다.
     *
     * @param code APF_STS 코드값 (예: "1"=결재중, "2"=결재완료)
     * @return 해당 코드의 {@link ApprovalStatus}
     * @throws IllegalArgumentException 등록되지 않은 코드값이 입력된 경우 (null 포함)
     */
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

    /**
     * 결재 처리가 완전히 종료된 상태인지 반환합니다.
     *
     * <p>종료 상태: {@link #COMPLETED}(결재완료), {@link #REJECTED}(반려), {@link #RECALLED}(회수). {@link
     * #IN_PROGRESS}(결재중)는 종료 상태가 아닙니다.
     *
     * @return 종료 상태이면 true
     */
    public boolean isTerminated() {
        return this == COMPLETED || this == REJECTED || this == RECALLED;
    }
}
