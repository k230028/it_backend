package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 진단 생성과 셀 읽기의 기계적인 부분을 모읍니다.
 *
 * <p>{@link MigrationValidator}가 "무엇이 잘못되었는지"를 판단하고, 이 클래스가 "그 판단을 어떤 모양으로 담아 돌려주는지"를 맡습니다. 검증 규칙과
 * 조립 코드를 한 파일에 두면 규칙을 읽을 때 조립 보일러플레이트를 계속 넘겨야 합니다.
 *
 * <p><b>보정값 우선</b>이 이 클래스의 불변식입니다 — {@link #cell}은 예외 없이 보정값을 먼저 봅니다. 어댑터는 보정값이 반영된 값을 저장하므로, 검증이
 * 원본 셀을 읽으면 사용자가 고친 값이 검증을 우회합니다. "사용자가 고르기 전의 값"이 필요한 곳(후보 재산출)만 {@link #rawCell}을 씁니다.
 */
final class MigrationDiagnostics {

    private MigrationDiagnostics() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 보정값 조회 키를 만듭니다.
     *
     * @param sheet 시트 종류
     * @param excelRow 엑셀 행 번호
     * @param column 정규 컬럼 id
     * @return 파이프로 이은 키
     */
    static String overrideKey(SheetKind sheet, int excelRow, String column) {
        return sheet.name() + "|" + excelRow + "|" + column;
    }

    /** 보정값이 있으면 그 값을, 없으면 원본 셀 값을 반환합니다. null은 빈 문자열로 접습니다. */
    static String cell(
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            MigrationDto.SheetPayload sheet) {
        String override = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
        if (override != null) {
            return override;
        }
        return rawCell(row, column);
    }

    /** 보정값을 보지 않는 원본 셀 값입니다. 후보 재산출처럼 "사용자가 고르기 전의 값"이 필요한 곳에서만 씁니다. */
    static String rawCell(MigrationDto.NormalizedRow row, String column) {
        String value = row.cells().get(column);
        return value == null ? "" : value;
    }

    /** 그 셀에 걸린 보정값을 반환합니다. 없으면 null. */
    static String overrideOf(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides) {
        return overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
    }

    /** BLOCKER 진단을 만듭니다. 하나라도 남으면 반영이 거부됩니다. */
    static MigrationDto.CellDiagnostic blocker(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            String code,
            String message,
            List<MigrationDto.Candidate> candidates) {
        return new MigrationDto.CellDiagnostic(
                sheet.kind(),
                row.excelRow(),
                column,
                code,
                MigrationDto.Severity.BLOCKER,
                message,
                candidates);
    }

    /** WARNING 진단을 만듭니다. 반영을 막지 않습니다. */
    static MigrationDto.CellDiagnostic warning(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            String code,
            String message) {
        return new MigrationDto.CellDiagnostic(
                sheet.kind(),
                row.excelRow(),
                column,
                code,
                MigrationDto.Severity.WARNING,
                message,
                List.of());
    }

    /**
     * 비목 후보 목록을 만듭니다.
     *
     * @param capitalOnly true면 자본예산 계열 비목만 (품목 비목 보정 후보)
     */
    static List<MigrationDto.Candidate> candidatesOfIoe(
            MigrationLookupIndex index, boolean capitalOnly) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        index.ioeCodeByName()
                .forEach(
                        (name, code) -> {
                            if (!capitalOnly || MigrationIoeCodes.isCapital(code)) {
                                out.add(new MigrationDto.Candidate(code, name));
                            }
                        });
        return out;
    }

    /** 등록된 통화 후보 목록입니다. 닫힌 소집합이라 전부 후보로 냅니다. */
    static List<MigrationDto.Candidate> candidatesOfCurrency(MigrationLookupIndex index) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        index.xcrByCurrency()
                .keySet()
                .forEach(currency -> out.add(new MigrationDto.Candidate(currency, currency)));
        return out;
    }

    /**
     * 코드 카탈로그 전체를 후보로 냅니다. 코드셋이 닫혀 있어 목록을 그대로 보여주는 것이 가장 정확합니다.
     *
     * @param catalog 코드 카탈로그
     * @param byName 카탈로그가 이름 → 코드 방향인지 여부
     */
    static List<MigrationDto.Candidate> candidatesOfCatalog(
            Map<String, String> catalog, boolean byName) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        catalog.forEach(
                (key, value) ->
                        out.add(
                                byName
                                        ? new MigrationDto.Candidate(value, key)
                                        : new MigrationDto.Candidate(key, value)));
        return out;
    }

    /**
     * 미해석 문구를 만듭니다. 유사도 제안이 붙었을 때도 "찾았다"로 읽히지 않도록 못 찾았다는 사실을 먼저 말합니다.
     *
     * @param notFound 못 찾았다는 문장
     * @param resolution 해석 결과 (유사도 제안 여부 판정용)
     */
    static String unresolvedMessage(String notFound, OrgIdentityResolver.Resolution resolution) {
        return resolution.candidates().isEmpty()
                ? notFound + " 직접 확인해 주세요."
                : notFound + " 이름이 비슷한 후보를 제시했으니 맞는 값을 선택하거나 원본을 고쳐 주세요.";
    }
}
