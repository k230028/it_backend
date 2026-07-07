package com.kdb.it.domain.budget.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BITEMM 조회 집계가 저장된 원화금액(amt)을 다시 환산하지 않는지 검증합니다.
 */
class BudgetItemReaderCurrencyRuleTest {

    private static final Path BACKEND_ROOT = Path.of("").toAbsolutePath();

    @Test
    @DisplayName("ItBudget 조회 집계는 BITEMM amt에 xcr을 다시 곱하지 않는다")
    void itBudgetReader_sumsPersistedAmtDirectly() throws IOException {
        String source = read("src/main/java/com/kdb/it/domain/budget/it/repository/ItBudgetQueryRepositoryImpl.java");

        assertThat(source).contains(".select(i.ioeC, i.sectSysUtzYn, i.amt.sum())");
        assertThat(source).doesNotContain("COALESCE({0}, 1.0) * {1}");
    }

    @Test
    @DisplayName("BudgetStatus 조회 집계는 BITEMM amt에 xcr을 다시 곱하지 않는다")
    void budgetStatusReader_sumsPersistedAmtDirectly() throws IOException {
        String source = read("src/main/java/com/kdb/it/domain/budget/status/repository/BudgetStatusQueryRepositoryImpl.java");

        assertThat(source).contains("\"COALESCE(SUM({0}), 0)\", i.amt");
        assertThat(source).contains("\"COALESCE(SUM(CASE WHEN {0} = {1} THEN {2} ELSE 0 END), 0)\"");
        assertThat(source).doesNotContain("SUM({0} * COALESCE({1}, 1))");
        assertThat(source).doesNotContain("THEN {2} * COALESCE({3}, 1)");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(BACKEND_ROOT.resolve(relativePath));
    }
}
