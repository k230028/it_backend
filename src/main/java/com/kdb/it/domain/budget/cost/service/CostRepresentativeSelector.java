package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** 비용 이력 목록의 대표 행 선택 규칙을 공유하는 유틸리티. */
@Slf4j
public final class CostRepresentativeSelector {

    private CostRepresentativeSelector() {
    }

    /**
     * 동일 관리번호 이력 목록에서 대표 행을 결정적으로 선택합니다. (BE-09)
     *
     * <p>선택 규칙: ① {@code LST_YN='Y'} 행 우선 → ② {@code BG_SNO} 내림차순(최신 일련번호).
     * {@code LST_YN='Y'} 행이 2건 이상이면 데이터 정합성 이상이므로 WARN 로그를 남기고
     * tie-break 결과를 사용합니다(장애 없이 동작).</p>
     *
     * @param costs 동일 {@code IT_MNGC_NO}의 미삭제 이력 목록
     * @return 대표 행
     * @throws IllegalArgumentException 목록이 비어 있는 경우
     */
    public static Bcostm pick(List<Bcostm> costs) {
        Bcostm primary = costs.stream()
                .min(Comparator.comparing((Bcostm c) -> "Y".equals(c.getLstYn()) ? 0 : 1)
                        .thenComparing(Bcostm::getBgSno,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .orElseThrow(() -> new IllegalArgumentException("비용 이력 목록이 비어 있습니다."));
        long latestCount = costs.stream().filter(c -> "Y".equals(c.getLstYn())).count();
        if (latestCount > 1) {
            log.warn("전산관리비 LST_YN='Y' 행이 {}건입니다 (costBgNo={}, 선택 bgSno={})",
                    latestCount, primary.getCostBgNo(), primary.getBgSno());
        }
        return primary;
    }

    /**
     * 동일 관리번호의 경량 비용 이력에서 대표 행을 결정적으로 선택합니다.
     *
     * @param costs 동일 비용관리번호의 미삭제 프로젝션 목록
     * @return 최신 표시 행 우선, 이후 일련번호 내림차순으로 선택한 대표 행
     * @throws IllegalArgumentException 목록이 비어 있는 경우
     */
    public static CostRepository.CostRepresentativeView pickView(
            List<CostRepository.CostRepresentativeView> costs) {
        CostRepository.CostRepresentativeView primary = costs.stream()
                .min(Comparator.comparing((CostRepository.CostRepresentativeView c) ->
                                "Y".equals(c.getLstYn()) ? 0 : 1)
                        .thenComparing(CostRepository.CostRepresentativeView::getBgSno,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .orElseThrow(() -> new IllegalArgumentException("비용 이력 목록이 비어 있습니다."));
        long latestCount = costs.stream().filter(c -> "Y".equals(c.getLstYn())).count();
        if (latestCount > 1) {
            log.warn("전산관리비 LST_YN='Y' 행이 {}건입니다 (costBgNo={}, 선택 bgSno={})",
                    latestCount, primary.getCostBgNo(), primary.getBgSno());
        }
        return primary;
    }
}
