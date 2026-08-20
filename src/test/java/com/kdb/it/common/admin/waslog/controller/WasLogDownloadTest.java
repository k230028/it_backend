package com.kdb.it.common.admin.waslog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
}
