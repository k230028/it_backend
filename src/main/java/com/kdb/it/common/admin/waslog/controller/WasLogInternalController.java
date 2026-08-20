package com.kdb.it.common.admin.waslog.controller;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.service.LevelOverrideService;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 피어 인스턴스 전용 내부 API.
 *
 * <p>사용자 JWT가 아니라 공유 비밀 헤더 {@code X-Internal-Token}으로만 인증한다. 그래서 비밀값이 비어 있으면 빈 자체를 등록하지 않는다 — 설정
 * 실수로 인증 없는 로그 엔드포인트가 열리는 경로를 구조적으로 없앤다.
 */
@RestController
@RequestMapping("/internal/was-logs")
@RequiredArgsConstructor
@ConditionalOnExpression("!'${app.was-log.internal-secret:}'.isBlank()")
public class WasLogInternalController {

    private final WasLogService service;
    private final WasLogProperties properties;
    private final LevelOverrideService levelOverrideService;
    private final WasLogAuditLogger auditLogger;

    /**
     * 로컬 버퍼 스냅샷. 라우팅하지 않는다(무한 위임 방지).
     *
     * <p>설계상 감사 로그가 로그 내용 비마스킹을 상쇄하는 보상 통제 중 하나다. 성공·실패(공유 비밀 불일치) 모두 원격 주소를 남긴다 — 이 경로는
     * permitAll이고 컨트롤러가 직접 401을 반환해 Spring Security 엔트리포인트가 실패를 기록하지 않으므로, 여기서 남기지 않으면 실패한 시도는 아무
     * 흔적도 남지 않는다.
     */
    @PostMapping("/snapshot")
    public ResponseEntity<WasLogDto.Snapshot> snapshot(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody WasLogDto.Query query,
            HttpServletRequest httpRequest) {
        if (!matches(token)) {
            auditLogger.logInternalTokenRejected("snapshot", httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        auditLogger.logInternalSnapshotAccess(httpRequest.getRemoteAddr());
        return ResponseEntity.ok(service.localSnapshot(query));
    }

    /** 로컬 인스턴스에 레벨을 적용한다. 라우팅하지 않는다. 감사 이유는 {@link #snapshot}과 같다. */
    @PostMapping("/level")
    public ResponseEntity<WasLogDto.LevelOverride> applyLevel(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody WasLogDto.LevelRequest request,
            HttpServletRequest httpRequest) {
        if (!matches(token)) {
            auditLogger.logInternalTokenRejected("level", httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        auditLogger.logInternalLevelChange(httpRequest.getRemoteAddr(), request);
        return ResponseEntity.ok(
                levelOverrideService.apply(
                        request.logger(), request.level(), request.ttlMinutes()));
    }

    private boolean matches(String token) {
        // 조건식과 이 검사는 같은 값을 서로 다른 경로로 읽는다 — 조건식은 Environment 키를 직접,
        // 이 필드는 @ConfigurationProperties 완화 바인딩(빈 값을 ""로 보정)을 거친다. 두 경로가
        // 어긋나 빈 비밀값으로 빈이 등록되면 MessageDigest.isEqual("", "")가 true라 무인증이 된다.
        // 보안 불변식을 한 경로에만 의존시키지 않는다.
        if (token == null || properties.internalSecret().isBlank()) return false;
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.internalSecret().getBytes(StandardCharsets.UTF_8));
    }
}
