package com.kdb.it.common.system.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.system.repository.LoginHistoryRepository.DailyLoginStatRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link DailyLoginStatRow#fromRow(Object[])} 팩토리 단위 테스트(정상·null 폴백·컬럼 수 가드). */
@DisplayName("DailyLoginStatRow.fromRow")
class DailyLoginStatRowTest {

    @Test
    @DisplayName("[일자, 접속 횟수, 접속자 수] 3컬럼을 정상 매핑한다")
    void fromRow_threeColumns_mapsAll() {
        DailyLoginStatRow row =
                DailyLoginStatRow.fromRow(
                        new Object[] {"2026-09-07", new BigDecimal("12"), new BigDecimal("5")});

        assertThat(row.label()).isEqualTo("2026-09-07");
        assertThat(row.count()).isEqualTo(12L);
        assertThat(row.uniqueUserCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("건수 컬럼이 null이면 0으로 폴백한다")
    void fromRow_nullCounts_fallBackToZero() {
        DailyLoginStatRow row = DailyLoginStatRow.fromRow(new Object[] {"2026-09-07", null, null});

        assertThat(row.count()).isZero();
        assertThat(row.uniqueUserCount()).isZero();
    }

    @Test
    @DisplayName("컬럼 수가 3이 아니면 IllegalStateException을 던진다")
    void fromRow_wrongColumnCount_throws() {
        assertThatThrownBy(() -> DailyLoginStatRow.fromRow(new Object[] {"2026-09-07", 1L}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("컬럼 수 불일치");
        assertThatThrownBy(() -> DailyLoginStatRow.fromRow(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
