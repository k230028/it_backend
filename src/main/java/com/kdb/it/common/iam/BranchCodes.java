package com.kdb.it.common.iam;

/** 부점코드 판정 규칙 모음. 편성요청서 반입과 결재라인 자동지정이 같은 기준을 쓴다. */
public final class BranchCodes {

    /** 국외 점포 부점코드의 앞자리. 실측 조직표에서 `9**`가 국외 점포다(예: 런던지점 `920`). */
    public static final String FOREIGN_PREFIX = "9";

    private BranchCodes() {}

    /**
     * 국외점포인지 판정합니다.
     *
     * @param bbrC 부점코드. null·빈 값은 국내로 봅니다
     * @return 국외점포면 true
     */
    public static boolean isForeign(String bbrC) {
        return bbrC != null && bbrC.startsWith(FOREIGN_PREFIX);
    }
}
