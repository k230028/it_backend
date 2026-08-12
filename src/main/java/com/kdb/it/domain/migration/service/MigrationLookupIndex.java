package com.kdb.it.domain.migration.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 한 요청 처리 동안 재사용하는 조회 인덱스 묶음입니다.
 *
 * @param org 조직·사용자 이름 역방향 인덱스
 * @param ioeCodeByName 비목 코드값명 → 코드값 (예: "국내전산임차료" → "001")
 * @param xcrByCurrency 통화 → 예산환율 (Ccodem C_TP='XCR')
 * @param abusUnitNameByCode 사업코드 → 코드값명 (예: "571" → "운영시스템 유지보수")
 * @param exePttCodeByName 추진가능성 코드값명 → 코드값 (예: "확정" → "1")
 * @param edrtCodeByName 전결권(자본 계열) 코드값명 → 코드값 (예: "부문장" → "22")
 */
public record MigrationLookupIndex(
        OrgIdentityResolver.Index org,
        Map<String, String> ioeCodeByName,
        Map<String, BigDecimal> xcrByCurrency,
        Map<String, String> abusUnitNameByCode,
        Map<String, String> exePttCodeByName,
        Map<String, String> edrtCodeByName) {

    /**
     * 코드 카탈로그 없이 조직·비목·환율만 담은 인덱스를 만듭니다. 코드 검증을 다루지 않는 단위 테스트가 씁니다.
     *
     * @param org 조직·사용자 인덱스
     * @param ioeCodeByName 비목 코드값명 → 코드값
     * @param xcrByCurrency 통화 → 예산환율
     */
    public MigrationLookupIndex(
            OrgIdentityResolver.Index org,
            Map<String, String> ioeCodeByName,
            Map<String, BigDecimal> xcrByCurrency) {
        this(org, ioeCodeByName, xcrByCurrency, Map.of(), Map.of(), Map.of());
    }
}
