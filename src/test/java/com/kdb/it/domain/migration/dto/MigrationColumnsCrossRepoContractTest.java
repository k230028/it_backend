package com.kdb.it.domain.migration.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정규 컬럼 id 계약이 프론트 파서와 어긋나지 않는지 대조합니다 (MIG-02).
 *
 * <p>{@link MigrationColumns}와 {@code it_frontend/app/composables/migration/columns.ts}가 같은 리터럴 목록을
 * 손으로 유지합니다. 한쪽만 바꾸면 프론트가 그 컬럼을 정규화해 보내지 않아 <b>dry-run이 조용히 빈 셀을 읽습니다</b> — 진단도 나지 않고 금액만 0으로 반영되므로
 * 사람이 알아채기 어렵습니다. 양쪽의 리터럴 고정 테스트({@link MigrationColumnsTest}, 프론트 {@code
 * tests/unit/composables/migration/columns.test.ts})는 각자의 목록만 지키므로 <b>두 목록이 서로 다르게</b> 바뀐 상태는
 * 통과합니다.
 *
 * <p>그 틈을 이 테스트가 메웁니다. 프론트 저장소는 형제 디렉터리에 있다는 프로젝트 규약(루트 CLAUDE.md §2 4-repo 토폴로지)을 그대로 씁니다. 프론트를 함께
 * 체크아웃하지 않은 환경에서는 대조할 근거가 없으므로 {@link org.junit.jupiter.api.Assumptions}로 건너뜁니다 — 그때는 프론트 저장소 쪽의 같은
 * 방향 테스트가 게이트가 됩니다.
 */
class MigrationColumnsCrossRepoContractTest {

    /** 프론트 컬럼 계약 파일. 형제 디렉터리 구조를 전제합니다. */
    private static final Path FRONTEND_COLUMNS =
            Path.of("..", "it_frontend", "app", "composables", "migration", "columns.ts");

    /** `KEY: [ ... ],` 형태의 시트별 컬럼 배열 블록. */
    private static final Pattern SHEET_BLOCK =
            Pattern.compile("(COST|CAPITAL_PROJECT|DELEGATED_BUDGET|PLAN_ADJUSTMENT)\\s*:\\s*\\[");

    /** 배열 안의 문자열 리터럴. */
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'([^']*)'");

    /** 줄 끝까지의 `//` 주석. 주석 안의 인용부호가 컬럼으로 오인되지 않게 먼저 지웁니다. */
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    @DisplayName("프론트 columns.ts의 시트별 컬럼 목록과 순서까지 일치한다")
    void 프론트_컬럼계약과_일치한다() throws IOException {
        assumeTrue(
                Files.exists(FRONTEND_COLUMNS),
                "프론트 저장소를 형제 디렉터리에 함께 체크아웃하지 않아 대조를 건너뜁니다: " + FRONTEND_COLUMNS);
        String source =
                LINE_COMMENT
                        .matcher(Files.readString(FRONTEND_COLUMNS, StandardCharsets.UTF_8))
                        .replaceAll("");

        for (SheetKind kind : SheetKind.values()) {
            assertThat(frontendColumnsOf(source, kind))
                    .as("시트 %s의 정규 컬럼 목록이 프론트 columns.ts와 다릅니다 — 두 파일을 함께 갱신하세요", kind)
                    .containsExactlyElementsOf(MigrationColumns.of(kind));
        }
    }

    /** 파싱 자체가 무력화되지 않았는지 확인합니다 — 빈 목록을 뽑으면 위 대조가 조용히 통과합니다. */
    @Test
    @DisplayName("프론트 파일에서 네 시트 모두의 컬럼을 실제로 뽑아낸다")
    void 프론트_파일_파싱이_비어있지_않다() throws IOException {
        assumeTrue(Files.exists(FRONTEND_COLUMNS), "프론트 저장소 미체크아웃");
        String source =
                LINE_COMMENT
                        .matcher(Files.readString(FRONTEND_COLUMNS, StandardCharsets.UTF_8))
                        .replaceAll("");

        for (SheetKind kind : SheetKind.values()) {
            assertThat(frontendColumnsOf(source, kind)).as("시트 %s 파싱 결과", kind).isNotEmpty();
        }
    }

    /**
     * `SHEET_COLUMNS`의 한 시트 블록에서 컬럼 id를 순서대로 뽑습니다.
     *
     * @param source 주석을 제거한 columns.ts 원문
     * @param kind 시트 종류
     * @return 컬럼 id 목록. 블록을 찾지 못하면 빈 목록
     */
    private static List<String> frontendColumnsOf(String source, SheetKind kind) {
        Matcher block = SHEET_BLOCK.matcher(source);
        while (block.find()) {
            if (!block.group(1).equals(kind.name())) {
                continue;
            }
            int close = source.indexOf(']', block.end());
            if (close < 0) {
                return List.of();
            }
            List<String> columns = new ArrayList<>();
            Matcher literal = SINGLE_QUOTED.matcher(source.substring(block.end(), close));
            while (literal.find()) {
                columns.add(literal.group(1));
            }
            return columns;
        }
        return List.of();
    }
}
