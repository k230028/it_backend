package com.kdb.it.common.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 관리자 상세 로그 조회 DTO 모음.
 */
public class AdminLogDto {

    @Schema(name = "AdminLogDto.LogTableResponse", description = "로그 테이블 메타 정보")
    public record LogTableResponse(
            String key,
            String title,
            String tableName,
            String entityName
    ) {}

    @Schema(name = "AdminLogDto.LogColumnResponse", description = "로그 컬럼 메타 정보")
    public record LogColumnResponse(
            String field,
            String columnName,
            String header,
            boolean userField,
            boolean primary
    ) {}

    @Schema(name = "AdminLogDto.LogPageResponse", description = "로그 목록 조회 응답")
    public record LogPageResponse(
            LogTableResponse table,
            List<LogColumnResponse> columns,
            List<Map<String, Object>> content,
            Map<String, String> userNames,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {}

    @Schema(name = "AdminLogDto.LogDetailResponse", description = "로그 상세 조회 응답")
    public record LogDetailResponse(
            LogTableResponse table,
            List<LogColumnResponse> columns,
            Map<String, Object> row,
            Map<String, String> userNames
    ) {}
}
