package com.kdb.it.domain.migration.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.service.MigrationImportService;
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
 * 수기 엑셀 일괄 이관 API입니다. 관리자만 호출할 수 있습니다.
 *
 * <p>{@code /api/admin/**}는 {@code SecurityConfig}에서 이미 {@code hasRole("ADMIN")}로 제한되어 있으므로 클래스 수준
 * {@link PreAuthorize}는 이중 방어입니다.
 */
@Tag(name = "데이터 이관", description = "수기 엑셀 일괄 반입")
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MigrationController {

    private final MigrationImportService migrationImportService;

    /**
     * 올린 시트를 검증해 행별 진단과 해석 후보를 돌려줍니다. 아무것도 저장하지 않습니다.
     *
     * @param request 시트 목록
     * @return 진단 목록과 요약
     * @throws IllegalArgumentException 시트 목록이 비었거나 지원하지 않는 시트 종류가 온 경우 (400)
     */
    @Operation(summary = "이관 사전검증", description = "조직·코드 해석과 중복·필수값 검증 결과를 돌려줍니다. 저장하지 않습니다.")
    @PostMapping(path = "/imports/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MigrationDto.DryRunResponse> dryRun(
            @Valid @RequestBody MigrationDto.DryRunRequest request) {
        return ResponseEntity.ok(migrationImportService.dryRun(request));
    }

    /**
     * 보정값을 반영해 원장과 결재 받이를 만들고 편성률을 적용합니다.
     *
     * <p>인증 사용자의 사번({@link CustomUserDetails#getEno()})을 업로드 작성자로 넘깁니다. 이 값은 반영되는 모든 행의 결재 요청자와 감사
     * 주체로 기록되므로 {@code getUsername()}이 아니라 {@code getEno()}를 사용합니다.
     *
     * @param request 시트 목록과 보정값
     * @param user 인증 사용자 (사번을 업로드 작성자로 씁니다)
     * @return 반영 건수와 생성한 관리번호
     * @throws com.kdb.it.exception.CustomGeneralException 재검증에 BLOCKER가 남은 경우 (400)
     */
    @Operation(summary = "이관 확정 반영", description = "단일 트랜잭션으로 원장을 만듭니다. 오류가 남아 있으면 전량 롤백됩니다.")
    @PostMapping(path = "/imports", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MigrationDto.CommitResponse> commit(
            @Valid @RequestBody MigrationDto.CommitRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(migrationImportService.commit(request, user.getEno()));
    }
}
