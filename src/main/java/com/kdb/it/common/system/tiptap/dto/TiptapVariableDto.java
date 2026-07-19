package com.kdb.it.common.system.tiptap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Tiptap 변수 API DTO 모음.
 * 설계 참조: §3.6 API 스키마
 */
public final class TiptapVariableDto {

    private TiptapVariableDto() {}

    @Schema(name = "TiptapVariableMetadataResponse", description = "변수 카탈로그 응답")
    public record MetadataResponse(List<CategoryMetadata> categories) {}

    @Schema(name = "TiptapVariableCategoryMetadata", description = "카테고리 메타데이터")
    public record CategoryMetadata(
            @Schema(description = "카테고리 코드", example = "IT_BUDGET") String code,
            @Schema(description = "표시 라벨", example = "전산예산") String label,
            @Schema(description = "지원 연도 목록") List<Integer> years,
            @Schema(description = "사업 목록 (PROJ 카테고리 전용)") List<ProjectRef> projects,
            @Schema(description = "항목 목록") List<ItemRef> items
    ) {}

    @Schema(name = "TiptapVariableProjectRef", description = "사업 참조")
    public record ProjectRef(String code, String name) {}

    @Schema(name = "TiptapVariableItemRef", description = "항목 참조")
    public record ItemRef(String key, String label) {}

    @Schema(name = "TiptapVariableResolveRequest", description = "변수 해석 요청")
    public record ResolveRequest(
            @NotNull
            @NotEmpty(message = "토큰은 1개 이상이어야 합니다")
            @Size(max = 200, message = "토큰은 200개를 초과할 수 없습니다")
            @Schema(description = "해석할 토큰 배열")
            List<String> tokens
    ) {}

    @Schema(name = "TiptapVariableResolveResponse", description = "변수 해석 응답")
    public record ResolveResponse(Map<String, ResolvedValue> results) {}

    @Schema(name = "TiptapVariableResolvedValue", description = "해석된 값")
    public record ResolvedValue(
            @Schema(description = "표시값 (포맷팅된 문자열). 상태가 OK가 아니면 빈 문자열.", example = "900억원") String value,
            @Schema(description = "상태",
                    allowableValues = {"OK", "MISSING", "FORBIDDEN", "INVALID"}) String status
    ) {
        public static ResolvedValue ok(String value)        { return new ResolvedValue(value, "OK"); }
        public static ResolvedValue missing()               { return new ResolvedValue("", "MISSING"); }
        public static ResolvedValue forbidden()             { return new ResolvedValue("", "FORBIDDEN"); }
        public static ResolvedValue invalid()               { return new ResolvedValue("", "INVALID"); }
    }
}
