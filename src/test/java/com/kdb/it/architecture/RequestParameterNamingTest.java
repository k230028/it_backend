package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 요청 파라미터 바인딩 어노테이션의 이름 명시 게이트 — BE-39.
 *
 * <p>{@code @RequestParam}·{@code @PathVariable}·{@code @RequestHeader}에 이름을 적지 않으면 Spring이 컴파일된
 * 파라미터명(javac {@code -parameters})으로 바인딩합니다. 그 이름은 컴파일 플래그에 의존하므로 플래그가 빠진 빌드에서는 {@code arg0}으로
 * 떨어지고, 같은 소스에서 생성한 OpenAPI 스펙과 프론트 생성 타입({@code it_frontend/app/types/api.d.ts})이 빌드마다 달라집니다. 이름을
 * 명시하면 고정됩니다.
 *
 * <p>정책 SoT는 백엔드 {@code CLAUDE.md} §4입니다. 이 테스트는 그 규칙을 소스 트리 전수 검사로 고정합니다.
 */
class RequestParameterNamingTest {

    /** 대상 어노테이션과 뒤따르는 괄호 인자(있으면)를 함께 잡는다. */
    private static final Pattern ANNOTATION =
            Pattern.compile("@(RequestParam|PathVariable|RequestHeader)\\s*(\\(([^)]*)\\))?");

    @Test
    @DisplayName("모든 @RequestParam·@PathVariable·@RequestHeader가 이름을 명시한다")
    void 요청_파라미터_이름을_명시한다() throws IOException {
        List<String> violations = findViolations(sourceRoot());

        assertThat(violations)
                .withFailMessage(
                        """
                        요청 파라미터 이름 미지정 %d건:

                        %s

                        정책: 백엔드 CLAUDE.md §4 — 모든 @RequestParam·@PathVariable·@RequestHeader에 name 또는 value를 명시합니다.
                        이유: 이름을 생략하면 바인딩 이름이 컴파일 플래그(-parameters)에 의존해 OpenAPI 스펙과 프론트 생성 타입이 빌드마다 흔들립니다.
                        """
                                .formatted(
                                        violations.size(),
                                        String.join(System.lineSeparator(), violations)))
                .isEmpty();
    }

    /**
     * 소스 트리를 훑어 이름을 명시하지 않은 어노테이션 위치를 모은다.
     *
     * @param root {@code src/main/java} 경로
     * @return "경로:줄번호 — 어노테이션" 형식의 위반 목록. 없으면 빈 리스트
     */
    private static List<String> findViolations(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = ANNOTATION.matcher(source);
                while (matcher.find()) {
                    if (hasExplicitName(matcher.group(3))) {
                        continue;
                    }
                    violations.add(
                            "%s:%d — @%s"
                                    .formatted(
                                            root.relativize(path).toString().replace('\\', '/'),
                                            lineOf(source, matcher.start()),
                                            matcher.group(1)));
                }
            }
        }
        return violations;
    }

    /**
     * 괄호 인자가 이름을 담고 있는지 판정한다.
     *
     * <p>{@code ("eno")} 축약형, {@code (name = "eno")}, {@code (value = "eno")}를 모두 허용합니다. {@code
     * (required = false)}처럼 이름 없이 다른 속성만 있는 경우는 위반입니다.
     *
     * @param arguments 괄호 안 문자열. 괄호가 없으면 null
     */
    private static boolean hasExplicitName(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return false;
        }
        String trimmed = arguments.trim();
        return trimmed.startsWith("\"")
                || trimmed.contains("name =")
                || trimmed.contains("value =");
    }

    /** 문자 오프셋이 속한 줄 번호(1-based)를 센다. */
    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** {@code src/main/java} 위치를 찾는다. Gradle은 프로젝트 디렉터리, IDE는 워크스페이스 루트에서 실행될 수 있다. */
    private static Path sourceRoot() {
        Path direct = Path.of("src", "main", "java");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = Path.of("it_backend", "src", "main", "java");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        throw new IllegalStateException(
                "src/main/java를 찾을 수 없습니다. 작업 디렉터리: " + Path.of("").toAbsolutePath());
    }
}
