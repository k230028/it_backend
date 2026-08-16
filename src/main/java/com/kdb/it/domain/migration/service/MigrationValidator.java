package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 정규화 행에 대한 셀 단위 진단을 만듭니다 (§6.1).
 *
 * <p>dry-run과 commit이 같은 인스턴스를 호출합니다. commit은 클라이언트가 보낸 값을 신뢰하지 않고 이 검증을 다시 돌린 뒤 BLOCKER가 하나라도 있으면
 * 아무것도 쓰지 않고 실패합니다.
 *
 * <p><b>보정값을 반영해 읽는다</b>는 것이 이 클래스의 불변식입니다. 어댑터는 보정값이 반영된 값을 저장하므로, 검증이 원본 셀을 보면 사용자가 고친 값이 검증을
 * 우회합니다. 셀 접근은 예외 없이 {@link MigrationDiagnostics#cell}을 거치며 {@code Map.of()}를 넘기지 않습니다.
 *
 * <p><b>길이 검증의 단위</b>는 물리 컬럼의 문자 의미를 따릅니다. 대부분은 {@code CHAR} 의미라 문자 수로 검사하지만 {@code
 * CTT_OPP_NM}·{@code GCL_NM}은 {@code BYTE} 의미(100바이트)라 UTF-8 바이트 수로 검사합니다 — 문자 수로 검사하면 한글 34자에서 이미
 * 넘는 값을 통과시켜 {@code ORA-12899}가 commit에서 터집니다.
 *
 * <p><b>검증 범위는 행의 처리 방식에 따라 갈립니다.</b> 이 화면은 원장을 새로 만들지 않는 것이 기본이라(설계 §2.1), 기존 원장에 매칭되는 행은 물리
 * 길이·필수값·코드 해석처럼 "원장을 새로 만들 때만" 의미 있는 검사를 걸지 않습니다 — 종합본의 긴 사업개요 한 칸 때문에 편성 전체가 막히면 안 됩니다. {@code
 * createNewRows}에 속한 행에만 {@link #validateForCreate}를 걸고, 금액·통화·기간처럼 편성 계산에 항상 관여하는 검사와 매칭 키의
 * 재료(부서·비목·사업명 등)가 되는 값의 해석은 행의 처리 방식과 무관하게 {@link #validateAlways}로 늘 검사합니다. 매칭 자체(어느 원장을 가리키는지,
 * 원장 후보가 없는지)는 이 클래스가 아니라 {@link MigrationMatchDiagnostics}가 맡습니다.
 */
@Component
public class MigrationValidator {

    /** `'26.05` 또는 `26.05` 형태의 연월 표기. */
    private static final Pattern YM = Pattern.compile("^'?(\\d{2})\\.(\\d{1,2})월?$");

    /** 원화 금액 대조 허용 오차 (원). */
    private static final BigDecimal AMOUNT_TOLERANCE = BigDecimal.ONE;

    /**
     * 보정값 조회 키를 만듭니다. commit이 {@code CellOverride} 목록을 이 키의 맵으로 접어 넘깁니다.
     *
     * @param sheet 시트 종류
     * @param excelRow 엑셀 행 번호
     * @param column 정규 컬럼 id
     * @return 파이프로 이은 키
     */
    public static String overrideKey(SheetKind sheet, int excelRow, String column) {
        return MigrationDiagnostics.overrideKey(sheet, excelRow, column);
    }

    /**
     * 시트 전체를 검증해 진단 목록을 만듭니다.
     *
     * @param sheets 올린 시트 목록
     * @param index 조직·비목·환율 조회 인덱스
     * @param snapshot 예산연도 기존 상태
     * @param overrides 보정값 맵 ({@link #overrideKey} 키)
     * @param createNewRows 시트 종류 → 원장을 새로 만들 엑셀 행 번호 집합. 이 집합에 속하지 않은 행(매칭된 행)에는 {@link
     *     #validateForCreate}를 걸지 않습니다
     * @return 진단 목록. 문제가 없으면 빈 목록
     */
    public List<MigrationDto.CellDiagnostic> validate(
            List<MigrationDto.SheetPayload> sheets,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides,
            Map<SheetKind, Set<Integer>> createNewRows) {
        List<MigrationDto.CellDiagnostic> out = new ArrayList<>();
        Set<String> namesInThisImport = collectProjectNames(sheets, overrides);

        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() == SheetKind.DELEGATED_BUDGET) {
                checkDelegatedFirstBranch(sheet, overrides, out);
            }
            // 같은 반영 안의 사업명 중복은 행 단위로는 보이지 않는다 — 시트별로 앞선 행을 기억해 뒤 행에서 짚는다
            Map<String, Integer> projectNameRows = new LinkedHashMap<>();
            Set<Integer> createNewForSheet = createNewRows.getOrDefault(sheet.kind(), Set.of());
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                boolean createNew = createNewForSheet.contains(row.excelRow());
                // 매칭된 행은 원장을 만들지 않으므로 물리 길이·필수값·코드 해석을 검사하지 않는다.
                // 전 행에 걸면 종합본의 긴 사업개요 하나 때문에 편성이 통째로 막힌다.
                if (createNew) {
                    validateForCreate(sheet, row, index, overrides, out);
                }
                validateAlways(sheet, row, index, snapshot, namesInThisImport, overrides, out);
                if (sheet.kind() == SheetKind.CAPITAL_PROJECT) {
                    checkIntraPayloadDuplicate(sheet, row, overrides, projectNameRows, out);
                }
            }
        }
        return out;
    }

    /** 같은 반영에 포함된 사업명(자본예산)을 모읍니다. PROJECT_NOT_FOUND 판정 기준입니다. */
    private Set<String> collectProjectNames(
            List<MigrationDto.SheetPayload> sheets, Map<String, String> overrides) {
        Set<String> names = new LinkedHashSet<>();
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() != SheetKind.CAPITAL_PROJECT) {
                continue;
            }
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                names.add(
                        MigrationYearSnapshot.normalizeName(
                                MigrationDiagnostics.cell(row, "projectName", overrides, sheet)));
            }
        }
        return names;
    }

    /**
     * 같은 반영 안에서 사업명이 중복되는지 확인합니다.
     *
     * <p>{@code MigrationImportService}의 {@code projectNoByName}은 정규화 사업명이 키라 같은 이름의 두 행 중 나중 것만
     * 남습니다. 그러면 앞 행의 사업은 만들어졌는데 편성률 대상에서 빠져 편성행이 없는 고아가 됩니다 — 목록에는 보이고 모든 예산 화면에서는 0인 상태입니다. 기존
     * 원장과의 일치는 이제 매칭 성공 조건이라({@link MigrationMatchDiagnostics}) 더 이상 BLOCKER가 아니지만, 이 검사는 페이로드 안에서만
     * 성립하므로(같은 반영의 두 행이 같은 원장을 가리키면 배분이 서로를 덮어씁니다) 별도로 둡니다.
     *
     * @param seenRows 이미 등장한 정규화 사업명 → 첫 등장 행 번호 (시트 단위로 누적)
     */
    private void checkIntraPayloadDuplicate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides,
            Map<String, Integer> seenRows,
            List<MigrationDto.CellDiagnostic> out) {
        String normalized =
                MigrationYearSnapshot.normalizeName(
                        MigrationDiagnostics.cell(row, "projectName", overrides, sheet));
        if (normalized.isEmpty()) {
            return;
        }
        Integer firstRow = seenRows.putIfAbsent(normalized, row.excelRow());
        if (firstRow != null) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            "projectName",
                            "DUPLICATE_EXISTS",
                            "같은 반영의 " + firstRow + "행과 사업명이 같습니다. 한 사업으로 합치거나 사업명을 구분해 주세요.",
                            List.of()));
        }
    }

    /**
     * 원장을 새로 만들 때만 의미 있는 검사를 겁니다 — 물리 길이·필수값·코드 해석. 매칭된 행은 원장을 만들지 않으므로 이 검사를 받지 않습니다.
     *
     * @param sheet 시트 페이로드
     * @param row 검사 대상 행
     * @param index 조회 인덱스
     * @param overrides 보정값 맵
     * @param out 진단을 누적할 목록
     */
    private void validateForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        switch (sheet.kind()) {
            case COST -> validateCostRowForCreate(sheet, row, index, overrides, out);
            case CAPITAL_PROJECT -> validateProjectRowForCreate(sheet, row, index, overrides, out);
            case DELEGATED_BUDGET -> validateDelegatedRowForCreate(sheet, row, overrides, out);
            case PLAN_ADJUSTMENT -> validatePlanRowForCreate(sheet, row, overrides, out);
        }
    }

    /**
     * 행의 처리 방식과 무관하게 항상 거는 검사입니다 — 금액·통화·기간·편성률처럼 편성 계산에 항상 관여하는 값과, 매칭 키의 재료가 되는 값(부서·비목·사업명 등)의
     * 해석입니다. 매칭 키 재료가 해석되지 않으면 매칭이 성립하지 않아 어차피 {@code LEDGER_NOT_MATCHED}가 나지만, 원인을 구체적으로 짚기 위해 여기서
     * 먼저 검사합니다.
     *
     * @param sheet 시트 페이로드
     * @param row 검사 대상 행
     * @param index 조회 인덱스
     * @param snapshot 예산연도 기존 상태 (부문계획의 사업 존재·계획 중복 판정용)
     * @param namesInThisImport 같은 반영에 포함된 자본예산 사업명 (정규화)
     * @param overrides 보정값 맵
     * @param out 진단을 누적할 목록
     */
    private void validateAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Set<String> namesInThisImport,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        switch (sheet.kind()) {
            case COST -> validateCostRowAlways(sheet, row, index, overrides, out);
            case CAPITAL_PROJECT -> validateProjectRowAlways(sheet, row, index, overrides, out);
            case DELEGATED_BUDGET -> validateDelegatedRowAlways(sheet, row, index, overrides, out);
            case PLAN_ADJUSTMENT ->
                    validatePlanRowAlways(sheet, row, snapshot, namesInThisImport, overrides, out);
        }
    }

    private void validateCostRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "requestDetail", overrides, out);
        limitLength(sheet, row, "requestDetail", 100, overrides, out); // CTT_NM VARCHAR2(100 CHAR)
        limitBytes(sheet, row, "vendorName", 100, overrides, out); // CTT_OPP_NM VARCHAR2(100 BYTE)
        limitLength(sheet, row, "remark", 200, overrides, out); // IND_RSN VARCHAR2(200 CHAR)
        resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);
        resolveCodeCell(
                sheet, row, "abusCode", index.abusUnitNameByCode(), overrides, out, "사업코드", false);
    }

    /**
     * 부서·비목은 전산업무비 매칭 키({@link
     * com.kdb.it.domain.migration.service.adapter.AllocationIntent.MatchKey#ofCost})의 재료라 항상 해석합니다.
     */
    private void validateCostRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);

        String ioeName = MigrationDiagnostics.cell(row, "ioeName", overrides, sheet);
        String ioeOverride = overrides.get(overrideKey(sheet.kind(), row.excelRow(), "ioeName"));
        String ioeCode;
        if (ioeOverride != null) {
            // 보정값은 이미 코드값이므로 이름 맵이 아니라 값 집합(index.ioeCodeByName().values())에 실재하는지
            // 확인한다 — resolveOrgCell의 override 경로(index.org().orgNameOf)·resolveUserCell의 override
            // 경로(index.org().userExists)와 같은 이유다. 확인 없이 통과시키면 CostSheetAdapter.resolveIoe의
            // "미해석이면 이미 코드값이라고 가정한다" 폴백이 검증되지 않은 값을 그대로 IOE_C에 써 버린다.
            ioeCode = index.ioeCodeByName().containsValue(ioeOverride) ? ioeOverride : null;
        } else {
            ioeCode = index.ioeCodeByName().get(ioeName);
        }
        if (ioeCode == null) {
            out.add(
                    ioeOverride != null
                            ? MigrationDiagnostics.blocker(
                                    sheet,
                                    row,
                                    "ioeName",
                                    "CODE_UNRESOLVED",
                                    "보정값 '"
                                            + ioeOverride
                                            + "'에 해당하는 비목코드를 찾지 못했습니다. 비목을 다시 선택해 주세요.",
                                    MigrationDiagnostics.candidatesOfIoe(index, false))
                            : MigrationDiagnostics.blocker(
                                    sheet,
                                    row,
                                    "ioeName",
                                    "CODE_UNRESOLVED",
                                    "비목 '" + ioeName + "'에 대응하는 비목코드를 찾지 못했습니다. 비목을 직접 선택해 주세요.",
                                    MigrationDiagnostics.candidatesOfIoe(index, false)));
        }

        checkCurrency(sheet, row, index, overrides, out);
        checkAmount(sheet, row, index, overrides, out);
    }

    private void validateProjectRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        limitLength(sheet, row, "projectName", 100, overrides, out); // ABUS_NM VARCHAR2(100 CHAR)
        limitLength(sheet, row, "projectType", 300, overrides, out); // ABUS_PPO_CONE
        limitLength(sheet, row, "projectOutline", 1000, overrides, out); // ABUS_CONE
        limitLength(sheet, row, "headquarters", 100, overrides, out); // PRLM_HRK_OGZ_C_CONE
        resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);
        resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);
        resolveOrgCell(sheet, row, "itTeamName", index, overrides, out, false);
        resolveUserCell(sheet, row, "managerName", "deptName", index, overrides, out);
        resolveUserCell(sheet, row, "teamLeaderName", "deptName", index, overrides, out);
        resolveCodeCell(
                sheet,
                row,
                "feasibility",
                index.exePttCodeByName(),
                overrides,
                out,
                "추진가능성",
                true); // EXE_PTT_YN VARCHAR2(1)
        resolveCodeCell(
                sheet,
                row,
                "delegationLabel",
                index.edrtCodeByName(),
                overrides,
                out,
                "전결권",
                true); // IT_PTL_EDRT_TC VARCHAR2(2)
    }

    /** 사업명은 정보화사업 매칭 키({@code MatchKey.ofProjectName})의 재료라 항상 해석(존재 확인)합니다. */
    private void validateProjectRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "projectName", overrides, out);
        checkYm(sheet, row, "startYm", overrides, out);
        checkYm(sheet, row, "endYm", overrides, out);
        checkRate(sheet, row, "adjustRate", overrides, out);
        checkCapitalIoeOverrides(sheet, row, index, overrides, out);
    }

    /**
     * 품목 비목 보정값(`devAmountIoeC`·`hwAmountIoeC`·`swAmountIoeC`)이 자본예산 계열 비목코드인지 확인합니다.
     *
     * <p>보정값이 없는 컬럼은 기본 비목({@link MigrationIoeCodes})을 그대로 쓰므로 검사하지 않습니다.
     */
    private void checkCapitalIoeOverrides(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        for (String column : List.of("devAmountIoeC", "hwAmountIoeC", "swAmountIoeC")) {
            String override = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
            if (override != null && !MigrationIoeCodes.CAPITAL_CODES.contains(override)) {
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                "CODE_UNRESOLVED",
                                "'" + override + "'는 자본예산 계열 비목이 아닙니다.",
                                MigrationDiagnostics.candidatesOfIoe(index, true)));
            }
        }
    }

    /**
     * 위임예산 시트 첫 행의 부점명을 확인합니다.
     *
     * <p>부점명은 병합 셀이라 이어지는 행에서 비는 것이 정상입니다(forward-fill 대상). 그러나 첫 행부터 비어 있으면 이후 행 전부를 귀속시킬 사업이 없으므로
     * 시트 단위 BLOCKER로 막습니다. 행별 검사({@link #validateDelegatedRowAlways})는 이 전제를 알고 부점명을 필수값으로 요구하지
     * 않습니다.
     */
    private void checkDelegatedFirstBranch(
            MigrationDto.SheetPayload sheet,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        if (sheet.rows().isEmpty()) {
            return;
        }
        MigrationDto.NormalizedRow first = sheet.rows().get(0);
        if (MigrationDiagnostics.cell(first, "branchName", overrides, sheet).isBlank()) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            first,
                            "branchName",
                            "REQUIRED_MISSING",
                            "첫 행의 부점명이 비어 있어 이후 행을 귀속시킬 사업을 만들 수 없습니다.",
                            List.of()));
        }
    }

    private void validateDelegatedRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "itemName", overrides, out);
        limitBytes(sheet, row, "itemName", 100, overrides, out); // GCL_NM VARCHAR2(100 BYTE)
    }

    /** 부점명은 경상사업 매칭 키({@code MatchKey.ofOrdinaryDept})의 재료라 항상 해석합니다. */
    private void validateDelegatedRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        // 부점명은 병합 셀이라 이어지는 행에서 비는 것이 정상이다(forward-fill). 첫 행 검사는 시트 단위로 별도 수행한다.
        resolveOrgCell(sheet, row, "branchName", index, overrides, out, false);
        checkCurrency(sheet, row, index, overrides, out);
        checkAmount(sheet, row, index, overrides, out);
    }

    private void validatePlanRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "projectName", overrides, out);
        limitLength(sheet, row, "projectName", 100, overrides, out);
    }

    private void validatePlanRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationYearSnapshot.Data snapshot,
            Set<String> namesInThisImport,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        checkYm(sheet, row, "startYm", overrides, out);
        checkYm(sheet, row, "endYm", overrides, out);

        String normalized =
                MigrationYearSnapshot.normalizeName(
                        MigrationDiagnostics.cell(row, "projectName", overrides, sheet));
        boolean inImport = namesInThisImport.contains(normalized);
        boolean inDb = snapshot.projectNoByName(normalized) != null;
        if (!inImport && !inDb) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            "projectName",
                            "PROJECT_NOT_FOUND",
                            "이 사업이 같은 반영의 자본예산 시트에도, 포탈에도 없습니다. 자본예산 편성요구서를 함께 올리거나 사업명을 확인해 주세요.",
                            List.of()));
        }
        if (snapshot.planExists("조정")) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            null,
                            "DUPLICATE_EXISTS",
                            sheet.bseYy() + "년 조정 계획이 이미 있습니다.",
                            List.of()));
        }
    }

    /**
     * 통화 코드가 예산환율 공통코드에 있는지 확인합니다 (§3.7).
     *
     * <p>금액 대조({@link #checkAmountPair})가 아니라 행 단위로 한 번만 검사합니다. 위임예산은 HW·SW 금액 쌍이 통화 컬럼 하나를 공유하므로
     * 금액 쌍마다 검사하면 같은 셀에 같은 진단이 두 번 붙고, 두 쌍이 모두 0인 행에서는 아예 검사되지 않아 미등록 통화가 {@code CUR_C
     * VARCHAR2(3)}까지 그대로 흘러갑니다.
     */
    private void checkCurrency(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String currency = MigrationDiagnostics.cell(row, "currency", overrides, sheet);
        if (currency.isBlank() || "KRW".equals(currency)) {
            return;
        }
        if (!index.xcrByCurrency().containsKey(currency)) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            "currency",
                            "CODE_UNRESOLVED",
                            "통화 '"
                                    + currency
                                    + "'의 예산환율이 공통코드에 없습니다. 등록된 통화 중에서 선택하거나 환율 시드를 먼저 적용해 주세요.",
                            MigrationDiagnostics.candidatesOfCurrency(index)));
        }
    }

    /**
     * 외화 행의 서버 재계산값을 엑셀 원화열과 대조합니다 (§3.7).
     *
     * <p>위임예산 시트({@link SheetKind#DELEGATED_BUDGET})는 HW·SW 두 금액 쌍이 통화 컬럼 하나를 공유합니다. 한 행이 HW만 채우거나
     * SW만 채우는 경우가 보통이지만 둘 다 채운 행도 있을 수 있어, 두 쌍을 각각 대조하지 않으면 채워진 쌍 중 나중 것(SW)은 한 번도 검사되지 않는다. 나머지 세
     * 시트는 금액 쌍이 하나뿐이라 그 한 쌍만 대조한다.
     */
    private void checkAmount(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        if (sheet.kind() == SheetKind.DELEGATED_BUDGET) {
            checkAmountPair(sheet, row, index, overrides, out, "hwFcAmount", "hwKrwAmount");
            checkAmountPair(sheet, row, index, overrides, out, "swFcAmount", "swKrwAmount");
            return;
        }
        checkAmountPair(sheet, row, index, overrides, out, "fcAmount", "krwAmount");
    }

    /**
     * 금액 쌍 하나(외화·원화)를 대조합니다.
     *
     * @param fcColumn 외화금액 컬럼 id
     * @param krwColumn 원화금액 컬럼 id. 진단의 {@code column}에 그대로 쓰여 UI가 어느 쌍이 어긋났는지 짚을 수 있게 합니다
     */
    private void checkAmountPair(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out,
            String fcColumn,
            String krwColumn) {
        String currency = MigrationDiagnostics.cell(row, "currency", overrides, sheet);
        BigDecimal fc =
                MigrationAmounts.number(MigrationDiagnostics.cell(row, fcColumn, overrides, sheet));
        BigDecimal krw =
                MigrationAmounts.number(
                        MigrationDiagnostics.cell(row, krwColumn, overrides, sheet));
        if (currency.isBlank() || "KRW".equals(currency) || fc == null || krw == null) {
            return;
        }
        // 위임예산의 두 쌍 중 이 행에서 쓰지 않은 쌍은 0으로 채워져 온다. 빈 쌍까지 대조하지 않도록 건너뛴다.
        if (fc.compareTo(BigDecimal.ZERO) == 0 && krw.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal xcr = index.xcrByCurrency().get(currency);
        if (xcr == null) {
            // 미등록 통화는 checkCurrency가 행 단위로 이미 짚었다.
            return;
        }
        // JPY는 엑셀 외화열이 천엔이므로 엔으로 올린다 (§5.1)
        BigDecimal fcInBaseUnit = "JPY".equals(currency) ? fc.multiply(new BigDecimal("1000")) : fc;
        BigDecimal recomputed = fcInBaseUnit.multiply(xcr).setScale(3, RoundingMode.HALF_UP);
        BigDecimal excelKrw =
                krw.multiply(MigrationAmounts.amountMultiplier(sheet.kind()))
                        .setScale(3, RoundingMode.HALF_UP);
        if (recomputed.subtract(excelKrw).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            out.add(
                    MigrationDiagnostics.warning(
                            sheet,
                            row,
                            krwColumn,
                            "AMOUNT_MISMATCH",
                            "서버 재계산액 "
                                    + recomputed.stripTrailingZeros().toPlainString()
                                    + "원이 엑셀 원화열 "
                                    + excelKrw.stripTrailingZeros().toPlainString()
                                    + "원과 다릅니다. 재계산액으로 저장됩니다."));
        }
    }

    private void checkRate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        BigDecimal rate =
                MigrationAmounts.number(MigrationDiagnostics.cell(row, column, overrides, sheet));
        if (rate == null) {
            return;
        }
        BigDecimal percent = rate.multiply(new BigDecimal("100"));
        if (percent.compareTo(BigDecimal.ZERO) < 0
                || percent.compareTo(new BigDecimal("100")) > 0) {
            out.add(
                    MigrationDiagnostics.warning(
                            sheet,
                            row,
                            column,
                            "RATE_OUT_OF_RANGE",
                            "조정비율 "
                                    + rate.toPlainString()
                                    + "이 0~1 범위를 벗어났습니다. 편성률은 0~100으로 잘립니다."));
        }
    }

    private void checkYm(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        if (value.isBlank()) {
            return;
        }
        if (!YM.matcher(value.trim()).matches()) {
            out.add(
                    MigrationDiagnostics.warning(
                            sheet,
                            row,
                            column,
                            "DATE_UNPARSEABLE",
                            "'" + value + "'을 연월로 읽지 못했습니다. 날짜가 비워진 채 저장됩니다."));
        }
    }

    /** 필수값이 비어 있지 않은지 확인합니다. 길이 제한은 호출자가 컬럼의 문자 의미에 맞는 검사를 이어 붙입니다. */
    private void requireText(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        if (MigrationDiagnostics.cell(row, column, overrides, sheet).isBlank()) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet, row, column, "REQUIRED_MISSING", "필수 값이 비어 있습니다.", List.of()));
        }
    }

    /** 문자 수 상한을 확인합니다 ({@code VARCHAR2(n CHAR)} 컬럼). */
    private void limitLength(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            int maxLength,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        if (value.length() > maxLength) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            "LENGTH_EXCEEDED",
                            "값이 " + value.length() + "자로 최대 " + maxLength + "자를 넘습니다.",
                            List.of()));
        }
    }

    /**
     * UTF-8 바이트 수 상한을 확인합니다 ({@code VARCHAR2(n BYTE)} 컬럼).
     *
     * <p>{@code CTT_OPP_NM}·{@code GCL_NM}이 바이트 의미라 한글은 한 자에 3바이트를 씁니다 — 문자 수로만 검사하면 한글 34자에서 이미 넘는
     * 값이 통과해 {@code ORA-12899}가 commit에서 터집니다.
     */
    private void limitBytes(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            int maxBytes,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxBytes) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            "LENGTH_EXCEEDED",
                            "값이 " + bytes + "바이트로 최대 " + maxBytes + "바이트를 넘습니다(한글은 한 자에 3바이트).",
                            List.of()));
        }
    }

    /**
     * 코드값명 또는 코드값이 공통코드에 있는지 확인합니다.
     *
     * <p>{@code EXE_PTT_YN}(1자)·{@code IT_PTL_EDRT_TC}(2자)·{@code BG_UNT_ABUS_C}(3자)는 물리 길이가 아주 짧아,
     * 엑셀 원문을 그대로 대입하면 값이 조금만 길어도 {@code ORA-12899}로 commit이 실패합니다(추진가능성 실 데이터는 `추진계획 검토중` 8자). 그래서
     * 라벨을 코드값으로 바꾸고, 바꿀 수 없으면 여기서 막아 미리보기에서 고르게 합니다.
     *
     * @param codeCatalog 코드 카탈로그. {@code byName=true}면 코드값명 → 코드값, 아니면 코드값 → 코드값명
     * @param label 사용자 문구에 쓰는 항목 이름
     * @param byName 카탈로그가 이름 → 코드 방향인지 여부
     */
    private void resolveCodeCell(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> codeCatalog,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out,
            String label,
            boolean byName) {
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet).trim();
        if (value.isBlank() || codeCatalog.isEmpty()) {
            // 빈 셀은 컬럼이 nullable이라 그대로 두고, 카탈로그가 비어 있으면(코드 조회를 하지 않는 단위 테스트) 판정 근거가 없다
            return;
        }
        boolean resolved =
                byName
                        ? codeCatalog.containsKey(value) || codeCatalog.containsValue(value)
                        : codeCatalog.containsKey(value);
        if (!resolved) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            "CODE_UNRESOLVED",
                            "'" + value + "'에 해당하는 " + label + " 코드를 찾지 못했습니다. 목록에서 선택해 주세요.",
                            MigrationDiagnostics.candidatesOfCatalog(codeCatalog, byName)));
        }
    }

    private void resolveOrgCell(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out,
            boolean required) {
        String overrideValue = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
        if (overrideValue != null) {
            if (index.org().orgNameOf(overrideValue) == null) {
                // 보정값이 실재하지 않으면(오래된 코드 등) 원본 셀 값으로 다시 후보를 뽑아 드롭다운을 살려 둔다
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                "ORG_UNRESOLVED",
                                "보정값 '" + overrideValue + "'에 해당하는 조직코드를 찾지 못했습니다. 조직을 다시 선택해 주세요.",
                                index.org()
                                        .resolveOrg(MigrationDiagnostics.rawCell(row, column))
                                        .candidates()));
            }
            return;
        }
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        OrgIdentityResolver.Resolution resolution = index.org().resolveOrg(value);
        if (resolution.code() != null) {
            return;
        }
        if (resolution.isAmbiguous()) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            "ORG_AMBIGUOUS",
                            "'" + value + "'에 해당하는 조직이 여러 개입니다. 하나를 선택해 주세요.",
                            resolution.candidates()));
            return;
        }
        if (required || !value.isBlank()) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            "ORG_UNRESOLVED",
                            MigrationDiagnostics.unresolvedMessage(
                                    "'" + value + "'에 해당하는 조직을 찾지 못했습니다.", resolution),
                            resolution.candidates()));
        }
    }

    private void resolveUserCell(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            String deptColumn,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String overrideValue = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
        if (overrideValue != null) {
            if (!index.org().userExists(overrideValue)) {
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                "USER_UNRESOLVED",
                                "보정값 '" + overrideValue + "'에 해당하는 사번을 찾지 못했습니다. 담당자를 다시 선택해 주세요.",
                                index.org()
                                        .resolveUser(
                                                MigrationDiagnostics.rawCell(row, column), null)
                                        .candidates()));
            }
            return;
        }
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        if (value.isBlank()) {
            return;
        }
        String deptHint = effectiveOrgCode(sheet, row, deptColumn, index, overrides);
        OrgIdentityResolver.Resolution resolution = index.org().resolveUser(value, deptHint);
        if (resolution.code() != null) {
            return;
        }
        out.add(
                MigrationDiagnostics.blocker(
                        sheet,
                        row,
                        column,
                        resolution.isAmbiguous() ? "USER_AMBIGUOUS" : "USER_UNRESOLVED",
                        resolution.isAmbiguous()
                                ? "'" + value + "'에 해당하는 직원이 여러 명입니다. 한 명을 선택해 주세요."
                                : MigrationDiagnostics.unresolvedMessage(
                                        "'" + value + "'에 해당하는 직원을 찾지 못했습니다.", resolution),
                        resolution.candidates()));
    }

    /**
     * 셀의 유효 조직코드를 판정합니다. 담당자 해석의 부서 힌트(deptHint)를 구할 때 씁니다.
     *
     * <p>같은 컬럼이라도 보정값이 있을 때와 없을 때 값의 의미가 다릅니다 — {@code CellOverride.value}는 사용자가 후보에서 고른 코드값
     * ({@code @Schema} 예시 "008")이고, 원본 셀 값은 엑셀에 적힌 조직 이름입니다. 이 둘을 구분하지 않고 코드값을 이름 해석기({@link
     * OrgIdentityResolver.Index#resolveOrg})에 그대로 넘기면 조용히 미해석 처리되어, 사용자가 중의적 부서명을 보정해도 담당자 힌트가 좁혀지지
     * 않는 결함이 있었다. 보정값 경로는 {@link OrgIdentityResolver.Index#orgNameOf}로 실재를 확인하고, 원본 셀 경로만 이름 해석을
     * 탄다.
     *
     * @param sheet 시트
     * @param row 행
     * @param column 정규 컬럼 id (부서·팀 컬럼)
     * @param index 조회 인덱스
     * @param overrides 보정값 맵
     * @return 유효 조직코드. 보정값이 미등록 코드이거나 이름이 미해석이면 null
     */
    private static String effectiveOrgCode(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            MigrationLookupIndex index,
            Map<String, String> overrides) {
        String override = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
        if (override != null) {
            return index.org().orgNameOf(override) != null ? override : null;
        }
        return index.org().resolveOrg(MigrationDiagnostics.rawCell(row, column)).code();
    }
}
