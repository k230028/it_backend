package com.kdb.it.common.admin.waslog.controller;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
        return service.snapshot(
                instanceId, new WasLogDto.Query(afterSeq, limit, splitLevels(levels), logger, q));
    }

    /** 조회 가능한 인스턴스 목록. */
    @GetMapping("/instances")
    @Operation(summary = "인스턴스 목록", description = "설정에 등록된 WAS 인스턴스를 반환합니다.")
    public List<WasLogDto.InstanceInfo> instances() {
        return service.instances();
    }

    private Set<String> splitLevels(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
