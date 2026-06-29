package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.dto.EvaluationItemAvgRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("#6 평가 항목평균 native → EvaluationItemAvgRow 매핑 동등성")
class EvaluationItemAvgMappingIt extends AbstractOracleRepositoryTest {

    @Autowired
    EvaluationRepository evaluationRepository;

    @Test
    @DisplayName("존재하는 협의회ID에 대해 Object[]와 DTO 결과가 일치한다(데이터 없으면 둘 다 빈 목록)")
    void avg_objectArray_equals_dto() {
        // 결정적: 존재하지 않는 ID는 항상 빈 목록 → 두 경로 모두 empty로 동등
        String asctId = "ASCT-0000-0000";
        List<Object[]> rows = evaluationRepository.findAverageScoreByItem(asctId, "N");
        List<EvaluationItemAvgRow> dtos = evaluationRepository.findAvgRowsByItem(asctId, "N");
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            EvaluationItemAvgRow d = dtos.get(i);
            assertThat(d.itPtlCkgItmTc()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.avgScore()).isEqualByComparingTo(
                    r[1] == null ? null : new BigDecimal(r[1].toString()));
        }
    }
}
