package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 진단 카탈로그(§6.1)의 코드별 발생 조건을 고정합니다. */
class MigrationValidatorTest {

    private final MigrationValidator validator = new MigrationValidator();

    /** 부서명이 CORGNI에 없으면 BLOCKER. deptName은 매칭 키 재료라 매칭 여부와 무관하게 항상 검사한다(createNewRows 없이도 발생). */
    @Test
    @DisplayName("미해석 부서명은 ORG_UNRESOLVED BLOCKER를 낸다")
    void 미해석_부서명은_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("deptName", "없는부서"))))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
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
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "ORG_AMBIGUOUS".equals(d.code()))
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).hasSize(2));
    }

    /** 비목명이 코드표에 없으면 CODE_UNRESOLVED. 전산회의비·국외전산기타제비가 실제 사례다(§3.1). ioeName도 매칭 키 재료라 항상 검사한다. */
    @Test
    @DisplayName("코드표에 없는 비목명은 CODE_UNRESOLVED를 낸다")
    void 미등록_비목명은_코드미해석이다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("ioeName", "전산회의비"))))),
                        TestSnapshots.indexWithIoe("001", "국내전산임차료"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.column()).isEqualTo("ioeName");
                        });
    }

    /** 보정값이 코드표에 실재하는 코드값이면 해당 셀의 미해석 진단이 사라진다. */
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
                        overrides,
                        Map.of());

        assertThat(result)
                .noneMatch(d -> "CODE_UNRESOLVED".equals(d.code()) && "ioeName".equals(d.column()));
    }

    /**
     * ioeName 보정값은 이름이 아니라 이미 코드값이므로, resolveOrgCell·resolveUserCell의 override 경로와 같이 코드표에 실재하는지
     * 확인해야 한다. 확인 없이 통과시키면 CostSheetAdapter.resolveIoe의 "미해석이면 이미 코드값이라고 가정한다" 폴백이 검증되지 않은 값을 그대로
     * IOE_C에 써 버린다.
     */
    @Test
    @DisplayName("코드표에 없는 비목코드로 ioeName을 보정하면 CODE_UNRESOLVED를 낸다")
    void 존재하지_않는_비목코드로_보정하면_코드미해석이다() {
        Map<String, String> overrides =
                Map.of(MigrationValidator.overrideKey(SheetKind.COST, 2, "ioeName"), "999");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("ioeName", "외주용역비"))))),
                        TestSnapshots.indexWithIoe("008", "외주용역(외주운영/관제 등)"),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "CODE_UNRESOLVED".equals(d.code()) && "ioeName".equals(d.column()))
                .singleElement()
                .satisfies(d -> assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER));
    }

    /** 필수값(계약명)이 비면 REQUIRED_MISSING. 물리 길이·필수값 검사는 원장을 새로 만드는 행에만 걸리므로 createNewRows에 그 행을 넣는다. */
    @Test
    @DisplayName("계약명이 비면 REQUIRED_MISSING을 낸다")
    void 계약명이_비면_필수값누락이다() {
        Map<String, String> cells = costCells(Map.of("requestDetail", ""));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.COST, Set.of(2)));

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("REQUIRED_MISSING");
                            assertThat(d.column()).isEqualTo("requestDetail");
                        });
    }

    /** 계약명 100자·비고 200자 초과는 LENGTH_EXCEEDED. 물리 길이 검사는 원장을 새로 만드는 행에만 건다. */
    @Test
    @DisplayName("물리 길이를 넘는 값은 LENGTH_EXCEEDED를 낸다")
    void 길이초과는_블로커다() {
        Map<String, String> cells = costCells(Map.of("requestDetail", "가".repeat(101)));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.COST, Set.of(2)));

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("LENGTH_EXCEEDED");
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                        });
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
                                // 서버 재계산 2890 × 1924 = 5,560,360원, 엑셀 원화열 9999 × 1,000(COST 배수) =
                                // 9,999,000원 — 둘이 달라 AMOUNT_MISMATCH가 난다
                                "krwAmount", "9999"));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.indexWithXcr("GBP", "1924"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
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
                        Map.of(),
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
                        Map.of(),
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
                        Map.of(),
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
                        Map.of(),
                        Map.of());

        assertThat(result).noneMatch(d -> "PROJECT_NOT_FOUND".equals(d.code()));
    }

    /** 담당자명이 인덱스에 전혀 없으면 USER_UNRESOLVED. 담당자 해석은 원장을 새로 만드는 행에만 건다. */
    @Test
    @DisplayName("등록되지 않은 담당자명은 USER_UNRESOLVED를 낸다")
    void 미등록_담당자명은_사용자미해석이다() {
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("managerName", "없는사람 과장");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "managerName".equals(d.column()))
                .filteredOn(d -> "USER_UNRESOLVED".equals(d.code()))
                .isNotEmpty();
    }

    /** 동명이인이 있고 부서 힌트가 없으면 좁혀지지 않아 USER_AMBIGUOUS. */
    @Test
    @DisplayName("동명이인 담당자명은 힌트가 없으면 USER_AMBIGUOUS를 낸다")
    void 동명이인_담당자명은_힌트없이_중의적이다() {
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("managerName", "김성원");
        // deptName은 빈 값 그대로 두어 부서 힌트를 주지 않는다.

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.indexWithUsers(
                                List.of(
                                        user("E001", "김성원", null, "0450", "T1", "금융공학팀"),
                                        user("E002", "김성원", null, "0451", "T2", "퀀트인프라팀"))),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "managerName".equals(d.column()))
                .filteredOn(d -> "USER_AMBIGUOUS".equals(d.code()))
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).hasSize(2));
    }

    /**
     * Finding 1 고정 테스트: 중의적 부서명을 보정값(코드)으로 확정하면, 그 코드가 담당자 해석의 부서 힌트로 그대로 쓰여 동명이인을 좁힐 수 있어야 한다. 회귀
     * 전에는 보정값(코드)이 이름 해석기로 넘어가 힌트가 조용히 null이 되고 USER_AMBIGUOUS가 계속 발생했다.
     */
    @Test
    @DisplayName("부서명 보정값이 있으면 담당자 힌트가 코드로 좁혀져 USER_AMBIGUOUS가 사라진다")
    void 부서보정값이_담당자_힌트를_좁힌다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(SheetKind.CAPITAL_PROJECT, 2, "deptName"),
                        "0450");
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("deptName", "금융공학");
        cells.put("managerName", "김성원");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.indexWithOrgsAndUsers(
                                List.of(org("0450", "금융공학실"), org("0451", "금융공학실 퀀트인프라팀")),
                                List.of(
                                        user("E001", "김성원", null, "0450", "T1", "금융공학팀"),
                                        user("E002", "김성원", null, "0451", "T2", "퀀트인프라팀"))),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result).noneMatch(d -> "USER_AMBIGUOUS".equals(d.code()));
    }

    /** 인덱스에 없는 통화는(금액이 채워져 있으면) currency 컬럼에 CODE_UNRESOLVED. */
    @Test
    @DisplayName("환율표에 없는 통화는 CODE_UNRESOLVED를 낸다")
    void 환율미등록_통화는_코드미해석이다() {
        Map<String, String> cells = costCells(Map.of("currency", "GBP", "fcAmount", "100"));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, cells))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "currency".equals(d.column()))
                .filteredOn(d -> "CODE_UNRESOLVED".equals(d.code()))
                .isNotEmpty();
    }

    /** 같은 연도에 '조정' 계획이 이미 있으면 부문계획 행은 DUPLICATE_EXISTS. */
    @Test
    @DisplayName("조정 계획이 이미 있는 연도의 부문계획 행은 DUPLICATE_EXISTS를 낸다")
    void 조정계획_중복은_블로커다() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT)) {
            cells.put(column, "");
        }
        cells.put("projectName", "웹한글 기안기 도입");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.snapshotWithPlanType("2026", "조정"),
                        Map.of(),
                        Map.of());

        assertThat(result).filteredOn(d -> "DUPLICATE_EXISTS".equals(d.code())).isNotEmpty();
    }

    /**
     * 매칭된 행(원장을 새로 만들지 않는 행)에는 물리 길이 검사를 걸지 않는다 — 매칭된 행은 새 원장을 만들지 않으므로 물리 컬럼 제약을 검증할 이유가 없다. 전 행에
     * 걸면 종합본의 긴 사업개요 하나 때문에 편성 전체가 막힌다.
     */
    @Test
    @DisplayName("validate_매칭된_행에는_길이초과_검증을_걸지_않는다")
    void validate_매칭된_행에는_길이초과_검증을_걸지_않는다() {
        MigrationDto.SheetPayload sheet =
                capitalSheet(
                        row(
                                2,
                                capitalCellsWith(
                                        Map.of(
                                                "projectName",
                                                "가".repeat(150),
                                                "swAmount",
                                                "100"))));

        // createNewRows가 비어 있다 = 이 행은 매칭됐다
        List<MigrationDto.CellDiagnostic> out =
                validator.validate(
                        List.of(sheet),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(out)
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("LENGTH_EXCEEDED");
    }

    /** 같은 행이 CREATE_NEW로 결정되면 원장을 새로 만들므로 물리 길이 검사가 다시 걸린다. */
    @Test
    @DisplayName("validate_CREATE_NEW_행에는_길이초과_검증을_건다")
    void validate_CREATE_NEW_행에는_길이초과_검증을_건다() {
        MigrationDto.SheetPayload sheet =
                capitalSheet(
                        row(
                                2,
                                capitalCellsWith(
                                        Map.of(
                                                "projectName",
                                                "가".repeat(150),
                                                "swAmount",
                                                "100"))));

        List<MigrationDto.CellDiagnostic> out =
                validator.validate(
                        List.of(sheet),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(out).extracting(MigrationDto.CellDiagnostic::code).contains("LENGTH_EXCEEDED");
    }

    /**
     * 기존 원장의 사업명과 같아도 더 이상 DUPLICATE_EXISTS를 내지 않는다 — 편성요청서가 원장을 만들고 이 화면은 그 원장에 편성률만 반영하므로, 기존 원장의
     * 존재는 이제 매칭 성공 조건이지 중복이 아니다({@link MigrationMatchDiagnostics}가 매칭을 담당한다).
     */
    @Test
    @DisplayName("validate_기존_사업명이_있어도_DUPLICATE_EXISTS를_내지_않는다")
    void validate_기존_사업명이_있어도_DUPLICATE_EXISTS를_내지_않는다() {
        MigrationDto.SheetPayload sheet =
                capitalSheet(
                        row(
                                2,
                                capitalCellsWith(
                                        Map.of("projectName", "웹한글 기안기 도입", "swAmount", "100"))));

        List<MigrationDto.CellDiagnostic> out =
                validator.validate(
                        List.of(sheet),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.snapshotWithProjectName(
                                "2026",
                                MigrationYearSnapshot.normalizeName("웹한글 기안기 도입"),
                                "PRJ-2026-0001"),
                        Map.of(),
                        Map.of());

        assertThat(out)
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("DUPLICATE_EXISTS");
    }

    /**
     * Finding 2 고정 테스트: 위임예산 시트는 HW·SW 두 금액 쌍이 통화 컬럼 하나를 공유한다. SW 쌍만 채워진 행에서 SW 쌍이 틀리면
     * AMOUNT_MISMATCH가 나야 한다(회귀 전에는 HW 쌍만 대조해 SW 쌍은 절대 검사되지 않았다). 비어 있는(0으로 채워진) HW 쌍은 진단을 내지 않아야
     * 한다.
     */
    @Test
    @DisplayName("위임예산 SW 금액쌍의 불일치도 AMOUNT_MISMATCH를 낸다")
    void 위임예산_SW쌍_불일치도_경고다() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.DELEGATED_BUDGET)) {
            cells.put(column, "");
        }
        cells.put("branchName", "IT기획부");
        cells.put("itemName", "백신 라이선스");
        cells.put("currency", "USD");
        cells.put("hwQty", "");
        cells.put("hwFcAmount", "");
        cells.put("hwKrwAmount", "");
        cells.put("swQty", "10");
        cells.put("swFcAmount", "1000");
        cells.put("swKrwAmount", "999"); // 1000 × 1300 = 1,300,000 ≠ 999 × 1(위임예산 배수)

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.DELEGATED_BUDGET,
                                        "2026",
                                        List.of(row(2, cells)))),
                        TestSnapshots.indexWithXcr("USD", "1300"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "swKrwAmount".equals(d.column()))
                .filteredOn(d -> "AMOUNT_MISMATCH".equals(d.code()))
                .isNotEmpty();
        assertThat(result).noneMatch(d -> "hwKrwAmount".equals(d.column()));
    }

    /** 위임예산 첫 행부터 부점명이 비면 이후 행을 귀속시킬 사업이 없으므로 시트 단위 BLOCKER. */
    @Test
    @DisplayName("위임예산 첫 행 부점명이 비면 REQUIRED_MISSING BLOCKER를 낸다")
    void 위임예산_첫행_부점명공백은_블로커다() {
        Map<String, String> first = delegatedCells(Map.of("branchName", ""));
        Map<String, String> second = delegatedCells(Map.of("branchName", "런던"));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.DELEGATED_BUDGET,
                                        "2026",
                                        List.of(row(2, first), row(3, second)))),
                        TestSnapshots.indexWithOrgs("0910", "런던"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .anySatisfy(
                        d -> {
                            assertThat(d.code()).isEqualTo("REQUIRED_MISSING");
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(d.column()).isEqualTo("branchName");
                            assertThat(d.excelRow()).isEqualTo(2);
                        });
    }

    /** 위임예산 두 번째 이후 행의 부점명 공백은 forward-fill 대상이라 ORG_UNRESOLVED를 내지 않는다. */
    @Test
    @DisplayName("위임예산 연속행 부점명 공백은 ORG_UNRESOLVED를 내지 않는다")
    void 위임예산_연속행_부점명공백은_미해석이_아니다() {
        Map<String, String> first = delegatedCells(Map.of("branchName", "런던"));
        Map<String, String> second = delegatedCells(Map.of("branchName", ""));

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.DELEGATED_BUDGET,
                                        "2026",
                                        List.of(row(2, first), row(3, second)))),
                        TestSnapshots.indexWithOrgs("0910", "런던"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .noneMatch(
                        d -> "ORG_UNRESOLVED".equals(d.code()) && "branchName".equals(d.column()));
        assertThat(result).noneMatch(d -> "REQUIRED_MISSING".equals(d.code()));
    }

    /** 자본예산 계열이 아닌 비목코드(999)로 개발비 비목을 보정하면 CODE_UNRESOLVED. */
    @Test
    @DisplayName("존재하지 않는 비목코드로 보정하면 devAmountIoeC에 CODE_UNRESOLVED를 낸다")
    void 미등록_비목코드_보정은_코드미해석이다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, "devAmountIoeC"),
                        "999");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(row(2, capitalCells())))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "devAmountIoeC".equals(d.column()))
                .filteredOn(d -> "CODE_UNRESOLVED".equals(d.code()))
                .isNotEmpty();
    }

    /**
     * 일반관리비 계열 비목코드(011=유지보수료)는 세 자리 숫자 형식은 맞지만 자본예산 계열이 아니므로 여전히 CODE_UNRESOLVED다. 사용자가 비목 목록에서
     * 엉뚱한 구간(일반관리비)을 고르는 실수를 잡아야 한다 — "숫자 세 자리인가"만 보는 검사로 느슨해지면 이 케이스가 통과해 버린다.
     */
    @Test
    @DisplayName("일반관리비 계열 비목코드(011)로 기타무형 비목을 보정해도 CODE_UNRESOLVED를 낸다")
    void 일반관리비_비목코드_보정도_코드미해석이다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, "swAmountIoeC"),
                        "011");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(row(2, capitalCells())))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "swAmountIoeC".equals(d.column()))
                .filteredOn(d -> "CODE_UNRESOLVED".equals(d.code()))
                .isNotEmpty();
    }

    /** 기계장치 국외(102)처럼 실재하는 자본예산 계열 코드로 보정하면 그 컬럼에 진단이 없다. */
    @Test
    @DisplayName("자본예산 계열 비목코드로 기계장치 비목을 보정하면 진단을 내지 않는다")
    void 자본예산_비목코드_보정은_진단이_없다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, "hwAmountIoeC"),
                        "102");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(row(2, capitalCells())))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        assertThat(result).noneMatch(d -> "hwAmountIoeC".equals(d.column()));
    }

    /** 개발비 감리(104)로 보정하면 그 컬럼에 진단이 없다. 세 컬럼 중 devAmountIoeC 경로도 함께 고정한다. */
    @Test
    @DisplayName("개발비 감리 코드(104)로 보정하면 devAmountIoeC에 진단을 내지 않는다")
    void 개발비_감리코드_보정은_진단이_없다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, "devAmountIoeC"),
                        "104");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(row(2, capitalCells())))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        assertThat(result).noneMatch(d -> "devAmountIoeC".equals(d.column()));
    }

    // ===== CRITICAL-2: 짧은 코드 컬럼의 라벨 → 코드 변환과 길이 가드 =====

    /**
     * 추진가능성 실 데이터는 `추진계획 검토중`(8자)인데 `EXE_PTT_YN`은 `VARCHAR2(1)`이다. 검증이 막지 않으면 dry-run이 초록인 채
     * commit에서 ORA-12899가 나고 셀을 짚지 못하는 500으로 끝난다. 코드 해석은 원장을 새로 만드는 행에만 건다.
     */
    @Test
    @DisplayName("추진가능성 라벨이 코드표에 없으면 CODE_UNRESOLVED와 코드셋 전체 후보를 낸다")
    void 미해석_추진가능성은_후보와_함께_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                capitalSheet(
                                        row(
                                                2,
                                                capitalCellsWith(
                                                        Map.of("feasibility", "추진계획 검토중"))))),
                        catalogIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "feasibility".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactlyInAnyOrder("1", "2");
                        });
    }

    @Test
    @DisplayName("추진가능성이 코드값명과 정확히 일치하면 진단을 내지 않는다")
    void 해석되는_추진가능성은_진단이_없다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                capitalSheet(
                                        row(2, capitalCellsWith(Map.of("feasibility", "확정"))))),
                        catalogIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result).noneMatch(d -> "feasibility".equals(d.column()));
    }

    @Test
    @DisplayName("전결권 라벨이 자본 계열 코드표에 없으면 CODE_UNRESOLVED를 낸다")
    void 미해석_전결권은_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                capitalSheet(
                                        row(
                                                2,
                                                capitalCellsWith(
                                                        Map.of("delegationLabel", "지점장 전결"))))),
                        catalogIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "delegationLabel".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .contains("22", "25");
                        });
    }

    @Test
    @DisplayName("코드표에 없는 사업코드는 CODE_UNRESOLVED와 후보를 낸다 (BG_UNT_ABUS_C VARCHAR2(3))")
    void 미등록_사업코드는_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("abusCode", "9999"))))),
                        catalogIndexWith(TestSnapshots.indexWithIoe("001", "국내전산임차료")),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.COST, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "abusCode".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .contains("571");
                        });
    }

    /**
     * `CTT_OPP_NM`은 `VARCHAR2(100 BYTE)`라 한글 34자에서 이미 넘는다. 형제 컬럼 `CTT_NM`(100 CHAR)과 단위가 다르다. 물리 길이
     * 검사는 원장을 새로 만드는 행에만 건다.
     */
    @Test
    @DisplayName("계약업체명이 100바이트를 넘으면 LENGTH_EXCEEDED를 낸다")
    void 계약업체명_바이트초과는_블로커다() {
        String longVendor = "가".repeat(40); // 120바이트

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("vendorName", longVendor))))),
                        TestSnapshots.indexWithIoe("001", "국내전산임차료"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.COST, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "vendorName".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("LENGTH_EXCEEDED");
                            assertThat(d.message()).contains("바이트");
                        });
    }

    // ===== CRITICAL-3: 미해석 셀도 보정 후보를 들고 온다 =====

    @Test
    @DisplayName("미해석 부서명에도 이름이 비슷한 후보가 함께 온다 (드롭다운을 그릴 수 있어야 한다)")
    void 미해석_부서명도_후보를_낸다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("deptName", "런던PF데스크"))))),
                        TestSnapshots.indexWithOrgs("0910", "런던지점", "0920", "뉴욕지점"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "deptName".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("ORG_UNRESOLVED");
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("0910");
                            // 문구가 "찾았다"로 읽히면 안 된다
                            assertThat(d.message()).contains("찾지 못했습니다");
                        });
    }

    @Test
    @DisplayName("빈 셀은 후보를 내지 않는다 (제안할 근거가 없다)")
    void 빈_부서명은_후보가_없다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(costSheet(row(2, costCells(Map.of("deptName", ""))))),
                        TestSnapshots.indexWithOrgs("0910", "런던지점"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "deptName".equals(d.column()))
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).isEmpty());
    }

    @Test
    @DisplayName("미해석 담당자명에도 이름이 비슷한 후보가 함께 온다")
    void 미해석_담당자명도_후보를_낸다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                capitalSheet(
                                        row(2, capitalCellsWith(Map.of("managerName", "김성완 과장"))))),
                        TestSnapshots.indexWithUsers(
                                List.of(user("K1", "김성원", "과장", "180", "18001", "IT기획팀"))),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of(SheetKind.CAPITAL_PROJECT, Set.of(2)));

        assertThat(result)
                .filteredOn(d -> "managerName".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("USER_UNRESOLVED");
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("K1");
                        });
    }

    @Test
    @DisplayName("미등록 통화에는 등록된 통화 전체가 후보로 온다")
    void 미등록_통화는_통화후보를_낸다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                costSheet(
                                        row(
                                                2,
                                                costCells(
                                                        Map.of(
                                                                "currency", "AUD",
                                                                "fcAmount", "100",
                                                                "krwAmount", "100"))))),
                        TestSnapshots.indexWithXcr("GBP", "1924"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "currency".equals(d.column()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.code()).isEqualTo("CODE_UNRESOLVED");
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("GBP");
                        });
    }

    /** 금액이 둘 다 0인 행도 통화 자체는 검사해야 한다 — 어댑터가 CUR_C(3자)에 그 값을 그대로 쓴다. */
    @Test
    @DisplayName("금액이 없는 행도 미등록 통화를 짚는다")
    void 금액이_없어도_통화를_검사한다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.DELEGATED_BUDGET,
                                        "2026",
                                        List.of(
                                                row(
                                                        2,
                                                        delegatedCells(
                                                                Map.of(
                                                                        "branchName", "런던지점",
                                                                        "currency", "AUDX",
                                                                        "hwKrwAmount", "0")))))),
                        TestSnapshots.indexWithOrgs("0910", "런던지점"),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "currency".equals(d.column()))
                .singleElement()
                .satisfies(d -> assertThat(d.code()).isEqualTo("CODE_UNRESOLVED"));
    }

    // ===== IMPORTANT-5: 검증이 보정값을 반영해 읽는다 =====

    /** 보정 이전 값으로 금액을 대조하면, 사용자가 통화·금액을 고쳐도 검증은 원본을 보고 어댑터는 보정값을 저장하는 어긋남이 생긴다(검증 우회). */
    @Test
    @DisplayName("금액 대조는 통화·금액 보정값을 반영해 읽는다")
    void 금액대조가_보정값을_반영한다() {
        Map<String, String> overrides =
                Map.of(MigrationValidator.overrideKey(SheetKind.COST, 2, "krwAmount"), "1924");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                costSheet(
                                        row(
                                                2,
                                                costCells(
                                                        Map.of(
                                                                "currency", "GBP",
                                                                "fcAmount", "1000",
                                                                "krwAmount", "999999"))))),
                        TestSnapshots.indexWithXcr("GBP", "1924"),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        // 1,000 GBP × 1,924 = 1,924,000원 = 보정된 엑셀 1,924천원이라 정확히 일치한다.
        // 보정 이전 값(999,999천원)을 읽으면 AMOUNT_MISMATCH가 남는다.
        assertThat(result).noneMatch(d -> "AMOUNT_MISMATCH".equals(d.code()));
    }

    @Test
    @DisplayName("부문계획의 사업 존재 판정은 자본예산 시트의 사업명 보정값을 반영한다")
    void 사업존재판정이_사업명_보정값을_반영한다() {
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(SheetKind.CAPITAL_PROJECT, 2, "projectName"),
                        "웹한글 기안기 도입");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                capitalSheet(
                                        row(2, capitalCellsWith(Map.of("projectName", "오타 사업명")))),
                                new MigrationDto.SheetPayload(
                                        SheetKind.PLAN_ADJUSTMENT,
                                        "2026",
                                        List.of(row(2, Map.of("projectName", "웹한글 기안기 도입"))))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        overrides,
                        Map.of());

        // 보정 이전 값(오타 사업명)만 모으면 부문계획 행이 PROJECT_NOT_FOUND로 잘못 막힌다.
        assertThat(result).noneMatch(d -> "PROJECT_NOT_FOUND".equals(d.code()));
    }

    // ===== IMPORTANT-10: 같은 반영 안의 사업명 중복 =====

    /**
     * 같은 이름의 두 행은 사업을 둘 만들지만 {@code projectNoByName}에는 나중 것만 남아, 앞 사업이 편성행 없는 고아가 된다(목록에는 보이고 모든 예산
     * 화면에서 0). 이 검사는 매칭 여부와 무관하게 항상 건다 — 같은 반영의 두 행이 같은 원장을 가리키면 배분이 서로를 덮어쓴다.
     */
    @Test
    @DisplayName("같은 반영 안에 사업명이 중복되면 뒤 행에 DUPLICATE_EXISTS를 낸다")
    void 페이로드_내_사업명중복은_블로커다() {
        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT,
                                        "2026",
                                        List.of(
                                                row(
                                                        2,
                                                        capitalCellsWith(
                                                                Map.of("projectName", "같은 사업"))),
                                                row(
                                                        3,
                                                        capitalCellsWith(
                                                                Map.of(
                                                                        "projectName",
                                                                        "같은  사업")))))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.empty("2026"),
                        Map.of(),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "DUPLICATE_EXISTS".equals(d.code()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.excelRow()).isEqualTo(3);
                            assertThat(d.column()).isEqualTo("projectName");
                            assertThat(d.message()).contains("2행");
                        });
    }

    private static MigrationDto.SheetPayload capitalSheet(MigrationDto.NormalizedRow row) {
        return new MigrationDto.SheetPayload(SheetKind.CAPITAL_PROJECT, "2026", List.of(row));
    }

    /** 자본예산 기본 셀에 인자만 덮어씁니다. */
    private static Map<String, String> capitalCellsWith(Map<String, String> overrides) {
        Map<String, String> cells = capitalCells();
        cells.putAll(overrides);
        return cells;
    }

    /** 사업코드·추진가능성·전결권 카탈로그를 갖춘 인덱스. */
    private static MigrationLookupIndex catalogIndex() {
        return catalogIndexWith(TestSnapshots.emptyIndex());
    }

    private static MigrationLookupIndex catalogIndexWith(MigrationLookupIndex base) {
        return TestSnapshots.withCatalogs(
                base,
                Map.of("571", "운영시스템 유지보수", "501", "정보시스템 계획수립 및 운용"),
                Map.of("확정", "1", "미정(검토중)", "2"),
                Map.of("부문장", "22", "이사회", "25"));
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

    /**
     * 전 컬럼을 채운 뒤 인자로 받은 것만 덮어씁니다. itemName·hwKrwAmount는 REQUIRED_MISSING·AMOUNT_MISMATCH를 피하는 값으로
     * 둡니다.
     */
    private static Map<String, String> delegatedCells(Map<String, String> overrides) {
        Map<String, String> cells = new HashMap<>();
        for (String column :
                com.kdb.it.domain.migration.dto.MigrationColumns.of(SheetKind.DELEGATED_BUDGET)) {
            cells.put(column, "");
        }
        cells.put("itemName", "데스크탑");
        cells.put("hwQty", "1");
        cells.put("hwFcAmount", "");
        cells.put("hwKrwAmount", "1000000");
        cells.putAll(overrides);
        return cells;
    }

    private static CorgnI org(String code, String name) {
        return CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    private static CuserI user(
            String eno, String name, String title, String bbrC, String temC, String temNm) {
        return CuserI.builder()
                .eno(eno)
                .usrNm(name)
                .ptCNm(title)
                .bbrC(bbrC)
                .temC(temC)
                .temNm(temNm)
                .build();
    }
}
