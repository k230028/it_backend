package com.kdb.it.domain.migration.dto;

import java.util.List;
import java.util.Objects;

/**
 * 시트별 정규 컬럼 id 목록입니다.
 *
 * <p>프론트 파서가 엑셀 헤더를 이 id로 정규화해 보내고 어댑터가 같은 id로 읽습니다. 엑셀 헤더 문자열은 파일마다 미묘하게 다르므로(병합 헤더, 공백, 줄바꿈) 전송
 * 계약에는 헤더 원문을 쓰지 않습니다. {@code app/composables/migration/columns.ts}가 같은 리터럴을 갖고 있으며 두 곳을 함께 바꿉니다.
 */
public final class MigrationColumns {

    private MigrationColumns() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /** 전산일반관리비 '전체취합(국내외)'. 25년 3열·증감액·증감률·세목코드는 미적재라 제외합니다. */
    private static final List<String> COST =
            List.of(
                    "abusCode",
                    "ioeName",
                    "abusTcLabel",
                    "vendorName",
                    "requestDetail",
                    "securityFlag",
                    "terminalFlag",
                    "deptName",
                    "teamName",
                    "currency",
                    "fcAmount",
                    "krwAmount",
                    "remark");

    /**
     * 자본예산 '1-1. 26년정보화사업(전산예산반영)'. 편성요청 3열·일반관리비와 조정비율·조정 3열까지 받습니다.
     *
     * <p>`일반관리비`는 편성요청 3열 다음, `총 사업예산` 앞에 있는 실제 엑셀 열입니다. 자본 세 그룹 어디에도 들지 않는 품목의 기준액이라 받지 않으면 그 품목들이
     * 조정비율을 못 받습니다 (설계 §3.4).
     */
    private static final List<String> CAPITAL_PROJECT =
            List.of(
                    "projectName",
                    "projectType",
                    "progressLabel",
                    "projectOutline",
                    "headquarters",
                    "deptName",
                    "teamName",
                    "managerName",
                    "teamLeaderName",
                    "itTeamName",
                    "feasibility",
                    "startYm",
                    "endYm",
                    "devAmount",
                    "hwAmount",
                    "swAmount",
                    "generalAmount",
                    "adjustRate",
                    "devAdjustAmount",
                    "hwAdjustAmount",
                    "swAdjustAmount",
                    "delegationLabel");

    /** 위임예산 '2. 위임예산(경상)'. HW·SW 수량·외화·원화를 각각 받습니다. */
    private static final List<String> DELEGATED_BUDGET =
            List.of(
                    "branchName",
                    "itemName",
                    "currency",
                    "hwQty",
                    "hwFcAmount",
                    "hwKrwAmount",
                    "swQty",
                    "swFcAmount",
                    "swKrwAmount");

    /** 부문계획 '26년정보화사업(자본예산)'. 집행 실적 4열은 스냅샷 보존용으로 받습니다. */
    private static final List<String> PLAN_ADJUSTMENT =
            List.of(
                    "projectName",
                    "projectType",
                    "headquarters",
                    "deptName",
                    "teamName",
                    "managerName",
                    "teamLeaderName",
                    "budgetChangeLabel",
                    "startYm",
                    "endYm",
                    "devAmount",
                    "hwAmount",
                    "swAmount",
                    "generalAmount",
                    "totalAmount",
                    "spentBefore",
                    "spent26",
                    "planned26",
                    "paymentSchedule",
                    "plannedAfter27",
                    "progressLabel",
                    "remark");

    /**
     * 시트 종류에 해당하는 정규 컬럼 id 목록을 반환합니다.
     *
     * @param kind 시트 종류 (null 아님)
     * @return 불변 컬럼 id 목록 (엑셀 열 순서)
     * @throws NullPointerException kind가 null인 경우
     */
    public static List<String> of(SheetKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case COST -> COST;
            case CAPITAL_PROJECT -> CAPITAL_PROJECT;
            case DELEGATED_BUDGET -> DELEGATED_BUDGET;
            case PLAN_ADJUSTMENT -> PLAN_ADJUSTMENT;
        };
    }
}
