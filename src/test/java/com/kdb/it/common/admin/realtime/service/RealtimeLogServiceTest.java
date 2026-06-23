package com.kdb.it.common.admin.realtime.service;

import com.kdb.it.common.admin.dto.AdminLogDto;
import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import com.kdb.it.common.admin.realtime.repository.RealtimeLogRepository;
import com.kdb.it.common.admin.service.AdminLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RealtimeLogServiceTest {

    @Mock private RealtimeLogRepository repository;
    @Mock private AdminLogService adminLogService;

    private RealtimeLogService service;
    private final Clock fixedClock = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 23, 14, 7).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        service = new RealtimeLogService(repository, adminLogService, fixedClock);
        lenient().when(adminLogService.getTables()).thenReturn(List.of(
                new AdminLogDto.LogTableResponse("bprojm", "정보화사업 로그", "TPRMPP_BPROJL", "BprojmL"),
                new AdminLogDto.LogTableResponse("bcostm", "전산업무비 로그", "TPRMPP_BCOSTL", "BcostmL")
        ));
        lenient().when(repository.findFeed(any())).thenReturn(List.of());
        lenient().when(repository.countByTableSince(any())).thenReturn(Map.of());
        lenient().when(repository.perMinuteSince(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("허용되지 않은 LOG_KEY 입력 시 IllegalArgumentException")
    void rejectsUnknownTableKey() {
        assertThatThrownBy(() ->
                service.snapshot(null, null, null, 200, List.of("bprojm", "unknown"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("허용되지 않은 chgType 입력 시 IllegalArgumentException")
    void rejectsUnknownChgType() {
        assertThatThrownBy(() ->
                service.snapshot(null, null, null, 200, null, List.of("X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X");
    }

    @Test
    @DisplayName("since 미지정 시 커서 컬럼은 null로 Repository에 전달")
    void initialSnapshotPassesNullCursor() {
        service.snapshot(null, null, null, 200, null, null);
        ArgumentCaptor<RealtimeLogDto.QueryCondition> captor =
                ArgumentCaptor.forClass(RealtimeLogDto.QueryCondition.class);
        org.mockito.Mockito.verify(repository).findFeed(captor.capture());
        assertThat(captor.getValue().since()).isNull();
        assertThat(captor.getValue().cursorLogTbl()).isNull();
        assertThat(captor.getValue().cursorLogSno()).isNull();
    }

    @Test
    @DisplayName("limit은 1~200 범위로 클램프")
    void clampsLimit() {
        service.snapshot(null, null, null, 9999, null, null);
        ArgumentCaptor<RealtimeLogDto.QueryCondition> captor =
                ArgumentCaptor.forClass(RealtimeLogDto.QueryCondition.class);
        org.mockito.Mockito.verify(repository).findFeed(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(200);
    }

    @Test
    @DisplayName("snapshot 결과의 serverTime은 주입된 Clock 시각")
    void serverTimeFromClock() {
        RealtimeLogDto.Snapshot snap = service.snapshot(null, null, null, 200, null, null);
        assertThat(snap.serverTime()).isEqualTo(LocalDateTime.of(2026, 5, 31, 23, 14, 7));
    }
}
