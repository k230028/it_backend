package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItBudgetCanonicalJsonTest {

    private final ItBudgetCanonicalJson canonical =
            new ItBudgetCanonicalJson(new ObjectMapper().findAndRegisterModules());

    @Test
    void write_ordersObjectKeysButPreservesListOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertThat(canonical.write(first)).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(canonical.write(new ObjectFields("second", "first")))
                .isEqualTo("{\"a\":\"first\",\"b\":\"second\"}");
        assertThat(canonical.digest(first)).isEqualTo(canonical.digest(second));
        assertThat(canonical.write(List.of("A", "B")))
                .isNotEqualTo(canonical.write(List.of("B", "A")));
    }

    @Test
    void digest_usesUtf8Sha256LowerHex() {
        assertThat(canonical.digest(Map.of("label", "가")))
                .isEqualTo("98660859d0766b2718224f9f53b21cacd5663faf168daec85a571cab04277e7f")
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    void write_usesACopyWithJavaTimeSerialization() throws Exception {
        ObjectMapper source = new ObjectMapper().findAndRegisterModules();
        ItBudgetCanonicalJson localCanonical = new ItBudgetCanonicalJson(source);
        Map<String, Integer> insertionOrdered = new LinkedHashMap<>();
        insertionOrdered.put("b", 2);
        insertionOrdered.put("a", 1);

        assertThat(
                        localCanonical.write(
                                Map.of("capturedAt", Instant.parse("2026-09-06T05:00:00Z"))))
                .isEqualTo("{\"capturedAt\":\"2026-09-06T05:00:00Z\"}");
        assertThat(source.writeValueAsString(insertionOrdered))
                .isNotEqualTo(localCanonical.write(insertionOrdered));
    }

    @Test
    void exactScale_normalizesEquivalentNumericValuesWithoutRounding() {
        assertThat(canonical.money(new BigDecimal("1")).toPlainString()).isEqualTo("1.000");
        assertThat(canonical.exchangeRate(new BigDecimal("1.2")).toPlainString())
                .isEqualTo("1.2000");
        assertThat(canonical.quantity(new BigDecimal("3.0")).toPlainString()).isEqualTo("3");
        assertThat(canonical.write(Map.of("amount", new BigDecimal("1E+3"))))
                .isEqualTo("{\"amount\":1000}");
    }

    @Test
    void exactScale_rejectsPrecisionThatWouldRequireRounding() {
        assertThatThrownBy(() -> canonical.money(new BigDecimal("1.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액");
        assertThatThrownBy(() -> canonical.exchangeRate(new BigDecimal("1.23456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환율");
        assertThatThrownBy(() -> canonical.quantity(new BigDecimal("1.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량");
    }

    @Test
    void canonicalization_preservesNullAndEmptyValuesWithoutConflatingThem() {
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("value", null);

        assertThat(canonical.write(nullValue)).isEqualTo("{\"value\":null}");
        assertThat(canonical.write(Map.of("value", ""))).isEqualTo("{\"value\":\"\"}");
        assertThat(canonical.write(Map.of())).isEqualTo("{}");
        assertThat(canonical.money(null)).isNull();
        assertThat(canonical.exchangeRate(null)).isNull();
        assertThat(canonical.quantity(null)).isNull();
    }

    @Test
    void internalSnapshot_keepsBigDecimalsAndPublicDtoCompatibleFields() {
        assertThat(recordComponentType(ItBudgetSnapshot.class, "form"))
                .isEqualTo(ItBudgetSnapshot.Form.class);
        assertThat(recordComponentType(ItBudgetSnapshot.class, "payload"))
                .isEqualTo(ItBudgetSnapshot.Payload.class);
        assertThat(recordComponentType(ItBudgetSnapshot.class, "approvalLine"))
                .isEqualTo(ItBudgetSnapshot.ApprovalLine.class);
        assertThat(recordComponentType(ItBudgetSnapshot.class, "integrity"))
                .isEqualTo(ItBudgetSnapshot.Integrity.class);
        assertThat(recordComponentType(ItBudgetSnapshot.ApprovalLine.class, "requester"))
                .isEqualTo(ItBudgetSnapshot.Person.class);
        assertThat(recordComponentType(ItBudgetSnapshot.ProjectItem.class, "quantity"))
                .isEqualTo(BigDecimal.class);
        assertThat(recordComponentType(ItBudgetSnapshot.ProjectItem.class, "amount"))
                .isEqualTo(BigDecimal.class);
        assertThat(recordComponentType(ItBudgetSnapshot.Terminal.class, "exchangeRate"))
                .isEqualTo(BigDecimal.class);
        assertThat(recordComponentType(ItBudgetSnapshot.Cost.class, "exchangeRateBaseDate"))
                .isEqualTo(LocalDate.class);
        assertThat(recordComponentType(ItBudgetSnapshot.Integrity.class, "capturedAt"))
                .isEqualTo(Instant.class);
        assertThat(recordComponentType(ItBudgetSnapshot.Source.class, "digest"))
                .isEqualTo(String.class);
    }

    @Test
    void snapshot_defensivelyCopiesCallerOwnedListsBeforeCanonicalDigest() {
        List<ItBudgetSnapshot.ApprovalPerson> approvers =
                new ArrayList<>(
                        List.of(new ItBudgetSnapshot.ApprovalPerson("E1", "결재자", "부장", null)));
        List<ItBudgetSnapshot.ProjectItem> items =
                new ArrayList<>(
                        List.of(
                                new ItBudgetSnapshot.ProjectItem(
                                        1, 1, null, null, null, null, null, null)));
        List<ItBudgetSnapshot.Project> projects = new ArrayList<>(List.of(project(items)));
        List<ItBudgetSnapshot.Terminal> terminals =
                new ArrayList<>(
                        List.of(
                                new ItBudgetSnapshot.Terminal(
                                        1, 1, null, null, null, null, null, null, null, null)));
        List<ItBudgetSnapshot.Cost> costs = new ArrayList<>(List.of(cost(terminals)));
        List<ItBudgetSnapshot.Source> sources =
                new ArrayList<>(
                        List.of(
                                new ItBudgetSnapshot.Source(
                                        "PROJECT", "P-1", 1, 1, "a".repeat(64))));
        ItBudgetSnapshot snapshot =
                new ItBudgetSnapshot(
                        new ItBudgetSnapshot.Form("it-budget", 2),
                        new ItBudgetSnapshot.Payload(
                                projects, costs, new ItBudgetSnapshot.Summary(null, null, null)),
                        new ItBudgetSnapshot.ApprovalLine(null, approvers),
                        new ItBudgetSnapshot.Integrity(
                                "SHA-256", "IT_BUDGET_V2", "b".repeat(64), null, sources));
        String digestBeforeMutation = canonical.digest(snapshot);

        approvers.clear();
        items.clear();
        projects.clear();
        terminals.clear();
        costs.clear();
        sources.clear();

        assertThat(snapshot.approvalLine().approvers()).hasSize(1);
        assertThat(snapshot.payload().projects()).hasSize(1);
        assertThat(snapshot.payload().projects().getFirst().items()).hasSize(1);
        assertThat(snapshot.payload().costs()).hasSize(1);
        assertThat(snapshot.payload().costs().getFirst().terminals()).hasSize(1);
        assertThat(snapshot.integrity().sources()).hasSize(1);
        assertThat(canonical.digest(snapshot)).isEqualTo(digestBeforeMutation);
    }

    @Test
    void requiredSnapshotLists_rejectNullReferencesAndNullElements() {
        assertThatThrownBy(() -> new ItBudgetSnapshot.ApprovalLine(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new ItBudgetSnapshot.ApprovalLine(
                                        null, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> project(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> project(Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cost(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cost(Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ItBudgetSnapshot.Payload(null, List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new ItBudgetSnapshot.Payload(
                                        Collections.singletonList(null), List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ItBudgetSnapshot.Integrity(null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new ItBudgetSnapshot.Integrity(
                                        null, null, null, null, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    private static Class<?> recordComponentType(Class<?> recordType, String componentName) {
        return java.util.Arrays.stream(recordType.getRecordComponents())
                .filter(component -> component.getName().equals(componentName))
                .findFirst()
                .orElseThrow()
                .getType();
    }

    private static ItBudgetSnapshot.Project project(List<ItBudgetSnapshot.ProjectItem> items) {
        return new ItBudgetSnapshot.Project(
                "P-1", 1, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, items);
    }

    private static ItBudgetSnapshot.Cost cost(List<ItBudgetSnapshot.Terminal> terminals) {
        return new ItBudgetSnapshot.Cost(
                "C-1", 1, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, terminals);
    }

    private record ObjectFields(String b, String a) {}
}
