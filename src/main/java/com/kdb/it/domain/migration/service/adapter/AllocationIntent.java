package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 편성 배분 의도입니다. 어댑터가 "어느 원장에 얼마를 편성할지"만 말하고, 실제 매칭과 배분은 {@code MigrationLedgerMatcher}·{@code
 * MigrationAllocationPlanner}가 맡습니다.
 *
 * <p>종전 {@code RateIntent}는 사업 하나에 편성률 하나였습니다. 종합본은 개발비·기계장치·기타무형 세 그룹에 각각 다른 금액을 주고, 하반기 조정은 비율이
 * 아니라 확정 금액을 주므로 편성률 하나로는 담기지 않습니다.
 *
 * @param sheet 시트 종류 (진단 좌표)
 * @param excelRow 엑셀 행 번호 (진단 좌표)
 * @param orcTb 대상 원본 테이블 — 접두어 없는 {@code BPROJM} 또는 {@code BCOSTM}
 * @param matchKey 기존 원장을 찾을 키
 * @param targetByColumn 정규 금액 컬럼 id → 목표 편성액(원 단위). 정보화사업은 {@code devAmount}·{@code
 *     hwAmount}·{@code swAmount}·{@code generalAmount}, 전산업무비·위임예산은 {@code costAmount}
 * @param declaredBase 종합본이 말한 요청 기준액 합계(원 단위). 요청 원장과 대사해 {@code AMOUNT_ADJUSTED}를 판정합니다. 대사할 값이
 *     없으면 null (하반기 조정은 확정금액만 있고 기준액이 없습니다)
 */
public record AllocationIntent(
        SheetKind sheet,
        int excelRow,
        String orcTb,
        MatchKey matchKey,
        Map<String, BigDecimal> targetByColumn,
        BigDecimal declaredBase) {

    /**
     * 기존 원장을 찾는 키입니다. 시트마다 찾는 방식이 다릅니다(설계 §4.1).
     *
     * @param type 매칭 방식
     * @param normalizedName 정규화 사업명. {@code PROJECT_NAME}에서만 씁니다
     * @param deptCode 부서코드. {@code ORDINARY_DEPT}·{@code COST_DEPT_KEY}에서 씁니다
     * @param ioeC 비목코드. {@code COST_DEPT_KEY}에서만 씁니다
     * @param vendorName 계약상대처명. {@code COST_DEPT_KEY}에서만 씁니다
     * @param contractName 계약명. {@code COST_DEPT_KEY}에서만 씁니다
     */
    public record MatchKey(
            Type type,
            String normalizedName,
            String deptCode,
            String ioeC,
            String vendorName,
            String contractName) {

        /** 매칭 방식입니다. */
        public enum Type {
            /** 정규화 사업명으로 정보화사업을 찾습니다. */
            PROJECT_NAME,
            /** 부서코드로 경상사업({@code ODN_YN='Y'})을 찾습니다. */
            ORDINARY_DEPT,
            /** 부서 기준 자연키로 전산업무비를 찾습니다. */
            COST_DEPT_KEY
        }

        /** 정규화 사업명 매칭 키입니다. */
        public static MatchKey ofProjectName(String normalizedName) {
            return new MatchKey(Type.PROJECT_NAME, normalizedName, null, null, null, null);
        }

        /** 부서코드 기준 경상사업 매칭 키입니다. */
        public static MatchKey ofOrdinaryDept(String deptCode) {
            return new MatchKey(Type.ORDINARY_DEPT, null, deptCode, null, null, null);
        }

        /** 부서 기준 전산업무비 매칭 키입니다. */
        public static MatchKey ofCost(
                String deptCode, String ioeC, String vendorName, String contractName) {
            return new MatchKey(Type.COST_DEPT_KEY, null, deptCode, ioeC, vendorName, contractName);
        }
    }
}
