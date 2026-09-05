package com.kdb.it.common.admin.metrics.controller;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.metrics.service.ServerMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 대시보드 서버 자원 사용량 API. {@code /api/admin/**} 보안 규칙으로 ROLE_ADMIN만 접근한다. */
@Tag(name = "Admin", description = "관리자 대시보드 서버 자원 사용량")
@RestController
@RequestMapping("/api/admin/dashboard/server-metrics")
@RequiredArgsConstructor
public class ServerMetricsController {

    private final ServerMetricsService service;

    /**
     * 모든 인스턴스의 최신 자원 사용량과 최근 이력을 조회한다.
     *
     * @return HTTP 200 + 인스턴스별 지표. 피어 조회 실패는 해당 인스턴스의 {@code peerError}로 실린다
     */
    @GetMapping
    @Operation(
            summary = "서버 자원 사용량 조회",
            description = "자기 자신과 설정된 피어 인스턴스의 CPU·메모리·load·디스크·스레드·DB 풀 지표와 최근 이력을 반환합니다.")
    public ResponseEntity<ServerMetricsDto.Response> getServerMetrics() {
        return ResponseEntity.ok(service.aggregate());
    }
}
