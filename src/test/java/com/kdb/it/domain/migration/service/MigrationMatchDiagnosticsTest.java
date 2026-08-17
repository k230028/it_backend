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
