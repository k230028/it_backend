package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.support.OracleAvailableCondition;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 게시판 답글 순서 보정 Flyway 스크립트를 실제 Oracle에서 검증합니다. */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
class BoardReplySequenceMigrationIT {

    private static final String MIGRATION =
            "db/migration/V20260730_001__RepairBoardReplySequences.sql";
    private static final String INDEX_NAME = "IX_TPRMPP_CCMMTM_01";
    private static final String ORIGINAL_ACTOR = "R7_ORIGINAL";
    private static final String MIGRATION_ACTOR = "SEC15_MIGRATOR";

    @Autowired private JdbcTemplate jdbcTemplate;

    private String suffix;
    private String boardId;
    private String postGroupId;
    private List<String> postIds;
    private List<Long> commentIds;
    private Long commentGroupId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        boardId = "R7" + suffix;
        postIds =
                List.of(
                        "R7P0" + suffix,
                        "R7P1" + suffix,
                        "R7P2" + suffix,
                        "R7P3" + suffix,
                        "R7P4" + suffix,
                        "R7P9" + suffix);
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
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTL WHERE NAC_NO = ?", postGroupId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTM WHERE NAC_NO = ?", postGroupId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCL WHERE BLB_ID = ?", boardId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE BLB_ID = ?", boardId);
    }

    @Test
    @DisplayName("마이그레이션은 활성 그룹만 결정적으로 보정하고 재실행해도 결과를 바꾸지 않는다")
    void migration_repairsActiveGroupsDeterministicallyAndIsIdempotent() throws Exception {
        assertThat(activePostDuplicateCount()).isEqualTo(1);
        assertThat(activeCommentDuplicateCount()).isEqualTo(1);
        assertThat(postAuditCount()).isZero();
        assertThat(commentAuditCount()).isZero();

        Map<String, Object> deletedPostBefore = postState(postIds.get(5));
        Map<String, Object> deletedCommentBefore = commentState(commentIds.get(5));

        executeMigration();

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
        assertCommentTailIndex();

        Timestamp postChangedAt = lastChangedAtPost(postIds.get(4));
        Timestamp commentChangedAt = lastChangedAtComment(commentIds.get(4));
        Thread.sleep(1100);

        executeMigration();

        assertThat(lastChangedAtPost(postIds.get(4))).isEqualTo(postChangedAt);
        assertThat(lastChangedAtComment(commentIds.get(4))).isEqualTo(commentChangedAt);
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

    @Test
    @DisplayName("보정 뒤 활성 중복이 남으면 마이그레이션 검증이 실패한다")
    void migration_validationRejectsRemainingActiveDuplicates() {
        createValidationSabotageTrigger();

        assertThatThrownBy(this::executeMigration)
                .rootCause()
                .hasMessageContaining("ORA-20071")
                .hasMessageContaining("게시물 활성 답글 그룹 순서 중복");
    }

    private void executeMigration() throws Exception {
        String script =
                new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8).trim();
        assertThat(script).endsWith("/");
        jdbcTemplate.execute(script.substring(0, script.length() - 1).trim());
    }

    private void insertPost(String postId, int sequence, String deleted) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CBLBCM (NAC_NO, BLB_ID, NAC_TTL, ANC_YN, XPO_YN, NAC_INQ_NBR, "
                        + "APG_FL_NBR, FL_APG_YN, GRP_SQN_SNO, NAC_LEV_MNG_SNO, NAC_UNQ_ID, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, 'SEC-15 보정 검증', 'N', 'Y', 0, 0, 'N', ?, 0, ?, "
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
                        + "VALUES (?, ?, 'SEC-15 보정 검증', 0, ?, ?, ?, SYSDATE, ?, ?, 1, ?, SYSDATE)",
                commentId,
                postGroupId,
                sequence,
                commentGroupId,
                ORIGINAL_ACTOR,
                deleted,
                UUID.randomUUID().toString(),
                ORIGINAL_ACTOR);
    }

    private void createValidationSabotageTrigger() {
        jdbcTemplate.execute(
                "CREATE OR REPLACE TRIGGER TR_SEC15_R7_VALIDATE "
                        + "BEFORE UPDATE OF GRP_SQN_SNO ON TPRMPP_CBLBCM "
                        + "FOR EACH ROW WHEN (NEW.NAC_NO = '"
                        + postIds.get(4)
                        + "') "
                        + "BEGIN :NEW.GRP_SQN_SNO := 3; END;");
    }

    private void dropValidationSabotageTrigger() {
        Integer exists =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ALL_TRIGGERS "
                                + "WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') "
                                + "AND TRIGGER_NAME = 'TR_SEC15_R7_VALIDATE'",
                        Integer.class);
        if (exists != null && exists > 0) {
            jdbcTemplate.execute("DROP TRIGGER TR_SEC15_R7_VALIDATE");
        }
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

    private void assertCommentTailIndex() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT COLUMN_NAME FROM ALL_IND_COLUMNS "
                                        + "WHERE INDEX_OWNER = 'ITPOWN' AND INDEX_NAME = ? "
                                        + "ORDER BY COLUMN_POSITION",
                                String.class,
                                INDEX_NAME))
                .containsExactly("NAC_NO", "CMMT_TGT_SNO", "DEL_YN", "CMMT_SQN_SNO");
    }
}
