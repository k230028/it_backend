package com.kdb.it.common.code.dto;

import com.kdb.it.common.code.entity.Ccodem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공통코드(Ccodem) 관련 DTO 클래스 모음
 *
 * <p>
 * 공통코드(TAAABB_CCODEM) 엔티티의 생성, 수정, 조회에 사용되는
 * Request/Response DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <p>
 * 포함된 DTO:
 * </p>
 * <ul>
 * <li>{@link CreateRequest}: 공통코드 생성 요청</li>
 * <li>{@link UpdateRequest}: 공통코드 수정 요청</li>
 * <li>{@link Response}: 공통코드 조회 응답 (BaseEntity 감사 필드 포함)</li>
 * </ul>
 */
public class CodeDto {

    /**
     * 공통코드 생성 요청 DTO
     *
     * <p>
     * 신규 공통코드를 등록할 때 사용합니다.
     * {@link #toEntity()} 메서드로 {@link Ccodem} 엔티티로 변환합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CodeDto.CreateRequest", description = "공통코드 생성 요청")
    public static class CreateRequest {
        /** 코드ID (PK, 고유 식별자) */
        @Schema(description = "코드ID", example = "CD001")
        private String cdId;

        /** 코드명 (코드의 표시 이름) */
        @Schema(description = "코드명", example = "프로젝트유형")
        private String cdNm;

        /** 코드값 (실제 사용되는 값) */
        @Schema(description = "코드값", example = "신규개발")
        private String cdva;

        /** 코드설명 (코드에 대한 상세 설명) */
        @Schema(description = "코드설명", example = "새로운 시스템을 개발하는 프로젝트")
        private String cdDes;

        /** 코드값구분 (코드 그룹 분류) */
        @Schema(description = "코드값구분", example = "PRJ_TP")
        private String cttTp;

        /** 코드값구분설명 (코드 그룹에 대한 설명) */
        @Schema(description = "코드값구분설명", example = "프로젝트 유형 구분")
        private String cttTpDes;

        /** 코드순서 (동일 그룹 내 표시 순서) */
        @Schema(description = "코드순서", example = "1")
        private Integer cdSqn;

        /** 시작일자 (코드 유효 시작일) */
        @Schema(description = "시작일자", example = "2026-01-01")
        private LocalDate sttDt;

        /** 종료일자 (코드 유효 종료일) */
        @Schema(description = "종료일자", example = "2099-12-31")
        private LocalDate endDt;

        /**
         * 요청 DTO를 {@link Ccodem} 엔티티로 변환하는 메서드
         *
         * @return 변환된 Ccodem 엔티티
         */
        public Ccodem toEntity() {
            return Ccodem.builder()
                    .cId(this.cdId)
                    .cNm(this.cdNm)
                    .cdva(this.cdva)
                    .cDes(this.cdDes)
                    .cttTp(this.cttTp)
                    .cttTpDes(this.cttTpDes)
                    .cSqn(this.cdSqn)
                    .sttDt(this.sttDt)
                    .endDt(this.endDt)
                    .build();
        }
    }

    /**
     * 공통코드 수정 요청 DTO
     *
     * <p>
     * 기존 공통코드를 수정할 때 사용합니다.
     * {@code cdId}는 URL PathVariable로 받으므로 이 DTO에는 포함하지 않습니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CodeDto.UpdateRequest", description = "공통코드 수정 요청")
    public static class UpdateRequest {
        /** 코드명 */
        @Schema(description = "코드명", example = "프로젝트유형")
        private String cdNm;

        /** 코드값 */
        @Schema(description = "코드값", example = "신규개발")
        private String cdva;

        /** 코드설명 */
        @Schema(description = "코드설명", example = "새로운 시스템을 개발하는 프로젝트")
        private String cdDes;

        /** 코드값구분 */
        @Schema(description = "코드값구분", example = "PRJ_TP")
        private String cttTp;

        /** 코드값구분설명 */
        @Schema(description = "코드값구분설명", example = "프로젝트 유형 구분")
        private String cttTpDes;

        /** 코드순서 */
        @Schema(description = "코드순서", example = "1")
        private Integer cdSqn;

        /** 시작일자 */
        @Schema(description = "시작일자", example = "2026-01-01")
        private LocalDate sttDt;

        /** 종료일자 */
        @Schema(description = "종료일자", example = "2099-12-31")
        private LocalDate endDt;
    }

    /**
     * 공통코드 조회 응답 DTO
     *
     * <p>
     * {@link Ccodem} 엔티티의 모든 필드와 {@link com.kdb.it.domain.entity.BaseEntity}의
     * 감사(Auditing) 필드를 포함합니다.
     * {@link #fromEntity(Ccodem)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
     * </p>
     */
    @Getter
    @Setter
    @Builder
    @Schema(name = "CodeDto.Response", description = "공통코드 조회 응답")
    public static class Response {
        /** 코드ID (PK) */
        @Schema(description = "코드ID")
        private String cdId;

        /** 코드명 */
        @Schema(description = "코드명")
        private String cdNm;

        /** 코드값 */
        @Schema(description = "코드값")
        private String cdva;

        /** 코드설명 */
        @Schema(description = "코드설명")
        private String cdDes;

        /** 코드값구분 */
        @Schema(description = "코드값구분")
        private String cttTp;

        /** 코드값구분설명 */
        @Schema(description = "코드값구분설명")
        private String cttTpDes;

        /** 코드순서 */
        @Schema(description = "코드순서")
        private Integer cdSqn;

        /** 시작일자 */
        @Schema(description = "시작일자")
        private LocalDate sttDt;

        /** 종료일자 */
        @Schema(description = "종료일자")
        private LocalDate endDt;

        /** 삭제여부 (Soft Delete, "Y": 삭제됨, "N": 정상) */
        @Schema(description = "삭제여부")
        private String delYn;

        /** 최초 등록 일시 (JPA Auditing) */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초 등록자 사번 (JPA Auditing) */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /** 마지막 수정 일시 (JPA Auditing) */
        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        /** 마지막 수정자 사번 (JPA Auditing) */
        @Schema(description = "마지막수정자")
        private String lstChgUsid;

        /**
         * {@link Ccodem} 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param ccodem 변환할 Ccodem 엔티티 (null이면 null 반환)
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(Ccodem ccodem) {
            if (ccodem == null)
                return null;
            return Response.builder()
                    .cdId(ccodem.getCdId())
                    .cdNm(ccodem.getCdNm())
                    .cdva(ccodem.getCdva())
                    .cdDes(ccodem.getCdDes())
                    .cttTp(ccodem.getCttTp())
                    .cttTpDes(ccodem.getCttTpDes())
                    .cdSqn(ccodem.getCdSqn())
                    .sttDt(ccodem.getSttDt())
                    .endDt(ccodem.getEndDt())
                    .delYn(ccodem.getDelYn())
                    .fstEnrDtm(ccodem.getFstEnrDtm())
                    .fstEnrUsid(ccodem.getFstEnrUsid())
                    .lstChgDtm(ccodem.getLstChgDtm())
                    .lstChgUsid(ccodem.getLstChgUsid())
                    .build();
        }
    }

    /**
     * 예산 신청 기간 응답 DTO
     */
    @Getter
    @Builder
    @Schema(name = "CodeDto.BudgetPeriodResponse", description = "예산 신청 기간 조회 응답")
    public static class BudgetPeriodResponse {
        /** 신청 기간 시작일자 (yyyy-MM-dd) */
        @Schema(description = "예산 신청기간 시작일자", example = "2026-04-15")
        private String startDate;

        /** 신청 기간 종료일자 (yyyy-MM-dd) */
        @Schema(description = "예산 신청기간 종료일자", example = "2026-12-31")
        private String endDate;
    }
}
