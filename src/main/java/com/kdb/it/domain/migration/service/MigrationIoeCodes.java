package com.kdb.it.domain.migration.service;

import java.util.Set;

/**
 * 이관이 쓰는 비목코드 기본값과 자본예산 계열 판정을 한 곳에서만 정의합니다.
 *
 * <p>같은 리터럴이 어댑터·오케스트레이션·검증기 세 곳에 흩어져 있으면 한쪽만 바꿨을 때 조용히 어긋납니다. 특히 자본예산 시트가 만드는 품목의 기본 비목과 부문계획 조정이
 * 다시 만드는 품목의 기본 비목은 **같은 값이어야** 조정이 같은 비목의 편성행을 교체합니다.
 *
 * <p>{@link #CAPITAL_CODES}는 {@code IoeCategories.CAPITAL_CTPS}(코드타입 기준)에 대응하는 실제 코드값 집합입니다. 이관 경로는
 * 공통코드 조회 없이 코드값만으로 판정해야 하는 지점(보정값 검증, 연도 스냅샷의 편성률 재구성)이 있어 코드값 집합을 함께 둡니다.
 */
public final class MigrationIoeCodes {

    /** 개발비 기본 비목 — 개발비(일반). 감리/컨설팅은 104. */
    public static final String IOE_DEV = "103";

    /** 기계장치 기본 비목 — 국내기계장치. 국외는 102. */
    public static final String IOE_HW = "101";

    /** 기타무형자산 기본 비목 — 국내기타무형자산(일반). 국외는 105, SW라이선스는 107. */
    public static final String IOE_SW = "106";

    /** 국외점포 기계장치 비목 (위임예산 HW). */
    public static final String IOE_HW_OVERSEA = "102";

    /** 국외점포 기타무형자산 비목 (위임예산 SW). */
    public static final String IOE_SW_OVERSEA = "105";

    /** 자본예산 계열 비목코드 — 개발비·기계장치·기타무형자산. */
    public static final Set<String> CAPITAL_CODES =
            Set.of("101", "102", "103", "104", "105", "106", "107");

    private MigrationIoeCodes() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 비목코드가 자본예산 계열인지 판정합니다.
     *
     * @param ioeC 비목코드 (null 허용)
     * @return 자본예산 계열이면 true. null·미등록은 false(일반관리비 취급)
     */
    public static boolean isCapital(String ioeC) {
        return ioeC != null && CAPITAL_CODES.contains(ioeC.trim());
    }
}
