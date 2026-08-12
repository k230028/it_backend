package com.kdb.it.domain.migration.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationColumns;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationLookupIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import com.kdb.it.domain.migration.service.TestSnapshots;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 부문계획 조정 시트 → 계획 조정 의도 변환 규칙을 고정합니다 (§5.4). */
class PlanAdjustmentSheetAdapterTest {

    private final PlanAdjustmentSheetAdapter adapter = new PlanAdjustmentSheetAdapter();

    @Test
    @DisplayName("조정액을 백만원에서 원 단위로 올려 PlanIntent에 담는다")
    void 조정액을_원단위로_올린다() {
        PlanIntent intent = adaptSingle(cells());

        assertThat(intent.swAmount()).isEqualByComparingTo(new BigDecimal("416000000.000"));
        assertThat(intent.devAmount()).isNull();
        assertThat(intent.hwAmount()).isNull();
    }

    @Test
    @DisplayName("사업명을 정규화해 동일성 판정 키로 담는다")
    void 사업명을_정규화한다() {
        assertThat(adaptSingle(cells()).normalizedProjectName()).isEqualTo("웹한글기안기도입을위한내규솔루션업그레이드");
    }

    @Test
    @DisplayName("예상지급일정을 BSE_YM 6자리로 바꾼다")
    void 예상지급일정을_연월6자리로_바꾼다() {
        assertThat(adaptSingle(cells()).paymentYm()).isEqualTo("202612");
    }

    @Test
    @DisplayName("금액 0은 품목을 만들지 않도록 null로 접는다")
    void 금액0은_null로_접는다() {
        Map<String, String> cells = cells();
        cells.put("swAmount", "0");

        assertThat(adaptSingle(cells).swAmount()).isNull();
    }

    @Test
    @DisplayName("집행 실적·사업진행·비고는 스냅샷 필드로만 담는다")
    void 집행실적은_스냅샷에만_담는다() {
        PlanIntent intent = adaptSingle(cells());

        assertThat(intent.snapshotFields())
                .containsEntry("progressLabel", "진행(품의)")
                .containsEntry("budgetChangeLabel", "감액")
                .containsEntry("planned26", "416")
                .containsEntry("remark", "6.10자 품의 완료");
    }

    @Test
    @DisplayName("조정된 사업에는 편성률 100의 RateIntent를 남긴다")
    void 편성률100을_남긴다() {
        AdapterOutput out = adapter.adapt(sheet(cells()), context());

        assertThat(out.rates())
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.orcTb()).isEqualTo("BPROJM");
                            assertThat(r.percent()).isEqualTo(100);
                            assertThat(r.naturalKeyOrPk()).isEqualTo("웹한글기안기도입을위한내규솔루션업그레이드");
                        });
    }

    @Test
    @DisplayName("사업·전산업무비 생성요청은 만들지 않는다")
    void 원장_생성요청은_만들지_않는다() {
        AdapterOutput out = adapter.adapt(sheet(cells()), context());

        assertThat(out.costs()).isEmpty();
        assertThat(out.projects()).isEmpty();
    }

    /**
     * 일반관리비가 스냅샷 텍스트에만 남고 의도에 실리지 않으면 계획 마스터의 {@code TOT_XP_AMT}가 0이 되고 {@code ADU_TOT_AMT}도 자본만 세게
     * 된다 (IMPORTANT-9).
     */
    @Test
    @DisplayName("일반관리비를 원 단위로 환산해 의도에 담는다")
    void 일반관리비를_의도에_담는다() {
        Map<String, String> cells = cells();
        cells.put("generalAmount", "300");

        assertThat(adaptSingle(cells).generalAmount())
                .isEqualByComparingTo(new java.math.BigDecimal("300000000"));
        // 원문도 스냅샷에 그대로 남는다
        assertThat(adaptSingle(cells).snapshotFields()).containsEntry("generalAmount", "300");
    }

    @Test
    @DisplayName("일반관리비가 비면 null로 둔다")
    void 일반관리비가_없으면_null이다() {
        assertThat(adaptSingle(cells()).generalAmount()).isNull();
    }

    private PlanIntent adaptSingle(Map<String, String> cells) {
        return adapter.adapt(sheet(cells), context()).plans().get(0);
    }

    private static MigrationDto.SheetPayload sheet(Map<String, String> cells) {
        return new MigrationDto.SheetPayload(
                SheetKind.PLAN_ADJUSTMENT,
                "2026",
                List.of(new MigrationDto.NormalizedRow(2, cells)));
    }

    private static AdapterContext context() {
        return new AdapterContext(
                "2026",
                new MigrationLookupIndex(
                        OrgIdentityResolver.Index.of(List.of(), List.of()), Map.of(), Map.of()),
                TestSnapshots.empty("2026"),
                Map.of(),
                "999999");
    }

    private static Map<String, String> cells() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String column : MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT)) {
            cells.put(column, "");
        }
        cells.put("projectName", "웹한글 기안기 도입을 위한 내규 솔루션 업그레이드");
        cells.put("projectType", "법률/규제대응");
        cells.put("headquarters", "기획관리부문");
        cells.put("deptName", "종합기획부");
        cells.put("teamName", "조직평가팀");
        cells.put("managerName", "김성원 과장");
        cells.put("teamLeaderName", "김도준 팀장");
        cells.put("budgetChangeLabel", "감액");
        cells.put("startYm", "'26.06");
        cells.put("endYm", "'26.12");
        cells.put("swAmount", "416");
        cells.put("totalAmount", "416");
        cells.put("planned26", "416");
        cells.put("paymentSchedule", "'26.12월");
        cells.put("progressLabel", "진행(품의)");
        cells.put("remark", "6.10자 품의 완료");
        return cells;
    }
}
