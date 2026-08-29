package com.kdb.it.domain.migration.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.terminal.TerminalBulkImportService;
import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 금융정보단말기 일괄업로드 API입니다. 관리자만 호출할 수 있습니다.
 *
 * <p>{@code /api/admin/**}는 {@code SecurityConfig}에서 이미 {@code hasRole("ADMIN")}로 제한되어 있으므로 클래스 수준
 * {@link PreAuthorize}는 이중 방어입니다.
 */
@Tag(name = "금융정보단말기", description = "금융정보단말기 엑셀 일괄업로드")
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MigrationController {

    private final TerminalBulkImportService terminalBulkImportService;

    /**
     * 올린 금융정보단말기 행을 검증해 연도·관리번호별 반영 예정 내역을 돌려줍니다. 아무것도 저장하지 않습니다.
     *
     * @param request 금융정보단말기 행 목록
     * @return 반영 예정 요약
     */
    @Operation(
            summary = "금융정보단말기 업로드 사전검증",
            description = "연도별 전산업무비 생성·수정 예정 내역을 검증합니다. 저장하지 않습니다.")
    @PostMapping(path = "/terminals/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TerminalBulkImportDto.Response> dryRun(
            @Valid @RequestBody TerminalBulkImportDto.Request request) {
        return ResponseEntity.ok(terminalBulkImportService.dryRun(request));
    }

    /**
     * 검증을 다시 수행한 뒤 연도별 전산업무비와 금융정보단말기를 단일 트랜잭션으로 반영합니다.
     *
     * @param request 금융정보단말기 행 목록
     * @param user 인증 사용자 (사번을 업로드 작성자로 씁니다)
     * @return 반영 결과
     */
    @Operation(summary = "금융정보단말기 업로드 확정 반영", description = "전산업무비와 단말기를 단일 트랜잭션으로 반영합니다.")
    @PostMapping(path = "/terminals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TerminalBulkImportDto.Response> commit(
            @Valid @RequestBody TerminalBulkImportDto.Request request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(terminalBulkImportService.commit(request, user.getEno()));
    }
}
