package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** 같은 품목(GCL_MNG_NO)의 BITEMM 버전 행에서 대표 행 선택 규칙을 공유하는 유틸리티. (BE-17 결정 #1) */
@Slf4j
public final class ItemRepresentativeSelector {

    private ItemRepresentativeSelector() {}

    /**
     * 같은 품목관리번호의 버전 행 목록에서 대표 행을 결정적으로 선택합니다.
     *
     * <p>선택 규칙: ① {@code LST_YN='Y'} 행 우선 → ② {@code SNO} 내림차순 폴백. {@code LST_YN='Y'} 행이 2건
     * 이상이면 데이터 정합성 이상이므로 WARN 로그를 남기고 tie-break 결과를 사용합니다(장애 없이 동작).
     *
     * @param items 같은 {@code gclMngNo}의 미삭제 버전 행 목록
     * @return 대표 행
     * @throws IllegalArgumentException 목록이 비어 있는 경우
     */
    public static Bitemm pick(List<Bitemm> items) {
        Bitemm primary =
                items.stream()
                        .min(
                                Comparator.comparing(
                                                (Bitemm item) ->
                                                        "Y".equals(item.getLstYn()) ? 0 : 1)
                                        .thenComparing(
                                                item -> item.getSno(),
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .orElseThrow(() -> new IllegalArgumentException("품목 버전 목록이 비어 있습니다."));
        long latestCount = items.stream().filter(item -> "Y".equals(item.getLstYn())).count();
        if (latestCount > 1) {
            log.warn(
                    "BITEMM LST_YN='Y' 행이 {}건입니다 (gclMngNo={}, 선택 sno={})",
                    latestCount,
                    primary.getGclMngNo(),
                    primary.getSno());
        }
        return primary;
    }
}
