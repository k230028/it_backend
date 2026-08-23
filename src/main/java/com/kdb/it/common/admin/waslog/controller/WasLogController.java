package com.kdb.it.common.admin.waslog.controller;

import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import com.kdb.it.common.admin.waslog.client.WasLogPeerException;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    private final Clock clock;

    /**
     * 애플리케이션명. 다운로드 본문이 파일 로그와 같은 자리에 {@code [이름] }을 넣는 데 쓴다.
     *
     * <p>{@code FILE_LOG_PATTERN}의 {@code %esb(){APPLICATION_NAME}}에 대응한다 — 이름이 비어 있으면 그 자리를 통째로
     * 비우는 것까지 같다.
     */
    @Value("${spring.application.name:}")
    private String applicationName;

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
        // 폭주 억제는 WasLogAuditLogger가 행위자+인스턴스별 스로틀로 서버 측에서 처리한다. 클라이언트가
        // 보낸 커서(afterSeq==0)로 최초 호출을 판정하면, 항상 0이 아닌 값을 보내는 호출자가 감사를 통째로
        // 회피할 수 있어 조건 없이 부른다.
        auditLogger.logSnapshotAccess(instanceId);
        // 서비스 상한이 다운로드를 위해 버퍼 용량까지 올라갔으므로, 폴링 경로는 여기서 다시 조여야
        // 화면이 한 번에 버퍼 전체(최대 24MB)를 받는 것을 막는다.
        int cappedLimit = Math.min(limit, WasLogService.MAX_LIMIT);
        return service.snapshot(
                instanceId,
                new WasLogDto.Query(afterSeq, cappedLimit, splitLevels(levels), logger, q));
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
     * <p>본문 형식은 파일 로그와 같은 도구로 열 수 있도록 파일 로그 패턴에 맞춘다. 자리·구분자와 PID 자리에 인스턴스ID를 넣는 이유는 {@link
     * #streamOf} 참고.
     */
    @GetMapping("/download")
    @Operation(summary = "WAS 로그 다운로드", description = "현재 필터 범위를 text/plain 첨부로 반환합니다.")
    public ResponseEntity<StreamingResponseBody> download(
            @RequestParam(name = "instanceId", required = false) String instanceId,
            @RequestParam(name = "levels", required = false) String levels,
            @RequestParam(name = "logger", required = false) String logger,
            @RequestParam(name = "q", required = false) String q) {
        // 설계 §5.6은 "버퍼 전체"를 요구한다. 폴링용 상한(MAX_LIMIT=200)을 그대로 쓰면 2000건 버퍼에서
        // 최신 200건만 담긴 파일이 아무 표시 없이 내려가 관리자가 완전한 로그로 오해한다.
        WasLogDto.Snapshot snapshot =
                service.snapshot(
                        instanceId,
                        new WasLogDto.Query(
                                0L, service.exportLimit(), splitLevels(levels), logger, q));

        // service.snapshot()은 폴링을 위해 피어 실패를 peerError가 채워진 200 스냅샷으로 위장한다.
        // 다운로드에서 이를 그대로 흘리면 0바이트 파일이 정상 파일명으로 내려가고, 아래
        // auditLogger.logDownload가 "0줄 성공"을 기록해 감사 기록조차 실패를 성공으로 증언한다.
        // handlePeerFailure와 같은 502 경로를 타도록 예외로 승격하고, 감사 줄을 남기지 않은 채 반환한다.
        if (snapshot.peerError() != null) {
            throw new WasLogPeerException(snapshot.peerError(), null);
        }

        String resolvedInstance = snapshot.instanceId() == null ? "unknown" : snapshot.instanceId();
        // 피어가 응답한 instanceId는 검증됐다 하더라도(DefaultWasLogPeerClient) 방어적으로 한 번 더
        // 화이트리스트 정화한다 — 파일명 컴포넌트에 그대로 꽂히므로 "가 섞이면 Content-Disposition에
        // 파라미터를 주입할 수 있다.
        String safeInstance = resolvedInstance.replaceAll("[^A-Za-z0-9_-]", "_");
        String fileName =
                "was-log_"
                        + safeInstance
                        + "_"
                        + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                .format(LocalDateTime.now(clock))
                        + ".log";
        auditLogger.logDownload(resolvedInstance, snapshot.entries().size());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(streamOf(snapshot, resolvedInstance, applicationName));
    }

    /**
     * 로거명을 {@code %-40.40logger{39}}와 같은 규칙으로 줄인다.
     *
     * <p>logback이 파일 로그에 쓰는 축약기를 그대로 쓴다 — 직접 구현하면 앞 패키지를 한 글자로 줄이는 규칙이 미묘하게 어긋나 같은 로거가 두 파일에서 다르게
     * 보인다.
     */
    private static final TargetLengthBasedClassNameAbbreviator LOGGER_ABBREVIATOR =
            new TargetLengthBasedClassNameAbbreviator(39);

    /** {@code FILE_LOG_PATTERN}의 {@code %d} 형식. Spring Boot 4 기본값과 같다. */
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * 스냅샷을 응답 스트림에 항목 단위로 내보낸다.
     *
     * <p><b>형식</b>은 파일 로그({@code FILE_LOG_PATTERN})와 같은 자리·같은 구분자를 쓴다. 기존 로그 파일과 같은 도구로 열 수 있게 하라는
     * 설계 §5.6의 요구다. 다만 <b>PID 자리에는 PID 대신 인스턴스ID</b>가 들어간다(FE-55).
     *
     * <p>PID를 쓸 수 없는 이유: 다운로드는 피어 인스턴스의 링버퍼를 위임 조회한 결과를 담을 수 있는데 {@link WasLogEntry}에는 그 피어의 PID가
     * 없다. 이 서버의 PID를 적으면 남의 로그에 이 인스턴스의 PID를 붙이는 셈이라 파서를 속인다. 다중 인스턴스 운영에서는 어느 서버의 로그인지가 PID보다
     * 유용하므로 인스턴스ID를 넣고, 그 사실을 파일 첫 줄에 주석으로 밝힌다.
     *
     * <p>{@code StringBuilder} → {@code String} → UTF-8 {@code byte[]}로 만들면 버퍼 상한(2000건 × 12KB ≈
     * 24MB)만 한 사본이 요청마다 세 벌 생긴다. ERROR 폭주 중 관리자 둘이 동시에 내려받으면 이미 불안정한 WAS에서 실제 GC 이벤트가 된다. 항목을 만드는
     * 즉시 흘려보내면 추가 상주 메모리가 한 줄 크기로 고정된다(BE-61).
     *
     * @param snapshot 내보낼 스냅샷
     * @param instanceId PID 자리에 넣을 인스턴스ID
     * @param applicationName {@code spring.application.name}. 비어 있으면 그 자리를 비운다
     * @return 응답 출력 스트림에 직접 쓰는 본문
     */
    private static StreamingResponseBody streamOf(
            WasLogDto.Snapshot snapshot, String instanceId, String applicationName) {
        // %esb(){APPLICATION_NAME}은 이름이 있으면 `[이름] `, 없으면 빈 문자열을 낸다.
        String applicationField =
                applicationName == null || applicationName.isBlank()
                        ? ""
                        : "[" + applicationName + "] ";
        return out -> {
            // Writer를 닫으면 컨테이너 출력 스트림까지 닫히므로 flush만 한다.
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write(
                    "# 형식은 파일 로그(FILE_LOG_PATTERN)와 같으나, PID 자리에 인스턴스ID가 들어갑니다"
                            + " — 피어 로그가 섞일 수 있어 어느 서버의 로그인지를 남깁니다.\n");
            // 그래도 잘렸다면(버퍼 용량보다 필터 결과가 많을 수는 없으나 방어적으로) 파일에 사실을 적는다.
            if (snapshot.dropped()) {
                writer.write("# 일부 로그가 생략되었습니다 — 버퍼에서 밀려났거나 조회 상한에 걸렸습니다.\n");
            }
            for (WasLogEntry entry : snapshot.entries()) {
                writer.write(
                        FILE_TIMESTAMP.format(
                                ZonedDateTime.ofInstant(
                                        Instant.ofEpochMilli(entry.timestamp()),
                                        ZoneId.systemDefault())));
                // %5p — 오른쪽 정렬 5칸
                writer.write(String.format(" %5s ", entry.level()));
                writer.write(instanceId);
                writer.write(" --- ");
                writer.write(applicationField);
                writer.write('[');
                writer.write(entry.thread());
                writer.write("] ");
                // %-40.40logger{39}
                writer.write(
                        String.format("%-40.40s", LOGGER_ABBREVIATOR.abbreviate(entry.logger())));
                writer.write(" : ");
                writer.write(entry.message());
                writer.write('\n');
                if (entry.throwable() != null) {
                    writer.write(entry.throwable());
                    writer.write('\n');
                }
            }
            writer.flush();
        };
    }

    /**
     * 피어 위임 실패를 502로 매핑한다.
     *
     * <p>{@code GlobalExceptionHandler}의 포괄 {@code RuntimeException} 핸들러(400)에 맡기면 "SVR2 다운"과 "로거명
     * 오타" 같은 클라이언트 입력 오류가 같은 상태코드로 뒤섞인다. 이 컨트롤러에서만 발생하는 피어 호출 실패이므로 여기서 502로 구분한다.
     *
     * <p>본문({@code e.getMessage()})은 피어가 통제하는 문자열을 포함할 수 있다({@code RestClientException} 메시지에 피어 응답
     * 본문 일부가 실리고, {@code DefaultWasLogPeerClient}는 피어의 원본 {@code instanceId}를 불일치 메시지에 그대로 넣는다).
     * 프론트엔드는 다운로드를 {@code window.open(url, '_blank')}로 트리거하는 최상위 탐색이라 {@code Accept}가 {@code
     * text/html}을 선호하고, Content-Type을 지정하지 않으면 콘텐츠 협상이 이 본문을 HTML로 렌더링해 API 원본에서 악성 피어가 만든 마크업이 실행될
     * 수 있다. {@code text/plain}을 명시해 그 경로를 구조적으로 막는다.
     */
    @ExceptionHandler(WasLogPeerException.class)
    public ResponseEntity<String> handlePeerFailure(WasLogPeerException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getMessage());
    }

    private Set<String> splitLevels(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
