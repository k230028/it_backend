package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 게시판 답글 보정 Flyway 스크립트를 폐기 가능한 전용 Oracle 스키마에서 검증합니다.
 *
 * <p>{@code SEC15_MIGRATION_IT_ENABLED=true}를 명시하고, 현재 스키마 이름과 marker 테이블 token까지 검증한 뒤에만 fixture와
 * DDL/DML을 실행합니다.
 */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("migration-it")
@EnabledIfEnvironmentVariable(named = "SEC15_MIGRATION_IT_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoardReplySequenceMigrationIT {

    private static final String DATA_MIGRATION =
            "db/migration/V20260730_001__RepairBoardReplySequences.sql";
    private static final String INDEX_MIGRATION =
            "db/migration/V20260730_002__AddBoardCommentReplyTailIndex.sql";
    private static final String DISPOSABLE_SCHEMA_PREFIX = "ITP_DISP_";
    private static final String MARKER_TABLE = "SEC15_MIGRATION_IT_MARKER";
    private static final String INDEX_NAME = "IX_TPRMPP_CCMMTM_01";
    private static final String TRIGGER_NAME = "TR_SEC15_R8_VALIDATE";
    private static final String ORIGINAL_ACTOR = "R8_ORIGINAL";
    private static final String MIGRATION_ACTOR = "SEC15_MIGRATOR";

    @Autowired private JdbcTemplate jdbcTemplate;

    private String schema;
    private String suffix;
    private String boardId;
    private String postGroupId;
    private List<String> postIds;
    private List<Long> commentIds;
    private Long commentGroupId;

    @BeforeAll
    void verifyDisposableSchemaMarker() {
        schema =
                jdbcTemplate.queryForObject(
                        "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL", String.class);
        Integer markerTableCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = ? AND TABLE_NAME = ?",
                        Integer.class,
                        schema,
                        MARKER_TABLE);
        String expectedToken = System.getenv().getOrDefault("SEC15_MIGRATION_IT_MARKER_TOKEN", "");
        Integer markerCount = 0;
        if (markerTableCount != null && markerTableCount == 1 && !expectedToken.isBlank()) {
            markerCount =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + MARKER_TABLE + " WHERE MARKER_TOKEN = ?",
                            Integer.class,
                            expectedToken);
        }
        verifySafetyInputs(schema, markerTableCount, expectedToken, markerCount);
    }

    private static void verifySafetyInputs(
            String schema, Integer markerTableCount, String expectedToken, Integer markerCount) {
        assertThat(schema)
                .as("마이그레이션 IT는 폐기 가능한 전용 스키마에서만 실행합니다.")
                .startsWith(DISPOSABLE_SCHEMA_PREFIX);
        assertThat(markerTableCount).as("전용 스키마 marker 테이블이 사전에 생성되어야 합니다.").isEqualTo(1);
        assertThat(expectedToken).as("SEC15_MIGRATION_IT_MARKER_TOKEN 환경변수가 필요합니다.").isNotBlank();
        assertThat(markerCount).as("전용 스키마 marker token이 일치해야 합니다.").isEqualTo(1);
    }

    @BeforeEach
    void setUp() {
        dropValidationSabotageTrigger();
        dropTargetIndex();
        assertNoEquivalentIndex();

        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        boardId = "R8" + suffix;
        postIds =
                List.of(
                        "R8P0" + suffix,
                        "R8P1" + suffix,
                        "R8P2" + suffix,
                        "R8P3" + suffix,
                        "R8P4" + suffix,
                        "R8P9" + suffix);
        postGroupId = postIds.getFirst();
        commentIds =
                List.of(
                        nextCommentId(),
                        nextCommentId(),
                        nextCommentId(),
                        nextCommentId(),
                        nextCommentId(),
                        nextCommentId());
        commentGroupId = commentIds.getFirst();

        insertPost(postIds.get(0), 0, "N");
        insertPost(postIds.get(1), 1, "N");
        insertPost(postIds.get(2), 2, "N");
        insertPost(postIds.get(3), 2, "N");
        insertPost(postIds.get(4), 5, "N");
        insertPost(postIds.get(5), 2, "Y");

        insertComment(commentIds.get(0), 0, "N");
        insertComment(commentIds.get(1), 1, "N");
        insertComment(commentIds.get(2), 2, "N");
        insertComment(commentIds.get(3), 2, "N");
        insertComment(commentIds.get(4), 5, "N");
        insertComment(commentIds.get(5), 2, "Y");
    }

    @AfterEach
    void tearDown() {
        dropValidationSabotageTrigger();
        dropTargetIndex();
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTL WHERE NAC_NO = ?", postGroupId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTM WHERE NAC_NO = ?", postGroupId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCL WHERE BLB_ID = ?", boardId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE BLB_ID = ?", boardId);
    }

    @Test
    @DisplayName("V001은 활성 그룹만 결정적으로 보정하고 재실행해도 결과를 바꾸지 않는다")
    void dataMigration_repairsActiveGroupsDeterministicallyAndIsIdempotent() throws Exception {
        Map<String, Object> deletedPostBefore = postState(postIds.get(5));
        Map<String, Object> deletedCommentBefore = commentState(commentIds.get(5));

        executeMigration(DATA_MIGRATION);

        assertRepairedOrder();
        assertThat(activePostDuplicateCount()).isZero();
        assertThat(activeCommentDuplicateCount()).isZero();
        assertThat(postState(postIds.get(5))).isEqualTo(deletedPostBefore);
        assertThat(commentState(commentIds.get(5))).isEqualTo(deletedCommentBefore);
        assertThat(lastChangedByPost(postIds.get(2))).isEqualTo(ORIGINAL_ACTOR);
        assertThat(lastChangedByPost(postIds.get(3))).isEqualTo(MIGRATION_ACTOR);
        assertThat(lastChangedByPost(postIds.get(4))).isEqualTo(MIGRATION_ACTOR);
        assertThat(lastChangedByComment(commentIds.get(2))).isEqualTo(ORIGINAL_ACTOR);
        assertThat(lastChangedByComment(commentIds.get(3))).isEqualTo(MIGRATION_ACTOR);
        assertThat(lastChangedByComment(commentIds.get(4))).isEqualTo(MIGRATION_ACTOR);
        assertThat(postAuditCount()).isZero();
        assertThat(commentAuditCount()).isZero();
        assertTargetIndexAbsent();

        Timestamp postChangedAt = lastChangedAtPost(postIds.get(4));
        Timestamp commentChangedAt = lastChangedAtComment(commentIds.get(4));
        Thread.sleep(1100);

        executeMigration(DATA_MIGRATION);

        assertRepairedOrder();
        assertThat(lastChangedAtPost(postIds.get(4))).isEqualTo(postChangedAt);
        assertThat(lastChangedAtComment(commentIds.get(4))).isEqualTo(commentChangedAt);
        assertTargetIndexAbsent();
    }

    @Test
    @DisplayName("V002는 대상 인덱스가 없는 상태에서 생성하고 재실행 시 기존 구성을 검증한다")
    void indexMigration_createsAbsentIndexAndRerunsGuard() throws Exception {
        assertTargetIndexAbsent();
        assertNoEquivalentIndex();

        executeMigration(INDEX_MIGRATION);
        assertCommentTailIndex();

        executeMigration(INDEX_MIGRATION);
        assertCommentTailIndex();
    }

    @ParameterizedTest(name = "{0} 오류가 V001 전체 보정을 롤백한다")
    @EnumSource(ValidationSabotage.class)
    @DisplayName("V001 최종 검증 오류는 게시물·댓글 보정 DML을 모두 롤백한다")
    void dataMigration_validationFailureRollsBackAllRepairs(ValidationSabotage sabotage) {
        createValidationSabotageTrigger(sabotage);

        assertThatThrownBy(() -> executeMigration(DATA_MIGRATION))
                .rootCause()
                .hasMessageContaining(sabotage.errorCode);

        assertOriginalOrder();
        assertThat(lastChangedByPost(postIds.get(3))).isEqualTo(ORIGINAL_ACTOR);
        assertThat(lastChangedByPost(postIds.get(4))).isEqualTo(ORIGINAL_ACTOR);
        assertThat(lastChangedByComment(commentIds.get(3))).isEqualTo(ORIGINAL_ACTOR);
        assertThat(lastChangedByComment(commentIds.get(4))).isEqualTo(ORIGINAL_ACTOR);
        assertTargetIndexAbsent();
    }

    @Test
    @DisplayName("V002 충돌 실패는 이미 완료된 V001 데이터 보정에 영향을 주지 않는다")
    void indexMigration_failureDoesNotAffectCommittedRepair() throws Exception {
        executeMigration(DATA_MIGRATION);
        assertRepairedOrder();
        Timestamp repairedAt = lastChangedAtPost(postIds.get(4));
        jdbcTemplate.execute("CREATE INDEX " + INDEX_NAME + " ON TPRMPP_CCMMTM (CMMT_SNO)");

        assertThatThrownBy(() -> executeMigration(INDEX_MIGRATION))
                .rootCause()
                .hasMessageContaining("ORA-20075");

        assertRepairedOrder();
        assertThat(lastChangedAtPost(postIds.get(4))).isEqualTo(repairedAt);
        assertThat(activePostDuplicateCount()).isZero();
        assertThat(activeCommentDuplicateCount()).isZero();
    }

    private void executeMigration(String resourcePath) throws Exception {
        String script =
                new ClassPathResource(resourcePath)
                        .getContentAsString(StandardCharsets.UTF_8)
                        .trim();
        assertThat(script).endsWith("/");
        jdbcTemplate.execute(script.substring(0, script.length() - 1).trim());
    }

    private void insertPost(String postId, int sequence, String deleted) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CBLBCM (NAC_NO, BLB_ID, NAC_TTL, ANC_YN, XPO_YN, NAC_INQ_NBR, "
                        + "APG_FL_NBR, FL_APG_YN, GRP_SQN_SNO, NAC_LEV_MNG_SNO, NAC_UNQ_ID, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, 'SEC-15 격리 검증', 'N', 'Y', 0, 0, 'N', ?, 0, ?, "
                        + "?, SYSDATE, ?, ?, 1, ?, SYSDATE)",
                postId,
                boardId,
                sequence,
                postGroupId,
                ORIGINAL_ACTOR,
                deleted,
                UUID.randomUUID().toString(),
                ORIGINAL_ACTOR);
    }

    private void insertComment(Long commentId, int sequence, String deleted) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CCMMTM (CMMT_SNO, NAC_NO, CMMT_CONE, CMMT_DEP_NBR, "
                        + "CMMT_SQN_SNO, CMMT_TGT_SNO, FST_ENR_USID, FST_ENR_DTM, DEL_YN, "
                        + "GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, 'SEC-15 격리 검증', 0, ?, ?, ?, SYSDATE, ?, ?, 1, ?, SYSDATE)",
                commentId,
                postGroupId,
                sequence,
                commentGroupId,
                ORIGINAL_ACTOR,
                deleted,
                UUID.randomUUID().toString(),
                ORIGINAL_ACTOR);
    }

    private void createValidationSabotageTrigger(ValidationSabotage sabotage) {
        String keyCondition =
                sabotage.comment
                        ? "NEW.CMMT_SNO = " + commentIds.get(4)
                        : "NEW.NAC_NO = '" + postIds.get(4) + "'";
        String table = sabotage.comment ? "TPRMPP_CCMMTM" : "TPRMPP_CBLBCM";
        String column = sabotage.comment ? "CMMT_SQN_SNO" : "GRP_SQN_SNO";
        jdbcTemplate.execute(
                "CREATE OR REPLACE TRIGGER "
                        + TRIGGER_NAME
                        + " BEFORE UPDATE OF "
                        + column
                        + " ON "
                        + table
                        + " FOR EACH ROW WHEN ("
                        + keyCondition
                        + ") BEGIN :NEW."
                        + column
                        + " := "
                        + sabotage.forcedSequence
                        + "; END;");
    }

    private void dropValidationSabotageTrigger() {
        if (objectExists("ALL_TRIGGERS", "TRIGGER_NAME", TRIGGER_NAME)) {
            jdbcTemplate.execute("DROP TRIGGER " + TRIGGER_NAME);
        }
    }

    private void dropTargetIndex() {
        if (objectExists("ALL_INDEXES", "INDEX_NAME", INDEX_NAME)) {
            jdbcTemplate.execute("DROP INDEX " + INDEX_NAME);
        }
    }

    private boolean objectExists(String dictionaryView, String nameColumn, String objectName) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + dictionaryView
                                + " WHERE OWNER = ? AND "
                                + nameColumn
                                + " = ?",
                        Integer.class,
                        schema,
                        objectName);
        return count != null && count > 0;
    }

    private void assertTargetIndexAbsent() {
        assertThat(objectExists("ALL_INDEXES", "INDEX_NAME", INDEX_NAME)).isFalse();
    }

    private void assertNoEquivalentIndex() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ("
                                + "SELECT INDEX_NAME, LISTAGG(COLUMN_NAME, ',') "
                                + "WITHIN GROUP (ORDER BY COLUMN_POSITION) COLUMN_LIST "
                                + "FROM ALL_IND_COLUMNS WHERE INDEX_OWNER = ? "
                                + "AND TABLE_NAME = 'TPRMPP_CCMMTM' GROUP BY INDEX_NAME) "
                                + "WHERE COLUMN_LIST = 'NAC_NO,CMMT_TGT_SNO,DEL_YN,CMMT_SQN_SNO'",
                        Integer.class,
                        schema);
        assertThat(count).isZero();
    }

    private void assertCommentTailIndex() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT COLUMN_NAME FROM ALL_IND_COLUMNS "
                                        + "WHERE INDEX_OWNER = ? AND INDEX_NAME = ? "
                                        + "ORDER BY COLUMN_POSITION",
                                String.class,
                                schema,
                                INDEX_NAME))
                .containsExactly("NAC_NO", "CMMT_TGT_SNO", "DEL_YN", "CMMT_SQN_SNO");
    }

    private Long nextCommentId() {
        return jdbcTemplate.queryForObject(
                "SELECT SQ_TPRMPP_CCMMTM_1.NEXTVAL FROM DUAL", Long.class);
    }

    private int activePostDuplicateCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT GRP_SQN_SNO FROM TPRMPP_CBLBCM "
                        + "WHERE BLB_ID = ? AND NAC_UNQ_ID = ? AND DEL_YN = 'N' "
                        + "GROUP BY GRP_SQN_SNO HAVING COUNT(*) > 1)",
                Integer.class,
                boardId,
                postGroupId);
    }

    private int activeCommentDuplicateCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT CMMT_SQN_SNO FROM TPRMPP_CCMMTM "
                        + "WHERE NAC_NO = ? AND CMMT_TGT_SNO = ? AND DEL_YN = 'N' "
                        + "GROUP BY CMMT_SQN_SNO HAVING COUNT(*) > 1)",
                Integer.class,
                postGroupId,
                commentGroupId);
    }

    private List<String> activePostOrder() {
        return jdbcTemplate.queryForList(
                "SELECT NAC_NO || ':' || TO_CHAR(GRP_SQN_SNO) FROM TPRMPP_CBLBCM "
                        + "WHERE BLB_ID = ? AND NAC_UNQ_ID = ? AND DEL_YN = 'N' "
                        + "ORDER BY GRP_SQN_SNO, NAC_NO",
                String.class,
                boardId,
                postGroupId);
    }

    private List<String> activeCommentOrder() {
        return jdbcTemplate.queryForList(
                "SELECT TO_CHAR(CMMT_SNO) || ':' || TO_CHAR(CMMT_SQN_SNO) FROM TPRMPP_CCMMTM "
                        + "WHERE NAC_NO = ? AND CMMT_TGT_SNO = ? AND DEL_YN = 'N' "
                        + "ORDER BY CMMT_SQN_SNO, CMMT_SNO",
                String.class,
                postGroupId,
                commentGroupId);
    }

    private void assertOriginalOrder() {
        assertThat(activePostOrder())
                .containsExactly(
                        postIds.get(0) + ":0",
                        postIds.get(1) + ":1",
                        postIds.get(2) + ":2",
                        postIds.get(3) + ":2",
                        postIds.get(4) + ":5");
        assertThat(activeCommentOrder())
                .containsExactly(
                        commentIds.get(0) + ":0",
                        commentIds.get(1) + ":1",
                        commentIds.get(2) + ":2",
                        commentIds.get(3) + ":2",
                        commentIds.get(4) + ":5");
    }

    private void assertRepairedOrder() {
        assertThat(activePostOrder())
                .containsExactly(
                        postIds.get(0) + ":0",
                        postIds.get(1) + ":1",
                        postIds.get(2) + ":2",
                        postIds.get(3) + ":3",
                        postIds.get(4) + ":4");
        assertThat(activeCommentOrder())
                .containsExactly(
                        commentIds.get(0) + ":0",
                        commentIds.get(1) + ":1",
                        commentIds.get(2) + ":2",
                        commentIds.get(3) + ":3",
                        commentIds.get(4) + ":4");
    }

    private Map<String, Object> postState(String postId) {
        return jdbcTemplate.queryForMap(
                "SELECT GRP_SQN_SNO, DEL_YN, LST_CHG_USID, LST_CHG_DTM "
                        + "FROM TPRMPP_CBLBCM WHERE NAC_NO = ?",
                postId);
    }

    private Map<String, Object> commentState(Long commentId) {
        return jdbcTemplate.queryForMap(
                "SELECT CMMT_SQN_SNO, DEL_YN, LST_CHG_USID, LST_CHG_DTM "
                        + "FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?",
                commentId);
    }

    private String lastChangedByPost(String postId) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_USID FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", String.class, postId);
    }

    private String lastChangedByComment(Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_USID FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?",
                String.class,
                commentId);
    }

    private Timestamp lastChangedAtPost(String postId) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_DTM FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", Timestamp.class, postId);
    }

    private Timestamp lastChangedAtComment(Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_DTM FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?",
                Timestamp.class,
                commentId);
    }

    private int postAuditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CBLBCL WHERE BLB_ID = ? AND NAC_UNQ_ID = ?",
                Integer.class,
                boardId,
                postGroupId);
    }

    private int commentAuditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CCMMTL WHERE NAC_NO = ? AND CMMT_TGT_SNO = ?",
                Integer.class,
                postGroupId,
                commentGroupId);
    }

    private enum ValidationSabotage {
        POST_DUPLICATE(false, 3, "ORA-20071"),
        COMMENT_DUPLICATE(true, 3, "ORA-20072"),
        POST_GAP(false, 6, "ORA-20073"),
        COMMENT_GAP(true, 6, "ORA-20074");

        private final boolean comment;
        private final int forcedSequence;
        private final String errorCode;

        ValidationSabotage(boolean comment, int forcedSequence, String errorCode) {
            this.comment = comment;
            this.forcedSequence = forcedSequence;
            this.errorCode = errorCode;
        }
    }
}
