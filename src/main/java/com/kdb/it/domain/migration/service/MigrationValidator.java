package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 */
@Component
public class MigrationValidator {

    /** `'26.05` 또는 `26.05` 형태의 연월 표기. */
    private static final Pattern YM = Pattern.compile("^'?(\\d{2})\\.(\\d{1,2})월?$");

    /** 원화 금액 대조 허용 오차 (원). */
    private static final BigDecimal AMOUNT_TOLERANCE = BigDecimal.ONE;

    /** 자본예산 계열 비목코드 — 개발비·기계장치·기타무형자산 (IoeCategories.CAPITAL_CTPS에 대응). */
    private static final Set<String> CAPITAL_IOE_CODES =
            Set.of("101", "102", "103", "104", "105", "106", "107");

    /**
     * 보정값 조회 키를 만듭니다. commit이 {@code CellOverride} 목록을 이 키의 맵으로 접어 넘깁니다.
     *
     * @param sheet 시트 종류
     * @param excelRow 엑셀 행 번호
     * @param column 정규 컬럼 id
     * @return 파이프로 이은 키
     */
    public static String overrideKey(SheetKind sheet, int excelRow, String column) {
        return sheet.name() + "|" + excelRow + "|" + column;
    }

    /**
     * 시트 전체를 검증해 진단 목록을 만듭니다.
     *
     * @param sheets 올린 시트 목록
     * @param index 조직·비목·환율 조회 인덱스
     * @param snapshot 예산연도 기존 상태
     * @param overrides 보정값 맵 ({@link #overrideKey} 키)
     * @return 진단 목록. 문제가 없으면 빈 목록
     */
    public List<MigrationDto.CellDiagnostic> validate(
            List<MigrationDto.SheetPayload> sheets,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides) {
        List<MigrationDto.CellDiagnostic> out = new ArrayList<>();
        Set<String> namesInThisImport = collectProjectNames(sheets);

        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() == SheetKind.DELEGATED_BUDGET) {
                checkDelegatedFirstBranch(sheet, overrides, out);
            }
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                switch (sheet.kind()) {
                    case COST -> validateCostRow(sheet, row, index, snapshot, overrides, out);
                    case CAPITAL_PROJECT ->
                            validateProjectRow(sheet, row, index, snapshot, overrides, out);
                    case DELEGATED_BUDGET ->
                            validateDelegatedRow(sheet, row, index, overrides, out);
                    case PLAN_ADJUSTMENT ->
                            validatePlanRow(
                                    sheet, row, index, snapshot, namesInThisImport, overrides, out);
                }
            }
        }
        return out;
    }

    /** 같은 반영에 포함된 사업명(자본예산·위임예산)을 모읍니다. PROJECT_NOT_FOUND 판정 기준입니다. */
    private Set<String> collectProjectNames(List<MigrationDto.SheetPayload> sheets) {
        Set<String> names = new LinkedHashSet<>();
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() != SheetKind.CAPITAL_PROJECT) {
                continue;
            }
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                names.add(
                        MigrationYearSnapshot.normalizeName(
                                cell(row, "projectName", Map.of(), sheet)));
            }
        }
        return names;
    }

    private void validateCostRow(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "requestDetail", 100, overrides, out);
        limitLength(sheet, row, "remark", 200, overrides, out);
        resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);
        resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);

        String ioeName = cell(row, "ioeName", overrides, sheet);
        String ioeCode = overrides.get(overrideKey(sheet.kind(), row.excelRow(), "ioeName"));
        if (ioeCode == null) {
            ioeCode = index.ioeCodeByName().get(ioeName);
        }
        if (ioeCode == null) {
            out.add(
                    blocker(
                            sheet,
                            row,
                            "ioeName",
                            "CODE_UNRESOLVED",
                            "비목 '" + ioeName + "'에 대응하는 비목코드를 찾지 못했습니다. 비목을 직접 선택해 주세요.",
                            candidatesOfIoe(index)));
        }

        checkAmount(sheet, row, index, out);

        if (ioeCode != null) {
            String key =
                    MigrationYearSnapshot.costNaturalKey(
                            sheet.bseYy(),
                            cell(row, "abusCode", overrides, sheet),
                            ioeCode,
                            cell(row, "vendorName", overrides, sheet),
                            cell(row, "requestDetail", overrides, sheet));
            if (snapshot.costNaturalKeys().contains(key)) {
                out.add(
                        blocker(
                                sheet,
                                row,
                                null,
                                "DUPLICATE_EXISTS",
                                "같은 사업코드·비목·계약상대처·계약명의 전산업무비가 이미 있습니다. 이 행은 제외하거나 기존 행을 확인해 주세요.",
                                List.of()));
            }
        }
    }

    private void validateProjectRow(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "projectName", 100, overrides, out);
        resolveOrgCell(sheet, row, "deptName", index, overrides, out, true);
        resolveOrgCell(sheet, row, "teamName", index, overrides, out, false);
        resolveUserCell(sheet, row, "managerName", "deptName", index, overrides, out);
        resolveUserCell(sheet, row, "teamLeaderName", "deptName", index, overrides, out);
        checkYm(sheet, row, "startYm", overrides, out);
        checkYm(sheet, row, "endYm", overrides, out);
        checkRate(sheet, row, "adjustRate", overrides, out);
        checkCapitalIoeOverrides(sheet, row, overrides, out);

        String normalized =
                MigrationYearSnapshot.normalizeName(cell(row, "projectName", overrides, sheet));
        if (snapshot.projectNoByName(normalized) != null) {
            out.add(
                    blocker(
                            sheet,
                            row,
                            "projectName",
                            "DUPLICATE_EXISTS",
                            "같은 예산연도에 같은 사업명의 사업이 이미 있습니다.",
                            List.of()));
        }
    }

    /**
     * 품목 비목 보정값(`devAmountIoeC`·`hwAmountIoeC`·`swAmountIoeC`)이 자본예산 계열 비목코드인지 확인합니다.
     *
     * <p>보정값이 없는 컬럼은 기본 비목(개발비 103·기계장치 101·기타무형 106)을 그대로 쓰므로 검사하지 않습니다.
     */
    private void checkCapitalIoeOverrides(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        for (String column : List.of("devAmountIoeC", "hwAmountIoeC", "swAmountIoeC")) {
            String override = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
            if (override != null && !CAPITAL_IOE_CODES.contains(override)) {
                out.add(
                        blocker(
                                sheet,
                                row,
                                column,
                                "CODE_UNRESOLVED",
                                "'" + override + "'는 자본예산 계열 비목이 아닙니다.",
                                List.of()));
            }
        }
    }

    /**
     * 위임예산 시트 첫 행의 부점명을 확인합니다.
     *
     * <p>부점명은 병합 셀이라 이어지는 행에서 비는 것이 정상입니다(forward-fill 대상). 그러나 첫 행부터 비어 있으면 이후 행 전부를 귀속시킬 사업이 없으므로
     * 시트 단위 BLOCKER로 막습니다. 행별 검사({@link #validateDelegatedRow})는 이 전제를 알고 부점명을 필수값으로 요구하지 않습니다.
     */
    private void checkDelegatedFirstBranch(
            MigrationDto.SheetPayload sheet,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        if (sheet.rows().isEmpty()) {
            return;
        }
        MigrationDto.NormalizedRow first = sheet.rows().get(0);
        if (cell(first, "branchName", overrides, sheet).isBlank()) {
            out.add(
                    blocker(
                            sheet,
                            first,
                            "branchName",
                            "REQUIRED_MISSING",
                            "첫 행의 부점명이 비어 있어 이후 행을 귀속시킬 사업을 만들 수 없습니다.",
                            List.of()));
        }
    }

    private void validateDelegatedRow(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "itemName", 100, overrides, out);
        // 부점명은 병합 셀이라 이어지는 행에서 비는 것이 정상이다(forward-fill). 첫 행 검사는 시트 단위로 별도 수행한다.
        resolveOrgCell(sheet, row, "branchName", index, overrides, out, false);
        checkAmount(sheet, row, index, out);
    }

    private void validatePlanRow(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            MigrationYearSnapshot.Data snapshot,
            Set<String> namesInThisImport,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        requireText(sheet, row, "projectName", 100, overrides, out);
        checkYm(sheet, row, "startYm", overrides, out);
        checkYm(sheet, row, "endYm", overrides, out);

        String normalized =
                MigrationYearSnapshot.normalizeName(cell(row, "projectName", overrides, sheet));
        boolean inImport = namesInThisImport.contains(normalized);
        boolean inDb = snapshot.projectNoByName(normalized) != null;
        if (!inImport && !inDb) {
            out.add(
                    blocker(
                            sheet,
                            row,
                            "projectName",
                            "PROJECT_NOT_FOUND",
                            "이 사업이 같은 반영의 자본예산 시트에도, 포탈에도 없습니다. 자본예산 편성요구서를 함께 올리거나 사업명을 확인해 주세요.",
                            List.of()));
        }
        if (snapshot.planExists("조정")) {
            out.add(
                    blocker(
                            sheet,
                            row,
                            null,
                            "DUPLICATE_EXISTS",
                            sheet.bseYy() + "년 조정 계획이 이미 있습니다.",
                            List.of()));
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
            List<MigrationDto.CellDiagnostic> out) {
        if (sheet.kind() == SheetKind.DELEGATED_BUDGET) {
            checkAmountPair(sheet, row, index, out, "hwFcAmount", "hwKrwAmount");
            checkAmountPair(sheet, row, index, out, "swFcAmount", "swKrwAmount");
            return;
        }
        checkAmountPair(sheet, row, index, out, "fcAmount", "krwAmount");
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
            List<MigrationDto.CellDiagnostic> out,
            String fcColumn,
            String krwColumn) {
        String currency = cell(row, "currency", Map.of(), sheet);
        BigDecimal fc = number(cell(row, fcColumn, Map.of(), sheet));
        BigDecimal krw = number(cell(row, krwColumn, Map.of(), sheet));
        if (currency.isBlank() || "KRW".equals(currency) || fc == null || krw == null) {
            return;
        }
        // 위임예산의 두 쌍 중 이 행에서 쓰지 않은 쌍은 0으로 채워져 온다. 빈 쌍까지 환율 미등록으로 진단하지 않도록 건너뛴다.
        if (fc.compareTo(BigDecimal.ZERO) == 0 && krw.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal xcr = index.xcrByCurrency().get(currency);
        if (xcr == null) {
            if (out.stream()
                    .noneMatch(
                            d ->
                                    d.excelRow() == row.excelRow()
                                            && "currency".equals(d.column())
                                            && "CODE_UNRESOLVED".equals(d.code()))) {
                out.add(
                        blocker(
                                sheet,
                                row,
                                "currency",
                                "CODE_UNRESOLVED",
                                "통화 '" + currency + "'의 예산환율이 공통코드에 없습니다. 환율 시드를 먼저 적용해 주세요.",
                                List.of()));
            }
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
                    warning(
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
        BigDecimal rate = number(cell(row, column, overrides, sheet));
        if (rate == null) {
            return;
        }
        BigDecimal percent = rate.multiply(new BigDecimal("100"));
        if (percent.compareTo(BigDecimal.ZERO) < 0
                || percent.compareTo(new BigDecimal("100")) > 0) {
            out.add(
                    warning(
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
        String value = cell(row, column, overrides, sheet);
        if (value.isBlank()) {
            return;
        }
        if (!YM.matcher(value.trim()).matches()) {
            out.add(
                    warning(
                            sheet,
                            row,
                            column,
                            "DATE_UNPARSEABLE",
                            "'" + value + "'을 연월로 읽지 못했습니다. 날짜가 비워진 채 저장됩니다."));
        }
    }

    private void requireText(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            int maxLength,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = cell(row, column, overrides, sheet);
        if (value.isBlank()) {
            out.add(blocker(sheet, row, column, "REQUIRED_MISSING", "필수 값이 비어 있습니다.", List.of()));
            return;
        }
        limitLength(sheet, row, column, maxLength, overrides, out);
    }

    private void limitLength(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            int maxLength,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = cell(row, column, overrides, sheet);
        if (value.length() > maxLength) {
            out.add(
                    blocker(
                            sheet,
                            row,
                            column,
                            "LENGTH_EXCEEDED",
                            "값이 " + value.length() + "자로 최대 " + maxLength + "자를 넘습니다.",
                            List.of()));
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
                out.add(
                        blocker(
                                sheet,
                                row,
                                column,
                                "ORG_UNRESOLVED",
                                "보정값 '" + overrideValue + "'에 해당하는 조직코드를 찾지 못했습니다. 조직을 다시 선택해 주세요.",
                                List.of()));
            }
            return;
        }
        String value = cell(row, column, overrides, sheet);
        OrgIdentityResolver.Resolution resolution = index.org().resolveOrg(value);
        if (resolution.code() != null) {
            return;
        }
        if (resolution.isAmbiguous()) {
            out.add(
                    blocker(
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
                    blocker(
                            sheet,
                            row,
                            column,
                            "ORG_UNRESOLVED",
                            "'" + value + "'에 해당하는 조직을 찾지 못했습니다. 조직을 선택해 주세요.",
                            List.of()));
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
                        blocker(
                                sheet,
                                row,
                                column,
                                "USER_UNRESOLVED",
                                "보정값 '" + overrideValue + "'에 해당하는 사번을 찾지 못했습니다. 담당자를 다시 선택해 주세요.",
                                List.of()));
            }
            return;
        }
        String value = cell(row, column, overrides, sheet);
        if (value.isBlank()) {
            return;
        }
        String deptHint = effectiveOrgCode(sheet, row, deptColumn, index, overrides);
        OrgIdentityResolver.Resolution resolution = index.org().resolveUser(value, deptHint);
        if (resolution.code() != null) {
            return;
        }
        out.add(
                blocker(
                        sheet,
                        row,
                        column,
                        resolution.isAmbiguous() ? "USER_AMBIGUOUS" : "USER_UNRESOLVED",
                        resolution.isAmbiguous()
                                ? "'" + value + "'에 해당하는 직원이 여러 명입니다. 한 명을 선택해 주세요."
                                : "'" + value + "'에 해당하는 직원을 찾지 못했습니다. 담당자를 선택해 주세요.",
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
        String value = row.cells().get(column);
        return index.org().resolveOrg(value == null ? "" : value).code();
    }

    private static List<MigrationDto.Candidate> candidatesOfIoe(MigrationLookupIndex index) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        index.ioeCodeByName()
                .forEach((name, code) -> out.add(new MigrationDto.Candidate(code, name)));
        return out;
    }

    private static MigrationDto.CellDiagnostic blocker(
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

    private static MigrationDto.CellDiagnostic warning(
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

    /** 보정값이 있으면 그 값을, 없으면 원본 셀 값을 반환합니다. null은 빈 문자열로 접습니다. */
    private static String cell(
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            MigrationDto.SheetPayload sheet) {
        String override = overrides.get(overrideKey(sheet.kind(), row.excelRow(), column));
        if (override != null) {
            return override;
        }
        String value = row.cells().get(column);
        return value == null ? "" : value;
    }

    /** 쉼표를 제거하고 숫자로 파싱합니다. 숫자가 아니면 null. */
    private static BigDecimal number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
