package com.kdb.it.common.iam.dto;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조직(부점) 관련 DTO 클래스 모음
 *
 * <p>조직 정보(TPRMPP_CORGNI) 조회에 사용되는 Response DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 *
 * <p>조직 구조는 {@code prlmHrkOgzCCone}(상위조직코드)로 계층 구조를 형성합니다.
 *
 * <p>포함된 DTO:
 *
 * <ul>
 *   <li>{@link Response}: 조직 정보 조회 응답
 * </ul>
 */
public class OrganizationDto {

    /**
     * 조직(부점) 정보 조회 응답 DTO
     *
     * <p>{@link CorgnI} 엔티티에서 클라이언트에 필요한 정보만 선택하여 반환합니다.
     *
     * <p>주요 활용처:
     *
     * <ul>
     *   <li>프론트엔드의 조직 선택 드롭다운/트리 구성
     *   <li>사용자 목록 조회 시 부점코드 입력
     *   <li>신청서 결재선 구성 시 조직 조회
     * </ul>
     *
     * <p>{@link #fromEntity(CorgnI)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "OrganizationResponse")
    public static class Response {
        /**
         * 조직코드 (PRLM_OGZ_C_CONE)
         *
         * <p>부점 고유 식별자. 사용자({@link com.kdb.it.common.iam.entity.CuserI})의 {@code bbrC} 필드와 연결됩니다.
         */
        @Schema(description = "조직코드")
        private String prlmOgzCCone;

        /**
         * 상위조직코드 (PRLM_HRK_OGZ_C_CONE)
         *
         * <p>계층 구조에서 상위 조직의 코드입니다. 최상위 조직인 경우 null 또는 자기 자신의 코드일 수 있습니다.
         */
        @Schema(description = "상위조직코드")
        private String prlmHrkOgzCCone;

        /**
         * 부점명 (BBR_NM)
         *
         * <p>조직의 한국어 명칭 (예: "서울영업부", "IT전략부").
         */
        @Schema(description = "부점명")
        private String bbrNm;

        /**
         * {@link CorgnI} 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param corgnI 변환할 CorgnI 엔티티
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(CorgnI corgnI) {
            return Response.builder()
                    .prlmOgzCCone(corgnI.getPrlmOgzCCone()) // 조직코드
                    .prlmHrkOgzCCone(corgnI.getPrlmHrkOgzCCone()) // 상위조직코드
                    .bbrNm(corgnI.getBbrNm()) // 부점명
                    .build();
        }

        /**
         * 조직 목록 조회 전용 프로젝션({@link OrganizationRepository.OrganizationListView})으로부터 응답 DTO를
         * 생성합니다. {@link #fromEntity(CorgnI)}와 동일한 3개 필드를 동일한 순서로 매핑합니다.
         *
         * @param row 조직 목록 프로젝션 행
         * @return 변환된 응답 DTO
         */
        public static Response fromView(OrganizationRepository.OrganizationListView row) {
            return Response.builder()
                    .prlmOgzCCone(row.getPrlmOgzCCone()) // 조직코드
                    .prlmHrkOgzCCone(row.getPrlmHrkOgzCCone()) // 상위조직코드
                    .bbrNm(row.getBbrNm()) // 부점명
                    .build();
        }
    }
}
