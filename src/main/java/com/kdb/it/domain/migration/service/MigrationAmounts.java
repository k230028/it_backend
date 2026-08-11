package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;

/**
 * 시트별 금액 배수를 한 곳에서만 정의합니다.
 *
 * <p>수기 엑셀은 시트마다 금액 단위가 다릅니다 — 일반관리비 시트({@link SheetKind#COST})는 천원 단위라 ×1,000, 자본예산·부문계획 시트({@link
 * SheetKind#CAPITAL_PROJECT}, {@link SheetKind#PLAN_ADJUSTMENT})는 백만원 단위라 ×1,000,000, 위임예산
 * 시트({@link SheetKind#DELEGATED_BUDGET})는 이미 원 단위라 ×1입니다. 이 배수는 {@link MigrationValidator}의 금액 대조와
 * 다음 태스크(시트 어댑터)의 실제 저장값 환산이 반드시 같은 규칙을 써야 하므로, 두 곳이 각자 {@code switch}를 갖지 않도록 이 클래스 하나로 모읍니다. 이후
 * 추가되는 {@code AdapterSupport.amount(String, SheetKind)}도 이 메서드를 호출해야 하며, 같은 배수 규칙을 다시 파생하지 않습니다.
 */
public final class MigrationAmounts {

    private MigrationAmounts() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 시트 종류에 해당하는 금액 배수를 반환합니다. 엑셀 표기 단위를 원 단위로 올리는 데 사용합니다.
     *
     * @param kind 시트 종류 (null 아님)
     * @return 금액 배수 (COST=1,000 / CAPITAL_PROJECT·PLAN_ADJUSTMENT=1,000,000 / DELEGATED_BUDGET=1)
     */
    public static BigDecimal amountMultiplier(SheetKind kind) {
        return switch (kind) {
            case COST -> new BigDecimal("1000");
            case CAPITAL_PROJECT, PLAN_ADJUSTMENT -> new BigDecimal("1000000");
            case DELEGATED_BUDGET -> BigDecimal.ONE;
        };
    }
}
