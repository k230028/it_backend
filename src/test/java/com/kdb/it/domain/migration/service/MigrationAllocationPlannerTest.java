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
        // 잔차는 요청금액이 가장 큰 품목이 흡수한다
        assertThat(allocated.items().get(1).amount()).isEqualByComparingTo("518.000");
        assertThat(allocated.items().get(0).amount()).isEqualByComparingTo("259.000");
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
