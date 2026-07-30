package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.context.ActiveProfiles;

/** Oracle 마이그레이션 통합 테스트가 기본 공유 스키마에서 실행되지 않는지 검증합니다. */
class BoardReplySequenceMigrationSafetyTest {

    @Test
    @DisplayName("마이그레이션 통합 테스트는 명시적 환경변수가 없으면 비활성이다")
    void integrationTest_requiresExplicitOptInEnvironmentVariable() {
        EnabledIfEnvironmentVariable condition =
                BoardReplySequenceMigrationIT.class.getAnnotation(
                        EnabledIfEnvironmentVariable.class);

        assertThat(condition).isNotNull();
        assertThat(condition.named()).isEqualTo("SEC15_MIGRATION_IT_ENABLED");
        assertThat(condition.matches()).isEqualTo("true");
    }

    @Test
    @DisplayName("마이그레이션 통합 테스트는 공유 test-it이 아닌 전용 프로파일을 사용한다")
    void integrationTest_usesDisposableMigrationProfile() {
        ActiveProfiles profiles =
                BoardReplySequenceMigrationIT.class.getAnnotation(ActiveProfiles.class);

        assertThat(profiles).isNotNull();
        assertThat(Arrays.asList(profiles.value())).containsExactly("migration-it");
    }

    @Test
    @DisplayName("마이그레이션 IT는 전체 애플리케이션 대신 JDBC 전용 슬라이스만 로드한다")
    void integrationTest_loadsOnlyJdbcSliceWithoutProductionImports() {
        assertThat(BoardReplySequenceMigrationIT.class.getAnnotation(SpringBootTest.class))
                .isNull();
        assertThat(BoardReplySequenceMigrationIT.class.getAnnotation(JdbcTest.class)).isNotNull();
        assertThat(BoardReplySequenceMigrationIT.class.getAnnotation(Import.class)).isNull();
    }

    @Test
    @DisplayName("마이그레이션 프로파일은 공유 DB 기본값과 스케줄러를 모두 차단한다")
    void migrationProfile_hasNoSharedFallbackAndDisablesSchedulers() throws Exception {
        Properties properties =
                PropertiesLoaderUtils.loadProperties(
                        new ClassPathResource("application-migration-it.properties"));

        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${SEC15_MIGRATION_DB_URL}");
        assertThat(properties.getProperty("spring.datasource.username"))
                .isEqualTo("${SEC15_MIGRATION_DB_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password"))
                .isEqualTo("${SEC15_MIGRATION_DB_PASSWORD}");
        assertThat(properties.getProperty("notification.retry.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.task.scheduling.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("격리 게이트는 전용 스키마와 marker token이 모두 일치할 때만 통과한다")
    void disposableGuard_acceptsOnlyMatchingDisposableSchemaMarker() {
        assertThatCode(() -> invokeGuard("ITP_DISP_SEC15", 1, "token-123", 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("격리 게이트는 공유 스키마 또는 불완전한 marker를 mutation 전에 거부한다")
    void disposableGuard_rejectsSharedSchemaAndIncompleteMarkers() {
        assertGuardRejected("ITPOWN", 1, "token-123", 1);
        assertGuardRejected("ITP_DISP_SEC15", 0, "token-123", 0);
        assertGuardRejected("ITP_DISP_SEC15", 1, "", 0);
        assertGuardRejected("ITP_DISP_SEC15", 1, "token-123", 0);
    }

    private void assertGuardRejected(
            String schema, int markerTableCount, String expectedToken, int markerCount) {
        assertThatThrownBy(() -> invokeGuard(schema, markerTableCount, expectedToken, markerCount))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }

    private void invokeGuard(
            String schema, int markerTableCount, String expectedToken, int markerCount)
            throws Exception {
        Method guard =
                BoardReplySequenceMigrationIT.class.getDeclaredMethod(
                        "verifySafetyInputs",
                        String.class,
                        Integer.class,
                        String.class,
                        Integer.class);
        guard.setAccessible(true);
        guard.invoke(null, schema, markerTableCount, expectedToken, markerCount);
    }
}
