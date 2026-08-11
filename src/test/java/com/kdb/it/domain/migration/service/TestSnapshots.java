package com.kdb.it.domain.migration.service;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이관 단위 테스트가 공유하는 인덱스·스냅샷 조립 헬퍼입니다.
 *
 * <p>검증기(Task 5)·시트 어댑터(Task 6~9)·오케스트레이션(Task 11) 테스트가 함께 씁니다. 어댑터 테스트는 {@code ...service.adapter}
 * 패키지에 있어 패키지-프라이빗으로 두면 보이지 않으므로 {@code public}으로 둡니다.
 */
public final class TestSnapshots {

    private TestSnapshots() {}

    /** 아무것도 해석되지 않는 빈 인덱스. */
    public static MigrationLookupIndex emptyIndex() {
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(List.of(), List.of()), Map.of(), Map.of());
    }

    /** 조직만 담긴 인덱스. 인자는 (코드, 이름) 쌍의 반복입니다. */
    public static MigrationLookupIndex indexWithOrgs(String... codeNamePairs) {
        List<CorgnI> orgs = new ArrayList<>();
        for (int i = 0; i < codeNamePairs.length; i += 2) {
            orgs.add(
                    CorgnI.builder()
                            .prlmOgzCCone(codeNamePairs[i])
                            .bbrNm(codeNamePairs[i + 1])
                            .build());
        }
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(orgs, List.of()), Map.of(), Map.of());
    }

    /** 비목 코드값명 → 코드값 맵만 담긴 인덱스. */
    public static MigrationLookupIndex indexWithIoe(String code, String name) {
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(List.of(), List.of()), Map.of(name, code), Map.of());
    }

    /** 통화별 환율만 담긴 인덱스. */
    public static MigrationLookupIndex indexWithXcr(String curC, String xcr) {
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(List.of(), List.of()),
                Map.of(),
                Map.of(curC, new BigDecimal(xcr)));
    }

    /** 사용자만 담긴 인덱스. USER_UNRESOLVED·USER_AMBIGUOUS 진단 픽스처용입니다. */
    public static MigrationLookupIndex indexWithUsers(List<CuserI> users) {
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(List.of(), users), Map.of(), Map.of());
    }

    /** 조직과 사용자를 함께 담은 인덱스. 부서 힌트로 동명이인을 좁히는 시나리오 픽스처용입니다. */
    public static MigrationLookupIndex indexWithOrgsAndUsers(
            List<CorgnI> orgs, List<CuserI> users) {
        return new MigrationLookupIndex(
                OrgIdentityResolver.Index.of(orgs, users), Map.of(), Map.of());
    }

    /** 기존 데이터가 없는 연도 스냅샷. */
    public static MigrationYearSnapshot.Data empty(String bseYy) {
        return new MigrationYearSnapshot.Data(
                bseYy, Set.of(), new LinkedHashMap<>(), Set.of(), new LinkedHashMap<>(), List.of());
    }

    /** 전산업무비 자연키 하나가 이미 있는 연도 스냅샷. */
    public static MigrationYearSnapshot.Data snapshotWithCostKey(String bseYy, String naturalKey) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(naturalKey);
        return new MigrationYearSnapshot.Data(
                bseYy, keys, new LinkedHashMap<>(), Set.of(), new LinkedHashMap<>(), List.of());
    }

    /** 특정 계획구분이 이미 존재하는 연도 스냅샷. PLAN_ADJUSTMENT의 DUPLICATE_EXISTS 픽스처용입니다. */
    public static MigrationYearSnapshot.Data snapshotWithPlanType(String bseYy, String planType) {
        Set<String> types = new LinkedHashSet<>();
        types.add(planType);
        return new MigrationYearSnapshot.Data(
                bseYy, Set.of(), new LinkedHashMap<>(), types, new LinkedHashMap<>(), List.of());
    }

    /** 정규화 사업명 하나가 이미 있는 연도 스냅샷. CAPITAL_PROJECT의 DUPLICATE_EXISTS 픽스처용입니다. */
    public static MigrationYearSnapshot.Data snapshotWithProjectName(
            String bseYy, String normalizedName, String projectNo) {
        Map<String, String> byName = new LinkedHashMap<>();
        byName.put(normalizedName, projectNo);
        return new MigrationYearSnapshot.Data(
                bseYy, Set.of(), byName, Set.of(), new LinkedHashMap<>(), List.of());
    }
}
