package com.kdb.it.domain.budget.cost.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전산업무비 DTO 중 목록 프로젝션·검색 조건·일괄 조회 요청 계약을 분리한 기반 타입입니다.
 *
 * <p>{@link CostDto}가 이 클래스를 상속하므로 기존 호출부는 {@code CostDto.SearchCondition}처럼 같은 이름으로 접근합니다. OpenAPI
 * 스키마 이름도 {@code @Schema(name)}으로 고정되어 프론트 생성 타입에 영향을 주지 않습니다.
 */
public class CostQueryDto extends CostTerminalDto {

    /**
     * 전산관리비 목록 경량 프로젝션 DTO(#7).
     *
     * <p>목록 화면에 필요한 식별/요약 컬럼만 담는다. Bcostm은 1000자+ 대용량 텍스트가 없어 제외 본문은 없으나, 목록에 불필요한 환산/외화/연기/담당자 등
     * 미표시 컬럼을 select에서 빼 적재 폭을 줄인다. 상세는 기존 엔티티 조회 경로를 유지한다(결정 B).
     *
     * @param costBgNo 전산업무비예산번호
     * @param bgSno 예산일련번호
     * @param lstYn 최종여부 ('Y'=현재 유효 레코드)
     * @param ioeC 비목코드
     * @param cttNm 계약명
     * @param cttOppNm 계약상대처명
     * @param costTotXpAmt 전산업무비예산금액
     * @param curC 통화코드
     * @param sectSysUtzYn 정보보호여부 (Y/N)
     * @param costSvnDpmC 담당부서코드 (주관부서코드)
     * @param svnTemC 담당팀코드 (주관팀코드)
     * @param bseYy 예산연도 (기준연도)
     * @param abusTc 사업구분코드
     * @param delYn 삭제여부 (Y/N)
     */
    @Schema(name = "CostListRow")
    public record CostListRow(
            String costBgNo,
            Integer bgSno,
            String lstYn,
            String ioeC,
            String cttNm,
            String cttOppNm,
            BigDecimal costTotXpAmt,
            String curC,
            String sectSysUtzYn,
            String costSvnDpmC,
            String svnTemC,
            String bseYy,
            String abusTc,
            String delYn) {}

    /**
     * 전산관리비 목록 조회 검색 조건 DTO
     *
     * <p>{@code GET /api/cost} 엔드포인트의 Query Parameter로 전달됩니다. 모든 필드가 null이면 전체 조회와 동일하게 동작합니다.
     *
     * <p>{@code apfSts} 값 규칙:
     *
     * <ul>
     *   <li>null (파라미터 미입력): 결재상태 필터 없음 → 전체 조회
     *   <li>{@code "none"}: 신청서가 없는 전산관리비 (apfSts IS NULL)
     *   <li>{@code "접수"}, {@code "결재중"}, {@code "결재완료"} 등: 최신 신청서의 결재상태가 해당 값인 전산관리비
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CostSearchCondition", description = "전산관리비 목록 조회 검색 조건")
    public static class SearchCondition {

        /**
         * 결재상태 필터
         *
         * <p>"none" → 신청서가 없는 전산관리비, 그 외 값 → 최신 신청서의 결재상태가 해당 값인 전산관리비 null 또는 미입력 → 필터 없음 (전체 조회)
         */
        @Schema(description = "결재상태 필터 (none=신청서없음, 접수/결재중/결재완료 등 실제 상태값). 미입력 시 전체 조회")
        private String apfSts;

        /** 연관부서 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관부서 코드. 미입력 시 전체 조회")
        private String costSvnDpmC;

        /** 연관팀 코드 필터. null이면 전체 조회 */
        @Schema(description = "연관팀 코드. 미입력 시 전체 조회")
        private String svnTemC;

        /** 정보보호여부 필터 ('Y'=정보보호, 'N'=일반). null이면 전체 조회 */
        @Schema(description = "정보보호여부 (Y/N). 미입력 시 전체 조회")
        private String sectSysUtzYn;

        /** 예산연도 필터 (예: "2026"). null이면 전체 조회 */
        @Schema(description = "예산연도 (예: 2026). 미입력 시 전체 조회")
        private String bseYy;

        /**
         * 소속 부서 한정 조회 여부
         *
         * <p>일반 사용자는 이 값과 무관하게 Service가 인증 사용자의 부점코드로 {@code costSvnDpmC}를 덮어씁니다. 시스템관리자는 true이면 본인
         * 부서, 그 외에는 전체를 조회합니다.
         */
        @Schema(description = "true면 로그인 사용자 소속 부서 항목만 조회 (관리자도 적용). 관리자가 false 또는 미입력 시 전체 조회")
        private Boolean myDeptOnly;

        /**
         * 모든 조건이 비어있는지 확인 (전체 조회 여부 판단용)
         *
         * @return 모든 필드가 null 또는 빈 문자열이면 true
         */
        public boolean isEmpty() {
            return isBlank(apfSts)
                    && isBlank(costSvnDpmC)
                    && isBlank(svnTemC)
                    && isBlank(sectSysUtzYn)
                    && isBlank(bseYy);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * 전산관리비 일괄 조회 요청 DTO
     *
     * <p>여러 전산관리비관리번호를 한 번에 조회할 때 사용합니다. 존재하지 않는 항목은 결과에서 자동 제외됩니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CostDto.BulkGetRequest", description = "전산업무비 일괄 조회 요청")
    public static class BulkGetRequest {
        /** 조회할 전산관리비관리번호 목록 */
        @Schema(description = "전산업무비코드 목록", example = "[\"COST_2026_0001\", \"COST_2026_0002\"]")
        private List<String> costBgNos;

        /** 편성예산 집계용 사업연도 (YYYY, 예: "2026") — TPRMPP_BBUGTM 조회 조건 */
        @Schema(description = "사업연도 (예: 2026). BBUGTM 편성예산 집계에 사용")
        private String bseYy;

        /**
         * 개정본을 명시하는 항목 (선택). 비우면 최종본({@code LST_YN='Y'})을 반환합니다.
         *
         * <p>재상신 초안을 화면에 띄운 채 보고서를 만들 때 최종본이 아니라 그 초안의 수치를 써야 합니다.
         */
        @Schema(description = "개정본 지정 (선택). 비우면 최종본을 반환")
        private List<VersionRef> versions;

        /**
         * 버전 지정 없이 최종본을 조회하는 기존 호출부용 생성자입니다.
         *
         * @param costBgNos 전산업무비예산번호 목록
         * @param bseYy 편성예산 집계용 사업연도
         */
        public BulkGetRequest(List<String> costBgNos, String bseYy) {
            this.costBgNos = costBgNos;
            this.bseYy = bseYy;
        }
    }

    /**
     * 개정본 지정 참조입니다.
     *
     * @param mngNo 전산업무비예산번호
     * @param sno 개정 순번
     */
    @Schema(name = "CostDto.VersionRef", description = "개정본 지정 참조")
    public record VersionRef(String mngNo, Integer sno) {}
}
