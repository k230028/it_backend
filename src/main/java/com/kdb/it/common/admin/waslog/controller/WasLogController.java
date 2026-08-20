package com.kdb.it.common.admin.waslog.controller;

import com.kdb.it.common.admin.waslog.client.WasLogPeerException;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 WAS 로그 조회 API.
 *
 * <p>관리자(ROLE_ADMIN) 전용. 응답은 인메모리 링버퍼 스냅샷이며 재기동 이전 로그는 포함하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/was-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin/WAS Logs", description = "실시간 WAS 로그")
public class WasLogController {

    private final WasLogService service;
    private final WasLogAuditLogger auditLogger;

    /**
     * 대상 인스턴스의 로그 스냅샷을 반환한다.
     *
     * @param instanceId 대상 인스턴스ID. 생략하면 요청을 받은 인스턴스
     * @param afterSeq 이 seq 초과분만 조회. 생략하면 0
     * @param limit 조회 상한. 생략하면 200이며 서비스에서 최대 200으로 제한
     * @param levels 쉼표로 구분된 레벨 목록
     * @param logger 로거명 접두사
     * @param q 메시지·로거 부분일치 키워드
     */
    @GetMapping
    @Operation(summary = "WAS 로그 조회", description = "seq 커서 기반 증분 조회입니다.")
    public WasLogDto.Snapshot snapshot(
            @RequestParam(name = "instanceId", required = false) String instanceId,
            @RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", defaultValue = "200") int limit,
            @RequestParam(name = "levels", required = false) String levels,
            @RequestParam(name = "logger", required = false) String logger,
            @RequestParam(name = "q", required = false) String q) {
        if (afterSeq == 0) auditLogger.logSnapshotAccess(instanceId);
        return service.snapshot(
                instanceId, new WasLogDto.Query(afterSeq, limit, splitLevels(levels), logger, q));
    }

    /** 조회 가능한 인스턴스 목록. */
    @GetMapping("/instances")
    @Operation(summary = "인스턴스 목록", description = "설정에 등록된 WAS 인스턴스를 반환합니다.")
    public List<WasLogDto.InstanceInfo> instances() {
        return service.instances();
    }

    /**
     * 런타임 로그레벨을 한시적으로 변경한다.
     *
     * @param request 대상 인스턴스·로거·레벨·TTL(1~120분)
     */
    @PostMapping("/level")
    @Operation(summary = "런타임 로그레벨 변경", description = "TTL이 지나면 자동으로 원래 레벨로 복원됩니다.")
    public WasLogDto.LevelOverride applyLevel(@RequestBody WasLogDto.LevelRequest request) {
        auditLogger.logLevelChange(request);
        return service.applyLevel(request);
    }

    /**
     * 현재 필터가 적용된 버퍼 내용을 텍스트 파일로 내려받는다.
     *
     * <p>본문 형식은 파일 로그와 같은 도구로 열 수 있도록 {@code yyyy-MM-dd HH:mm:ss.SSS LEVEL [thread] logger -
     * message} 형태로 맞춘다.
     */
    @GetMapping("/download")
    @Operation(summary = "WAS 로그 다운로드", description = "현재 필터 범위를 text/plain 첨부로 반환합니다.")
    public ResponseEntity<String> download(
            @RequestParam(name = "instanceId", required = false) String instanceId,
            @RequestParam(name = "levels", required = false) String levels,
            @RequestParam(name = "logger", required = false) String logger,
            @RequestParam(name = "q", required = false) String q) {
        WasLogDto.Snapshot snapshot =
                service.snapshot(
                        instanceId,
                        new WasLogDto.Query(
                                0L, WasLogService.MAX_LIMIT, splitLevels(levels), logger, q));

        StringBuilder body = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        for (WasLogEntry entry : snapshot.entries()) {
            body.append(
                            formatter.format(
                                    LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(entry.timestamp()),
                                            ZoneId.systemDefault())))
                    .append(' ')
                    .append(entry.level())
                    .append(" [")
                    .append(entry.thread())
                    .append("] ")
                    .append(entry.logger())
                    .append(" - ")
                    .append(entry.message())
                    .append('\n');
            if (entry.throwable() != null) {
                body.append(entry.throwable()).append('\n');
            }
        }

        String resolvedInstance = snapshot.instanceId() == null ? "unknown" : snapshot.instanceId();
        String fileName =
                "was-log_"
                        + resolvedInstance
                        + "_"
                        + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                        + ".log";
        auditLogger.logDownload(resolvedInstance, snapshot.entries().size());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(body.toString());
    }

    /**
     * 피어 위임 실패를 502로 매핑한다.
     *
     * <p>{@code GlobalExceptionHandler}의 포괄 {@code RuntimeException} 핸들러(400)에 맡기면 "SVR2 다운"과 "로거명
     * 오타" 같은 클라이언트 입력 오류가 같은 상태코드로 뒤섞인다. 이 컨트롤러에서만 발생하는 피어 호출 실패이므로 여기서 502로 구분한다.
     */
    @ExceptionHandler(WasLogPeerException.class)
    public ResponseEntity<String> handlePeerFailure(WasLogPeerException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }

    private Set<String> splitLevels(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
