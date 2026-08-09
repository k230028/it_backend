package com.kdb.it.domain.budget.work.service;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BudgetReadView;
import java.util.Comparator;
import java.util.List;

/** 같은 그룹(비목/품목)의 편성(BBUGTM) 행에서 대표 행 선택 규칙을 공유하는 유틸리티. (BE-17 결정 #2) */
public final class BudgetRepresentativeSelector {

    private BudgetRepresentativeSelector() {}

    /**
     * 같은 그룹에 속한 편성 행에서 대표 행을 결정적으로 선택합니다.
     *
     * <p>선택 규칙: 최신 편성 실행(BG_NO 내림차순) 우선 → 동일 BG_NO는 SNO 내림차순. null BG_NO/SNO는 후순위.
     *
     * @param budgets 같은 그룹의 편성 행 목록
     * @return 대표 행
     * @throws IllegalArgumentException 목록이 비어 있는 경우
     */
    public static Bbugtm pick(List<Bbugtm> budgets) {
        return budgets.stream()
                .max(
                        Comparator.comparing(
                                        (Bbugtm budget) -> budget.getBgNo(),
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(
                                        budget -> budget.getSno(),
                                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new IllegalArgumentException("편성 행 목록이 비어 있습니다."));
    }

    /** 읽기 프로젝션 목록에 엔티티와 동일한 대표행 규칙을 적용합니다. */
    public static BudgetReadView pickView(List<BudgetReadView> budgets) {
        return budgets.stream()
                .max(
                        Comparator.comparing(
                                        BudgetReadView::getBgNo,
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(
                                        BudgetReadView::getSno,
                                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new IllegalArgumentException("편성 행 목록이 비어 있습니다."));
    }
}
