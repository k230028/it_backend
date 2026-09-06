package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.kdb.it.exception.DataCorruptionException;
import org.junit.jupiter.api.Test;

class ItBudgetSnapshotReaderTest {
    private final ItBudgetSnapshotReader reader = reader();

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
}
