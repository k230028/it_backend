package com.kdb.it.domain.budget.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정보화사업 DTO 중 검색 조건·일괄 조회 요청 계약을 분리한 기반 타입입니다.
 *
 * <p>{@link ProjectDto}가 이 클래스를 상속하므로 기존 호출부는 {@code ProjectDto.SearchCondition}처럼 같은 이름으로 접근합니다.
 * OpenAPI 스키마 이름도 {@code @Schema(name)}으로 고정되어 프론트 생성 타입에 영향을 주지 않습니다.
 */
public class ProjectQueryDto {

    /**
     * 정보화사업 목록 조회 검색 조건 DTO
     *
     * <p>{@code GET /api/projects} 엔드포인트의 Query Parameter로 전달됩니다. 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     *
     * <p>{@code apfSts} 값 규칙:
     *
     * <ul>
     *   <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회
     *   <li>{@code "none"}: 신청서가 없는 프로젝트 (apfSts IS NULL)
     *   <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 프로젝트
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectSearchCondition", description = "정보화사업 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         *
         * <p>"none" → 신청서가 없는 프로젝트, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 프로젝트 null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 사업연도 필터 (예: "2026"). null이면 전체 연도 조회 */
        @Schema(description = "사업연도 (예: 2026). 미입력 시 전체 조회")
        private String bseYy;

        /** 프로젝트상태 필터 (예: "계획", "진행중", "완료"). null이면 전체 상태 조회 */
        @Schema(description = "프로젝트상태 (예: 계획, 진행중, 완료). 미입력 시 전체 조회")
        private String stsTc;

        /** 프로젝트유형 필터 (예: "신규", "유지보수", "고도화"). null이면 전체 유형 조회 */
        @Schema(description = "프로젝트유형 (예: 신규, 유지보수, 고도화). 미입력 시 전체 조회")
        private String bzTpC;

        /** IT부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "IT부서 코드. 미입력 시 전체 조회")
        private String dvmDpmC;

        /** 주관부서 코드 필터. null이면 전체 부서 조회 */
        @Schema(description = "주관부서 코드. 미입력 시 전체 조회")
        private String svnDpmC;

        /**
         * 경상여부 필터
         *
         * <p>"Y" → 경상사업만 조회, "N" → 일반 정보화사업만 조회 (ODN_YN IS NULL 또는 'N') null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "경상여부 (Y=경상사업만, N=일반사업만). 미입력 시 전체 조회")
        private String odnYn;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return ProjectDtoSupport.isEmpty(this);
        }
    }

    /**
     * 정보화사업 일괄 조회 요청 DTO
     *
     * <p>여러 프로젝트관리번호를 한 번에 조회할 때 사용합니다. 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "ProjectBulkGetRequest", description = "일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 프로젝트관리번호 목록 (예: ["PRJ-2026-0001", "PRJ-2026-0002"]) */
        @NotEmpty
        @Schema(description = "조회할 프로젝트관리번호 목록")
        private java.util.List<@NotBlank @Size(max = 30) String> prjMngNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TPRMPP_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bseYy;

        /**
         * 개정본을 명시하는 항목 (선택). 비우면 최종본({@code LST_YN='Y'})을 반환합니다.
         *
         * <p>재상신 초안을 화면에 띄운 채 보고서를 만들 때 최종본이 아니라 그 초안의 수치를 써야 합니다. 지정하지 않은 관리번호는 종전대로 최종본을 씁니다.
         */
        @Schema(description = "개정본 지정 (선택). 비우면 최종본을 반환")
        private java.util.List<VersionRef> versions;
    }

    /**
     * 개정본 지정 참조입니다.
     *
     * @param mngNo 사업관리번호
     * @param sno 개정 순번
     */
    @Schema(name = "ProjectDto.VersionRef", description = "개정본 지정 참조")
    public record VersionRef(String mngNo, Integer sno) {}
}
