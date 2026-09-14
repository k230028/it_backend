package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.kdb.it.exception.DataCorruptionException;
import org.junit.jupiter.api.Test;

class ItBudgetSnapshotReaderTest {
    private final ItBudgetSnapshotReader reader = reader();

    @Test
    void keepsStoredV2FixtureDigestFrozen() throws Exception {
        var root = v2();

        assertThat(root.at("/integrity/payloadDigest").textValue()).isEqualTo(V2_PAYLOAD_DIGEST);
        var parsed = reader.read(v2Json());
        assertThat(parsed.version()).isEqualTo(2);
        assertThat(parsed.payload()).isNotNull();
        assertThat(parsed.write()).isEqualTo(v2Json());
    }

    @Test
    void readsV3AndKeepsV2ProjectionForExistingConsumers() throws Exception {
        var parsed = reader.read(v3Json());

        assertThat(parsed.version()).isEqualTo(3);
        assertThat(parsed.v3Payload().projects().getFirst().items().getFirst().foreignAmount())
                .isEqualByComparingTo("1000.000");
        assertThat(parsed.payload().projects().getFirst().name()).isEqualTo("사업 요약");
        assertThat(parsed.payload().projects().getFirst().items().getFirst().amount())
                .isEqualByComparingTo("100.000");
    }

    @Test
    void rejectsTamperedV3LedgerColumnWithOriginalDigest() throws Exception {
        var root = v3();
        object(root, "/payload/ledger/aggregates/0/children/0/columns").put("FC_AMT", "999.000");

        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class)
                .hasMessageContaining("payloadDigest");
    }

    @Test
    void rejectsMissingV3LedgerIdentityEvenWhenResigned() throws Exception {
        var root = v3();
        object(root, "/payload/ledger/aggregates/0/parent/columns").remove("ABUS_MNG_NO");
        resignV3(root);

        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void acceptsAdditionalScalarV3LedgerColumns() throws Exception {
        var root = v3();
        var columns = object(root, "/payload/ledger/aggregates/0/parent/columns");
        columns.put("FUTURE_STRING", "value");
        columns.put("FUTURE_NUMBER", 7);
        columns.put("FUTURE_BOOLEAN", true);
        columns.putNull("FUTURE_NULL");
        resignV3(root);

        assertThat(reader.read(root.toString()).version()).isEqualTo(3);
    }

    @Test
    void rejectsObjectAndArrayV3LedgerColumnValuesEvenWhenResigned() throws Exception {
        var objectValue = v3();
        object(objectValue, "/payload/ledger/aggregates/0/parent/columns")
                .set("FUTURE_VALUE", MAPPER.createObjectNode().put("nested", true));
        resignV3(objectValue);
        var arrayValue = v3();
        object(arrayValue, "/payload/ledger/aggregates/0/parent/columns")
                .set("FUTURE_VALUE", MAPPER.createArrayNode().add("nested"));
        resignV3(arrayValue);

        assertThatThrownBy(() -> reader.read(objectValue.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(arrayValue.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsUnknownV3EnvelopeFieldEvenWithMatchingPayloadDigest() throws Exception {
        var root = v3();
        root.put("unexpected", "value");

        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsMismatchedV3AggregateIdentityEvenWhenResigned() throws Exception {
        var root = v3();
        object(root, "/payload/ledger/aggregates/0").put("id", "P2");
        resignV3(root);

        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsMalformedV3LedgerTablesEvenWhenResigned() throws Exception {
        assertRejectedAfterV3Resign(
                root -> object(root, "/payload/ledger/aggregates/0/parent").put("table", "BCOSTM"));
        assertRejectedAfterV3Resign(
                root ->
                        object(root, "/payload/ledger/aggregates/0/children/0")
                                .put("table", "BTERMM"));
        assertRejectedAfterV3Resign(
                root -> object(root, "/payload/ledger/aggregates/1/parent").put("table", "BPROJM"));
        assertRejectedAfterV3Resign(
                root ->
                        object(root, "/payload/ledger/aggregates/1/children/0")
                                .put("table", "BITEMM"));
    }

    @Test
    void rejectsMalformedV3LedgerCompositeKeysEvenWhenResigned() throws Exception {
        assertRejectedAfterV3Resign(
                root ->
                        object(root, "/payload/ledger/aggregates/0/children/0/columns")
                                .put("GCL_MNG_NO", "I2"));
        assertRejectedAfterV3Resign(
                root ->
                        object(root, "/payload/ledger/aggregates/0/children/0/columns")
                                .put("FNT_TB_CRY_SNO", 3));
        assertRejectedAfterV3Resign(
                root ->
                        object(root, "/payload/ledger/aggregates/1/children/0/columns")
                                .put("BG_NO", "C2"));
    }

    @Test
    void rejectsBlankOrNonPositiveDeletedV3ChildPrimaryKeysEvenWhenResigned() throws Exception {
        var blankId = v3();
        var deletedWithBlankId =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        blankId.at("/payload/ledger/aggregates/0/children/0").deepCopy();
        object(deletedWithBlankId, "/columns").put("GCL_MNG_NO", "").put("DEL_YN", "Y");
        ((ArrayNode) blankId.at("/payload/ledger/aggregates/0/children")).add(deletedWithBlankId);
        resignV3(blankId);
        var zeroSequence = v3();
        var deletedWithZeroSequence =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        zeroSequence.at("/payload/ledger/aggregates/1/children/0").deepCopy();
        object(deletedWithZeroSequence, "/columns").put("SNO", 0).put("DEL_YN", "Y");
        ((ArrayNode) zeroSequence.at("/payload/ledger/aggregates/1/children"))
                .add(deletedWithZeroSequence);
        resignV3(zeroSequence);

        assertThatThrownBy(() -> reader.read(blankId.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(zeroSequence.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsUnknownAndDuplicateV3LedgerAggregatesEvenWhenResigned() throws Exception {
        var unknown = v3();
        object(unknown, "/payload/ledger/aggregates/0").put("kind", "UNKNOWN");
        resignV3(unknown);
        var duplicate = v3();
        ((ArrayNode) duplicate.at("/payload/ledger/aggregates"))
                .add(duplicate.at("/payload/ledger/aggregates/0").deepCopy());
        resignV3(duplicate);

        assertThatThrownBy(() -> reader.read(unknown.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(duplicate.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsMalformedV3ApprovalRoles() throws Exception {
        var empty = v3();
        ((ArrayNode) empty.at("/approvalLine/approvers")).removeAll();
        var duplicate = v3();
        object(duplicate, "/approvalLine/approvers/1").put("role", "TEAM_LEAD");
        var reversed = v3();
        object(reversed, "/approvalLine/approvers/0").put("role", "DEPT_HEAD");
        object(reversed, "/approvalLine/approvers/1").put("role", "TEAM_LEAD");

        assertThatThrownBy(() -> reader.read(empty.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(duplicate.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(reversed.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsMismatchedAndDuplicateV3SourceIdentities() throws Exception {
        var mismatch = v3();
        object(mismatch, "/integrity/sources/0").put("id", "P2");
        var duplicate = v3();
        object(duplicate, "/integrity/sources/1")
                .put("kind", "PROJECT")
                .put("id", "P1")
                .put("revision", 2);

        assertThatThrownBy(() -> reader.read(mismatch.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(duplicate.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsUnsupportedV3CanonicalizationAndLedgerFormat() throws Exception {
        var canonicalization = v3();
        object(canonicalization, "/integrity").put("canonicalization", "IT_BUDGET_V2");
        var ledgerFormat = v3();
        object(ledgerFormat, "/payload/ledger").put("format", "IT_BUDGET_LEDGER_V2");

        assertThatThrownBy(() -> reader.read(canonicalization.toString()))
                .isInstanceOf(DataCorruptionException.class);
        assertThatThrownBy(() -> reader.read(ledgerFormat.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void v3PayloadIsUnavailableForV1AndV2() throws Exception {
        assertThatThrownBy(() -> reader.read(v2Json()).v3Payload())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                reader.read("{\"form\":{\"id\":\"it-budget\",\"version\":1}}")
                                        .v3Payload())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingRoleInV2() throws Exception {
        var root = v2();
        object(root, "/approvalLine/approvers/0").remove("role");
        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void invalidRolesAndRoleOrderFailOnReadAndMutation() throws Exception {
        for (String role : new String[] {"TEAM_LEAD", "UNKNOWN", ""}) {
            var root = v2();
            object(root, "/approvalLine/approvers/1").put("role", role);
            assertThatThrownBy(() -> reader.read(root.toString()))
                    .isInstanceOf(DataCorruptionException.class);
        }
        var root = v2();
        object(root, "/approvalLine/approvers/0").putNull("role");
        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
        var reversed = v2();
        object(reversed, "/approvalLine/approvers/0").put("role", "DEPT_HEAD");
        object(reversed, "/approvalLine/approvers/1").put("role", "TEAM_LEAD");
        assertThatThrownBy(() -> reader.read(reversed.toString()))
                .isInstanceOf(DataCorruptionException.class);
        var parsed = reader.read(v2().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        parsed.approvalLine(true).at("/approvers/1"))
                .put("role", "TEAM_LEAD");
        assertThatThrownBy(parsed::write).isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsStoredSnapshotWithEmptyApprovalLine() throws Exception {
        var root = v2();
        ((ArrayNode) root.at("/approvalLine/approvers")).removeAll();

        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void dispatchesLegacyAndV2WithoutRewritingLegacy() throws Exception {
        String legacy =
                "{\"form\":{\"id\":\"it-budget\",\"version\":1},\"projects\":[],\"extension\":true}";
        var parsed = reader.read(legacy);
        assertThat(parsed.version()).isEqualTo(1);
        assertThat(parsed.write()).isEqualTo(legacy);
        assertThat(
                        reader.read(
                                        "{\"form\":{\"id\":\"another\",\"version\":9},\"payload\":{\"extension\":1}}")
                                .version())
                .isEqualTo(1);
        assertThat(reader.read(v2().toString()).version()).isEqualTo(2);
    }

    @Test
    void publicNumericStringsRoundTripToOriginalCanonicalPayload() throws Exception {
        var root = v2();
        var parsed = reader.read(root.toString());
        var canonical = new ItBudgetCanonicalJson(MAPPER);
        assertThat(canonical.digest(parsed.payload()))
                .isEqualTo(root.at("/integrity/payloadDigest").textValue());
        assertThat(canonical.digest(root.get("payload")))
                .isNotEqualTo(root.at("/integrity/payloadDigest").textValue());
        assertThat(
                        MAPPER.<com.fasterxml.jackson.databind.JsonNode>valueToTree(
                                ItBudgetSnapshotCodec.toPublic(
                                        MAPPER, canonical, parsed.payload())))
                .isEqualTo(root.get("payload"));
    }

    @Test
    void rejectsDuplicatePropertiesAndTrailingTokensWithoutRawCause() throws Exception {
        for (String raw :
                new String[] {
                    v2() + " {}",
                    v2().toString().replace("\"version\":2", "\"version\":2,\"version\":2"),
                    "{\"secretName\":PRIVATE_JSON_VALUE}"
                }) {
            assertThatThrownBy(() -> reader.read(raw))
                    .isInstanceOf(DataCorruptionException.class)
                    .hasNoCause()
                    .hasMessageNotContaining("PRIVATE_JSON_VALUE");
        }
    }

    @Test
    void nullVersusMissingAndScalarTokenTypeRemainDistinctEvenWithMatchingDigest()
            throws Exception {
        var missing = v2();
        object(missing, "/payload/projects/0").remove("outline");
        resign(missing);
        assertThatThrownBy(() -> reader.read(missing.toString()))
                .isInstanceOf(DataCorruptionException.class);
        var number = v2();
        object(number, "/payload/projects/0").put("currentRequestAmount", 100.000);
        resign(number);
        assertThatThrownBy(() -> reader.read(number.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void rejectsNullCollectionElementsAndMismatchedChildRevisionEvenWhenResigned()
            throws Exception {
        var root = v2();
        ((ArrayNode) root.at("/payload/projects/0/items")).addNull();
        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
        var changed = v2();
        object(changed, "/payload/projects/0/items/0").put("revision", 9);
        resign(changed);
        String raw = changed.toString();
        assertThatThrownBy(() -> reader.read(raw)).isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void sourceDigestRemainsHistoricalMetadataAndDoesNotUsePayloadDigestScope() throws Exception {
        var root = v2();
        object(root, "/integrity/sources/0").put("digest", "c".repeat(64));
        assertThat(reader.read(root.toString()).write()).isEqualTo(root.toString());
    }

    @Test
    void mutableLineIsRevalidatedBeforeSerialization() throws Exception {
        var parsed = reader.read(v2().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        parsed.approvalLine(true).at("/approvers/0"))
                .putNull("eno");
        assertThatThrownBy(parsed::write).isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void legacyAdapterCannotExposeMutableV2Root() throws Exception {
        var parsed = reader.read(v2().toString());
        assertThatThrownBy(
                        () ->
                                parsed.legacyValue(
                                        com.fasterxml.jackson.databind.node.ObjectNode.class))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recallExtensionIsExplicitAndStrict() throws Exception {
        var parsed = reader.read(v2().toString());
        parsed.recall("U1", null);
        assertThat(reader.read(parsed.write()).version()).isEqualTo(2);
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(parsed.write());
        object(root, "/recallInfo").put("unexpected", "private");
        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }

    private void assertRejectedAfterV3Resign(
            java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> mutation)
            throws Exception {
        var root = v3();
        mutation.accept(root);
        resignV3(root);
        assertThatThrownBy(() -> reader.read(root.toString()))
                .isInstanceOf(DataCorruptionException.class);
    }
}
