package com.kdb.it.domain.migration.service;

/**
 * 수기 엑셀 이관 진단 코드의 단일 출처 — BE-38.
 *
 * <p>{@link MigrationDiagnostics#blocker}·{@link MigrationDiagnostics#warning}가 이 타입만 받으므로 목록에 없는
 * 코드는 애초에 발행할 수 없습니다. 종전에는 각 호출부의 문자열 리터럴이라 {@code MigrationDto.CellDiagnostic.code}의 {@code
 * allowableValues}와 대조할 장치가 없었고, 실제로 {@code CREATE_NOT_SUPPORTED}가 발행되면서도 목록에서 빠져 프론트 생성 타입({@code
 * api.d.ts})의 union에 누락된 적이 있습니다.
 *
 * <p>코드를 추가하면 {@code MigrationDto.CellDiagnostic.code}의 {@code allowableValues}에도 같은 값을 넣어야 합니다. 두
 * 목록의 일치는 {@code MigrationDiagnosticCodeContractTest}가 고정합니다.
 */
enum MigrationDiagnosticCode {

    /** 부점·조직명을 원장에서 찾지 못했습니다. */
    ORG_UNRESOLVED,

    /** 같은 이름의 부점·조직이 둘 이상입니다. */
    ORG_AMBIGUOUS,

    /** 담당자를 사번·성명으로 찾지 못했습니다. */
    USER_UNRESOLVED,

    /** 같은 이름의 사용자가 둘 이상입니다. */
    USER_AMBIGUOUS,

    /** 공통코드 카탈로그에서 대응 코드를 찾지 못했습니다. */
    CODE_UNRESOLVED,

    /** 필수 값이 비어 있습니다. */
    REQUIRED_MISSING,

    /** 같은 키의 행이 이미 있습니다. */
    DUPLICATE_EXISTS,

    /** 값이 대상 컬럼 길이를 초과합니다. */
    LENGTH_EXCEEDED,

    /** 자본예산 시트에도 포탈에도 없는 사업을 참조했습니다. */
    PROJECT_NOT_FOUND,

    /** 서버 재계산액이 시트에 적힌 금액과 다릅니다. */
    AMOUNT_MISMATCH,

    /** 조정비율이 허용 범위를 벗어났습니다. */
    RATE_OUT_OF_RANGE,

    /** 값을 날짜(연월)로 읽지 못했습니다. */
    DATE_UNPARSEABLE,

    /** 이 행에 해당하는 원장을 찾지 못했습니다. */
    LEDGER_NOT_MATCHED,

    /** 이 행에 해당하는 원장 후보가 둘 이상입니다. */
    LEDGER_AMBIGUOUS,

    /** 대상 원장의 요청금액이 0원이라 편성액을 배분할 수 없습니다. */
    ITEM_BASE_ZERO,

    /** 종합본 금액이 부서 제출 요청 금액과 달라 종합본 기준으로 편성합니다. */
    AMOUNT_ADJUSTED,

    /** 엑셀의 조정 금액이 `기준액 × 조정비율`과 다릅니다. */
    RATE_RECONCILE_MISMATCH,

    /** 원장을 새로 만들 수 없는 시트에 '새로 만들고 편성' 결정이 왔습니다. */
    CREATE_NOT_SUPPORTED,

    /** 원장을 새로 만드는 행이라 일반관리비 목표액을 담을 품목이 없습니다. */
    GENERAL_AMOUNT_NOT_CREATABLE,

    /** 일반관리비 열이 비어 있고 기존 편성률도 없어 그 품목이 100%로 편성됩니다. */
    GENERAL_RATE_DEFAULTED
}
