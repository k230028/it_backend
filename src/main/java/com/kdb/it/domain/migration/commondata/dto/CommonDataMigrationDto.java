package com.kdb.it.domain.migration.commondata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/** 공통 데이터 이관(개발→운영) API 계약입니다. 행 record는 export 응답과 업로드 요청이 공유합니다. */
public final class CommonDataMigrationDto {

    private CommonDataMigrationDto() {}

    /** 메뉴(TPRMPP_CMENUM) 한 행입니다. excelRow는 업로드 파일의 행 번호이며 export 시 0입니다. */
    public record MenuRow(
            int excelRow,
            @NotBlank String mnuId,
            String hrkMnuId,
            @NotBlank String mnuNm,
            @NotBlank String mnuTpC,
            String imkNm,
            String srePth,
            @NotNull Integer mnuSotSqnSno,
            @NotBlank String hidYn,
            @NotNull Integer mnuDep,
            @NotBlank String whlMnuPth) {}

    /** 메뉴권한(TPRMPP_CMENUA) 한 행입니다. */
    public record MenuAuthRow(int excelRow, @NotBlank String mnuId, @NotBlank String athId) {}

    /** 경로 카탈로그(TPRMPP_CMENUD) 한 행입니다. */
    public record RouteRow(
            int excelRow,
            @NotBlank String srePth,
            @NotBlank String sreMnuNm,
            @NotBlank String useYn,
            String rmk) {}

    /** 공통코드(TPRMPP_CCODEM) 한 행입니다. 필드명은 Ccodem 엔티티의 Java 필드명을 따릅니다. */
    public record CodeRow(
            int excelRow,
            @NotBlank String cId,
            @NotBlank String cdva,
            @NotBlank String sttDt,
            String endDt,
            String cNm,
            String cdvaNm,
            String cdvaDes,
            String cdvaDtl,
            String cdvaDtlC,
            String cTp,
            String cTpDes,
            String hrkC,
            Integer cSqn) {}

    /** 다국어(TPRMPP_CLANGM) 한 행입니다. */
    public record TranslationRow(
            int excelRow,
            @NotBlank String tcIdCone,
            @NotBlank String tcColNm,
            @NotBlank String dttLanC,
            @NotBlank String tcDes,
            @NotBlank String dttNm) {}

    /** dry-run과 확정 반영이 공유하는 업로드 요청입니다. 시트가 비어 있어도 목록 자체는 필수입니다. */
    public record Request(
            @NotNull List<@Valid MenuRow> menus,
            @NotNull List<@Valid MenuAuthRow> menuAuths,
            @NotNull List<@Valid RouteRow> routes,
            @NotNull List<@Valid CodeRow> codes,
            @NotNull List<@Valid TranslationRow> translations) {}

    /** 5개 테이블 전량 내보내기 응답입니다. */
    public record ExportResponse(
            List<MenuRow> menus,
            List<MenuAuthRow> menuAuths,
            List<RouteRow> routes,
            List<CodeRow> codes,
            List<TranslationRow> translations) {}

    /** 테이블별 반영(예정) 건수 요약입니다. table은 한국어 시트명(메뉴/메뉴권한/경로/공통코드/다국어)입니다. */
    public record TableSummary(String table, int added, int updated, int restored) {}

    /** dry-run·확정 반영 결과입니다. errors가 비어 있지 않으면 확정 반영은 거부됩니다. */
    public record Response(
            boolean committed,
            List<TableSummary> summaries,
            List<String> warnings,
            List<String> errors) {

        /** 커밋 후 시퀀스 동기화 경고를 덧붙인 사본을 돌려줍니다. */
        public Response withWarning(String warning) {
            List<String> merged = new ArrayList<>(warnings);
            merged.add(warning);
            return new Response(committed, summaries, merged, errors);
        }
    }
}
