package com.kdb.it.common.system.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.system.repository.LoginHistoryRepository.DailyLoginStatRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#6 일별 로그인 통계 native → DailyLoginStatRow 매핑 동등성")
class DailyLoginStatsMappingIt extends AbstractOracleRepositoryTest {

    @Autowired LoginHistoryRepository loginHistoryRepository;

    @Test
    @DisplayName("findDailyLoginStats: Object[] 경로와 DailyLoginStatRow 경로가 컬럼별로 동일하다")
    void dailyStats_objectArray_equals_dto() {
        // 최근 30일 실데이터 기준 — 두 경로가 같은 SQL을 호출하므로 행 수·컬럼값이 동일해야 한다.
        List<Object[]> rows = loginHistoryRepository.findDailyLoginStats();
        List<DailyLoginStatRow> dtos = loginHistoryRepository.findDailyLoginStatRows();
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            DailyLoginStatRow d = dtos.get(i);
            assertThat(d.label()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.count()).isEqualTo(r[1] == null ? 0L : ((Number) r[1]).longValue());
            assertThat(d.uniqueUserCount())
                    .isEqualTo(r[2] == null ? 0L : ((Number) r[2]).longValue());
            // 행번 중복을 제거한 접속자 수는 접속 횟수를 넘을 수 없다.
            assertThat(d.uniqueUserCount()).isLessThanOrEqualTo(d.count());
        }
    }
}
