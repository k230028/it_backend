package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 게시판 답글 보정 마이그레이션의 패키징 원본과 트랜잭션 경계를 검증합니다. */
class BoardReplySequenceMigrationSourceTest {

    private static final String DATA_MIGRATION = "V20260730_001__RepairBoardReplySequences.sql";
    private static final String INDEX_MIGRATION =
            "V20260730_002__AddBoardCommentReplyTailIndex.sql";
    private static final String DATA_SHA256 =
            "1EB9C0C59D91B83DF3A34CB12BBBBA27F2E8C8269201F82FD410DFD4E4D666A2";
    private static final String INDEX_SHA256 =
            "8E66FE5B8C79EC6C6DF05399D288913A90DA2775B88DC20048D33066E61C1ED5";

    @Test
    @DisplayName("데이터 보정과 인덱스 DDL은 서로 다른 Flyway 마이그레이션에만 존재한다")
    void migrations_separateRepairDmlFromIndexDdl() throws Exception {
        String dataSql = executableSql(classpathMigration(DATA_MIGRATION));
        String indexSql = executableSql(classpathMigration(INDEX_MIGRATION));

        assertThat(dataSql).contains("MERGE INTO");
        assertThat(dataSql)
                .doesNotContain("CREATE INDEX")
                .doesNotContain("DROP INDEX")
                .doesNotContain("COMMIT")
                .doesNotContain("ROLLBACK");
        assertThat(indexSql).contains("CREATE INDEX");
        assertThat(indexSql)
                .doesNotContain("MERGE INTO")
                .doesNotContain("UPDATE ")
                .doesNotContain("LOCK TABLE");
    }

    @Test
    @DisplayName("패키징 마이그레이션은 DB SoT 원본 및 고정 SHA와 일치한다")
    void classpathMigrations_matchDatabaseSourcesAndPinnedHashes() throws Exception {
        assertSourceAndHash(DATA_MIGRATION, DATA_SHA256);
        assertSourceAndHash(INDEX_MIGRATION, INDEX_SHA256);
    }

    @Test
    @DisplayName("데이터 보정은 게시물과 댓글의 중복·공백 검증 오류를 각각 고정한다")
    void dataMigration_definesEveryDuplicateAndGapValidationBranch() throws Exception {
        String dataSql = executableSql(classpathMigration(DATA_MIGRATION));

        assertThat(dataSql).contains("-20071", "-20072", "-20073", "-20074");
        assertThat(dataSql).doesNotContain("-20075");
    }

    @Test
    @DisplayName("인덱스 마이그레이션은 동명 인덱스 충돌 오류만 소유한다")
    void indexMigration_ownsIndexConflictValidation() throws Exception {
        String indexSql = executableSql(classpathMigration(INDEX_MIGRATION));

        assertThat(indexSql).contains("-20075");
        assertThat(indexSql).doesNotContain("-20071", "-20072", "-20073", "-20074");
    }

    private void assertSourceAndHash(String fileName, String expectedHash) throws Exception {
        byte[] packaged = classpathMigration(fileName);
        Path source = Path.of("..", "it_database", "migrations", fileName).toAbsolutePath();

        assertThat(source).exists().isRegularFile();
        byte[] databaseSource = Files.readAllBytes(source);
        assertThat(packaged).isEqualTo(databaseSource);
        assertThat(sha256(packaged)).isEqualTo(expectedHash);
    }

    private byte[] classpathMigration(String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/" + fileName);
        assertThat(resource.exists()).as("패키징 마이그레이션 %s", fileName).isTrue();
        return resource.getContentAsByteArray();
    }

    private String executableSql(byte[] source) {
        StringBuilder executable = new StringBuilder();
        for (String line : new String(source, StandardCharsets.UTF_8).split("\\R")) {
            String trimmed = line.stripLeading();
            if (!trimmed.startsWith("--")) {
                executable.append(line).append('\n');
            }
        }
        return executable.toString().toUpperCase();
    }

    private String sha256(byte[] source) throws NoSuchAlgorithmException {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(source));
    }
}
