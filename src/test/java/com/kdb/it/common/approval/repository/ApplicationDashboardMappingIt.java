package com.kdb.it.common.approval.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#6 결재 대시보드 native → DTO 매핑 동등성")
class ApplicationDashboardMappingIt extends AbstractOracleRepositoryTest {

    @Autowired ApplicationRepository applicationRepository;

    @Test
    @DisplayName("findMonthlyTrendByBbrC: Object[] 경로와 LabeledCountRow 경로가 컬럼별로 동일하다")
    void monthlyTrend_objectArray_equals_dto() {
        // 존재하지 않는 부서코드는 결정적으로 빈 목록 → 두 경로 동등. 실데이터가 있으면 컬럼별 비교.
        // 주의: 빈 목록만 반환하므로 컬럼별 매핑 검증은 서비스 단위 테스트의 fromRow fixture에 의존함
        String bbrC = "ZZZZZ";
        List<Object[]> rows = applicationRepository.findMonthlyTrendByBbrC(bbrC);
        List<LabeledCountRow> dtos = applicationRepository.findMonthlyTrendRowsByBbrC(bbrC);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            LabeledCountRow d = dtos.get(i);
            assertThat(d.label()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.count()).isEqualTo(r[1] == null ? 0L : ((Number) r[1]).longValue());
        }
    }

    @Test
    @DisplayName("findPendingListByEno: Object[] 경로와 PendingApprovalRow 경로가 컬럼별로 동일하다")
    void pendingList_objectArray_equals_dto() {
        // 주의: 빈 목록만 반환하므로 컬럼별 매핑 검증은 서비스 단위 테스트의 fromRow fixture에 의존함
        String eno = "00000000";
        List<Object[]> rows = applicationRepository.findPendingListByEno(eno);
        List<PendingApprovalRow> dtos = applicationRepository.findPendingRowsByEno(eno);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            PendingApprovalRow d = dtos.get(i);
            assertThat(d.apfDcmNo()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.title()).isEqualTo(r[1] == null ? null : r[1].toString());
            assertThat(d.usrNm()).isEqualTo(r[2] == null ? null : r[2].toString());
            assertThat(d.rqsDt()).isEqualTo(r[3] == null ? null : r[3].toString());
        }
    }
}
