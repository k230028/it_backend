package com.kdb.it.domain.migration.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalBulkImportPlannerTest {

    private final TerminalBulkImportPlanner planner = new TerminalBulkImportPlanner();

    @Test
    void 전년도집행액이_있고_ID가_비어있으면_전년도_신규그룹을_만든다() {
        List<TerminalBulkImportDto.Row> rows =
                List.of(
                        row(
                                3,
                                null,
                                null,
                                new BigDecimal("100"),
                                "COST-2026-0001",
                                "유지",
                                new BigDecimal("200")),
                        row(4, null, null, null, "COST-2026-0001", "유지", new BigDecimal("300")));

        List<TerminalBulkImportPlanner.PlannedGroup> groups = planner.plan(rows);

        assertEquals(2, groups.size());
        assertEquals("2025", groups.get(0).bseYy());
        assertTrue(groups.get(0).createNew());
        assertEquals(new BigDecimal("100"), groups.get(0).totalKrwAmount());
        assertEquals("2026", groups.get(1).bseYy());
        assertFalse(groups.get(1).createNew());
        assertEquals("COST-2026-0001", groups.get(1).costId());
        assertEquals(2, groups.get(1).rows().size());
        assertEquals(new BigDecimal("500"), groups.get(1).totalKrwAmount());
    }

    @Test
    void 해지이고_당해연도_금액이_없으면_당해연도_반영에서_제외한다() {
        List<TerminalBulkImportPlanner.PlannedGroup> groups =
                planner.plan(
                        List.of(
                                row(
                                        3,
                                        null,
                                        "COST-2025-0001",
                                        new BigDecimal("100"),
                                        null,
                                        "해지",
                                        null)));

        assertEquals(1, groups.size());
        assertEquals("2025", groups.get(0).bseYy());
        assertFalse(groups.get(0).createNew());
        assertEquals("COST-2025-0001", groups.get(0).costId());
    }

    @Test
    void 신규ID는_같은연도_부서와단말기명기준으로_하나만만든다() {
        List<TerminalBulkImportDto.Row> rows =
                List.of(
                        rowWithDepartmentAndTerminal(
                                3, "부서", "블룸버그", new BigDecimal("100")),
                        rowWithDepartmentAndTerminal(
                                4, "부서", "블룸버그(***)", new BigDecimal("200")));

        List<TerminalBulkImportPlanner.PlannedGroup> groups = planner.plan(rows);

        assertEquals(2, groups.size());
        TerminalBulkImportPlanner.PlannedGroup previousYear = groups.get(0);
        assertEquals("2025", previousYear.bseYy());
        assertTrue(previousYear.createNew());
        assertEquals(2, previousYear.rows().size());
        assertEquals(new BigDecimal("300"), previousYear.totalKrwAmount());
    }

    @Test
    void 신규ID는_부서가다르면_각각만든다() {
        List<TerminalBulkImportDto.Row> rows =
                List.of(
                        rowWithDepartmentAndTerminal(
                                3, "부서A", "블룸버그", new BigDecimal("100")),
                        rowWithDepartmentAndTerminal(
                                4, "부서B", "블룸버그", new BigDecimal("200")));

        List<TerminalBulkImportPlanner.PlannedGroup> groups = planner.plan(rows);

        assertEquals(4, groups.size());
        assertEquals(1, groups.get(0).rows().size());
        assertEquals(1, groups.get(1).rows().size());
    }

    private static TerminalBulkImportDto.Row rowWithDepartmentAndTerminal(
            int excelRow, String department, String terminalName, BigDecimal previousAnnual) {
        TerminalBulkImportDto.Row base =
                row(excelRow, null, null, previousAnnual, null, "신규", new BigDecimal("300"));
        return new TerminalBulkImportDto.Row(
                base.excelRow(),
                base.costId2026(),
                base.costId2025(),
                department,
                base.team(),
                base.managerName(),
                terminalName,
                base.usageMethod(),
                base.purpose(),
                base.service(),
                base.currency(),
                base.previousForeignMonthly(),
                base.previousKrwMonthly(),
                base.previousAnnual(),
                base.currentKind(),
                base.increaseRate(),
                base.currentForeignMonthly(),
                base.currentKrwMonthly(),
                base.currentAnnual(),
                base.paymentCycle(),
                base.note());
    }

    private static TerminalBulkImportDto.Row row(
            int excelRow,
            String costId2026,
            String costId2025,
            BigDecimal previousAnnual,
            String currentCostId,
            String currentKind,
            BigDecimal currentAnnual) {
        return new TerminalBulkImportDto.Row(
                excelRow,
                currentCostId != null ? currentCostId : costId2026,
                costId2025,
                "부서",
                "팀",
                "관리자",
                "블룸버그",
                "별도단말",
                "리서치",
                "Bloomberg Service",
                "KRW",
                null,
                previousAnnual,
                previousAnnual,
                currentKind,
                null,
                null,
                currentAnnual,
                currentAnnual,
                "분기별",
                null);
    }
}
