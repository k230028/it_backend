package com.kdb.it.domain.migration.terminal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

/** 금융정보단말기 일괄업로드 API 계약입니다. */
public final class TerminalBulkImportDto {

    private TerminalBulkImportDto() {}

    /** 한 연도의 월·연간 집행금액 묶음입니다. */
    public record YearValues(BigDecimal foreignMonthly, BigDecimal krwMonthly, BigDecimal annual) {
        public BigDecimal annualAmount() {
            if (annual != null) return annual;
            return krwMonthly == null
                    ? BigDecimal.ZERO
                    : krwMonthly.multiply(BigDecimal.valueOf(12));
        }
    }

    /** 프론트에서 2단 헤더를 해석한 단말기 한 행입니다. */
    public record Row(
            int excelRow,
            String previousCostId,
            String currentCostId,
            String department,
            String team,
            String managerName,
            String terminalName,
            String usageMethod,
            String purpose,
            String service,
            String currency,
            BigDecimal previousForeignMonthly,
            BigDecimal previousKrwMonthly,
            BigDecimal previousAnnual,
            String currentKind,
            BigDecimal increaseRate,
            BigDecimal currentForeignMonthly,
            BigDecimal currentKrwMonthly,
            BigDecimal currentAnnual,
            String paymentCycle,
            String note) {

        public boolean hasPreviousData() {
            return previousForeignMonthly != null
                    || previousKrwMonthly != null
                    || previousAnnual != null;
        }

        public boolean hasCurrentData() {
            return !"해지".equals(currentKind)
                    && (currentForeignMonthly != null
                            || currentKrwMonthly != null
                            || currentAnnual != null);
        }

        public BigDecimal previousAnnualAmount() {
            return previousValues().annualAmount();
        }

        public BigDecimal currentAnnualAmount() {
            return currentValues().annualAmount();
        }

        public YearValues previousValues() {
            return new YearValues(previousForeignMonthly, previousKrwMonthly, previousAnnual);
        }

        public YearValues currentValues() {
            return new YearValues(currentForeignMonthly, currentKrwMonthly, currentAnnual);
        }
    }

    /** 일괄업로드 요청입니다. 이전 연도는 기준연도에서 1을 뺀 값으로 계산합니다. */
    public record Request(@Min(2000) @Max(2100) int baseYear, @NotEmpty List<@Valid Row> rows) {}

    /** 미리보기·확정 반영 결과의 요약입니다. */
    public record Response(
            int rowCount,
            int terminalCount,
            int createCount,
            int updateCount,
            List<Group> groups) {}

    /** 연도·관리번호별 반영 예정 그룹입니다. */
    public record Group(
            String bseYy,
            String costId,
            boolean createNew,
            int terminalCount,
            BigDecimal totalKrwAmount,
            List<Integer> excelRows) {}
}
