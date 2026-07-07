package com.kdb.it.common.admin.realtime.repository;

import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeLogRepositoryTest {

    @Test
    @DisplayName("since/복합 커서가 있으면 더 최신 행 조건으로 조회")
    void findFeedUsesNewerRowsPredicateForSinceCursor() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        RealtimeLogRepository repository = new RealtimeLogRepository(entityManager);

        LocalDateTime since = LocalDateTime.of(2026, 5, 31, 23, 14, 7);
        repository.findFeed(new RealtimeLogDto.QueryCondition(
                since,
                "TPRMPP_BPROJL",
                42L,
                50,
                List.of("BPROJM"),
                List.of("U")));

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .contains("CHG_DTM > :since")
                .contains("CHG_DTM = :since AND LOG_TBL > :cursorLogTbl")
                .contains("CHG_DTM = :since AND LOG_TBL = :cursorLogTbl AND LOG_HIS_TGR_SNO > :cursorLogSno")
                .contains("LOG_KEY IN (:tableKeys)")
                .contains("CHG_DTT_YN IN (:chgTypes)")
                .contains("ORDER BY CHG_DTM DESC, LOG_TBL DESC, LOG_HIS_TGR_SNO DESC");
        verify(query).setParameter("since", Timestamp.valueOf(since));
        verify(query).setParameter("cursorLogTbl", "TPRMPP_BPROJL");
        verify(query).setParameter("cursorLogSno", 42L);
        verify(query).setParameter("limit", 50);
        verify(query).setParameter("tableKeys", List.of("BPROJM"));
        verify(query).setParameter("chgTypes", List.of("U"));
    }
}
