package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 진단 카탈로그(§6.1)의 코드별 발생 조건을 고정합니다. */
class MigrationValidatorTest {

    private final MigrationValidator validator = new MigrationValidator();

    /** 부서명이 CORGNI에 없으면 BLOCKER. */
    @Test
    @DisplayName("미해석 부서명은 ORG_UNRESOLVED BLOCKER를 낸다")
    void 미해석_부서명은_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("deptName", "없는부서"))))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("ORG_UNRESOLVED");
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(d.column()).isEqualTo("deptName");
                            assertThat(d.excelRow()).isEqualTo(2);
                        });
    }

    /** 후보가 둘 이상이면 ORG_AMBIGUOUS이며 후보가 함께 온다. */
    @Test
    @DisplayName("중의적 부서명은 ORG_AMBIGUOUS와 후보를 낸다")
    void 중의적_부서명은_후보를_낸다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("deptName", "금융공학"))))),
                        TestSnapshots.indexWithOrgs("0450", "금융공학실", "0451", "금융공학실 퀀트인프라팀"),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "ORG_AMBIGUOUS".equals(d.code()))
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).hasSize(2));
    }

    /** 비목명이 코드표에 없으면 CODE_UNRESOLVED. 전산회의비·국외전산기타제비가 실제 사례다(§3.1). */
    @Test
    @DisplayName("코드표에 없는 비목명은 CODE_UNRESOLVED를 낸다")
    void 미등록_비목명은_코드미해석이다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("ioeName", "전산회의비"))))),
                        TestSnapshots.indexWithIoe("001", "국내전산임차료"),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.column()).isEqualTo("ioeName");
                        });
    }

    /** 보정값이 오면 해당 셀의 미해석 진단이 사라진다. */
    @Test
    @DisplayName("보정값이 있으면 그 셀의 미해석 진단을 내지 않는다")
    void 보정값이_있으면_진단을_내지_않는다() {
        Map<String, String> overrides =
                Map.of(MigrationValidator.overrideKey(SheetKind.COST, 2, "ioeName"), "008");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("ioeName", "외주용역비"))))),
                        TestSnapshots.indexWithIoe("008", "외주용역(외주운영/관제 등)"),
                        TestSnapshots.empty("2026"),
                        overrides);

        assertThat(result).noneMatch(d -> "CODE_UNRESOLVED".equals(d.code()));
    }

    /** 필수값(계약명)이 비면 REQUIRED_MISSING. */
    @Test
    @DisplayName("계약명이 비면 REQUIRED_MISSING을 낸다")
    void 계약명이_비면_필수값누락이다() {
        Map<String, String> cells = costCells(Map.of("requestDetail", ""));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("REQUIRED_MISSING");
                            assertThat(d.column()).isEqualTo("requestDetail");
                        });
    }

    /** 계약명 100자·비고 200자 초과는 LENGTH_EXCEEDED. */
    @Test
    @DisplayName("물리 길이를 넘는 값은 LENGTH_EXCEEDED를 낸다")
    void 길이초과는_블로커다() {
        Map<String, String> cells = costCells(Map.of("requestDetail", "가".repeat(101)));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("LENGTH_EXCEEDED");
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                        });
    }

    /** 같은 자연키의 전산업무비가 이미 있으면 DUPLICATE_EXISTS. */
    @Test
    @DisplayName("자연키가 중복되면 DUPLICATE_EXISTS를 낸다")
    void 자연키_중복은_블로커다() {
        Map<String, String> cells =
                costCells(
                        Map.of(
                                "abusCode", "571",
                                "ioeName", "국내전산임차료",
                                "vendorName", "커브",
                                "requestDetail", "올인원워크스페이스"));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.indexWithIoe("001", "국내전산임차료"),
                        TestSnapshots.snapshotWithCostKey("2026", "2026|571|001|커브|올인원워크스페이스"),
                        Map.of());

        assertThat(result).anyMatch(d -> "DUPLICATE_EXISTS".equals(d.code()));
    }

    /** 외화 재계산값이 엑셀 원화열과 1원 넘게 다르면 WARNING. */
    @Test
    @DisplayName("금액 불일치는 AMOUNT_MISMATCH WARNING이며 반영을 막지 않는다")
    void 금액불일치는_경고다() {
        Map<String, String> cells =
                costCells(
                        Map.of(
                                "currency", "GBP",
                                "fcAmount", "2890",
                                "krwAmount", "9999")); // 2890 × 1924 / 1000 = 5560.36 이어야 한다

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.indexWithXcr("GBP", "1924"),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "AMOUNT_MISMATCH".equals(d.code()))
                .singleElement()
                .satisfies(d -> assertThat(d.severity()).isEqualTo(MigrationDto.Severity.WARNING));
    }

    /** 조정비율이 0~100 밖이면 WARNING. */
    @Test
    @DisplayName("편성률 범위를 벗어나면 RATE_OUT_OF_RANGE WARNING을 낸다")
    void 편성률_범위이탈은_경고다() {
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("adjustRate", "1.5");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "RATE_OUT_OF_RANGE".equals(d.code()))
                .singleElement()
                .satisfies(d -> assertThat(d.severity()).isEqualTo(MigrationDto.Severity.WARNING));
    }

    /** `'26.05` 형식이 아니면 WARNING이며 날짜를 null로 둔다. */
    @Test
    @DisplayName("파싱 불가 기간은 DATE_UNPARSEABLE WARNING을 낸다")
    void 파싱불가_기간은_경고다() {
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("startYm", "미정");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result).anyMatch(d -> "DATE_UNPARSEABLE".equals(d.code()));
    }

    /** 부문계획 행의 사업이 같은 반영에도 DB에도 없으면 PROJECT_NOT_FOUND BLOCKER. */
    @Test
    @DisplayName("대상 사업이 없는 부문계획 행은 PROJECT_NOT_FOUND를 낸다")
    void 대상사업이_없는_부문계획행은_블로커다() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT)) {
            cells.put(column, "");
        }
        cells.put("projectName", "포탈에 없는 사업");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result).anyMatch(d -> "PROJECT_NOT_FOUND".equals(d.code()));
    }

    /** 같은 반영에 자본예산 행으로 들어오는 사업은 PROJECT_NOT_FOUND가 아니다. */
    @Test
    @DisplayName("같은 반영의 자본예산 시트에 있는 사업은 PROJECT_NOT_FOUND를 내지 않는다")
    void 같은반영에_있는_사업은_블로커가_아니다() {
        Map<String, String> capital = new LinkedHashMap<>(capitalCells());
        capital.put("projectName", "웹한글 기안기 도입");
        Map<String, String> plan = new LinkedHashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT)) {
            plan.put(column, "");
        }
        plan.put("projectName", "웹한글 기안기 도입");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(row(2, capital))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT, "2026", List.of(row(2, plan)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of());

        assertThat(result).noneMatch(d -> "PROJECT_NOT_FOUND".equals(d.code()));
    }

    private static MigrationDto.SheetPayload costSheet(MigrationDto.NormalizedRow row) {
        return new MigrationDto.SheetPayload(SheetKind.COST, "2026", List.of(row));
    }

    private static MigrationDto.NormalizedRow row(int excelRow, Map<String, String> cells) {
        return new MigrationDto.NormalizedRow(excelRow, cells);
    }

    /** 전 컬럼을 유효한 기본값으로 채운 뒤 인자로 받은 것만 덮어씁니다. 검사 대상 외의 진단이 섞이지 않게 합니다. */
    private static Map<String, String> costCells(Map<String, String> overrides) {
        Map<String, String> cells = new HashMap<>();
        cells.put("abusCode", "571");
        cells.put("ioeName", "국내전산임차료");
        cells.put("abusTcLabel", "계속");
        cells.put("vendorName", "커브");
        cells.put("requestDetail", "올인원워크스페이스");
        cells.put("securityFlag", "");
        cells.put("terminalFlag", "");
        cells.put("deptName", "IT기획부");
        cells.put("teamName", "IT기획팀");
        cells.put("currency", "KRW");
        cells.put("fcAmount", "");
        cells.put("krwAmount", "15401");
        cells.put("remark", "전년도 동일수준");
        cells.putAll(overrides);
        return cells;
    }

    private static Map<String, String> capitalCells() {
        Map<String, String> cells = new HashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.CAPITAL_PROJECT)) {
            cells.put(column, "");
        }
        cells.put("projectName", "테스트 사업");
        cells.put("progressLabel", "신규");
        cells.put("startYm", "'26.05");
        cells.put("endYm", "'26.12");
        cells.put("devAmount", "1406");
        cells.put("adjustRate", "0.7");
        return cells;
    }
}
