package com.kdb.it.domain.migration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 수기 엑셀 이관 대상 시트 종류입니다. */
@Schema(name = "MigrationSheetKind", description = "이관 대상 시트 종류")
public enum SheetKind {
    /** 전산일반관리비 편성 요구서 '전체취합(국내외)' → BCOSTM */
    COST,
    /** 전산자본예산 편성 요구서 '1-1. 26년정보화사업(전산예산반영)' → BPROJM·BITEMM */
    CAPITAL_PROJECT,
    /** 전산자본예산 편성 요구서 '2. 위임예산(경상)' → 경상 BPROJM·BITEMM */
    DELEGATED_BUDGET,
    /** 정보기술부문계획 조정 '26년정보화사업(자본예산)' → BPLANM·BPLANA */
    PLAN_ADJUSTMENT;

    /**
     * 이 시트의 행으로 원장을 새로 만들 수 있는지 여부입니다.
     *
     * <p>부문계획 조정 시트는 계획 문서({@code BPLANM}·{@code BPLANA})만 만들고 {@code BPROJM}·{@code BCOSTM} 생성요청을
     * 내지 않습니다({@code PlanAdjustmentSheetAdapter}의 {@code costs}·{@code projects}는 항상 빈 목록). 그래서 이
     * 시트의 행에 {@code CREATE_NEW} 결정을 줘도 만들 원장이 없어 그 행의 조정이 통째로 사라집니다 — 결정 후보에서 아예 빼서 관리자가 고를 수 없게
     * 합니다.
     *
     * @return 원장 생성이 가능하면 true
     */
    public boolean canCreateLedger() {
        return this != PLAN_ADJUSTMENT;
    }
}
