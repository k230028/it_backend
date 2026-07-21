package com.kdb.it.domain.budget.cost.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#7 전산관리비 목록 경량 프로젝션 동등성 + 미표시 컬럼 제외")
class CostListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired CostRepository costRepository;

    @Test
    @DisplayName("경량 목록 행의 식별/요약 필드가 엔티티 경로와 일치한다")
    void lightList_matches_entityPath_onListedFields() {
        // 전체 조건(필터 없음) — 동일 WHERE(DEL_YN='N')에서 두 경로 비교
        CostDto.SearchCondition cond = new CostDto.SearchCondition();
        List<Bcostm> entities = costRepository.searchByCondition(cond);
        List<CostDto.CostListRow> rows = costRepository.searchListByCondition(cond);

        assertThat(rows).hasSameSizeAs(entities);

        Map<String, Bcostm> byKey =
                entities.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        e -> e.getCostBgNo() + "#" + e.getBgSno(),
                                        Function.identity(),
                                        (a, b) -> a));
        for (CostDto.CostListRow row : rows) {
            Bcostm e = byKey.get(row.costBgNo() + "#" + row.bgSno());
            assertThat(e).as("동일 키 엔티티 존재").isNotNull();
            assertThat(row.lstYn()).isEqualTo(e.getLstYn());
            assertThat(row.ioeC()).isEqualTo(e.getIoeC());
            assertThat(row.cttNm()).isEqualTo(e.getCttNm());
            assertThat(row.cttOppNm()).isEqualTo(e.getCttOppNm());
            if (e.getCostTotXpAmt() == null) {
                assertThat(row.costTotXpAmt()).isNull();
            } else {
                assertThat(row.costTotXpAmt()).isEqualByComparingTo(e.getCostTotXpAmt());
            }
            assertThat(row.curC()).isEqualTo(e.getCurC());
            assertThat(row.sectSysUtzYn()).isEqualTo(e.getSectSysUtzYn());
            assertThat(row.costSvnDpmC()).isEqualTo(e.getCostSvnDpmC());
            assertThat(row.svnTemC()).isEqualTo(e.getSvnTemC());
            assertThat(row.bseYy()).isEqualTo(e.getBseYy());
            assertThat(row.abusTc()).isEqualTo(e.getAbusTc());
            assertThat(row.delYn()).isEqualTo(e.getDelYn());
        }
    }
}
