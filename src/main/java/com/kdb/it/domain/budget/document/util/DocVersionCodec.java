package com.kdb.it.domain.budget.document.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 문서버전(DOC_VRS_SNO) 저장/표시 변환 유틸리티
 *
 * <p>
 * 물리 컬럼 {@code TPRMPP_BRDOCM.DOC_VRS_SNO}, {@code TPRMPP_BRIVGM.DOC_VRS_SNO}는
 * Oracle {@code NUMBER(9,0)}(정수)로 정의되어 있어 소수 버전(0.01 단위)을 그대로 저장하면
 * 소수부가 절삭됩니다. 예를 들어 {@code 0.01}이 {@code 0}으로 저장되어 다음 버전과
 * 기본키(PK)가 충돌(ORA-00001)합니다.
 * </p>
 *
 * <p>
 * 컬럼 스키마를 변경하지 않고 이 문제를 해결하기 위해, 버전 값을 다음 규약으로 다룹니다.
 * </p>
 * <ul>
 *   <li><b>저장(toStored)</b>: 화면 표시용 소수 버전 × 100 → 정수로 저장. (예: {@code 0.01 → 1}, {@code 1.00 → 100})</li>
 *   <li><b>표시(toDisplay)</b>: 저장된 정수 ÷ 100 → 소수 버전으로 변환. (예: {@code 1 → 0.01}, {@code 100 → 1.00})</li>
 * </ul>
 *
 * <p>
 * 화면/API 레이어는 항상 소수 버전(0.01, 1.00 ...)을 사용하고, DB 영속화/조회 키로 사용할 때만
 * {@code toStored}로 변환합니다. 엔티티에서 읽어 화면으로 내보낼 때는 {@code toDisplay}로 변환합니다.
 * </p>
 */
public final class DocVersionCodec {

    /** 저장 배율 (소수 2자리 → 정수). */
    private static final BigDecimal SCALE_FACTOR = new BigDecimal("100");

    private DocVersionCodec() {
        // 유틸리티 클래스: 인스턴스화 금지
    }

    /**
     * 화면 표시용 소수 버전을 저장용 정수 버전으로 변환합니다. (× 100)
     *
     * @param display 화면 표시용 소수 버전 (예: 0.01, 1.00). {@code null}이면 {@code null} 반환.
     * @return 저장용 정수 버전 (예: 1, 100)
     */
    public static BigDecimal toStored(BigDecimal display) {
        if (display == null) {
            return null;
        }
        return display.multiply(SCALE_FACTOR).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 저장용 정수 버전을 화면 표시용 소수 버전으로 변환합니다. (÷ 100)
     *
     * @param stored 저장용 정수 버전 (예: 1, 100). {@code null}이면 {@code null} 반환.
     * @return 화면 표시용 소수 버전 (소수 2자리, 예: 0.01, 1.00)
     */
    public static BigDecimal toDisplay(BigDecimal stored) {
        if (stored == null) {
            return null;
        }
        return stored.divide(SCALE_FACTOR).setScale(2, RoundingMode.HALF_UP);
    }
}
