package com.kdb.it.common.code.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CcodemResponseRow;
import com.kdb.it.common.i18n.model.TranslationColumns;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공통코드(Ccodem) 관련 DTO 클래스 모음 */
public class CodeDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CodeDto.CreateRequest", description = "공통코드 생성 요청")
    public static class CreateRequest {

        @Schema(description = "코드ID", example = "PRJ_TP")
        private String cId;

        @Schema(description = "코드값", example = "001")
        private String cdva;

        @Schema(description = "코드값명", example = "개발비")
        private String cdvaNm;

        @Schema(description = "코드명 (구 CDVA)", example = "신규개발")
        private String cNm;

        @Schema(description = "코드값설명", example = "사업유형")
        private String cdvaDes;

        @Schema(description = "코드값상세 (구 C_NM)", example = "USD")
        private String cdvaDtl;

        @Schema(description = "코드값상세코드 (예: 237-0700)")
        private String cdvaDtlC;

        @Schema(description = "코드타입 (구 CTT_TP)")
        private String cTp;

        @Schema(description = "코드타입설명")
        private String cTpDes;

        @Schema(description = "상위코드 {C_ID}_{CDVA}")
        private String hrkC;

        @Schema(description = "코드순서", example = "1")
        private Integer cSqn;

        @Schema(description = "시작일자 (YYYYMMDD)", example = "20260101")
        private String sttDt;

        @Schema(description = "종료일자 (YYYYMMDD)", example = "20991231")
        private String endDt;

        public Ccodem toEntity() {
            return Ccodem.builder()
                    .cId(this.cId)
                    .cdva(this.cdva)
                    .cdvaNm(this.cdvaNm)
                    .cNm(this.cNm)
                    .cdvaDes(this.cdvaDes)
                    .cdvaDtl(this.cdvaDtl)
                    .cdvaDtlC(this.cdvaDtlC)
                    .cTp(this.cTp)
                    .cTpDes(this.cTpDes)
                    .hrkC(this.hrkC)
                    .cSqn(this.cSqn)
                    .sttDt(this.sttDt)
                    .endDt(this.endDt)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "CodeDto.UpdateRequest", description = "공통코드 수정 요청")
    public static class UpdateRequest {

        @Schema(description = "코드명")
        private String cNm;

        @Schema(description = "코드값명")
        private String cdvaNm;

        @Schema(description = "코드값설명")
        private String cdvaDes;

        @Schema(description = "코드값상세")
        private String cdvaDtl;

        @Schema(description = "코드값상세코드 (예: 237-0700)")
        private String cdvaDtlC;

        @Schema(description = "코드타입")
        private String cTp;

        @Schema(description = "코드타입설명")
        private String cTpDes;

        @Schema(description = "상위코드")
        private String hrkC;

        @Schema(description = "코드순서")
        private Integer cSqn;

        @Schema(description = "종료일자 (YYYYMMDD)")
        private String endDt;
    }

    @Getter
    @Setter
    @Builder
    @Schema(name = "CodeDto.Response", description = "공통코드 조회 응답")
    public static class Response {

        @JsonProperty("cId")
        @Schema(description = "코드ID")
        private String cId;

        @Schema(description = "코드값")
        private String cdva;

        @Schema(description = "코드값명")
        private String cdvaNm;

        @JsonProperty("cNm")
        @Schema(description = "코드명 (구 CDVA)")
        private String cNm;

        @JsonProperty("cdvaDes")
        @Schema(description = "코드값설명")
        private String cdvaDes;

        @Schema(description = "코드값상세 (구 C_NM)")
        private String cdvaDtl;

        @Schema(description = "코드값상세코드 (예: 237-0700)")
        private String cdvaDtlC;

        @JsonProperty("cTp")
        @Schema(description = "코드타입")
        private String cTp;

        @JsonProperty("cTpDes")
        @Schema(description = "코드타입설명")
        private String cTpDes;

        @Schema(description = "상위코드")
        private String hrkC;

        @JsonProperty("cSqn")
        @Schema(description = "코드순서")
        private Integer cSqn;

        @Schema(description = "시작일자 (YYYYMMDD)")
        private String sttDt;

        @Schema(description = "종료일자 (YYYYMMDD)")
        private String endDt;

        @Schema(description = "삭제여부")
        private String delYn;

        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        @Schema(description = "마지막수정시간")
        private LocalDateTime lstChgDtm;

        @Schema(description = "마지막수정자")
        private String lstChgUsid;

        public static Response fromEntity(Ccodem ccodem) {
            if (ccodem == null) return null;
            return Response.builder()
                    .cId(ccodem.getCId())
                    .cdva(ccodem.getCdva())
                    .cdvaNm(ccodem.getCdvaNm())
                    .cNm(ccodem.getCNm())
                    .cdvaDes(ccodem.getCdvaDes())
                    .cdvaDtl(ccodem.getCdvaDtl())
                    .cdvaDtlC(ccodem.getCdvaDtlC())
                    .cTp(ccodem.getCTp())
                    .cTpDes(ccodem.getCTpDes())
                    .hrkC(ccodem.getHrkC())
                    .cSqn(ccodem.getCSqn())
                    .sttDt(ccodem.getSttDt())
                    .endDt(ccodem.getEndDt())
                    .delYn(ccodem.getDelYn())
                    .fstEnrDtm(ccodem.getFstEnrDtm())
                    .fstEnrUsid(ccodem.getFstEnrUsid())
                    .lstChgDtm(ccodem.getLstChgDtm())
                    .lstChgUsid(ccodem.getLstChgUsid())
                    .build();
        }

        /**
         * REST 응답 전용 경량 프로젝션({@link CcodemResponseRow})으로부터 응답 DTO를 생성합니다. {@link
         * #fromEntity(Ccodem)}와 동일한 18개 필드를 동일한 순서로 매핑합니다.
         *
         * @param row 공통코드 응답 프로젝션 행
         * @return 응답 DTO (row가 null이면 null)
         */
        public static Response fromRow(CcodemResponseRow row) {
            return fromRow(row, Map.of());
        }

        /** 원본 프로젝션에 필드별 번역을 덮어써 사용자 표시 DTO를 생성합니다. */
        public static Response fromRow(CcodemResponseRow row, Map<String, String> translations) {
            if (row == null) return null;
            return Response.builder()
                    .cId(row.cId())
                    .cdva(row.cdva())
                    .cdvaNm(translations.getOrDefault(TranslationColumns.CDVA_NM, row.cdvaNm()))
                    .cNm(translations.getOrDefault(TranslationColumns.CO_C_NM, row.cNm()))
                    .cdvaDes(
                            translations.getOrDefault(
                                    TranslationColumns.CO_CDVA_ABV_NM, row.cdvaDes()))
                    .cdvaDtl(
                            translations.getOrDefault(
                                    TranslationColumns.CO_CDVA_SPS, row.cdvaDtl()))
                    .cdvaDtlC(row.cdvaDtlC())
                    .cTp(row.cTp())
                    .cTpDes(
                            translations.getOrDefault(
                                    TranslationColumns.CO_C_INTN_CONE, row.cTpDes()))
                    .hrkC(row.hrkC())
                    .cSqn(row.cSqn())
                    .sttDt(row.sttDt())
                    .endDt(row.endDt())
                    .delYn(row.delYn())
                    .fstEnrDtm(row.fstEnrDtm())
                    .fstEnrUsid(row.fstEnrUsid())
                    .lstChgDtm(row.lstChgDtm())
                    .lstChgUsid(row.lstChgUsid())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(name = "CodeDto.BudgetPeriodResponse", description = "예산 신청 기간 조회 응답")
    public static class BudgetPeriodResponse {
        @Schema(description = "예산 신청기간 시작일자", example = "2026-04-15")
        private String startDate;

        @Schema(description = "예산 신청기간 종료일자", example = "2026-12-31")
        private String endDate;
    }
}
