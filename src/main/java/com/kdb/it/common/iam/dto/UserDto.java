package com.kdb.it.common.iam.dto;

import com.kdb.it.common.iam.entity.CuserI;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자(직원) 관련 DTO 클래스 모음
 *
 * <p>사용자 정보(TPRMPP_CUSERI) 조회에 사용되는 Response DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 *
 * <p>포함된 DTO:
 *
 * <ul>
 *   <li>{@link ListResponse}: 부점별 사용자 목록 조회 응답 (기본 정보)
 *   <li>{@link DetailResponse}: 사번별 사용자 상세 조회 응답 (연락처 등 추가 정보)
 * </ul>
 *
 * <p>부점명({@code bbrNm})은 {@link CuserI} 엔티티의 {@code @ManyToOne} 연관관계 ({@link
 * com.kdb.it.common.iam.entity.CorgnI})에서 가져오므로, {@code fromEntity()} 메서드에 별도 파라미터로 전달합니다.
 */
public class UserDto {

    /** 사용자 목록 조회에 필요한 컬럼만 담는 읽기 전용 행. */
    public record ListRow(
            String eno,
            String bbrC,
            String bbrNm,
            String temC,
            String temNm,
            String usrNm,
            String ptCNm) {}

    /** 사용자 상세 조회에 필요한 컬럼만 담는 읽기 전용 행. */
    public record DetailRow(
            String eno,
            String bbrC,
            String bbrNm,
            String temC,
            String temNm,
            String usrNm,
            String ptCNm,
            String etrMilAddrNm,
            String inleNo,
            String cpnTpn,
            String dtsDtlCone,
            String prlmHrkOgzCCone,
            String prlmHrkOgzCNm) {}

    /**
     * 사용자 목록 조회 응답 DTO
     *
     * <p>부점코드별 사용자 목록 조회 시 반환되는 기본 정보입니다. 상세 연락처 정보는 포함하지 않습니다.
     *
     * <p>{@link #fromEntity(CuserI, String)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "UserListResponse")
    public static class ListResponse {
        /** 행번(사번) - 직원 고유 식별자 */
        @Schema(description = "행번")
        private String eno;

        /** 부점코드 (BBR_C) - 소속 부점 코드 */
        @Schema(description = "부점코드")
        private String bbrC;

        /**
         * 부점명
         *
         * <p>{@link com.kdb.it.common.iam.entity.CorgnI#getBbrNm()}에서 조회됩니다. {@code CuserI.bbrC}
         * 필드로 연결된 {@code CorgnI}의 부점명입니다.
         */
        @Schema(description = "부점명")
        private String bbrNm;

        /** 팀코드 (TEM_C) */
        @Schema(description = "팀코드")
        private String temC;

        /** 팀명 (TEM_NM) */
        @Schema(description = "팀명")
        private String temNm;

        /** 사용자명 (USR_NM, 한국어 이름) */
        @Schema(description = "사용자명")
        private String usrNm;

        /** 직위명 (PT_C_NM, 예: "과장", "팀장", "부장") */
        @Schema(description = "직위명")
        private String ptCNm;

        /**
         * {@link CuserI} 엔티티를 목록 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * <p>부점명({@code bbrNm})은 {@link CuserI}가 직접 갖지 않고 연관관계에서 조회하므로, {@link
         * com.kdb.it.common.iam.entity.CuserI#getBbrNm()} 결과를 파라미터로 전달합니다.
         *
         * @param user 변환할 CuserI 엔티티
         * @param bbrNm 부점명 (CorgnI.bbrNm, CuserI.getBbrNm()으로 획득)
         * @return 변환된 목록 응답 DTO
         */
        public static ListResponse fromEntity(CuserI user, String bbrNm) {
            return ListResponse.builder()
                    .eno(user.getEno()) // 행번(사번)
                    .bbrC(user.getBbrC()) // 부점코드
                    .bbrNm(bbrNm) // 부점명 (연관관계에서 조회)
                    .temC(user.getTemC()) // 팀코드
                    .temNm(user.getTemNm()) // 팀명
                    .usrNm(user.getUsrNm()) // 사용자명
                    .ptCNm(user.getPtCNm()) // 직위명
                    .build();
        }

        /**
         * 읽기 전용 조회 행을 목록 응답으로 변환합니다.
         *
         * @param row 사용자 목록 조회 행
         * @return 사용자 목록 응답
         */
        public static ListResponse fromRow(ListRow row) {
            return ListResponse.builder()
                    .eno(row.eno())
                    .bbrC(row.bbrC())
                    .bbrNm(row.bbrNm())
                    .temC(row.temC())
                    .temNm(row.temNm())
                    .usrNm(row.usrNm())
                    .ptCNm(row.ptCNm())
                    .build();
        }
    }

    /**
     * 사용자 상세 조회 응답 DTO
     *
     * <p>사번별 상세 조회 시 반환됩니다. {@link ListResponse}의 기본 정보 외에 연락처(내선번호, 휴대폰번호)와 상세직무 정보를 추가로 포함합니다.
     *
     * <p>{@link #fromEntity(CuserI, String)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "UserDetailResponse")
    public static class DetailResponse {
        /** 행번(사번) */
        @Schema(description = "행번")
        private String eno;

        /** 부점코드 (BBR_C) - 소속 부점 코드 */
        @Schema(description = "부점코드")
        private String bbrC;

        /** 부점명 (연관관계의 CorgnI.bbrNm) */
        @Schema(description = "부점명")
        private String bbrNm;

        /** 팀코드 (TEM_C) */
        @Schema(description = "팀코드")
        private String temC;

        /** 팀명 */
        @Schema(description = "팀명")
        private String temNm;

        /** 사용자명 */
        @Schema(description = "사용자명")
        private String usrNm;

        /** 직위명 */
        @Schema(description = "직위명")
        private String ptCNm;

        /** 전자우편주소 (ETR_MIL_ADDR_NM) */
        @Schema(description = "전자우편주소")
        private String etrMilAddrNm;

        /** 내선번호 (INLE_NO, 사내 전화 번호) */
        @Schema(description = "내선번호")
        private String inleNo;

        /** 휴대폰번호 (CPN_TPN) */
        @Schema(description = "휴대폰번호")
        private String cpnTpn;

        /** 상세직무내용 (DTS_DTL_CONE, 담당 업무 설명) */
        @Schema(description = "상세직무내용")
        private String dtsDtlCone;

        /** 보유 자격등급명 (TPRMPP_CROLEI → TPRMPP_CAUTHI.QLF_GR_NM) */
        @Builder.Default
        @Schema(description = "보유 자격등급명 목록")
        private List<String> qlfGrNms = List.of();

        /** 상위조직코드 (PRLM_HRK_OGZ_C_CONE, 소속 부점의 상위 조직 코드) */
        @Schema(description = "상위조직코드")
        private String prlmHrkOgzCCone;

        /** 상위조직명 (상위 CorgnI의 bbrNm, 소속 부점의 상위 조직 명칭) */
        @Schema(description = "상위조직명")
        private String prlmHrkOgzCNm;

        /**
         * {@link CuserI} 엔티티를 상세 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param user 변환할 CuserI 엔티티
         * @param bbrNm 부점명 (CorgnI.bbrNm, CuserI.getBbrNm()으로 획득)
         * @return 변환된 상세 응답 DTO
         */
        public static DetailResponse fromEntity(CuserI user, String bbrNm) {
            return DetailResponse.builder()
                    .eno(user.getEno()) // 행번(사번)
                    .bbrC(user.getBbrC()) // 부점코드
                    .bbrNm(bbrNm) // 부점명 (연관관계에서 조회)
                    .temC(user.getTemC()) // 팀코드
                    .temNm(user.getTemNm()) // 팀명
                    .usrNm(user.getUsrNm()) // 사용자명
                    .ptCNm(user.getPtCNm()) // 직위명
                    .etrMilAddrNm(user.getEtrMilAddrNm()) // 전자우편주소
                    .inleNo(user.getInleNo()) // 내선번호
                    .cpnTpn(user.getCpnTpn()) // 휴대폰번호
                    .dtsDtlCone(user.getDtsDtlCone()) // 상세직무내용
                    .qlfGrNms(List.of())
                    .prlmHrkOgzCCone(user.getPrlmHrkOgzCCone()) // 상위조직코드
                    .prlmHrkOgzCNm(user.getPrlmHrkOgzCNm()) // 상위조직명
                    .build();
        }

        /**
         * 읽기 전용 조회 행을 상세 응답으로 변환합니다.
         *
         * @param row 사용자 상세 조회 행
         * @param qlfGrNms 활성 보유 자격등급명 목록
         * @return 사용자 상세 응답
         */
        public static DetailResponse fromRow(DetailRow row, List<String> qlfGrNms) {
            return DetailResponse.builder()
                    .eno(row.eno())
                    .bbrC(row.bbrC())
                    .bbrNm(row.bbrNm())
                    .temC(row.temC())
                    .temNm(row.temNm())
                    .usrNm(row.usrNm())
                    .ptCNm(row.ptCNm())
                    .etrMilAddrNm(row.etrMilAddrNm())
                    .inleNo(row.inleNo())
                    .cpnTpn(row.cpnTpn())
                    .dtsDtlCone(row.dtsDtlCone())
                    .qlfGrNms(List.copyOf(qlfGrNms))
                    .prlmHrkOgzCCone(row.prlmHrkOgzCCone())
                    .prlmHrkOgzCNm(row.prlmHrkOgzCNm())
                    .build();
        }
    }
}
