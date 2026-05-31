package com.kdb.it.common.admin.realtime.controller;

import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import com.kdb.it.common.admin.realtime.service.RealtimeLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 실시간 로그 모니터링 API.
 *
 * <p>관리자(ROLE_ADMIN) 전용. 응답은 표준 로그 컬럼만 포함하며 변경 본문은 제외한다.</p>
 */
@RestController
@RequestMapping("/api/admin/realtime-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin/Realtime Logs", description = "실시간 로그 모니터링")
public class RealtimeLogController {

    private final RealtimeLogService service;

    /**
     * 통합 실시간 로그 스냅샷을 반환한다.
     *
     * @param since         이 시각 이후 로그만 조회. null이면 최신 200건.
     * @param cursorLogTbl  복합 커서의 LOG_TBL. since와 함께 사용.
     * @param cursorLogSno  복합 커서의 LOG_HIS_TGR_SNO. since와 함께 사용.
     * @param limit         최대 200.
     * @param tables        쉼표로 구분된 허용 LOG_KEY 목록.
     * @param chgTypes      쉼표로 구분된 C/U/D 부분집합.
     */
    @GetMapping
    @Operation(summary = "통합 실시간 로그 조회",
            description = "since/복합 커서 기반 증분 조회와 최근 5분/30분 집계를 함께 반환합니다.")
    public RealtimeLogDto.Snapshot get(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(required = false) String cursorLogTbl,
            @RequestParam(required = false) Long cursorLogSno,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String tables,
            @RequestParam(required = false) String chgTypes
    ) {
        return service.snapshot(since, cursorLogTbl, cursorLogSno, limit,
                split(tables), split(chgTypes));
    }

    private List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
