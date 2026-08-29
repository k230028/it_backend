package com.kdb.it.domain.migration.commondata.dto;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 공통 데이터 이관(개발→운영) API 계약입니다. 행 record는 export 응답과 업로드 요청이 공유합니다.
 *
 * <p>행 record에는 필드 레벨 Bean Validation을 두지 않습니다. {@code @Valid @RequestBody}가 필드 검증을 먼저 통과시켜 버리면
 * {@link com.kdb.it.domain.migration.commondata.CommonDataMigrationPlanner}가 시도조차 못 하고 {@code
 * MethodArgumentNotValidException}(0-기반 인덱스, 첫 위반만 보고)으로 끝나 "메뉴 시트 N행: ..." 형식의 다건 집계 오류 메시지가 무력화된다.
 * 필수값·허용값 검증은 전부 {@code CommonDataMigrationPlanner}가 담당한다.
 */
public final class CommonDataMigrationDto {

    private CommonDataMigrationDto() {}

    /** 메뉴(TPRMPP_CMENUM) 한 행입니다. excelRow는 업로드 파일의 행 번호이며 export 시 0입니다. */
    public record MenuRow(
            int excelRow,
            String mnuId,
            String hrkMnuId,
            String mnuNm,
            String mnuTpC,
            String imkNm,
            String srePth,
            Integer mnuSotSqnSno,
            String hidYn,
            Integer mnuDep,
            String whlMnuPth) {}

    /** 메뉴권한(TPRMPP_CMENUA) 한 행입니다. */
    public record MenuAuthRow(int excelRow, String mnuId, String athId) {}

    /** 경로 카탈로그(TPRMPP_CMENUD) 한 행입니다. */
    public record RouteRow(int excelRow, String srePth, String sreMnuNm, String useYn, String rmk) {}

    /** 공통코드(TPRMPP_CCODEM) 한 행입니다. 필드명은 Ccodem 엔티티의 Java 필드명을 따릅니다. */
    public record CodeRow(
            int excelRow,
            String cId,
            String cdva,
            String sttDt,
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
            int excelRow, String tcIdCone, String tcColNm, String dttLanC, String tcDes, String dttNm) {}

    /** dry-run과 확정 반영이 공유하는 업로드 요청입니다. 시트가 비어 있어도 목록 자체는 필수입니다. */
    public record Request(
            @NotNull List<MenuRow> menus,
            @NotNull List<MenuAuthRow> menuAuths,
            @NotNull List<RouteRow> routes,
            @NotNull List<CodeRow> codes,
            @NotNull List<TranslationRow> translations) {}

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
