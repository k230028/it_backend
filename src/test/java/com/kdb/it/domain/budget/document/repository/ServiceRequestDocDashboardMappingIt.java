package com.kdb.it.domain.budget.document.repository;

import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.common.util.NativeRowMapper;
import com.kdb.it.domain.budget.document.dto.RecentReviewingRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("#6 요구사항정의서 대시보드 native → DTO 매핑 동등성")
class ServiceRequestDocDashboardMappingIt extends AbstractOracleRepositoryTest {

    @Autowired
    ServiceRequestDocRepository serviceRequestDocRepository;

    @Test
    @DisplayName("findMonthlyTrendByBbrC: Object[] 경로와 LabeledCountRow 경로가 컬럼별로 동일하다")
    void monthlyTrend_objectArray_equals_dto() {
        String bbrC = "ZZZZZ";
        List<Object[]> rows = serviceRequestDocRepository.findMonthlyTrendByBbrC(bbrC);
        List<LabeledCountRow> dtos = serviceRequestDocRepository.findMonthlyTrendRowsByBbrC(bbrC);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            LabeledCountRow d = dtos.get(i);
            assertThat(d.label()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.count()).isEqualTo(r[1] == null ? 0L : ((Number) r[1]).longValue());
        }
    }

    @Test
    @DisplayName("findRecentReviewingByBbrC: Object[] 경로와 RecentReviewingRow 경로가 컬럼별로 동일하다")
    void recentReviewing_objectArray_equals_dto() {
        String bbrC = "ZZZZZ";
        List<Object[]> rows = serviceRequestDocRepository.findRecentReviewingByBbrC(bbrC);
        List<RecentReviewingRow> dtos = serviceRequestDocRepository.findRecentReviewingRowsByBbrC(bbrC);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            RecentReviewingRow d = dtos.get(i);
            assertThat(d.docMngNo()).isEqualTo(NativeRowMapper.toStr(r[0]));
            assertThat(d.reqTtl()).isEqualTo(NativeRowMapper.toStr(r[1]));
            assertThat(d.usrNm()).isEqualTo(NativeRowMapper.toStr(r[2]));
            assertThat(d.createdAt()).isEqualTo(NativeRowMapper.toStr(r[3]));
            assertThat(d.fsgTlm()).isEqualTo(NativeRowMapper.toLd(r[4]));
        }
    }
}
