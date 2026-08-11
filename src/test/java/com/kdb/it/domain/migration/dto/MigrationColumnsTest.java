package com.kdb.it.domain.migration.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정규 컬럼 id 계약을 고정합니다.
 *
 * <p>이 목록은 프론트 파서(app/composables/migration/columns.ts)와 동일한 리터럴이어야 합니다. 한쪽만 바꾸면 dry-run이 조용히 빈 셀을
 * 읽으므로, 컬럼을 추가·삭제할 때 두 파일과 이 테스트를 함께 갱신합니다.
 */
class MigrationColumnsTest {

    @Test
    @DisplayName("전산일반관리비 시트의 정규 컬럼 id 목록을 고정한다")
    void 일반관리비_컬럼계약() {
        assertThat(MigrationColumns.of(SheetKind.COST))
                .containsExactly(
                        "abusCode",
                        "ioeName",
                        "abusTcLabel",
                        "vendorName",
                        "requestDetail",
                        "securityFlag",
                        "terminalFlag",
                        "deptName",
                        "teamName",
                        "currency",
                        "fcAmount",
                        "krwAmount",
                        "remark");
    }

    @Test
    @DisplayName("자본예산 시트의 정규 컬럼 id 목록을 고정한다")
    void 자본예산_컬럼계약() {
        assertThat(MigrationColumns.of(SheetKind.CAPITAL_PROJECT))
                .containsExactly(
                        "projectName",
                        "projectType",
                        "progressLabel",
                        "projectOutline",
                        "headquarters",
                        "deptName",
                        "teamName",
                        "managerName",
                        "teamLeaderName",
                        "itTeamName",
                        "feasibility",
                        "startYm",
                        "endYm",
                        "devAmount",
                        "hwAmount",
                        "swAmount",
                        "adjustRate",
                        "delegationLabel");
    }

    @Test
    @DisplayName("위임예산 시트의 정규 컬럼 id 목록을 고정한다")
    void 위임예산_컬럼계약() {
        assertThat(MigrationColumns.of(SheetKind.DELEGATED_BUDGET))
                .containsExactly(
                        "branchName",
                        "itemName",
                        "currency",
                        "hwQty",
                        "hwFcAmount",
                        "hwKrwAmount",
                        "swQty",
                        "swFcAmount",
                        "swKrwAmount");
    }

    @Test
    @DisplayName("부문계획 시트의 정규 컬럼 id 목록을 고정한다")
    void 부문계획_컬럼계약() {
        assertThat(MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT))
                .containsExactly(
                        "projectName",
                        "projectType",
                        "headquarters",
                        "deptName",
                        "teamName",
                        "managerName",
                        "teamLeaderName",
                        "budgetChangeLabel",
                        "startYm",
                        "endYm",
                        "devAmount",
                        "hwAmount",
                        "swAmount",
                        "generalAmount",
                        "totalAmount",
                        "spentBefore",
                        "spent26",
                        "planned26",
                        "paymentSchedule",
                        "plannedAfter27",
                        "progressLabel",
                        "remark");
    }

    @Test
    @DisplayName("컬럼 id는 시트 간 의미가 같으면 같은 이름을 쓴다")
    void 시트간_공통컬럼은_같은_id를_쓴다() {
        List<String> capital = MigrationColumns.of(SheetKind.CAPITAL_PROJECT);
        List<String> plan = MigrationColumns.of(SheetKind.PLAN_ADJUSTMENT);

        assertThat(capital).contains("projectName", "deptName", "managerName");
        assertThat(plan).contains("projectName", "deptName", "managerName");
    }

    @Test
    @DisplayName("null 시트 종류는 거부한다")
    void null_시트종류는_거부한다() {
        assertThatThrownBy(() -> MigrationColumns.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
