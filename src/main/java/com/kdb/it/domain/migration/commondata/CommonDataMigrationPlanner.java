package com.kdb.it.domain.migration.commondata;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Component;

/**
 * 공통 데이터 이관 요청을 저장 없이 검증하고 테이블별 추가/갱신/부활 건수로 분류합니다.
 *
 * <p>Spring 빈으로만 등록될 뿐 조회·저장에는 관여하지 않습니다. Task 3의 서비스가 dry-run과 확정 반영(commit) 양쪽에서 같은 {@link
 * #plan(CommonDataMigrationDto.Request, Snapshot)}을 호출해 동일한 검증 결과를 얻습니다.
 */
@Component
public class CommonDataMigrationPlanner {

    private static final String SHEET_MENU = "메뉴";
    private static final String SHEET_MENU_AUTH = "메뉴권한";
    private static final String SHEET_ROUTE = "경로";
    private static final String SHEET_CODE = "공통코드";
    private static final String SHEET_TRANSLATION = "다국어";

    /** 복합키 필드를 하나의 맵 키 문자열로 합칠 때 쓰는 구분자입니다. 업무 데이터(ID·코드값)에 나타나지 않는 :: 문자열을 씁니다. */
    private static final String KEY_SEP = "::";

    private static final int MIN_MENU_DEPTH = 1;
    private static final int MAX_MENU_DEPTH = 4;
    private static final int LANGUAGE_CODE_LENGTH = 2;
    private static final Set<String> MENU_TYPES = Set.of("GRP", "LNK", "PGE");
    private static final Set<String> YN = Set.of("Y", "N");

    /** 삭제 행을 포함한 현재 DB 상태 스냅샷입니다. 코드·번역은 파일이 참조하는 키 범위만 담습니다. */
    public record Snapshot(
            List<Cmenum> allMenus,
            List<Cmenua> allMenuAuths,
            List<Cmenud> allRoutes,
            List<Ccodem> codesForFileCIds,
            List<Clangm> translationsForFileKeys,
            Set<String> athIds) {}

    /** 검증·분류 결과입니다. errors가 비어 있지 않으면 확정 반영을 차단해야 합니다. */
    public record Plan(
            CommonDataMigrationDto.TableSummary menus,
            CommonDataMigrationDto.TableSummary menuAuths,
            CommonDataMigrationDto.TableSummary routes,
            CommonDataMigrationDto.TableSummary codes,
            CommonDataMigrationDto.TableSummary translations,
            List<String> warnings,
            List<String> errors) {

        /** 메뉴→메뉴권한→경로→공통코드→다국어 순서로 테이블별 요약을 반환합니다. */
        public List<CommonDataMigrationDto.TableSummary> summaries() {
            return List.of(menus, menuAuths, routes, codes, translations);
        }
    }

    /**
     * 업로드 요청을 검증하고 테이블별 추가/갱신/부활 건수로 분류합니다. 저장은 하지 않습니다.
     *
     * @param request 업로드된 5개 시트 행 묶음
     * @param snapshot 호출자가 미리 조회해 넘긴 현재 DB 상태(삭제 행 포함)
     * @return 검증 결과(경고·오류)와 분류 결과를 담은 계획
     */
    public Plan plan(CommonDataMigrationDto.Request request, Snapshot snapshot) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, String> menuDelYnByKey = snapshotDelYnMap(snapshot.allMenus(), Cmenum::getMnuId);
        Map<String, String> menuAuthDelYnByKey =
                snapshotDelYnMap(snapshot.allMenuAuths(), a -> a.getMnuId() + KEY_SEP + a.getAthId());
        Map<String, String> routeDelYnByKey = snapshotDelYnMap(snapshot.allRoutes(), Cmenud::getSrePth);
        Map<String, String> codeDelYnByKey =
                snapshotDelYnMap(
                        snapshot.codesForFileCIds(),
                        c -> c.getCId() + KEY_SEP + c.getCdva() + KEY_SEP + c.getSttDt());
        Map<String, String> translationDelYnByKey =
                snapshotDelYnMap(
                        snapshot.translationsForFileKeys(),
                        t -> t.getTcIdCone() + KEY_SEP + t.getTcColNm() + KEY_SEP + t.getDttLanC());

        Set<String> activeSnapshotMenuIds = activeKeys(snapshot.allMenus(), Cmenum::getMnuId);
        Set<String> activeSnapshotRoutePaths = activeKeys(snapshot.allRoutes(), Cmenud::getSrePth);
        Map<String, Cmenum> activeSnapshotMenuById = activeEntitiesByKey(snapshot.allMenus(), Cmenum::getMnuId);

        Set<String> fileMenuIds = new HashSet<>();
        for (CommonDataMigrationDto.MenuRow row : request.menus()) {
            fileMenuIds.add(row.mnuId());
        }
        Set<String> fileRoutePaths = new HashSet<>();
        for (CommonDataMigrationDto.RouteRow row : request.routes()) {
            fileRoutePaths.add(row.srePth());
        }

        // 시트 내 PK 중복 검사 (5개 시트 각각)
        checkDuplicates(
                errors,
                SHEET_MENU,
                request.menus(),
                CommonDataMigrationDto.MenuRow::excelRow,
                CommonDataMigrationDto.MenuRow::mnuId,
                "메뉴ID가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_MENU_AUTH,
                request.menuAuths(),
                CommonDataMigrationDto.MenuAuthRow::excelRow,
                r -> r.mnuId() + KEY_SEP + r.athId(),
                "메뉴ID/자격등급ID 조합이 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_ROUTE,
                request.routes(),
                CommonDataMigrationDto.RouteRow::excelRow,
                CommonDataMigrationDto.RouteRow::srePth,
                "화면경로가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_CODE,
                request.codes(),
                CommonDataMigrationDto.CodeRow::excelRow,
                c -> c.cId() + KEY_SEP + c.cdva() + KEY_SEP + c.sttDt(),
                "코드 키가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_TRANSLATION,
                request.translations(),
                CommonDataMigrationDto.TranslationRow::excelRow,
                t -> t.tcIdCone() + KEY_SEP + t.tcColNm() + KEY_SEP + t.dttLanC(),
                "다국어 키가 중복되었습니다");

        // 시트별 값 검증 + 참조 무결성 + 경고
        for (CommonDataMigrationDto.MenuRow row : request.menus()) {
            validateMenuRow(row, fileMenuIds, activeSnapshotMenuIds, errors);
            warnAgainstSnapshot(row, activeSnapshotMenuById, warnings);
            warnMissingRouteForPage(row, fileRoutePaths, activeSnapshotRoutePaths, warnings);
        }
        for (CommonDataMigrationDto.MenuAuthRow row : request.menuAuths()) {
            validateMenuAuthRow(row, fileMenuIds, activeSnapshotMenuIds, snapshot.athIds(), errors);
        }
        for (CommonDataMigrationDto.RouteRow row : request.routes()) {
            validateRouteRow(row, errors);
        }
        for (CommonDataMigrationDto.CodeRow row : request.codes()) {
            validateCodeRow(row, errors);
        }
        for (CommonDataMigrationDto.TranslationRow row : request.translations()) {
            validateTranslationRow(row, fileMenuIds, activeSnapshotMenuIds, errors, warnings);
        }

        // 메뉴 행이 있는데 메뉴권한 행이 0건이면 오류
        if (!request.menus().isEmpty() && request.menuAuths().isEmpty()) {
            errors.add(message(SHEET_MENU_AUTH, 0, "메뉴 행이 있는데 메뉴권한 행이 없습니다."));
        }

        // 분류: 스냅샷에 같은 PK 없으면 added, 있고 delYn='Y'면 restored, 활성이면 updated
        CommonDataMigrationDto.TableSummary menus =
                classify(SHEET_MENU, request.menus(), CommonDataMigrationDto.MenuRow::mnuId, menuDelYnByKey);
        CommonDataMigrationDto.TableSummary menuAuths =
                classify(
                        SHEET_MENU_AUTH,
                        request.menuAuths(),
                        r -> r.mnuId() + KEY_SEP + r.athId(),
                        menuAuthDelYnByKey);
        CommonDataMigrationDto.TableSummary routes =
                classify(
                        SHEET_ROUTE, request.routes(), CommonDataMigrationDto.RouteRow::srePth, routeDelYnByKey);
        CommonDataMigrationDto.TableSummary codes =
                classify(
                        SHEET_CODE,
                        request.codes(),
                        c -> c.cId() + KEY_SEP + c.cdva() + KEY_SEP + c.sttDt(),
                        codeDelYnByKey);
        CommonDataMigrationDto.TableSummary translations =
                classify(
                        SHEET_TRANSLATION,
                        request.translations(),
                        t -> t.tcIdCone() + KEY_SEP + t.tcColNm() + KEY_SEP + t.dttLanC(),
                        translationDelYnByKey);

        return new Plan(menus, menuAuths, routes, codes, translations, warnings, errors);
    }

    /** 오류 ①②③④⑦: 메뉴 행 필수값·허용값·상위메뉴 참조를 검증합니다. */
    private static void validateMenuRow(
            CommonDataMigrationDto.MenuRow row,
            Set<String> fileMenuIds,
            Set<String> activeSnapshotMenuIds,
            List<String> errors) {
        requireNotBlank(errors, SHEET_MENU, row.excelRow(), "메뉴ID", row.mnuId());
        requireNotBlank(errors, SHEET_MENU, row.excelRow(), "메뉴명", row.mnuNm());
        requireNotBlank(errors, SHEET_MENU, row.excelRow(), "전체메뉴경로", row.whlMnuPth());

        if (row.mnuSotSqnSno() == null) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴정렬순서는 필수입니다."));
        }

        if (row.mnuTpC() == null || row.mnuTpC().isBlank()) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴유형은 필수입니다."));
        } else if (!MENU_TYPES.contains(row.mnuTpC())) {
            errors.add(
                    message(
                            SHEET_MENU, row.excelRow(), "메뉴유형은 GRP/LNK/PGE만 허용합니다 (값: " + row.mnuTpC() + ")"));
        }

        if (row.hidYn() == null || row.hidYn().isBlank()) {
            errors.add(message(SHEET_MENU, row.excelRow(), "숨김여부는 필수입니다."));
        } else if (!YN.contains(row.hidYn())) {
            errors.add(message(SHEET_MENU, row.excelRow(), "숨김여부는 Y/N만 허용합니다 (값: " + row.hidYn() + ")"));
        }

        if (row.mnuDep() == null) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴깊이는 필수입니다."));
        } else if (row.mnuDep() < MIN_MENU_DEPTH || row.mnuDep() > MAX_MENU_DEPTH) {
            errors.add(
                    message(SHEET_MENU, row.excelRow(), "메뉴깊이는 1~4만 허용합니다 (값: " + row.mnuDep() + ")"));
        }

        if (row.hrkMnuId() != null
                && !row.hrkMnuId().isBlank()
                && !fileMenuIds.contains(row.hrkMnuId())
                && !activeSnapshotMenuIds.contains(row.hrkMnuId())) {
            errors.add(
                    message(
                            SHEET_MENU, row.excelRow(), "상위메뉴ID가 존재하지 않습니다 (값: " + row.hrkMnuId() + ")"));
        }
    }

    /** 오류 ①⑧⑨: 메뉴권한 행 필수값과 메뉴·자격등급 참조를 검증합니다. */
    private static void validateMenuAuthRow(
            CommonDataMigrationDto.MenuAuthRow row,
            Set<String> fileMenuIds,
            Set<String> activeSnapshotMenuIds,
            Set<String> athIds,
            List<String> errors) {
        requireNotBlank(errors, SHEET_MENU_AUTH, row.excelRow(), "메뉴ID", row.mnuId());
        requireNotBlank(errors, SHEET_MENU_AUTH, row.excelRow(), "자격등급ID", row.athId());

        if (row.mnuId() != null
                && !row.mnuId().isBlank()
                && !fileMenuIds.contains(row.mnuId())
                && !activeSnapshotMenuIds.contains(row.mnuId())) {
            errors.add(
                    message(
                            SHEET_MENU_AUTH, row.excelRow(), "메뉴ID가 존재하지 않습니다 (값: " + row.mnuId() + ")"));
        }
        if (row.athId() != null && !row.athId().isBlank() && !athIds.contains(row.athId())) {
            errors.add(
                    message(
                            SHEET_MENU_AUTH,
                            row.excelRow(),
                            "자격등급ID가 존재하지 않습니다 (값: " + row.athId() + ")"));
        }
    }

    /** 오류 ①③: 경로 행 필수값과 사용여부 허용값을 검증합니다. */
    private static void validateRouteRow(CommonDataMigrationDto.RouteRow row, List<String> errors) {
        requireNotBlank(errors, SHEET_ROUTE, row.excelRow(), "화면경로", row.srePth());
        requireNotBlank(errors, SHEET_ROUTE, row.excelRow(), "화면메뉴명", row.sreMnuNm());

        if (row.useYn() == null || row.useYn().isBlank()) {
            errors.add(message(SHEET_ROUTE, row.excelRow(), "사용여부는 필수입니다."));
        } else if (!YN.contains(row.useYn())) {
            errors.add(message(SHEET_ROUTE, row.excelRow(), "사용여부는 Y/N만 허용합니다 (값: " + row.useYn() + ")"));
        }
    }

    /** 오류 ①: 공통코드 행 복합키 필수값을 검증합니다. */
    private static void validateCodeRow(CommonDataMigrationDto.CodeRow row, List<String> errors) {
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "공통코드ID", row.cId());
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "코드값ID", row.cdva());
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "시작일자", row.sttDt());
    }

    /** 오류 ①⑤⑥, 경고 ②: 다국어 행 필수값·언어코드 길이·구분명/컬럼명 조합·대상 메뉴 존재를 검증합니다. */
    private static void validateTranslationRow(
            CommonDataMigrationDto.TranslationRow row,
            Set<String> fileMenuIds,
            Set<String> activeSnapshotMenuIds,
            List<String> errors,
            List<String> warnings) {
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드ID내용", row.tcIdCone());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드컬럼명", row.tcColNm());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드설명", row.tcDes());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분명", row.dttNm());

        if (row.dttLanC() == null || row.dttLanC().isBlank()) {
            errors.add(message(SHEET_TRANSLATION, row.excelRow(), "언어코드는 필수입니다."));
        } else if (row.dttLanC().length() != LANGUAGE_CODE_LENGTH) {
            errors.add(
                    message(
                            SHEET_TRANSLATION, row.excelRow(), "언어코드는 2자여야 합니다 (값: " + row.dttLanC() + ")"));
        }

        if (row.dttNm() == null || row.dttNm().isBlank()) {
            return;
        }
        TranslationTarget target = findTarget(row.dttNm());
        if (target == null) {
            errors.add(
                    message(
                            SHEET_TRANSLATION,
                            row.excelRow(),
                            "다국어 구분명은 메뉴/공통코드만 허용합니다 (값: " + row.dttNm() + ")"));
            return;
        }
        if (row.tcColNm() != null && !row.tcColNm().isBlank()) {
            try {
                target.validateColumn(row.tcColNm());
            } catch (IllegalArgumentException e) {
                errors.add(message(SHEET_TRANSLATION, row.excelRow(), e.getMessage()));
            }
        }

        // 경고 ②: 다국어 대상키(구분명=메뉴)가 파일 메뉴에도 스냅샷 활성 메뉴에도 없음
        if (target == TranslationTarget.MENU
                && row.tcIdCone() != null
                && !row.tcIdCone().isBlank()
                && !fileMenuIds.contains(row.tcIdCone())
                && !activeSnapshotMenuIds.contains(row.tcIdCone())) {
            warnings.add(
                    message(
                            SHEET_TRANSLATION,
                            row.excelRow(),
                            "대상 메뉴가 존재하지 않습니다 (값: " + row.tcIdCone() + ")"));
        }
    }

    /** 구분명 문자열을 dbName() 비교로 TranslationTarget에 매핑합니다. 못 찾으면 null입니다. */
    private static TranslationTarget findTarget(String dttNm) {
        for (TranslationTarget target : TranslationTarget.values()) {
            if (target.dbName().equals(dttNm)) {
                return target;
            }
        }
        return null;
    }

    /** 경고 ①: 같은 메뉴ID의 스냅샷 활성 행과 화면경로 또는 메뉴명이 다르면 운영 독자 메뉴 덮어쓰기 가능성을 경고합니다. */
    private static void warnAgainstSnapshot(
            CommonDataMigrationDto.MenuRow row,
            Map<String, Cmenum> activeSnapshotMenuById,
            List<String> warnings) {
        Cmenum existing = activeSnapshotMenuById.get(row.mnuId());
        if (existing == null) {
            return;
        }
        boolean pathDiffers = !Objects.equals(existing.getSrePth(), row.srePth());
        boolean nameDiffers = !Objects.equals(existing.getMnuNm(), row.mnuNm());
        if (pathDiffers || nameDiffers) {
            warnings.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "운영 중인 메뉴와 이름 또는 경로가 다릅니다 (메뉴ID: " + row.mnuId() + ")"));
        }
    }

    /** 경고 ③: PGE 메뉴의 화면경로가 경로 시트에도 스냅샷 활성 카탈로그에도 없으면 경고합니다. */
    private static void warnMissingRouteForPage(
            CommonDataMigrationDto.MenuRow row,
            Set<String> fileRoutePaths,
            Set<String> activeSnapshotRoutePaths,
            List<String> warnings) {
        if (!"PGE".equals(row.mnuTpC()) || row.srePth() == null || row.srePth().isBlank()) {
            return;
        }
        if (!fileRoutePaths.contains(row.srePth()) && !activeSnapshotRoutePaths.contains(row.srePth())) {
            warnings.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "화면경로가 경로 카탈로그에 없습니다 (값: " + row.srePth() + ")"));
        }
    }

    /** 오류 ①(안전망): 값이 비어 있으면 오류를 추가합니다. 어노테이션 검증을 거치지 않고 호출되는 경로를 대비합니다. */
    private static void requireNotBlank(
            List<String> errors, String sheet, int excelRow, String fieldLabel, String value) {
        if (value == null || value.isBlank()) {
            errors.add(message(sheet, excelRow, fieldLabel + "은(는) 필수입니다."));
        }
    }

    /** 오류 ⑩: 시트 내에서 같은 키가 두 번째 이상 나타나면 그 행에 중복 오류를 추가합니다. */
    private static <T> void checkDuplicates(
            List<String> errors,
            String sheet,
            List<T> rows,
            ToIntFunction<T> rowNumberFn,
            Function<T, String> keyFn,
            String detail) {
        Set<String> seen = new HashSet<>();
        for (T row : rows) {
            String key = keyFn.apply(row);
            if (!seen.add(key)) {
                errors.add(message(sheet, rowNumberFn.applyAsInt(row), detail + " (값: " + key + ")"));
            }
        }
    }

    /** 분류 규칙: 스냅샷에 같은 키가 없으면 added, 있고 delYn='Y'면 restored, 그 외(활성)는 updated로 센다. */
    private static <T> CommonDataMigrationDto.TableSummary classify(
            String table, List<T> rows, Function<T, String> keyFn, Map<String, String> snapshotDelYnByKey) {
        int added = 0;
        int updated = 0;
        int restored = 0;
        for (T row : rows) {
            String key = keyFn.apply(row);
            if (!snapshotDelYnByKey.containsKey(key)) {
                added++;
            } else if ("Y".equals(snapshotDelYnByKey.get(key))) {
                restored++;
            } else {
                updated++;
            }
        }
        return new CommonDataMigrationDto.TableSummary(table, added, updated, restored);
    }

    private static <E extends BaseEntity> Map<String, String> snapshotDelYnMap(
            List<E> entities, Function<E, String> keyFn) {
        Map<String, String> map = new HashMap<>();
        for (E entity : entities) {
            map.put(keyFn.apply(entity), entity.getDelYn());
        }
        return map;
    }

    private static <E extends BaseEntity> Set<String> activeKeys(List<E> entities, Function<E, String> keyFn) {
        Set<String> keys = new HashSet<>();
        for (E entity : entities) {
            if (!"Y".equals(entity.getDelYn())) {
                keys.add(keyFn.apply(entity));
            }
        }
        return keys;
    }

    private static <E extends BaseEntity> Map<String, E> activeEntitiesByKey(
            List<E> entities, Function<E, String> keyFn) {
        Map<String, E> map = new HashMap<>();
        for (E entity : entities) {
            if (!"Y".equals(entity.getDelYn())) {
                map.put(keyFn.apply(entity), entity);
            }
        }
        return map;
    }

    private static String message(String sheet, int excelRow, String detail) {
        return sheet + " 시트 " + excelRow + "행: " + detail;
    }
}
