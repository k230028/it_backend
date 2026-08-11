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
    PLAN_ADJUSTMENT
}
