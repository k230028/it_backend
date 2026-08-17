package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.RowDecision;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationYearSnapshot.RequestItem;
import com.kdb.it.domain.migration.service.adapter.AllocationIntent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 매칭·배분 진단(§6.1 신설분)의 발생 조건을 고정합니다. */
class MigrationMatchDiagnosticsTest {

    private final MigrationMatchDiagnostics diagnostics =
            new MigrationMatchDiagnostics(new MigrationLedgerMatcher());
    private final MigrationAllocationPlanner planner = new MigrationAllocationPlanner();

    @Test
    @DisplayName("resolve_매칭되면_결정없이_PK를_돌려준다")
    void resolve_매칭되면_결정없이_PK를_돌려준다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject("웹한글기안기도입", "PRJ-2026-0001", List.of());

        MigrationMatchDiagnostics.Resolved resolved =
                diagnostics.resolve(sheet(), intentOfProject("웹한글기안기도입"), snapshot, Map.of());

        assertThat(resolved.action()).isEqualTo(RowDecision.Kind.MATCH);
        assertThat(resolved.pk()).isEqualTo("PRJ-2026-0001");
        assertThat(resolved.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("resolve_미매칭이면_결정을_요구하는_BLOCKER를_낸다")
    void resolve_미매칭이면_결정을_요구하는_BLOCKER를_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject("웹한글기안기도입", "PRJ-2026-0001", List.of());

        MigrationMatchDiagnostics.Resolved resolved =
                diagnostics.resolve(sheet(), intentOfProject("없는사업"), snapshot, Map.of());

        assertThat(resolved.action()).isNull();
        MigrationDto.CellDiagnostic diagnostic = resolved.diagnostics().get(0);
        assertThat(diagnostic.code()).isEqualTo("LEDGER_NOT_MATCHED");
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
        assertThat(diagnostic.column()).isEqualTo(RowDecision.COLUMN);
        assertThat(diagnostic.candidates())
                .extracting(MigrationDto.Candidate::code)
                .contains("MATCH:PRJ-2026-0001", "CREATE_NEW", "SKIP");
    }

    @Test
    @DisplayName("resolve_관리자가_고른_결정이_있으면_그대로_따른다")
    void resolve_관리자가_고른_결정이_있으면_그대로_따른다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject("웹한글기안기도입", "PRJ-2026-0001", List.of());
        Map<String, String> overrides =
                Map.of(
                        MigrationValidator.overrideKey(
                                SheetKind.CAPITAL_PROJECT, 2, RowDecision.COLUMN),
                        "CREATE_NEW");

        MigrationMatchDiagnostics.Resolved resolved =
                diagnostics.resolve(sheet(), intentOfProject("없는사업"), snapshot, overrides);

        assertThat(resolved.action()).isEqualTo(RowDecision.Kind.CREATE_NEW);
        assertThat(resolved.pk()).isNull();
        assertThat(resolved.diagnostics()).isEmpty();
    }

    /** 경상사업처럼 후보가 둘 이상이면 대상을 하나로 특정하지 못해 LEDGER_AMBIGUOUS를 낸다. */
    @Test
    @DisplayName("resolve_후보가_둘이면_LEDGER_AMBIGUOUS를_낸다")
    void resolve_후보가_둘이면_LEDGER_AMBIGUOUS를_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithOrdinaryProjects(
                        "920", Map.of("PRJ-2026-0002", "런던 위임예산", "PRJ-2026-0003", "런던 PF 위임예산"));

        MigrationMatchDiagnostics.Resolved resolved =
                diagnostics.resolve(
                        delegatedSheet(), intentOfOrdinaryDept("920"), snapshot, Map.of());

        assertThat(resolved.action()).isNull();
        assertThat(resolved.pk()).isNull();
        MigrationDto.CellDiagnostic diagnostic = resolved.diagnostics().get(0);
        assertThat(diagnostic.code()).isEqualTo("LEDGER_AMBIGUOUS");
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
        assertThat(diagnostic.candidates())
                .extracting(MigrationDto.Candidate::code)
                .contains("MATCH:PRJ-2026-0002", "MATCH:PRJ-2026-0003", "CREATE_NEW", "SKIP");
    }

    @Test
    @DisplayName("checkAllocation_요청합계가_0인데_목표액이_있으면_BLOCKER다")
    void checkAllocation_요청합계가_0인데_목표액이_있으면_BLOCKER다() {
        // snapshot의 PRJ-2026-0001 품목 금액이 전부 0인 상태
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "웹한글기안기도입",
                        "PRJ-2026-0001",
                        List.of(new RequestItem("GCL-1", 1, "106", BigDecimal.ZERO)));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkAllocation(
                        sheet(),
                        intentWithTarget("swAmount", "416000000"),
                        "PRJ-2026-0001",
                        snapshot,
                        planner);

        MigrationDto.CellDiagnostic diagnostic =
                out.stream()
                        .filter(d -> "ITEM_BASE_ZERO".equals(d.code()))
                        .findFirst()
                        .orElseThrow();
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
        assertThat(diagnostic.column()).isEqualTo("swAmount");
    }

    @Test
    @DisplayName("checkAllocation_종합본_기준액이_요청_원장과_다르면_WARNING이다")
    void checkAllocation_종합본_기준액이_요청_원장과_다르면_WARNING이다() {
        // 원장 품목 합계 1,406,000,000 / 종합본 declaredBase 1,200,000,000
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "웹한글기안기도입",
                        "PRJ-2026-0001",
                        List.of(new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000"))));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkAllocation(
                        sheet(),
                        intentWithDeclaredBase("1200000000"),
                        "PRJ-2026-0001",
                        snapshot,
                        planner);

        MigrationDto.CellDiagnostic diagnostic =
                out.stream()
                        .filter(d -> "AMOUNT_ADJUSTED".equals(d.code()))
                        .findFirst()
                        .orElseThrow();
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.WARNING);
    }

    /**
     * 위임예산({@code costAmount})의 배분 대상은 그 사업의 <b>모든</b> 품목입니다.
     *
     * <p>회귀 고정: 종전에는 {@code costAmount}가 비목그룹을 갖지 않는다는 이유로 "자본 계열 밖 품목"으로 떨어졌습니다. 그런데 1단계가 만든 위임예산
     * 경상사업의 품목은 전부 자본 계열({@code 102}·{@code 105})이라 대상이 빈 목록이 되고, 목표액이 0보다 크므로 <b>매칭에 성공한 전 행</b>이
     * {@code ITEM_BASE_ZERO} BLOCKER로 막혔습니다.
     */
    @Test
    @DisplayName("checkAllocation_위임예산의_costAmount는_자본계열_품목에도_배분된다")
    void checkAllocation_위임예산의_costAmount는_자본계열_품목에도_배분된다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "2026년런던지점위임예산경상",
                        "PRJ-2026-0002",
                        List.of(
                                new RequestItem("GCL-1", 1, "102", new BigDecimal("30000000")),
                                new RequestItem("GCL-2", 1, "105", new BigDecimal("20000000"))));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkAllocation(
                        delegatedSheet(),
                        intentOfDelegated("50000000"),
                        "PRJ-2026-0002",
                        snapshot,
                        planner);

        assertThat(out).as("배분 대상이 있으므로 ITEM_BASE_ZERO도, 기준액 대사 WARNING도 나지 않는다").isEmpty();
    }

    /** 일반관리비 목표액은 자본 세 그룹 밖 품목에만 배분됩니다 (설계 §3.4의 네 번째 그룹). */
    @Test
    @DisplayName("checkAllocation_일반관리비_목표액은_자본계열_밖_품목에_배분된다")
    void checkAllocation_일반관리비_목표액은_자본계열_밖_품목에_배분된다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "웹한글기안기도입",
                        "PRJ-2026-0001",
                        List.of(
                                new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")),
                                new RequestItem("GCL-2", 1, "001", new BigDecimal("100000000"))));

        assertThat(
                        diagnostics.checkAllocation(
                                sheet(),
                                intentWithTarget("generalAmount", "70000000"),
                                "PRJ-2026-0001",
                                snapshot,
                                planner))
                .as("일반관리비 품목(001)이 있으므로 배분 대상이 비지 않는다")
                .isEmpty();

        assertThat(
                        diagnostics.checkAllocation(
                                sheet(),
                                intentWithTarget("generalAmount", "70000000"),
                                "PRJ-2026-0001",
                                snapshotWithProject(
                                        "웹한글기안기도입",
                                        "PRJ-2026-0001",
                                        List.of(
                                                new RequestItem(
                                                        "GCL-1",
                                                        1,
                                                        "106",
                                                        new BigDecimal("1406000000")))),
                                planner))
                .as("자본 계열 품목만 있으면 일반관리비 목표액을 배분할 대상이 없어 BLOCKER다")
                .extracting(MigrationDto.CellDiagnostic::code)
                .contains("ITEM_BASE_ZERO");
    }

    /**
     * MIG-23② — 일반관리비 열이 비었고 기존 편성률도 없으면 그 품목이 조용히 100%로 편성됩니다.
     *
     * <p>설계 §3.4의 "빈 열은 기존 편성률 유지" 규칙은 유지할 기존값이 있을 때만 성립합니다. 기존 편성행이 없으면 {@code applyItemRates}의
     * {@code DEFAULT_DUP_RT = 100}이 실려, 조정비율 0.7 사업이어도 일반관리비 품목만 100%로 편성됩니다.
     */
    @Test
    @DisplayName("checkAllocation_일반관리비_열이_비고_기존_편성률도_없으면_WARNING이다")
    void checkAllocation_일반관리비_열이_비고_기존_편성률도_없으면_WARNING이다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "웹한글기안기도입",
                        "PRJ-2026-0001",
                        List.of(
                                new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")),
                                new RequestItem("GCL-2", 1, "001", new BigDecimal("100000000"))));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkAllocation(
                        sheet(),
                        intentWithTarget("swAmount", "984200000"),
                        "PRJ-2026-0001",
                        snapshot,
                        planner);

        MigrationDto.CellDiagnostic diagnostic =
                out.stream()
                        .filter(d -> "GENERAL_RATE_DEFAULTED".equals(d.code()))
                        .findFirst()
                        .orElseThrow();
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.WARNING);
        assertThat(diagnostic.column()).isEqualTo("generalAmount");
    }

    /** 기존 편성률이 있으면 설계 §3.4의 "빈 열은 기존 편성률 유지"가 실제로 성립하므로 알릴 것이 없다. */
    @Test
    @DisplayName("checkAllocation_기존_편성률이_있으면_일반관리비_경고를_내지_않는다")
    void checkAllocation_기존_편성률이_있으면_일반관리비_경고를_내지_않는다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProjectAndRates(
                        "PRJ-2026-0001",
                        List.of(
                                new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")),
                                new RequestItem("GCL-2", 1, "001", new BigDecimal("100000000"))),
                        Map.of("GCL-2", new BigDecimal("70")));

        assertThat(
                        diagnostics.checkAllocation(
                                sheet(),
                                intentWithTarget("swAmount", "984200000"),
                                "PRJ-2026-0001",
                                snapshot,
                                planner))
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("GENERAL_RATE_DEFAULTED");
    }

    /** 일반관리비 열을 채운 행은 목표액이 실리므로 기본 편성률로 떨어지지 않는다. */
    @Test
    @DisplayName("checkAllocation_일반관리비_열을_채우면_경고를_내지_않는다")
    void checkAllocation_일반관리비_열을_채우면_경고를_내지_않는다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "웹한글기안기도입",
                        "PRJ-2026-0001",
                        List.of(
                                new RequestItem("GCL-1", 1, "106", new BigDecimal("1406000000")),
                                new RequestItem("GCL-2", 1, "001", new BigDecimal("100000000"))));

        assertThat(
                        diagnostics.checkAllocation(
                                sheet(),
                                intentWithTarget("generalAmount", "70000000"),
                                "PRJ-2026-0001",
                                snapshot,
                                planner))
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("GENERAL_RATE_DEFAULTED");
    }

    /**
     * 위임예산은 이 경고의 대상이 아니다.
     *
     * <p>{@code costAmount}의 배분 대상은 그 사업의 <b>모든</b> 품목이라 자본 계열 밖 품목도 이미 편성률을 받는다. 일반관리비 열이라는 개념 자체가
     * 없는 시트에 "열이 비었다"고 알리면 손댈 곳이 없는 경고가 된다.
     */
    @Test
    @DisplayName("checkAllocation_위임예산에는_일반관리비_경고를_내지_않는다")
    void checkAllocation_위임예산에는_일반관리비_경고를_내지_않는다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject(
                        "2026년런던지점위임예산경상",
                        "PRJ-2026-0002",
                        List.of(new RequestItem("GCL-1", 1, "001", new BigDecimal("50000000"))));

        assertThat(
                        diagnostics.checkAllocation(
                                delegatedSheet(),
                                intentOfDelegated("50000000"),
                                "PRJ-2026-0002",
                                snapshot,
                                planner))
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("GENERAL_RATE_DEFAULTED");
    }

    /**
     * MIG-23① — 원장을 새로 만드는 행은 일반관리비 목표액을 담을 품목이 없습니다.
     *
     * <p>{@code CapitalProjectSheetAdapter.items()}는 자본 3열만 품목으로 만듭니다(엑셀에 비자본 비목 구분이 없어 001/007/013
     * 중 무엇인지 정할 근거가 없음). 그래서 {@code CREATE_NEW} 행에 일반관리비 열이 채워져 있어도 그 목표액은 반영되지 않습니다.
     */
    @Test
    @DisplayName("generalAmountNotCreatable_신규_생성행의_일반관리비_목표액은_WARNING이다")
    void generalAmountNotCreatable_신규_생성행의_일반관리비_목표액은_WARNING이다() {
        MigrationDto.CellDiagnostic diagnostic = diagnostics.generalAmountNotCreatable(sheet(), 2);

        assertThat(diagnostic.code()).isEqualTo("GENERAL_AMOUNT_NOT_CREATABLE");
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.WARNING);
        assertThat(diagnostic.column()).isEqualTo("generalAmount");
        assertThat(diagnostic.excelRow()).isEqualTo(2);
    }

    @Test
    @DisplayName("createNotSupported_원장을_만들_수_없는_시트의_CREATE_NEW는_WARNING이다")
    void createNotSupported_원장을_만들_수_없는_시트의_CREATE_NEW는_WARNING이다() {
        MigrationDto.SheetPayload plan =
                new MigrationDto.SheetPayload(
                        SheetKind.PLAN_ADJUSTMENT,
                        "2026",
                        List.of(new MigrationDto.NormalizedRow(2, Map.of())));

        MigrationDto.CellDiagnostic diagnostic = diagnostics.createNotSupported(plan, 2);

        assertThat(diagnostic.code()).isEqualTo("CREATE_NOT_SUPPORTED");
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.WARNING);
        assertThat(diagnostic.column()).isEqualTo(RowDecision.COLUMN);
    }

    @Test
    @DisplayName("checkRateReconcile_조정열과_기준액곱이_어긋나면_WARNING이다")
    void checkRateReconcile_조정열과_기준액곱이_어긋나면_WARNING이다() {
        MigrationDto.NormalizedRow row =
                rowOf(Map.of("swAmount", "1406", "adjustRate", "0.7", "swAdjustAmount", "500"));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkRateReconcile(sheet(), row, Map.of());

        assertThat(out)
                .extracting(MigrationDto.CellDiagnostic::code)
                .containsExactly("RATE_RECONCILE_MISMATCH");
    }

    @Test
    @DisplayName("checkRateReconcile_반올림_차이는_통과한다")
    void checkRateReconcile_반올림_차이는_통과한다() {
        // 1406 × 0.7 = 984.2 인데 엑셀은 984로 적었다 — 백만원 단위 1 미만 차이는 반올림으로 본다
        MigrationDto.NormalizedRow row =
                rowOf(Map.of("swAmount", "1406", "adjustRate", "0.7", "swAdjustAmount", "984"));

        assertThat(diagnostics.checkRateReconcile(sheet(), row, Map.of())).isEmpty();
    }

    /**
     * 두 열(dev·sw)이 동시에 어긋나면 삽입 순서(dev→hw→sw)대로 진단이 난다. {@code Map.of()}로 순회했다면 실행마다 순서가 달라져 이 단정이
     * 흔들렸을 것이다 — hw는 조정열이 비어 건너뛰므로 dev 다음 곧바로 sw가 온다.
     */
    @Test
    @DisplayName("checkRateReconcile_두_열이_동시에_어긋나면_dev_sw_순서로_난다")
    void checkRateReconcile_두_열이_동시에_어긋나면_dev_sw_순서로_난다() {
        MigrationDto.NormalizedRow row =
                rowOf(
                        Map.of(
                                "devAmount", "1000",
                                "adjustRate", "0.7",
                                "devAdjustAmount", "100",
                                "swAmount", "1406",
                                "swAdjustAmount", "500"));

        List<MigrationDto.CellDiagnostic> out =
                diagnostics.checkRateReconcile(sheet(), row, Map.of());

        assertThat(out)
                .extracting(MigrationDto.CellDiagnostic::column)
                .containsExactly("devAdjustAmount", "swAdjustAmount");
    }

    /** 진단 좌표(시트 종류·행)만 필요한 테스트가 공유하는 자본예산 시트. 셀 내용은 checkRateReconcile이 받는 row가 따로 담당한다. */
    private static MigrationDto.SheetPayload sheet() {
        return new MigrationDto.SheetPayload(
                SheetKind.CAPITAL_PROJECT,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, Map.of())));
    }

    /** 위임예산 매칭(ORDINARY_DEPT) 테스트가 쓰는 시트. */
    private static MigrationDto.SheetPayload delegatedSheet() {
        return new MigrationDto.SheetPayload(
                SheetKind.DELEGATED_BUDGET,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, Map.of())));
    }

    private static MigrationDto.NormalizedRow rowOf(Map<String, String> cells) {
        return new MigrationDto.NormalizedRow(2, cells);
    }

    /** 정규화 사업명 매칭 키를 가진 배분 의도. resolve 테스트 전용이라 목표액·기준액은 쓰지 않는다. */
    private static AllocationIntent intentOfProject(String projectName) {
        return new AllocationIntent(
                SheetKind.CAPITAL_PROJECT,
                2,
                "BPROJM",
                AllocationIntent.MatchKey.ofProjectName(
                        MigrationYearSnapshot.normalizeName(projectName)),
                Map.of(),
                null);
    }

    /** 부서코드 매칭 키를 가진 배분 의도(경상사업). resolve의 AMBIGUOUS 판정 테스트 전용이다. */
    private static AllocationIntent intentOfOrdinaryDept(String deptCode) {
        return new AllocationIntent(
                SheetKind.DELEGATED_BUDGET,
                2,
                "BPROJM",
                AllocationIntent.MatchKey.ofOrdinaryDept(deptCode),
                Map.of(),
                null);
    }

    /** 목표 편성액 하나만 채운 배분 의도. checkAllocation의 ITEM_BASE_ZERO 판정 전용이라 매칭 키는 쓰지 않는다. */
    private static AllocationIntent intentWithTarget(String column, String amount) {
        return new AllocationIntent(
                SheetKind.CAPITAL_PROJECT,
                2,
                "BPROJM",
                AllocationIntent.MatchKey.ofProjectName("무관"),
                Map.of(column, new BigDecimal(amount)),
                null);
    }

    /**
     * 위임예산 배분 의도. 목표액과 기준액이 같은 값이라(편성률 100%가 "적어 낸 금액 그대로") 배분만 성립하면 진단이 하나도 나지 않아야 한다.
     *
     * @param amountKrw 부점 그룹의 원화환산 합계
     */
    private static AllocationIntent intentOfDelegated(String amountKrw) {
        BigDecimal amount = new BigDecimal(amountKrw);
        return new AllocationIntent(
                SheetKind.DELEGATED_BUDGET,
                2,
                "BPROJM",
                AllocationIntent.MatchKey.ofOrdinaryDept("920"),
                Map.of("costAmount", amount),
                amount);
    }

    /** 종합본 기준액만 채운 배분 의도. swAmount 그룹으로 원장 품목을 걸어 ledgerBase를 계산시킨다. */
    private static AllocationIntent intentWithDeclaredBase(String declaredBase) {
        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        targets.put("swAmount", BigDecimal.ZERO);
        return new AllocationIntent(
                SheetKind.CAPITAL_PROJECT,
                2,
                "BPROJM",
                AllocationIntent.MatchKey.ofProjectName("무관"),
                targets,
                new BigDecimal(declaredBase));
    }

    /** 정규화 사업명 하나와 그 품목을 가진 연도 스냅샷. */
    private static MigrationYearSnapshot.Data snapshotWithProject(
            String normalizedName, String projectNo, List<RequestItem> items) {
        Map<String, String> byName = new LinkedHashMap<>();
        byName.put(normalizedName, projectNo);
        Map<String, List<RequestItem>> itemsByProject = new LinkedHashMap<>();
        itemsByProject.put(projectNo, items);
        return new MigrationYearSnapshot.Data(
                "2026",
                byName,
                Map.of(projectNo, "웹한글 기안기 도입"),
                Map.of(),
                itemsByProject,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                List.of(projectNo),
                List.of());
    }

    /**
     * 품목과 그 품목의 기존 편성률을 함께 담은 연도 스냅샷.
     *
     * @param ratesByItemNo 품목관리번호 → 기존 편성률. 여기 없는 품목은 기존 편성률이 없는 상태다
     */
    private static MigrationYearSnapshot.Data snapshotWithProjectAndRates(
            String projectNo, List<RequestItem> items, Map<String, BigDecimal> ratesByItemNo) {
        Map<String, List<RequestItem>> itemsByProject = new LinkedHashMap<>();
        itemsByProject.put(projectNo, items);
        return new MigrationYearSnapshot.Data(
                "2026",
                Map.of(),
                Map.of(projectNo, "웹한글 기안기 도입"),
                Map.of(),
                itemsByProject,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                new LinkedHashMap<>(ratesByItemNo),
                List.of(projectNo),
                List.of());
    }

    /** 한 부서에 경상사업이 둘 이상 있는 연도 스냅샷. resolve의 AMBIGUOUS 판정 전용이다. */
    private static MigrationYearSnapshot.Data snapshotWithOrdinaryProjects(
            String deptCode, Map<String, String> projectNameByNo) {
        Map<String, List<String>> ordinaryByDept = new LinkedHashMap<>();
        ordinaryByDept.put(deptCode, new ArrayList<>(projectNameByNo.keySet()));
        return new MigrationYearSnapshot.Data(
                "2026",
                Map.of(),
                new LinkedHashMap<>(projectNameByNo),
                ordinaryByDept,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                List.copyOf(projectNameByNo.keySet()),
                List.of());
    }
}
