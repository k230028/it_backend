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
                                // 서버 재계산 2890 × 1924 = 5,560,360원, 엑셀 원화열 9999 × 1,000(COST 배수) =
                                // 9,999,000원 — 둘이 달라 AMOUNT_MISMATCH가 난다
                                "krwAmount", "9999"));

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

    /** 담당자명이 인덱스에 전혀 없으면 USER_UNRESOLVED. */
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
                        Map.of());

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
                        Map.of());

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
                        overrides);

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
                        Map.of());

        assertThat(result).filteredOn(d -> "DUPLICATE_EXISTS".equals(d.code())).isNotEmpty();
    }

    /** 같은 연도에 같은 정규화 사업명이 이미 있으면 자본예산 행은 DUPLICATE_EXISTS. */
    @Test
    @DisplayName("같은 사업명이 이미 있는 연도의 자본예산 행은 DUPLICATE_EXISTS를 낸다")
    void 사업명_중복은_블로커다() {
        Map<String, String> cells = new LinkedHashMap<>(capitalCells());
        cells.put("projectName", "웹한글 기안기 도입");

        List<MigrationDto.CellDiagnostic> result =
                validator.validate(
                        List.of(
                                new MigrationDto.SheetPayload(
                                        SheetKind.CAPITAL_PROJECT, "2026", List.of(row(2, cells)))),
                        TestSnapshots.emptyIndex(),
                        TestSnapshots.snapshotWithProjectName(
                                "2026",
                                MigrationYearSnapshot.normalizeName("웹한글 기안기 도입"),
                                "PRJ-2026-0001"),
                        Map.of());

        assertThat(result)
                .filteredOn(d -> "projectName".equals(d.column()))
                .filteredOn(d -> "DUPLICATE_EXISTS".equals(d.code()))
                .isNotEmpty();
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
                        overrides);

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
                        overrides);

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
                        overrides);

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
                        overrides);

        assertThat(result).noneMatch(d -> "devAmountIoeC".equals(d.column()));
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
