package com.kdb.it.common.admin.metrics.controller;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.metrics.service.ServerMetricsService;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 피어 인스턴스 전용 서버 자원 사용량 내부 API.
 *
 * <p>WAS 로그 뷰어와 같이 사용자 JWT가 아니라 공유 비밀 헤더 {@code X-Internal-Token}으로만 인증하고, 비밀값이 비어 있으면 빈 자체를 등록하지
 * 않는다. 로컬 버퍼만 돌려주고 다른 피어로 위임하지 않는다(무한 위임 방지).
 */
@RestController
@RequestMapping("/internal/server-metrics")
@RequiredArgsConstructor
@ConditionalOnExpression("!'${app.was-log.internal-secret:}'.isBlank()")
public class ServerMetricsInternalController {

    private final ServerMetricsService service;
    private final WasLogProperties properties;

    /** 로컬 인스턴스의 최신 샘플과 이력. */
    @GetMapping("/snapshot")
    public ResponseEntity<ServerMetricsDto.InstanceMetrics> snapshot(
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        if (!matches(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.local());
    }

    private boolean matches(String token) {
        // 조건식과 이 검사는 같은 값을 다른 경로로 읽는다. 완화 바인딩이 빈 값을 ""로 보정해도 무인증이 되지 않게 이중으로 막는다.
        if (token == null || properties.internalSecret().isBlank()) return false;
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.internalSecret().getBytes(StandardCharsets.UTF_8));
    }
}
