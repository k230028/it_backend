package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.model.ItBudgetLedgerSnapshot;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.infra.file.entity.Cfilem;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItBudgetLedgerCaptureTest {

    private final ItBudgetLedgerCapture capture =
            new ItBudgetLedgerCapture(
                    new ItBudgetCanonicalJson(new ObjectMapper().findAndRegisterModules()));

    @Test
    @DisplayName("ledger 캡처 키는 네 원장과 BaseEntity의 모든 @Column 물리명과 일치한다")
    void capturedColumnsMatchJpaColumns() {
        assertThat(capture.declaredColumns(Bprojm.class))
                .containsExactlyInAnyOrderElementsOf(persistentColumnNames(Bprojm.class));
        assertThat(capture.declaredColumns(Bitemm.class))
                .containsExactlyInAnyOrderElementsOf(persistentColumnNames(Bitemm.class));
        assertThat(capture.declaredColumns(Bcostm.class))
                .containsExactlyInAnyOrderElementsOf(persistentColumnNames(Bcostm.class));
        assertThat(capture.declaredColumns(Btermm.class))
                .containsExactlyInAnyOrderElementsOf(persistentColumnNames(Btermm.class));
        assertThat(capture.declaredColumns(Cfilem.class))
                .containsExactlyInAnyOrderElementsOf(persistentColumnNames(Cfilem.class));
    }

    @Test
    @DisplayName("PROJECT와 COST 원장의 모든 행을 물리 컬럼명과 정규 금액으로 캡처한다")
    void capturesProjectAndCostLedgerRows() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 15, 10, 30);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("P-2027-001")
                        .sno(2)
                        .fstEnrDtm(createdAt)
                        .lstChgDtm(changedAt)
                        .build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("I-001")
                        .sno(1)
                        .abusMngNo("P-2027-001")
                        .fntTbCrySno(2)
                        .curC("USD")
                        .amt(new BigDecimal("1350000"))
                        .fcAmt(new BigDecimal("1000"))
                        .xcr(new BigDecimal("1350"))
                        .fstEnrDtm(createdAt)
                        .lstChgDtm(changedAt)
                        .build();
        Bcostm cost = Bcostm.builder().costBgNo("C-001").bgSno(3).build();
        Btermm terminal =
                Btermm.builder().tmnMngNo("T-001").sno(4).termBgNo("C-001").termBgSno(3).build();
        Cfilem projectFile =
                Cfilem.builder()
                        .flMpnId("FL-00000002")
                        .flNm("project.pdf")
                        .apgFlKdNm("정보화사업")
                        .apgFlLnkCtzNm("P-2027-001")
                        .delYn("N")
                        .fstEnrDtm(createdAt)
                        .lstChgDtm(changedAt)
                        .build();
        Cfilem costFile =
                Cfilem.builder()
                        .flMpnId("FL-00000001")
                        .flNm("cost.pdf")
                        .apgFlKdNm("전산업무비")
                        .apgFlLnkCtzNm("C-001")
                        .delYn("N")
                        .build();

        ItBudgetLedgerSnapshot ledger =
                capture.capture(
                        List.of(
                                aggregate(
                                        SourceKind.PROJECT,
                                        "P-2027-001",
                                        2,
                                        project,
                                        item,
                                        projectFile),
                                aggregate(SourceKind.COST, "C-001", 3, cost, terminal, costFile)));

        assertThat(ledger.format()).isEqualTo("IT_BUDGET_LEDGER_V1");
        assertThat(ledger.aggregates())
                .extracting(ItBudgetLedgerSnapshot.Aggregate::kind)
                .containsExactly("PROJECT", "COST");
        assertThat(ledger.aggregates())
                .extracting(ItBudgetLedgerSnapshot.Aggregate::id)
                .containsExactly("P-2027-001", "C-001");
        assertThat(ledger.aggregates())
                .extracting(ItBudgetLedgerSnapshot.Aggregate::revision)
                .containsExactly(2, 3);
        assertThat(ledger.aggregates())
                .extracting(aggregate -> aggregate.parent().table())
                .containsExactly("BPROJM", "BCOSTM");
        assertThat(ledger.aggregates())
                .flatExtracting(ItBudgetLedgerSnapshot.Aggregate::children)
                .extracting(ItBudgetLedgerSnapshot.Row::table)
                .containsExactly("BITEMM", "BTERMM");
        assertThat(ledger.aggregates())
                .flatExtracting(ItBudgetLedgerSnapshot.Aggregate::attachments)
                .extracting(ItBudgetLedgerSnapshot.Row::table)
                .containsExactly("CFILEM", "CFILEM");

        ItBudgetLedgerSnapshot.Row capturedItem =
                ledger.aggregates().getFirst().children().getFirst();
        assertThat(capturedItem.columns())
                .containsEntry("CUR_C", "USD")
                .containsEntry("AMT", "1350000.000")
                .containsEntry("FC_AMT", "1000.000")
                .containsEntry("XCR", "1350.0000")
                .containsEntry("FST_ENR_DTM", createdAt)
                .containsEntry("LST_CHG_DTM", changedAt);
        assertThat(ledger.aggregates().getFirst().parent().columns().keySet())
                .containsExactlyInAnyOrderElementsOf(capture.declaredColumns(Bprojm.class));
        assertThat(capturedItem.columns().keySet())
                .containsExactlyInAnyOrderElementsOf(capture.declaredColumns(Bitemm.class));
        assertThat(ledger.aggregates().get(1).parent().columns().keySet())
                .containsExactlyInAnyOrderElementsOf(capture.declaredColumns(Bcostm.class));
        assertThat(ledger.aggregates().get(1).children().getFirst().columns().keySet())
                .containsExactlyInAnyOrderElementsOf(capture.declaredColumns(Btermm.class));
        assertThat(ledger.aggregates().getFirst().attachments().getFirst().columns())
                .containsEntry("FL_MPN_ID", "FL-00000002")
                .containsEntry("FL_NM", "project.pdf")
                .containsEntry("APG_FL_KD_NM", "정보화사업")
                .containsEntry("APG_FL_LNK_CTZ_NM", "P-2027-001")
                .containsEntry("DEL_YN", "N");
        assertThat(ledger.aggregates().getFirst().attachments().getFirst().columns().keySet())
                .containsExactlyInAnyOrderElementsOf(capture.declaredColumns(Cfilem.class));
    }

    @Test
    @DisplayName("ledger 모델은 호출자가 전달한 컬렉션을 복사하고 지원하지 않는 형식을 거부한다")
    void ledgerModelIsImmutableAndValidatesFormat() {
        List<ItBudgetLedgerSnapshot.Row> children =
                new ArrayList<>(
                        List.of(new ItBudgetLedgerSnapshot.Row("BITEMM", java.util.Map.of())));
        List<ItBudgetLedgerSnapshot.Aggregate> aggregates =
                new ArrayList<>(
                        List.of(
                                new ItBudgetLedgerSnapshot.Aggregate(
                                        "PROJECT",
                                        "P-1",
                                        1,
                                        new ItBudgetLedgerSnapshot.Row(
                                                "BPROJM", java.util.Map.of()),
                                        children,
                                        List.of())));

        ItBudgetLedgerSnapshot ledger =
                new ItBudgetLedgerSnapshot("IT_BUDGET_LEDGER_V1", aggregates);
        children.clear();
        aggregates.clear();

        assertThat(ledger.aggregates()).hasSize(1);
        assertThat(ledger.aggregates().getFirst().children()).hasSize(1);
        assertThatThrownBy(() -> ledger.aggregates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ledger.aggregates().getFirst().children().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ledger.aggregates().getFirst().parent().columns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ItBudgetLedgerSnapshot("UNKNOWN", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ledger 형식이 올바르지 않습니다.");
        assertThatThrownBy(
                        () ->
                                new ItBudgetLedgerSnapshot(
                                        "IT_BUDGET_LEDGER_V1", Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    private static ItBudgetSourceLoader.SourceAggregate aggregate(
            SourceKind kind,
            String id,
            int revision,
            BaseEntity parent,
            BaseEntity child,
            Cfilem attachment) {
        return new ItBudgetSourceLoader.SourceAggregate(
                new SourceRef(kind, id, revision, 1),
                parent,
                List.of(child),
                List.of(attachment),
                null);
    }

    private static Set<String> persistentColumnNames(Class<? extends BaseEntity> entityType) {
        Set<String> names = new HashSet<>();
        for (Class<?> type = entityType;
                type != null && BaseEntity.class.isAssignableFrom(type);
                type = type.getSuperclass()) {
            Arrays.stream(type.getDeclaredFields())
                    .map(field -> field.getAnnotation(Column.class))
                    .filter(java.util.Objects::nonNull)
                    .map(Column::name)
                    .forEach(names::add);
        }
        return names;
    }
}
