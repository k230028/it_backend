package com.kdb.it.domain.migration.commondata.controller;

import com.kdb.it.domain.migration.commondata.CommonDataExportService;
import com.kdb.it.domain.migration.commondata.CommonDataMigrationService;
import com.kdb.it.domain.migration.commondata.MenuSequenceSynchronizer;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 데이터 이관(개발→운영) API입니다. 관리자만 호출할 수 있습니다.
 *
 * <p>{@code /api/admin/**}는 {@code SecurityConfig}에서 이미 {@code hasRole("ADMIN")}로 제한되어 있으므로 클래스 수준
 * {@link PreAuthorize}는 이중 방어입니다.
 */
@Tag(name = "공통 데이터 이관", description = "메뉴·경로·공통코드·다국어 개발→운영 이관")
@RestController
@RequestMapping("/api/admin/migration/common-data")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CommonDataMigrationController {

    private final CommonDataExportService exportService;
    private final CommonDataMigrationService migrationService;
    private final MenuSequenceSynchronizer menuSequenceSynchronizer;

    /** 5개 테이블 활성 행 전량을 내려줍니다. 프론트가 xlsx 파일을 생성합니다. */
    @Operation(
            summary = "공통 데이터 전량 내보내기",
            description = "메뉴·메뉴권한·경로·공통코드·다국어 활성 행 전량을 JSON으로 내려줍니다.")
    @GetMapping("/export")
    public ResponseEntity<CommonDataMigrationDto.ExportResponse> export() {
        return ResponseEntity.ok(exportService.export());
    }

    /** 저장 없이 업로드 내용을 검증하고 테이블별 반영 예정 건수·경고·오류를 돌려줍니다. */
    @Operation(summary = "공통 데이터 업로드 사전검증", description = "테이블별 추가/갱신/부활 예정 건수를 검증합니다. 저장하지 않습니다.")
    @PostMapping(path = "/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonDataMigrationDto.Response> dryRun(
            @Valid @RequestBody CommonDataMigrationDto.Request request) {
        return ResponseEntity.ok(migrationService.dryRun(request));
    }

    /** 검증을 다시 수행한 뒤 5개 테이블을 단일 트랜잭션으로 업서트하고, 트랜잭션 밖에서 메뉴 시퀀스를 동기화합니다. */
    @Operation(summary = "공통 데이터 업로드 확정 반영", description = "경로→메뉴→메뉴권한→공통코드→다국어 순서로 업서트합니다.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonDataMigrationDto.Response> commit(
            @Valid @RequestBody CommonDataMigrationDto.Request request) {
        CommonDataMigrationDto.Response response = migrationService.commit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        menuSequenceSynchronizer
                                .advanceTo(request.menus())
                                .map(response::withWarning)
                                .orElse(response));
    }
}
