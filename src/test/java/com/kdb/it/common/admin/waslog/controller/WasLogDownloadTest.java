package com.kdb.it.common.admin.waslog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
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
import java.time.OffsetDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

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

        performDownload(get("/api/admin/was-logs/download").param("instanceId", "SVR1"))
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

        performDownload(get("/api/admin/was-logs/download")).andExpect(status().isOk());

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

        performDownload(get("/api/admin/was-logs/download").param("instanceId", "SVR1"))
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

        performDownload(get("/api/admin/was-logs/download"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("일부 로그가 생략")));
    }

    @Test
    @DisplayName("피어 위임이 실패하면 502를 반환하고 성공 감사를 남기지 않는다")
    void download_피어실패_502_감사없음() throws Exception {
        // service.snapshot()은 피어 실패를 예외로 던지지 않고 peerError가 채워진 200 스냅샷으로
        // 위장한다(폴링 경로를 위한 설계). 다운로드가 이를 그대로 흘리면 0바이트 파일이 정상
        // 파일명으로 내려가고 auditLogger.logDownload(instanceId, 0)이 "성공"을 기록한다.
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR2", null, List.of(), 0L, false, List.of(), "SVR2 인스턴스 조회 실패"));

        mockMvc.perform(get("/api/admin/was-logs/download").param("instanceId", "SVR2"))
                .andExpect(status().isBadGateway())
                // window.open()으로 트리거되는 다운로드는 최상위 탐색이라 Accept가 text/html을
                // 선호한다. Content-Type을 명시하지 않으면 피어가 통제하는 이 본문이 API 원본에서
                // HTML로 렌더링될 수 있으므로, 절대 text/html로 협상되지 않는지 검증한다.
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("SVR2 인스턴스 조회 실패")));

        verifyNoInteractions(auditLogger);
    }

    /**
     * 다운로드 성공 응답을 비동기 디스패치까지 진행시켜 완성된 응답을 돌려준다.
     *
     * <p>본문이 {@link org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody}라
     * Spring MVC가 비동기로 처리한다. 최초 {@code perform}은 헤더만 채운 상태로 끝나므로, 본문을 검증하려면 {@code asyncDispatch}로
     * 한 번 더 돌려야 한다(BE-61).
     */
    private ResultActions performDownload(RequestBuilder request) throws Exception {
        MvcResult started = mockMvc.perform(request).andReturn();
        return mockMvc.perform(asyncDispatch(started));
    }

    @Test
    @DisplayName("본문 한 줄은 FILE_LOG_PATTERN과 같은 자리를 쓰되 PID 자리에 인스턴스ID를 넣는다")
    void download_파일로그형식_인스턴스ID() throws Exception {
        // 파일 로그 한 줄의 모양(Spring Boot 4 FILE_LOG_PATTERN):
        //   2026-08-20T18:00:00.000+09:00  INFO 46012 --- [it] [http-1] c.k.i.A   : 메시지
        // 다운로드는 PID 자리(46012)에만 인스턴스ID를 넣는다 — 피어 로그가 섞일 수 있어
        // 어느 서버의 로그인지가 PID보다 유용하기 때문이다(FE-55).
        WasLogEntry entry =
                new WasLogEntry(1L, 1755680400000L, "INFO", "http-1", "com.kdb.it.A", "메시지", null);
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR2", "e1", List.of(entry), 1L, false, List.of(), null));

        String body =
                performDownload(get("/api/admin/was-logs/download").param("instanceId", "SVR2"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // 첫 줄은 형식이 다르다는 사실을 밝히는 주석이다.
        assertThat(body.lines().findFirst().orElseThrow())
                .startsWith("#")
                .contains("PID 자리에 인스턴스ID");

        String logLine =
                body.lines().filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
        // ISO8601 + 오프셋(%d) → 5칸 우측정렬 레벨(%5p) → 인스턴스ID(PID 자리) → ` --- `
        // → `[앱명] `(%esb) → `[스레드]` → 40칸 로거(%-40.40logger{39}) → ` : ` → 메시지
        // 타임스탬프는 ISO8601 + 오프셋
        // 타임스탬프가 ISO8601+오프셋인지는 실제로 파싱해 확인한다 — 정규식보다 강한 검증이다.
        String timestamp = logLine.substring(0, logLine.indexOf(' '));
        assertThat(OffsetDateTime.parse(timestamp)).isNotNull();
        // 나머지 칸은 파일 로그와 같은 순서·구분자를 쓴다. PID 자리에만 인스턴스ID가 들어간다.
        String afterTimestamp = logLine.substring(logLine.indexOf(' ') + 1);
        // 39자 안에 드는 짧은 로거명은 축약하지 않는다(logback과 같은 동작).
        assertThat(afterTimestamp)
                .isEqualTo(
                        " INFO SVR2 --- [it] [http-1] "
                                + String.format("%-40.40s", "com.kdb.it.A")
                                + " : 메시지");
    }

    @Test
    @DisplayName("긴 로거명은 파일 로그와 같은 규칙으로 39자로 줄이고 40칸에 맞춘다")
    void download_로거명_축약() throws Exception {
        String longLogger = "com.kdb.it.common.admin.waslog.controller.WasLogController";
        WasLogEntry entry =
                new WasLogEntry(1L, 1755680400000L, "WARN", "http-1", longLogger, "m", null);
        given(service.exportLimit()).willReturn(2000);
        given(service.snapshot(any(), any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "e1", List.of(entry), 1L, false, List.of(), null));

        String body =
                performDownload(get("/api/admin/was-logs/download"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String logLine =
                body.lines().filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
        // logback의 축약기를 그대로 쓰므로 파일 로그와 같은 문자열이 나온다.
        // 39자 안에 들어가는 순간 축약을 멈추므로 뒤쪽 `controller`는 줄지 않는다 —
        // 직접 구현했다면 `c.k.i.c.a.w.c.WasLogController`로 만들어 파일 로그와 어긋났을 지점이다.
        assertThat(logLine).contains("c.k.i.c.a.w.controller.WasLogController");
        // 로거 칸은 정확히 40칸이다 — ` : ` 앞이 밀리면 고정폭 파서가 깨진다.
        int loggerStart = logLine.indexOf("] ", logLine.indexOf("] ") + 1) + 2;
        assertThat(logLine.substring(loggerStart, logLine.indexOf(" : "))).hasSize(40);
    }
}
