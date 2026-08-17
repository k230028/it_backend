package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * createNewRows}에 속한 행에만 {@link #validateForCreate}를 걸고, 금액·통화·기간처럼 편성 계산에 항상 관여하는 검사, 매칭 키의
 * 재료(부서·비목·사업명 등)가 되는 값의 해석, 그리고 <b>매칭된 행에도 원장에 써 넣는 값</b>(전산업무비의 사업코드)의 해석은 행의 처리 방식과 무관하게 {@link
 * #validateAlways}로 늘 검사합니다. 매칭 자체(어느 원장을 가리키는지, 원장 후보가 없는지)는 이 클래스가 아니라 {@link
 * MigrationMatchDiagnostics}가 맡습니다.
 */
@Component
public class MigrationValidator {

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
        MigrationCellChecks.requireText(sheet, row, "requestDetail", overrides, out);
        MigrationCellChecks.limitLength(
                sheet, row, "requestDetail", 100, overrides, out); // CTT_NM VARCHAR2(100 CHAR)
        MigrationCellChecks.limitBytes(
                sheet, row, "vendorName", 100, overrides, out); // CTT_OPP_NM VARCHAR2(100 BYTE)
        MigrationCellChecks.limitLength(
                sheet, row, "remark", 200, overrides, out); // IND_RSN VARCHAR2(200 CHAR)
        MigrationCellChecks.resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);
    }

    /**
     * 부서·비목은 전산업무비 매칭 키({@link
     * com.kdb.it.domain.migration.service.adapter.AllocationIntent.MatchKey#ofCost})의 재료라 항상 해석합니다.
     *
     * <p>사업코드도 항상 해석합니다 — <b>매칭된</b> 전산업무비의 빈 {@code BG_UNT_ABUS_C}를 채우는 값이라(§4.1), 생성 전용으로 두면 정작 그
     * 값을 쓰는 행이 검증을 비켜 가 3자 초과 원문이 flush에서 {@code ORA-12899}로 터지거나 오타가 조용히 저장돼 엉뚱한 예산 집계 버킷에 들어갑니다.
     */
    private void validateCostRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        MigrationCellChecks.resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);
        MigrationCellChecks.resolveCodeCell(
                sheet, row, "abusCode", index.abusUnitNameByCode(), overrides, out, "사업코드", false);

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

        MigrationCellChecks.checkCurrency(sheet, row, index, overrides, out);
        MigrationCellChecks.checkAmount(sheet, row, index, overrides, out);
    }

    private void validateProjectRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        MigrationCellChecks.limitLength(
                sheet, row, "projectName", 100, overrides, out); // ABUS_NM VARCHAR2(100 CHAR)
        MigrationCellChecks.limitLength(
                sheet, row, "projectType", 300, overrides, out); // ABUS_PPO_CONE
        MigrationCellChecks.limitLength(
                sheet, row, "projectOutline", 1000, overrides, out); // ABUS_CONE
        MigrationCellChecks.limitLength(
                sheet, row, "headquarters", 100, overrides, out); // PRLM_HRK_OGZ_C_CONE
        MigrationCellChecks.resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);
        MigrationCellChecks.resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);
        MigrationCellChecks.resolveOrgCell(sheet, row, "itTeamName", index, overrides, out, false);
        MigrationCellChecks.resolveUserCell(
                sheet, row, "managerName", "deptName", index, overrides, out);
        MigrationCellChecks.resolveUserCell(
                sheet, row, "teamLeaderName", "deptName", index, overrides, out);
        MigrationCellChecks.resolveCodeCell(
                sheet,
                row,
                "feasibility",
                index.exePttCodeByName(),
                overrides,
                out,
                "추진가능성",
                true); // EXE_PTT_YN VARCHAR2(1)
        MigrationCellChecks.resolveCodeCell(
                sheet,
                row,
                "delegationLabel",
                index.edrtCodeByName(),
                overrides,
                out,
                "전결권",
                true); // IT_PTL_EDRT_TC VARCHAR2(2)
        MigrationCellChecks.checkCapitalIoeOverrides(sheet, row, index, overrides, out);
    }

    /** 사업명은 정보화사업 매칭 키({@code MatchKey.ofProjectName})의 재료라 항상 해석(존재 확인)합니다. */
    private void validateProjectRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        MigrationCellChecks.requireText(sheet, row, "projectName", overrides, out);
        MigrationCellChecks.checkYm(sheet, row, "startYm", overrides, out);
        MigrationCellChecks.checkYm(sheet, row, "endYm", overrides, out);
        MigrationCellChecks.checkRate(sheet, row, "adjustRate", overrides, out);
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
        MigrationCellChecks.requireText(sheet, row, "itemName", overrides, out);
        MigrationCellChecks.limitBytes(
                sheet, row, "itemName", 100, overrides, out); // GCL_NM VARCHAR2(100 BYTE)
    }

    /** 부점명은 경상사업 매칭 키({@code MatchKey.ofOrdinaryDept})의 재료라 항상 해석합니다. */
    private void validateDelegatedRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        // 부점명은 병합 셀이라 이어지는 행에서 비는 것이 정상이다(forward-fill). 첫 행 검사는 시트 단위로 별도 수행한다.
        MigrationCellChecks.resolveOrgCell(sheet, row, "branchName", index, overrides, out, false);
        MigrationCellChecks.checkCurrency(sheet, row, index, overrides, out);
        MigrationCellChecks.checkAmount(sheet, row, index, overrides, out);
    }

    private void validatePlanRowForCreate(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        MigrationCellChecks.requireText(sheet, row, "projectName", overrides, out);
        MigrationCellChecks.limitLength(sheet, row, "projectName", 100, overrides, out);
    }

    private void validatePlanRowAlways(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationYearSnapshot.Data snapshot,
            Set<String> namesInThisImport,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        MigrationCellChecks.checkYm(sheet, row, "startYm", overrides, out);
        MigrationCellChecks.checkYm(sheet, row, "endYm", overrides, out);

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
}
