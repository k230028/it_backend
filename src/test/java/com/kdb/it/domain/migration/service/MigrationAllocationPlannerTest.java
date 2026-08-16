package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.service.MigrationYearSnapshot.RequestItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationAllocationPlannerTest {

    private final MigrationAllocationPlanner planner = new MigrationAllocationPlanner();

    @Test
    @DisplayName("allocate_종합본금액이_요청합계와_같으면_조정비율이_그대로_실효율이다")
    void allocate_종합본금액이_요청합계와_같으면_조정비율이_그대로_실효율이다() {
        List<RequestItem> items =
                List.of(new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")));

        MigrationAllocationPlanner.Allocation result =
                planner.allocate(items, new BigDecimal("984200000"));

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("70.00000");
        assertThat(allocated.items().get(0).amount()).isEqualByComparingTo("984200000.000");
    }

    @Test
    @DisplayName("allocate_하반기_확정금액은_소수_실효율로_표현된다")
    void allocate_하반기_확정금액은_소수_실효율로_표현된다() {
        List<RequestItem> items =
                List.of(new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")));

        MigrationAllocationPlanner.Allocation result =
                planner.allocate(items, new BigDecimal("416000000"));

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("29.58748");
        // 잔차를 흡수해 합계가 목표액과 정확히 일치한다
        assertThat(allocated.items().get(0).amount()).isEqualByComparingTo("416000000.000");
    }

    @Test
    @DisplayName("allocate_여러_품목이면_금액이_비례_배분되고_합계가_목표액과_같다")
    void allocate_여러_품목이면_금액이_비례_배분되고_합계가_목표액과_같다() {
        List<RequestItem> items =
                List.of(
                        new RequestItem("GCL-1", 1, "103", new BigDecimal("1000")),
                        new RequestItem("GCL-2", 2, "104", new BigDecimal("2000")));

        MigrationAllocationPlanner.Allocation result = planner.allocate(items, new BigDecimal("777"));

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        BigDecimal sum =
                allocated.items().stream()
                        .map(MigrationAllocationPlanner.ItemAllocation::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("777");
        // 이 입력은 나누어떨어져(25.9%) 잔차가 0이다 — 잔차 흡수 분기 자체는 검증하지 않는다
        assertThat(allocated.items().get(1).amount()).isEqualByComparingTo("518.000");
        assertThat(allocated.items().get(0).amount()).isEqualByComparingTo("259.000");
    }

    @Test
    @DisplayName("allocate_양수_잔차는_요청금액이_가장_큰_품목이_흡수하고_공통_실효율은_유지된다")
    void allocate_양수_잔차는_요청금액이_가장_큰_품목이_흡수하고_공통_실효율은_유지된다() {
        List<RequestItem> items =
                List.of(
                        new RequestItem("GCL-1", 1, "103", new BigDecimal("1000000")),
                        new RequestItem("GCL-2", 2, "104", new BigDecimal("2000000")));

        MigrationAllocationPlanner.Allocation result =
                planner.allocate(items, new BigDecimal("1000000"));

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        // rate = 100,000,000 / 3,000,000 = 33.33333... → HALF_UP 스케일5 = 33.33333
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("33.33333");
        // 반올림 전 배분 합계는 999,999.900으로 목표액에 +0.100 모자란다
        MigrationAllocationPlanner.ItemAllocation a = allocated.items().get(0);
        MigrationAllocationPlanner.ItemAllocation b = allocated.items().get(1);
        // 잔차는 요청금액이 더 큰 B(2,000,000)가 흡수한다
        assertThat(a.amount()).isEqualByComparingTo("333333.300");
        assertThat(b.amount()).isEqualByComparingTo("666666.700");
        BigDecimal sum = a.amount().add(b.amount());
        assertThat(sum).isEqualByComparingTo("1000000.000");
        // 잔차를 흡수한 품목도 rate 필드는 그룹 공통 실효율 그대로다(금액만 조정됨)
        assertThat(a.rate()).isEqualByComparingTo("33.33333");
        assertThat(b.rate()).isEqualByComparingTo("33.33333");
    }

    @Test
    @DisplayName("allocate_음수_잔차는_요청금액이_가장_큰_품목의_금액을_줄여_흡수한다")
    void allocate_음수_잔차는_요청금액이_가장_큰_품목의_금액을_줄여_흡수한다() {
        List<RequestItem> items =
                List.of(
                        new RequestItem("GCL-1", 1, "103", new BigDecimal("1000000")),
                        new RequestItem("GCL-2", 2, "104", new BigDecimal("2000000")));

        MigrationAllocationPlanner.Allocation result =
                planner.allocate(items, new BigDecimal("2000000"));

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        // rate = 200,000,000 / 3,000,000 = 66.66666... → HALF_UP 스케일5 = 66.66667 (올림)
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("66.66667");
        MigrationAllocationPlanner.ItemAllocation a = allocated.items().get(0);
        MigrationAllocationPlanner.ItemAllocation b = allocated.items().get(1);
        assertThat(a.amount()).isEqualByComparingTo("666666.700");
        // 반올림 전 배분 합계는 2,000,000.100으로 목표액을 +0.100 초과한다.
        // 잔차는 요청금액이 더 큰 B가 흡수하며, 이번엔 흡수된 금액이 줄어든다(1,333,333.400 → 1,333,333.300)
        assertThat(b.amount()).isEqualByComparingTo("1333333.300");
        BigDecimal sum = a.amount().add(b.amount());
        assertThat(sum).isEqualByComparingTo("2000000.000");
    }

    @Test
    @DisplayName("allocate_목표액이_0이면_편성률과_금액이_모두_0이다")
    void allocate_목표액이_0이면_편성률과_금액이_모두_0이다() {
        List<RequestItem> items =
                List.of(new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")));

        MigrationAllocationPlanner.Allocation result = planner.allocate(items, BigDecimal.ZERO);

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("0");
        assertThat(allocated.items().get(0).amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("allocate_요청합계가_0인데_목표액이_있으면_배분할_수_없다")
    void allocate_요청합계가_0인데_목표액이_있으면_배분할_수_없다() {
        List<RequestItem> items = List.of(new RequestItem("GCL-1", 1, "106", BigDecimal.ZERO));

        MigrationAllocationPlanner.Allocation result =
                planner.allocate(items, new BigDecimal("416000000"));

        assertThat(result).isInstanceOf(MigrationAllocationPlanner.Allocation.BaseZero.class);
    }

    @Test
    @DisplayName("allocate_품목이_없고_목표액도_0이면_빈_배분이다")
    void allocate_품목이_없고_목표액도_0이면_빈_배분이다() {
        MigrationAllocationPlanner.Allocation result =
                planner.allocate(List.of(), BigDecimal.ZERO);

        MigrationAllocationPlanner.Allocation.Allocated allocated =
                (MigrationAllocationPlanner.Allocation.Allocated) result;
        assertThat(allocated.items()).isEmpty();
        assertThat(allocated.effectiveRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("groupOf_금액컬럼마다_비목그룹이_다르다")
    void groupOf_금액컬럼마다_비목그룹이_다르다() {
        assertThat(MigrationAllocationPlanner.groupOf("devAmount")).containsExactly("103", "104");
        assertThat(MigrationAllocationPlanner.groupOf("hwAmount")).containsExactly("101", "102");
        assertThat(MigrationAllocationPlanner.groupOf("swAmount"))
                .containsExactly("105", "106", "107");
        assertThat(MigrationAllocationPlanner.groupOf("generalAmount")).isEmpty();
    }

    @Test
    @DisplayName("itemsOutsideCapitalGroups_자본계열이_아닌_품목만_남는다")
    void itemsOutsideCapitalGroups_자본계열이_아닌_품목만_남는다() {
        List<RequestItem> items =
                List.of(
                        new RequestItem("GCL-1", 1, "103", new BigDecimal("100")),
                        new RequestItem("GCL-2", 2, "001", new BigDecimal("200")),
                        new RequestItem("GCL-3", 3, "013", new BigDecimal("300")));

        assertThat(MigrationAllocationPlanner.itemsOutsideCapitalGroups(items))
                .extracting(RequestItem::gclMngNo)
                .containsExactly("GCL-2", "GCL-3");
    }

    @Test
    @DisplayName("itemsInGroup_그룹에_드는_품목만_남는다")
    void itemsInGroup_그룹에_드는_품목만_남는다() {
        List<RequestItem> items =
                List.of(
                        new RequestItem("GCL-1", 1, "103", new BigDecimal("100")),
                        new RequestItem("GCL-2", 2, "104", new BigDecimal("200")),
                        new RequestItem("GCL-3", 3, "101", new BigDecimal("300")));

        assertThat(
                        MigrationAllocationPlanner.itemsInGroup(
                                items, MigrationAllocationPlanner.GROUP_DEV))
                .extracting(RequestItem::gclMngNo)
                .containsExactly("GCL-1", "GCL-2");
    }
}
