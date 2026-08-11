package com.kdb.it.domain.migration.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 한 요청 처리 동안 재사용하는 조회 인덱스 묶음입니다.
 *
 * @param org 조직·사용자 이름 역방향 인덱스
 * @param ioeCodeByName 비목 코드값명 → 코드값 (예: "국내전산임차료" → "001")
 * @param xcrByCurrency 통화 → 예산환율 (Ccodem C_TP='XCR')
 */
public record MigrationLookupIndex(
        OrgIdentityResolver.Index org,
        Map<String, String> ioeCodeByName,
        Map<String, BigDecimal> xcrByCurrency) {}
