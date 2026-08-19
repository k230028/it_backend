package com.kdb.it.common.admin.waslog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WasLogController.class)
@WithMockUser(roles = "ADMIN")
class WasLogControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WasLogService service;

    // WasLogController가 @PreAuthorize를 갖고 있어 @WebMvcTest 슬라이스가 Filter 빈으로
    // JwtAuthenticationFilter를 자동 포함시킨다. 해당 필터의 생성자 의존성을 채우기 위한 목이며
    // 이 테스트는 @WithMockUser로 인증을 직접 주입하므로 실제 토큰 검증 로직은 쓰이지 않는다.
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    @DisplayName("조회는 스냅샷을 그대로 직렬화한다")
    void snapshot_정상응답() throws Exception {
        WasLogEntry entry =
                new WasLogEntry(7L, 1000L, "ERROR", "http-1", "com.kdb.it.A", "실패", "stack");
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "epoch-1", List.of(entry), 7L, false, List.of(), null));

        mockMvc.perform(get("/api/admin/was-logs").param("afterSeq", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("SVR1"))
                .andExpect(jsonPath("$.bufferEpoch").value("epoch-1"))
                .andExpect(jsonPath("$.lastSeq").value(7))
                .andExpect(jsonPath("$.entries[0].seq").value(7))
                .andExpect(jsonPath("$.entries[0].throwable").value("stack"));
    }

    @Test
    @DisplayName("허용되지 않은 레벨이면 400을 준다")
    void snapshot_잘못된레벨_400() throws Exception {
        given(service.snapshot(any(), any()))
                .willThrow(new IllegalArgumentException("허용되지 않은 로그 레벨: FATAL"));

        mockMvc.perform(get("/api/admin/was-logs").param("levels", "FATAL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인스턴스 목록을 반환한다")
    void instances_정상응답() throws Exception {
        given(service.instances())
                .willReturn(
                        List.of(
                                new WasLogDto.InstanceInfo("SVR1", true, true),
                                new WasLogDto.InstanceInfo("SVR2", false, true)));

        mockMvc.perform(get("/api/admin/was-logs/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("SVR1"))
                .andExpect(jsonPath("$[0].self").value(true))
                .andExpect(jsonPath("$[1].id").value("SVR2"));
    }

    @Test
    @DisplayName("질의 파라미터를 파싱해 서비스에 그대로 넘긴다")
    void snapshot_파라미터전달() throws Exception {
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR2", "e1", List.of(), 9L, false, List.of(), null));

        mockMvc.perform(
                        get("/api/admin/was-logs")
                                .param("instanceId", "SVR2")
                                .param("afterSeq", "9")
                                .param("limit", "50")
                                .param("levels", " ERROR , WARN ,")
                                .param("logger", "com.kdb.it")
                                .param("q", "실패"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> instanceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WasLogDto.Query> queryCaptor =
                ArgumentCaptor.forClass(WasLogDto.Query.class);
        verify(service).snapshot(instanceCaptor.capture(), queryCaptor.capture());

        assertThat(instanceCaptor.getValue()).isEqualTo("SVR2");
        WasLogDto.Query query = queryCaptor.getValue();
        assertThat(query.afterSeq()).isEqualTo(9L);
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.levels()).containsExactlyInAnyOrder("ERROR", "WARN");
        assertThat(query.logger()).isEqualTo("com.kdb.it");
        assertThat(query.keyword()).isEqualTo("실패");
    }
}
