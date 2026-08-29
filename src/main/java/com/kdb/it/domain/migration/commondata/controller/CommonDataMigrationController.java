package com.kdb.it.domain.migration.commondata.controller;

import com.kdb.it.domain.migration.commondata.CommonDataExportService;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** 5개 테이블 활성 행 전량을 내려줍니다. 프론트가 xlsx 파일을 생성합니다. */
    @Operation(summary = "공통 데이터 전량 내보내기", description = "메뉴·메뉴권한·경로·공통코드·다국어 활성 행 전량을 JSON으로 내려줍니다.")
    @GetMapping("/export")
    public ResponseEntity<CommonDataMigrationDto.ExportResponse> export() {
        return ResponseEntity.ok(exportService.export());
    }
}
