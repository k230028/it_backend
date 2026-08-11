package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.migration.service.MigrationLookupIndex;
import com.kdb.it.domain.migration.service.MigrationYearSnapshot;
import java.util.Map;

/**
 * 어댑터 변환에 필요한 요청 범위 컨텍스트입니다.
 *
 * @param bseYy 예산연도 (4자리)
 * @param index 조직·비목·환율 조회 인덱스
 * @param snapshot 예산연도 기존 상태
 * @param overrides 미리보기 보정값 ({@code MigrationValidator.overrideKey} 키)
 * @param actorEno 업로드 사용자 사번. 엑셀에 담당자가 없는 시트의 기본 담당자로 씁니다
 */
public record AdapterContext(
        String bseYy,
        MigrationLookupIndex index,
        MigrationYearSnapshot.Data snapshot,
        Map<String, String> overrides,
        String actorEno) {}
