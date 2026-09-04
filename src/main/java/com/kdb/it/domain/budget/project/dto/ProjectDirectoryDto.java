package com.kdb.it.domain.budget.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 전 직원 사업 검색과 접근 제한 안내에 공개할 수 있는 최소 정보 계약입니다. */
public final class ProjectDirectoryDto {

    private ProjectDirectoryDto() {}

    /** 사업 상세 본문·금액·첨부를 제외한 검색 및 담당자 안내용 응답입니다. */
    @Schema(name = "ProjectDirectoryResponse", description = "전 직원 사업 검색용 안전한 요약")
    public record Response(
            @Schema(description = "사업관리번호") String abusMngNo,
            @Schema(description = "사업명") String abusNm,
            @Schema(description = "사업현황 코드") String stsTc,
            @Schema(description = "사업현황 코드명") String stsTcNm,
            @Schema(description = "주관부서명") String svnDpmCNm,
            @Schema(description = "주관부서 담당팀장 사번") String tlrUsid,
            @Schema(description = "주관부서 담당팀장명") String tlrUsidNm,
            @Schema(description = "주관부서 담당자 사번") String usid,
            @Schema(description = "주관부서 담당자명") String usidNm) {}
}
