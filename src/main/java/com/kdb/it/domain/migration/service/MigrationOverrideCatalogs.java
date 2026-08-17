package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 미리보기에 그릴 보정 전용 컬럼 카탈로그를 만듭니다 (MIG-10·MIG-03).
 *
 * <p>보정 드롭다운의 선택지는 원래 <b>그 셀에 걸린 진단의 후보</b>에서만 나왔습니다. 그래서 "값이 틀리지 않았지만 다른 값으로 바꾸고 싶은" 보정은 화면에서 도달할
 * 수 없었습니다 — 자본예산 품목 비목이 그 경우로, 기본값이 정상이라 진단이 붙지 않아 감리·국외·SW라이선스로 바꿀 수단이 없었습니다.
 *
 * <p>카탈로그는 <b>어떤 컬럼을 그릴지</b>도 정합니다. 해당 시트를 올리지 않았으면 목록이 비어 컬럼 자체가 나타나지 않습니다 — 쓸 수 없는 컬럼을 그리면 항상 빈
 * 드롭다운이 되고, 그것은 "고르라"고 해놓고 고를 수단이 없는 상태입니다.
 *
 * <p>선택지가 <b>행마다 다른</b> 컬럼은 후보를 비워 컬럼만 선언하고 실제 후보는 그 행의 진단이 싣습니다 — 위임예산 담당자가 그 경우로, 부점마다 소속 사용자가
 * 다릅니다.
 */
final class MigrationOverrideCatalogs {

    private MigrationOverrideCatalogs() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 이번 요청의 시트 구성에 맞는 카탈로그를 모읍니다.
     *
     * @param sheets 올린 시트 목록
     * @param index 코드 조회 인덱스
     * @return 컬럼별 카탈로그. 해당 시트가 없으면 빈 목록
     */
    static List<MigrationDto.ColumnCatalog> of(
            List<MigrationDto.SheetPayload> sheets, MigrationLookupIndex index) {
        List<MigrationDto.ColumnCatalog> catalogs = new ArrayList<>(capitalIoe(sheets, index));
        if (contains(sheets, SheetKind.DELEGATED_BUDGET)) {
            catalogs.add(
                    new MigrationDto.ColumnCatalog(
                            SheetKind.DELEGATED_BUDGET,
                            MigrationColumns.DELEGATED_OWNER_OVERRIDE,
                            List.of()));
        }
        return List.copyOf(catalogs);
    }

    /** 자본예산 품목 비목 보정의 선택지입니다. 세 컬럼이 같은 후보 목록을 공유합니다. */
    private static List<MigrationDto.ColumnCatalog> capitalIoe(
            List<MigrationDto.SheetPayload> sheets, MigrationLookupIndex index) {
        if (!contains(sheets, SheetKind.CAPITAL_PROJECT)) {
            return List.of();
        }
        // 드롭다운 순서를 고정하려고 코드 오름차순으로 정렬한다 — 진단 후보는 조회 순서를 그대로
        // 쓰지만, 이 카탈로그는 화면에 상시 노출되므로 재조회마다 순서가 흔들리면 눈에 띈다.
        List<MigrationDto.Candidate> candidates =
                MigrationDiagnostics.candidatesOfIoe(index, true).stream()
                        .sorted(Comparator.comparing(MigrationDto.Candidate::code))
                        .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        return MigrationColumns.CAPITAL_IOE_OVERRIDES.stream()
                .map(
                        column ->
                                new MigrationDto.ColumnCatalog(
                                        SheetKind.CAPITAL_PROJECT, column, candidates))
                .toList();
    }

    private static boolean contains(List<MigrationDto.SheetPayload> sheets, SheetKind kind) {
        return sheets.stream().anyMatch(sheet -> sheet.kind() == kind);
    }
}
