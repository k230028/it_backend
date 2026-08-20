package com.kdb.it.common.admin.waslog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtUtil;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WasLogController.class)
@WithMockUser(roles = "ADMIN")
class WasLogDownloadTest {

    @Autowired private MockMvc mockMvc;

    // JwtAuthenticationFilter는 @Component Filter라 @WebMvcTest가 자동 포함한다. 그 생성자 의존을 채운다.
    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private WasLogService service;
    @MockitoBean private WasLogAuditLogger auditLogger;

    // WasLogController가 파일명 시각에 주입된 Clock을 쓴다. 실제 Clock 빈(ClockConfig)은
    // @WebMvcTest 슬라이스가 스캔하지 않으므로 목으로 채워야 컨텍스트가 뜬다.
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(Instant.parse("2026-08-20T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("다운로드는 첨부 헤더와 로그 본문을 반환한다")
    void download_첨부응답() throws Exception {
        WasLogEntry entry =
                new WasLogEntry(
                        1L, 1755680400000L, "ERROR", "http-1", "com.kdb.it.A", "실패", "at A.b()");
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "e1", List.of(entry), 1L, false, List.of(), null));

        mockMvc.perform(get("/api/admin/was-logs/download").param("instanceId", "SVR1"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.startsWith(
                                                "attachment; filename=\"was-log_SVR1_")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ERROR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("실패")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("at A.b()")));
    }

    @Test
    @DisplayName("다운로드는 폴링 상한이 아니라 버퍼 전체를 요청한다")
    void download_버퍼전체요청() throws Exception {
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "e1", List.of(), 0L, false, List.of(), null));

        mockMvc.perform(get("/api/admin/was-logs/download")).andExpect(status().isOk());

        ArgumentCaptor<WasLogDto.Query> captor = ArgumentCaptor.forClass(WasLogDto.Query.class);
        verify(service).snapshot(any(), captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(2000);
    }

    @Test
    @DisplayName("다운로드는 감사 기록을 남기고 text/plain으로 응답한다")
    void download_감사기록_컨텐츠타입() throws Exception {
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "e1", List.of(), 0L, false, List.of(), null));

        mockMvc.perform(get("/api/admin/was-logs/download").param("instanceId", "SVR1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));

        verify(auditLogger).logDownload("SVR1", 0);
    }

    @Test
    @DisplayName("잘린 응답이면 파일 첫 줄에 생략 사실을 적는다")
    void download_생략표시() throws Exception {
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot("SVR1", "e1", List.of(), 0L, true, List.of(), null));

        mockMvc.perform(get("/api/admin/was-logs/download"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("일부 로그가 생략")));
    }
}
