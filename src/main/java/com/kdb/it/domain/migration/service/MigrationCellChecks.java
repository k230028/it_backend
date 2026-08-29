package com.kdb.it.domain.migration.service;

import com.kdb.it.common.util.Utf8ByteLimit;
import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 셀 값 자체가 올바른지에 대한 공통 판정을 모읍니다.
 *
 * <p>{@link MigrationValidator}가 "이 시트의 어느 칸에 어떤 검사를 거는가"를 정하고, 이 클래스는 시트 종류와 무관한 "값이 올바른가"만 판단합니다.
 * 두 관심사를 한 파일에 두면 {@code MigrationValidator}가 너무 커져({@code MaxLinesRatchetTest}) 시트별 라우팅을 읽을 때 셀 판정
 * 보일러플레이트를 계속 넘겨야 합니다.
 *
 * <p>보정값 우선 원칙은 {@link MigrationDiagnostics#cell}을 거치는 모든 호출에 그대로 적용됩니다 — 이 클래스는 그 원칙을 새로 만들지 않고
 * 따릅니다.
 */
final class MigrationCellChecks {

    /** `'26.05` 또는 `26.05` 형태의 연월 표기. */
    private static final Pattern YM = Pattern.compile("^'?(\\d{2})\\.(\\d{1,2})월?$");

    /** 원화 금액 대조 허용 오차 (원). */
    private static final BigDecimal AMOUNT_TOLERANCE = BigDecimal.ONE;

    private MigrationCellChecks() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 품목 비목 보정값(`devAmountIoeC`·`hwAmountIoeC`·`swAmountIoeC`)이 자본예산 계열 비목코드인지 확인합니다.
     *
     * <p>보정값이 없는 컬럼은 기본 비목({@link MigrationIoeCodes})을 그대로 쓰므로 검사하지 않습니다. 이 보정값은 품목을 새로 만들 때만 의미가
     * 있어(매칭된 행은 품목을 만들지 않습니다) {@code MigrationValidator.validateProjectRowForCreate}에서만 검사합니다 —
     * {@code validateAlways}에 두면 관리자가 CREATE_NEW로 비목을 보정했다가 나중에 MATCH로 바꿔도 이미 무의미해진 값 때문에 풀 수 없는
     * BLOCKER가 남습니다.
     */
    static void checkCapitalIoeOverrides(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        for (String column : MigrationColumns.CAPITAL_IOE_OVERRIDES) {
            String override =
                    overrides.get(
                            MigrationDiagnostics.overrideKey(sheet.kind(), row.excelRow(), column));
            if (override != null && !MigrationIoeCodes.CAPITAL_CODES.contains(override)) {
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                MigrationDiagnosticCode.CODE_UNRESOLVED,
                                "'" + override + "'는 자본예산 계열 비목이 아닙니다.",
                                MigrationDiagnostics.candidatesOfIoe(index, true)));
            }
        }
    }

    /**
     * 통화 코드가 예산환율 공통코드에 있는지 확인합니다 (§3.7).
     *
     * <p>금액 대조({@link #checkAmountPair})가 아니라 행 단위로 한 번만 검사합니다. 위임예산은 HW·SW 금액 쌍이 통화 컬럼 하나를 공유하므로
     * 금액 쌍마다 검사하면 같은 셀에 같은 진단이 두 번 붙고, 두 쌍이 모두 0인 행에서는 아예 검사되지 않아 미등록 통화가 {@code CUR_C
     * VARCHAR2(3)}까지 그대로 흘러갑니다.
     */
    static void checkCurrency(
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
                            MigrationDiagnosticCode.CODE_UNRESOLVED,
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
    static void checkAmount(
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
    private static void checkAmountPair(
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
                            MigrationDiagnosticCode.AMOUNT_MISMATCH,
                            "서버 재계산액 "
                                    + recomputed.stripTrailingZeros().toPlainString()
                                    + "원이 엑셀 원화열 "
                                    + excelKrw.stripTrailingZeros().toPlainString()
                                    + "원과 다릅니다. 재계산액으로 저장됩니다."));
        }
    }

    static void checkRate(
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
                            MigrationDiagnosticCode.RATE_OUT_OF_RANGE,
                            "조정비율 "
                                    + rate.toPlainString()
                                    + "이 0~1 범위를 벗어났습니다. 편성률은 0~100으로 잘립니다."));
        }
    }

    static void checkYm(
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
                            MigrationDiagnosticCode.DATE_UNPARSEABLE,
                            "'" + value + "'을 연월로 읽지 못했습니다. 날짜가 비워진 채 저장됩니다."));
        }
    }

    /** 필수값이 비어 있지 않은지 확인합니다. 길이 제한은 호출자가 컬럼의 문자 의미에 맞는 검사를 이어 붙입니다. */
    static void requireText(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        if (MigrationDiagnostics.cell(row, column, overrides, sheet).isBlank()) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            MigrationDiagnosticCode.REQUIRED_MISSING,
                            "필수 값이 비어 있습니다.",
                            List.of()));
        }
    }

    /** 문자 수 상한을 확인합니다 ({@code VARCHAR2(n CHAR)} 컬럼). */
    static void limitLength(
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
                            MigrationDiagnosticCode.LENGTH_EXCEEDED,
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
    static void limitBytes(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            int maxBytes,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String value = MigrationDiagnostics.cell(row, column, overrides, sheet);
        int bytes = Utf8ByteLimit.length(value);
        if (bytes > maxBytes) {
            out.add(
                    MigrationDiagnostics.blocker(
                            sheet,
                            row,
                            column,
                            MigrationDiagnosticCode.LENGTH_EXCEEDED,
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
    static void resolveCodeCell(
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
                            MigrationDiagnosticCode.CODE_UNRESOLVED,
                            "'" + value + "'에 해당하는 " + label + " 코드를 찾지 못했습니다. 목록에서 선택해 주세요.",
                            MigrationDiagnostics.candidatesOfCatalog(codeCatalog, byName)));
        }
    }

    static void resolveOrgCell(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out,
            boolean required) {
        String overrideValue =
                overrides.get(
                        MigrationDiagnostics.overrideKey(sheet.kind(), row.excelRow(), column));
        if (overrideValue != null) {
            if (index.org().orgNameOf(overrideValue) == null) {
                // 보정값이 실재하지 않으면(오래된 코드 등) 원본 셀 값으로 다시 후보를 뽑아 드롭다운을 살려 둔다
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                MigrationDiagnosticCode.ORG_UNRESOLVED,
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
                            MigrationDiagnosticCode.ORG_AMBIGUOUS,
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
                            MigrationDiagnosticCode.ORG_UNRESOLVED,
                            MigrationDiagnostics.unresolvedMessage(
                                    "'" + value + "'에 해당하는 조직을 찾지 못했습니다.", resolution),
                            resolution.candidates()));
        }
    }

    static void resolveUserCell(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            String deptColumn,
            MigrationLookupIndex index,
            Map<String, String> overrides,
            List<MigrationDto.CellDiagnostic> out) {
        String overrideValue =
                overrides.get(
                        MigrationDiagnostics.overrideKey(sheet.kind(), row.excelRow(), column));
        if (overrideValue != null) {
            if (!index.org().userExists(overrideValue)) {
                out.add(
                        MigrationDiagnostics.blocker(
                                sheet,
                                row,
                                column,
                                MigrationDiagnosticCode.USER_UNRESOLVED,
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
                        resolution.isAmbiguous()
                                ? MigrationDiagnosticCode.USER_AMBIGUOUS
                                : MigrationDiagnosticCode.USER_UNRESOLVED,
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
        String override =
                overrides.get(
                        MigrationDiagnostics.overrideKey(sheet.kind(), row.excelRow(), column));
        if (override != null) {
            return index.org().orgNameOf(override) != null ? override : null;
        }
        return index.org().resolveOrg(MigrationDiagnostics.rawCell(row, column)).code();
    }
}
