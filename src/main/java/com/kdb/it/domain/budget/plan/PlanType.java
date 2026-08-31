package com.kdb.it.domain.budget.plan;

import java.util.Arrays;

/** 정보기술부문계획 유형의 저장 코드와 표시명을 관리합니다. */
public enum PlanType {
    ESTABLISHMENT("01", "신규", "수립"),
    ADJUSTMENT("02", "조정", "조정");

    private final String code;
    private final String displayName;
    private final String planName;

    PlanType(String code, String displayName, String planName) {
        this.code = code;
        this.displayName = displayName;
        this.planName = planName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String planName() {
        return planName;
    }

    /** 저장 코드가 지원하는 계획 유형인지 확인해 반환합니다. */
    public static PlanType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 계획유형 코드입니다."));
    }
}
