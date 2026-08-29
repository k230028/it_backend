package com.kdb.it.domain.migration.terminal;

import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 금융정보단말기 행을 연도·전산업무비 단위 반영 그룹으로 묶습니다. */
@Component
public final class TerminalBulkImportPlanner {

    /** 저장 대상 전산업무비 한 건에 해당하는 계획입니다. */
    public record PlannedGroup(
            String bseYy,
            String costId,
            boolean createNew,
            List<TerminalBulkImportDto.Row> rows,
            BigDecimal totalKrwAmount) {}

    /** 2025년 집행 데이터와 2026년 집행 예상 데이터를 각각 계획합니다. */
    public List<PlannedGroup> plan(List<TerminalBulkImportDto.Row> rows) {
        Map<String, MutableGroup> grouped = new LinkedHashMap<>();
        for (TerminalBulkImportDto.Row row : rows) {
            if (row.hasPreviousData()) {
                add(grouped, "2025", row.costId2025(), row, row.previousAnnualAmount());
            }
            if (row.hasCurrentData()) {
                add(grouped, "2026", row.costId2026(), row, row.currentAnnualAmount());
            }
        }
        return grouped.values().stream().map(MutableGroup::toPlan).toList();
    }

    private static void add(
            Map<String, MutableGroup> grouped,
            String year,
            String rawCostId,
            TerminalBulkImportDto.Row row,
            BigDecimal amount) {
        String costId = normalize(rawCostId);
        boolean createNew = costId == null;
        String groupKey =
                year
                        + ":"
                        + (createNew
                                ? "NEW:" + groupingKey(row.department(), row.terminalName())
                                : costId);
        MutableGroup group =
                grouped.computeIfAbsent(groupKey, key -> new MutableGroup(year, costId, createNew));
        group.rows.add(row);
        group.totalKrwAmount = group.totalKrwAmount.add(amount);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 신규 전산업무비는 같은 부서·단말기명 행을 하나의 원장으로 묶습니다. */
    private static String groupingKey(String department, String terminalName) {
        return normalizeGroupingText(department) + ":" + normalizeTerminalName(terminalName);
    }

    private static String normalizeGroupingText(String value) {
        String normalized = normalize(value);
        return normalized == null
                ? ""
                : normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 블룸버그 옵션 표기(블룸버그(***) 등)는 기본 단말기명과 동일하게 취급합니다. */
    private static String normalizeTerminalName(String value) {
        String normalized = normalizeGroupingText(value);
        if (normalized.matches("블룸버그\\(.*\\)")) return "블룸버그";
        return normalized;
    }

    private static final class MutableGroup {
        private final String year;
        private final String costId;
        private final boolean createNew;
        private final List<TerminalBulkImportDto.Row> rows = new ArrayList<>();
        private BigDecimal totalKrwAmount = BigDecimal.ZERO;

        private MutableGroup(String year, String costId, boolean createNew) {
            this.year = year;
            this.costId = costId;
            this.createNew = createNew;
        }

        private PlannedGroup toPlan() {
            return new PlannedGroup(year, costId, createNew, List.copyOf(rows), totalKrwAmount);
        }
    }
}
