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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 공통 데이터 이관 요청을 저장 없이 검증하고 테이블별 추가/갱신/부활 건수로 분류합니다.
 *
 * <p>Spring 빈으로만 등록될 뿐 조회·저장에는 관여하지 않습니다. 이관 서비스가 dry-run과 확정 반영(commit) 양쪽에서 같은 {@link
 * #plan(CommonDataMigrationDto.Request, Snapshot)}을 호출해 동일한 검증 결과를 얻습니다.
 */
@Component
public class CommonDataMigrationPlanner {

    private static final String SHEET_MENU = "메뉴";
    private static final String SHEET_MENU_AUTH = "메뉴권한";
    private static final String SHEET_ROUTE = "경로";
    private static final String SHEET_CODE = "공통코드";
    private static final String SHEET_TRANSLATION = "다국어";

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

        Map<String, String> menuDelYnByKey =
                snapshotDelYnMap(snapshot.allMenus(), Cmenum::getMnuId);
        Map<List<String>, String> menuAuthDelYnByKey =
                snapshotDelYnMap(
                        snapshot.allMenuAuths(), a -> Arrays.asList(a.getMnuId(), a.getAthId()));
        Map<String, String> routeDelYnByKey =
                snapshotDelYnMap(snapshot.allRoutes(), Cmenud::getSrePth);
        Map<List<String>, String> codeDelYnByKey =
                snapshotDelYnMap(
                        snapshot.codesForFileCIds(),
                        c -> Arrays.asList(c.getCId(), c.getCdva(), c.getSttDt()));
        Map<List<String>, String> translationDelYnByKey =
                snapshotDelYnMap(
                        snapshot.translationsForFileKeys(),
                        t -> Arrays.asList(t.getTcIdCone(), t.getTcColNm(), t.getDttLanC()));

        Set<String> activeSnapshotMenuIds = activeKeys(snapshot.allMenus(), Cmenum::getMnuId);
        Set<String> activeSnapshotRoutePaths = activeKeys(snapshot.allRoutes(), Cmenud::getSrePth);
        Set<String> activeSnapshotCodeKeys =
                snapshot.codesForFileCIds().stream()
                        .filter(code -> !"Y".equals(code.getDelYn()))
                        .map(CommonDataMigrationPlanner::codeKey)
                        .collect(Collectors.toSet());
        Set<String> fileCodeKeys =
                request.codes().stream()
                        .map(CommonDataMigrationPlanner::codeKey)
                        .collect(Collectors.toSet());
        Map<String, Cmenum> activeSnapshotMenuById =
                activeEntitiesByKey(snapshot.allMenus(), Cmenum::getMnuId);
        Map<String, String> activeSnapshotMnuIdBySrePth =
                activeMenuIdsBySrePth(snapshot.allMenus());

        Set<String> fileMenuIds = new HashSet<>();
        for (CommonDataMigrationDto.MenuRow row : request.menus()) {
            fileMenuIds.add(row.mnuId());
        }
        Set<String> fileRoutePaths = new HashSet<>();
        for (CommonDataMigrationDto.RouteRow row : request.routes()) {
            fileRoutePaths.add(row.srePth());
        }

        // 시트 내 PK 중복 검사 (5개 시트 각각). 복합키는 문자열 결합 대신 Arrays.asList(...)를 맵/셋 키로 써서
        // 필드 경계가 다른 값끼리(예: cId="A::B"+cdva="C" vs cId="A"+cdva="B::C") 같은 키로 뭉치는
        // 구분자 충돌 자체를 구조적으로 없앤다. List.of(...)는 null 원소에서 NPE를 던져 필수값이 빈
        // 행(오류 ①이 나중에 잡아야 할 케이스)에서 planner 자체가 죽으므로, null을 허용하는
        // Arrays.asList를 쓴다(equals/hashCode는 List.of와 동일하게 원소 단위 비교). 오류 메시지의
        // "(값: ...)"는 사람이 읽는 표기이므로 별도 displayFn으로 "/" 결합해 내부 키 표현과 분리한다.
        checkDuplicates(
                errors,
                SHEET_MENU,
                request.menus(),
                CommonDataMigrationDto.MenuRow::excelRow,
                CommonDataMigrationDto.MenuRow::mnuId,
                CommonDataMigrationDto.MenuRow::mnuId,
                "메뉴ID가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_MENU_AUTH,
                request.menuAuths(),
                CommonDataMigrationDto.MenuAuthRow::excelRow,
                r -> Arrays.asList(r.mnuId(), r.athId()),
                r -> r.mnuId() + "/" + r.athId(),
                "메뉴ID/자격등급ID 조합이 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_ROUTE,
                request.routes(),
                CommonDataMigrationDto.RouteRow::excelRow,
                CommonDataMigrationDto.RouteRow::srePth,
                CommonDataMigrationDto.RouteRow::srePth,
                "화면경로가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_CODE,
                request.codes(),
                CommonDataMigrationDto.CodeRow::excelRow,
                c -> Arrays.asList(c.cId(), c.cdva(), c.sttDt()),
                c -> c.cId() + "/" + c.cdva() + "/" + c.sttDt(),
                "코드 키가 중복되었습니다");
        checkDuplicates(
                errors,
                SHEET_TRANSLATION,
                request.translations(),
                CommonDataMigrationDto.TranslationRow::excelRow,
                t -> Arrays.asList(t.tcIdCone(), t.tcColNm(), t.dttLanC()),
                t -> t.tcIdCone() + "/" + t.tcColNm() + "/" + t.dttLanC(),
                "다국어 키가 중복되었습니다");

        // 시트별 값 검증 + 참조 무결성 + 경고
        for (CommonDataMigrationDto.MenuRow row : request.menus()) {
            validateMenuRow(row, fileMenuIds, activeSnapshotMenuIds, errors);
            warnAgainstSnapshot(row, activeSnapshotMenuById, warnings);
            warnMissingRouteForPage(row, fileRoutePaths, activeSnapshotRoutePaths, warnings);
            warnPathUsedByOtherMenu(row, activeSnapshotMnuIdBySrePth, warnings);
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
            validateTranslationRow(
                    row,
                    fileMenuIds,
                    activeSnapshotMenuIds,
                    fileCodeKeys,
                    activeSnapshotCodeKeys,
                    errors,
                    warnings);
        }

        // 메뉴 행이 있는데 메뉴권한 행이 0건이면 오류
        if (!request.menus().isEmpty() && request.menuAuths().isEmpty()) {
            errors.add(message(SHEET_MENU_AUTH, 0, "메뉴 행이 있는데 메뉴권한 행이 없습니다."));
        }

        // 분류: 스냅샷에 같은 PK 없으면 added, 있고 delYn='Y'면 restored, 활성이면 updated
        CommonDataMigrationDto.TableSummary menus =
                classify(
                        SHEET_MENU,
                        request.menus(),
                        CommonDataMigrationDto.MenuRow::mnuId,
                        menuDelYnByKey);
        CommonDataMigrationDto.TableSummary menuAuths =
                classify(
                        SHEET_MENU_AUTH,
                        request.menuAuths(),
                        r -> Arrays.asList(r.mnuId(), r.athId()),
                        menuAuthDelYnByKey);
        CommonDataMigrationDto.TableSummary routes =
                classify(
                        SHEET_ROUTE,
                        request.routes(),
                        CommonDataMigrationDto.RouteRow::srePth,
                        routeDelYnByKey);
        CommonDataMigrationDto.TableSummary codes =
                classify(
                        SHEET_CODE,
                        request.codes(),
                        c -> Arrays.asList(c.cId(), c.cdva(), c.sttDt()),
                        codeDelYnByKey);
        CommonDataMigrationDto.TableSummary translations =
                classify(
                        SHEET_TRANSLATION,
                        request.translations(),
                        t -> Arrays.asList(t.tcIdCone(), t.tcColNm(), t.dttLanC()),
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
        validateLength(errors, SHEET_MENU, row.excelRow(), "메뉴ID", row.mnuId(), 10);
        validateLength(errors, SHEET_MENU, row.excelRow(), "상위메뉴ID", row.hrkMnuId(), 10);
        validateLength(errors, SHEET_MENU, row.excelRow(), "메뉴명", row.mnuNm(), 100);
        validateLength(errors, SHEET_MENU, row.excelRow(), "메뉴유형", row.mnuTpC(), 3);
        validateLength(errors, SHEET_MENU, row.excelRow(), "화면경로", row.srePth(), 300);
        validateLength(errors, SHEET_MENU, row.excelRow(), "전체메뉴경로", row.whlMnuPth(), 500);

        if (row.mnuSotSqnSno() == null) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴정렬순서는 필수입니다."));
        }

        if (row.mnuTpC() == null || row.mnuTpC().isBlank()) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴유형은 필수입니다."));
        } else if (!MENU_TYPES.contains(row.mnuTpC())) {
            errors.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "메뉴유형은 GRP/LNK/PGE만 허용합니다 (값: " + row.mnuTpC() + ")"));
        }

        if (row.hidYn() == null || row.hidYn().isBlank()) {
            errors.add(message(SHEET_MENU, row.excelRow(), "숨김여부는 필수입니다."));
        } else if (!YN.contains(row.hidYn())) {
            errors.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "숨김여부는 Y/N만 허용합니다 (값: " + row.hidYn() + ")"));
        }

        if (row.mnuDep() == null) {
            errors.add(message(SHEET_MENU, row.excelRow(), "메뉴깊이는 필수입니다."));
        } else if (row.mnuDep() < MIN_MENU_DEPTH || row.mnuDep() > MAX_MENU_DEPTH) {
            errors.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "메뉴깊이는 1~4만 허용합니다 (값: " + row.mnuDep() + ")"));
        }

        if (row.hrkMnuId() != null
                && !row.hrkMnuId().isBlank()
                && !fileMenuIds.contains(row.hrkMnuId())
                && !activeSnapshotMenuIds.contains(row.hrkMnuId())) {
            errors.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "상위메뉴ID가 존재하지 않습니다 (값: " + row.hrkMnuId() + ")"));
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
                            SHEET_MENU_AUTH,
                            row.excelRow(),
                            "메뉴ID가 존재하지 않습니다 (값: " + row.mnuId() + ")"));
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
        validateLength(errors, SHEET_ROUTE, row.excelRow(), "화면경로", row.srePth(), 300);
        validateLength(errors, SHEET_ROUTE, row.excelRow(), "화면메뉴명", row.sreMnuNm(), 100);
        validateLength(errors, SHEET_ROUTE, row.excelRow(), "비고", row.rmk(), 300);

        if (row.useYn() == null || row.useYn().isBlank()) {
            errors.add(message(SHEET_ROUTE, row.excelRow(), "사용여부는 필수입니다."));
        } else if (!YN.contains(row.useYn())) {
            errors.add(
                    message(
                            SHEET_ROUTE,
                            row.excelRow(),
                            "사용여부는 Y/N만 허용합니다 (값: " + row.useYn() + ")"));
        }
    }

    /** 오류 ①: 공통코드 행 복합키 필수값을 검증합니다. */
    private static void validateCodeRow(CommonDataMigrationDto.CodeRow row, List<String> errors) {
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "공통코드ID", row.cId());
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "코드값ID", row.cdva());
        requireNotBlank(errors, SHEET_CODE, row.excelRow(), "시작일자", row.sttDt());
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드ID", row.cId(), 100);
        validateLength(errors, SHEET_CODE, row.excelRow(), "코드값ID", row.cdva(), 40);
        validateLength(errors, SHEET_CODE, row.excelRow(), "시작일자", row.sttDt(), 8);
        validateLength(errors, SHEET_CODE, row.excelRow(), "종료일자", row.endDt(), 8);
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드명", row.cNm(), 100);
        validateLength(errors, SHEET_CODE, row.excelRow(), "코드값명", row.cdvaNm(), 200);
        validateLength(errors, SHEET_CODE, row.excelRow(), "코드값적요", row.cdvaDtl(), 2000);
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드값약어명", row.cdvaDes(), 100);
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드인스턴스명", row.cTp(), 200);
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드인스턴스내용", row.cTpDes(), 500);
        validateLength(errors, SHEET_CODE, row.excelRow(), "공통코드값명", row.cdvaDtlC(), 500);
        validateLength(errors, SHEET_CODE, row.excelRow(), "상위코드값ID", row.hrkC(), 40);
    }

    /** 오류 ①⑤⑥, 경고 ②: 다국어 행 필수값·언어코드 길이·구분명/컬럼명 조합·대상 메뉴 존재를 검증합니다. */
    private static void validateTranslationRow(
            CommonDataMigrationDto.TranslationRow row,
            Set<String> fileMenuIds,
            Set<String> activeSnapshotMenuIds,
            Set<String> fileCodeKeys,
            Set<String> activeSnapshotCodeKeys,
            List<String> errors,
            List<String> warnings) {
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드ID내용", row.tcIdCone());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드컬럼명", row.tcColNm());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드설명", row.tcDes());
        requireNotBlank(errors, SHEET_TRANSLATION, row.excelRow(), "구분명", row.dttNm());
        validateLength(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드ID내용", row.tcIdCone(), 255);
        validateLength(errors, SHEET_TRANSLATION, row.excelRow(), "구분코드컬럼명", row.tcColNm(), 255);
        validateLength(errors, SHEET_TRANSLATION, row.excelRow(), "번역문", row.tcDes(), 2000);
        validateLength(errors, SHEET_TRANSLATION, row.excelRow(), "구분명", row.dttNm(), 100);

        if (row.dttLanC() == null || row.dttLanC().isBlank()) {
            errors.add(message(SHEET_TRANSLATION, row.excelRow(), "언어코드는 필수입니다."));
        } else if (row.dttLanC().length() != LANGUAGE_CODE_LENGTH) {
            errors.add(
                    message(
                            SHEET_TRANSLATION,
                            row.excelRow(),
                            "언어코드는 2자여야 합니다 (값: " + row.dttLanC() + ")"));
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
        if (target == TranslationTarget.COMMON_CODE
                && row.tcIdCone() != null
                && !row.tcIdCone().isBlank()) {
            List<String> codeParts = parseCodeKey(row.tcIdCone());
            if (codeParts == null) {
                warnings.add(
                        message(
                                SHEET_TRANSLATION,
                                row.excelRow(),
                                "대상 공통코드 키 형식을 해석할 수 없습니다 (값: " + row.tcIdCone() + ")"));
            } else {
                if (!fileCodeKeys.contains(row.tcIdCone())
                        && !activeSnapshotCodeKeys.contains(row.tcIdCone())) {
                    warnings.add(
                            message(
                                    SHEET_TRANSLATION,
                                    row.excelRow(),
                                    "대상 공통코드가 존재하지 않습니다 (값: " + row.tcIdCone() + ")"));
                }
            }
        }
    }

    /** 공통코드 복합키를 번역 마스터가 사용하는 길이-prefix 형식으로 만듭니다. */
    private static String codeKey(CommonDataMigrationDto.CodeRow row) {
        return prefixed(row.cId()) + prefixed(row.cdva()) + prefixed(row.sttDt());
    }

    private static String codeKey(Ccodem code) {
        return prefixed(code.getCId()) + prefixed(code.getCdva()) + prefixed(code.getSttDt());
    }

    private static String prefixed(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    /** 길이-prefix 공통코드 키를 세 구성요소로 해석합니다. 형식이 틀리면 null을 반환합니다. */
    private static List<String> parseCodeKey(String value) {
        List<String> parts = new ArrayList<>();
        int cursor = 0;
        while (parts.size() < 3) {
            int colon = value.indexOf(':', cursor);
            if (colon <= cursor) return null;
            int length;
            try {
                length = Integer.parseInt(value.substring(cursor, colon));
            } catch (NumberFormatException e) {
                return null;
            }
            int start = colon + 1;
            int end = start + length;
            if (length < 0 || end > value.length()) return null;
            parts.add(value.substring(start, end));
            cursor = end;
        }
        return cursor == value.length() ? parts : null;
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
        if (!fileRoutePaths.contains(row.srePth())
                && !activeSnapshotRoutePaths.contains(row.srePth())) {
            warnings.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "화면경로가 경로 카탈로그에 없습니다 (값: " + row.srePth() + ")"));
        }
    }

    /**
     * 경고 ④: 파일 메뉴의 화면경로를 운영 스냅샷의 다른 활성 메뉴가 이미 쓰고 있으면 경고합니다.
     *
     * <p>시드가 서버별 시퀀스로 메뉴를 채번해 dev/prod 메뉴ID가 어긋날 수 있다. 이때 파일이 dev 기준 메뉴ID로 새 메뉴를 추가하면서 운영에 이미 있는
     * 화면경로를 그대로 쓰면, 반영 후 같은 경로를 가리키는 활성 메뉴가 2개가 된다. 자기 자신(같은 메뉴ID)을 갱신하는 정상 케이스는 제외한다.
     */
    private static void warnPathUsedByOtherMenu(
            CommonDataMigrationDto.MenuRow row,
            Map<String, String> activeSnapshotMnuIdBySrePth,
            List<String> warnings) {
        if (row.srePth() == null || row.srePth().isBlank()) {
            return;
        }
        String conflictingMnuId = activeSnapshotMnuIdBySrePth.get(row.srePth());
        if (conflictingMnuId != null && !conflictingMnuId.equals(row.mnuId())) {
            warnings.add(
                    message(
                            SHEET_MENU,
                            row.excelRow(),
                            "같은 화면경로("
                                    + row.srePth()
                                    + ")를 운영의 다른 메뉴("
                                    + conflictingMnuId
                                    + ")가 사용 중입니다 — 반영 시 같은 경로의 활성 메뉴가 2개가 됩니다."));
        }
    }

    /** 오류 ①(안전망): 값이 비어 있으면 오류를 추가합니다. 어노테이션 검증을 거치지 않고 호출되는 경로를 대비합니다. */
    private static void requireNotBlank(
            List<String> errors, String sheet, int excelRow, String fieldLabel, String value) {
        if (value == null || value.isBlank()) {
            errors.add(message(sheet, excelRow, fieldLabel + "은(는) 필수입니다."));
        }
    }

    /** 문자열 컬럼의 물리 길이를 dry-run에서 미리 검증합니다. */
    private static void validateLength(
            List<String> errors, String sheet, int row, String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            errors.add(
                    message(
                            sheet,
                            row,
                            field
                                    + "이(가) 최대 "
                                    + maxLength
                                    + "자를 초과했습니다 (현재: "
                                    + value.length()
                                    + "자)"));
        }
    }

    /**
     * 오류 ⑩: 시트 내에서 같은 키가 두 번째 이상 나타나면 그 행에 중복 오류를 추가합니다.
     *
     * <p>{@code keyFn}은 동등성 판정에만 쓰는 키(단일 필드는 String, 복합 필드는 {@link Arrays#asList}로 묶은 List)이고,
     * {@code displayFn}은 오류 메시지의 "(값: ...)"에 넣을 사람이 읽는 표기입니다. 키를 문자열로 결합하면 필드 경계가 다른 값끼리(예:
     * "A::B"+"C" vs "A"+"B::C") 같은 문자열로 뭉쳐 중복을 오판할 수 있어 표기와 동등성 판정을 분리했습니다. 복합키는 {@code List.of}가
     * 아니라 {@code Arrays.asList}를 씁니다 — {@code List.of}는 null 원소에서 NPE를 던져, 필수값이 빈 행에서 오류 ①(안전망)이 이
     * 오류를 잡기도 전에 planner가 죽습니다.
     */
    private static <T, K> void checkDuplicates(
            List<String> errors,
            String sheet,
            List<T> rows,
            ToIntFunction<T> rowNumberFn,
            Function<T, K> keyFn,
            Function<T, String> displayFn,
            String detail) {
        Set<K> seen = new HashSet<>();
        for (T row : rows) {
            K key = keyFn.apply(row);
            if (!seen.add(key)) {
                errors.add(
                        message(
                                sheet,
                                rowNumberFn.applyAsInt(row),
                                detail + " (값: " + displayFn.apply(row) + ")"));
            }
        }
    }

    /**
     * 분류 규칙: 스냅샷에 같은 키가 없으면 added, 있고 delYn='Y'면 restored, 그 외(활성)는 updated로 센다.
     *
     * <p>{@code K}는 단일 필드 키(String)이거나 {@link Arrays#asList}로 묶은 복합 필드 키(null 허용)입니다. List는 원소 단위로
     * equals/hashCode를 계산하므로 문자열 결합과 달리 필드 경계 충돌이 없습니다.
     */
    private static <T, K> CommonDataMigrationDto.TableSummary classify(
            String table, List<T> rows, Function<T, K> keyFn, Map<K, String> snapshotDelYnByKey) {
        int added = 0;
        int updated = 0;
        int restored = 0;
        for (T row : rows) {
            K key = keyFn.apply(row);
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

    private static <E extends BaseEntity, K> Map<K, String> snapshotDelYnMap(
            List<E> entities, Function<E, K> keyFn) {
        Map<K, String> map = new HashMap<>();
        for (E entity : entities) {
            map.put(keyFn.apply(entity), entity.getDelYn());
        }
        return map;
    }

    private static <E extends BaseEntity> Set<String> activeKeys(
            List<E> entities, Function<E, String> keyFn) {
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

    /** 활성 메뉴의 srePth→mnuId 맵입니다. srePth가 비어 있는 메뉴(GRP/LNK 등)는 제외합니다. */
    private static Map<String, String> activeMenuIdsBySrePth(List<Cmenum> menus) {
        Map<String, String> map = new HashMap<>();
        for (Cmenum menu : menus) {
            if (!"Y".equals(menu.getDelYn())
                    && menu.getSrePth() != null
                    && !menu.getSrePth().isBlank()) {
                map.put(menu.getSrePth(), menu.getMnuId());
            }
        }
        return map;
    }

    private static String message(String sheet, int excelRow, String detail) {
        return sheet + " 시트 " + excelRow + "행: " + detail;
    }
}
