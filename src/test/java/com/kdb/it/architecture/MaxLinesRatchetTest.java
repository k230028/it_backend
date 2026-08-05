package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 백엔드 운영 소스 파일 크기 동결선(ratchet) 게이트 — CQ-01 / Wave D-1.
 *
 * <p>기준선은 {@code src/test/resources/architecture/max-lines-baselines.properties}가 단일 진실 공급원입니다. 기준값
 * 변경이 diff에 한 줄로 드러나도록 테스트 코드와 분리했습니다.
 *
 * <p>줄 수 측정 정의는 {@code Files.readAllLines(path, UTF_8).size()}입니다. PowerShell {@code Get-Content |
 * Measure-Object -Line}은 빈 줄을 세지 않으므로({@code Measure-Object -Line}이 빈 문자열의 줄 수를 0으로 계산한다) 기준값 스팟체크에
 * 쓰지 마십시오 — 파일의 빈 줄 수만큼 작게 나옵니다. 인코딩·BOM과는 무관합니다. 대신 {@code
 * [System.IO.File]::ReadAllLines(path).Length} 또는 {@code (Get-Content path).Count}를 쓰거나 이 테스트 자체를
 * 신뢰하십시오. 다른 방식으로 세면 기준값이 흔들리므로 이 정의를 바꾸지 마십시오.
 */
class MaxLinesRatchetTest {

    /** 기준선에 없는 파일에 허용되는 최대 줄 수 */
    private static final int LIMIT = 800;

    private static final String BASELINE_RESOURCE = "/architecture/max-lines-baselines.properties";

    // =====================================================================
    // 판정 로직 (순수 함수 — 아래 단위 테스트가 이 로직 자체를 검증한다)
    // =====================================================================

    /**
     * 기준선과 실측값을 비교해 위반 메시지 목록을 만든다.
     *
     * @param baseline 기준선 (경로 → 기준 줄 수). 경로는 {@code src/main/java} 기준 상대 경로
     * @param actual 실측값 (경로 → 실제 줄 수). 기준선 경로가 없으면 "실재하지 않음" 위반이 된다
     * @param limit 기준선에 없는 파일에 허용되는 최대 줄 수
     * @return 위반 메시지 목록. 위반이 없으면 빈 리스트
     */
    static List<String> findViolations(
            Map<String, Integer> baseline, Map<String, Integer> actual, int limit) {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : new TreeMap<>(baseline).entrySet()) {
            String path = entry.getKey();
            int expected = entry.getValue();
            Integer measured = actual.get(path);

            if (measured == null) {
                violations.add(
                        "[경로 부재] %s — 기준선에 있으나 파일이 없습니다. 파일을 옮기거나 지웠다면 기준선 항목도 함께 갱신하십시오."
                                .formatted(path));
            } else if (measured > expected) {
                violations.add(
                        "[증가] %s — 기준 %d줄, 실측 %d줄 (+%d). 같은 PR에서 동등 이상 분량을 추출해 상쇄하거나, 분해 후 기준값을 낮추십시오."
                                .formatted(path, expected, measured, measured - expected));
            } else if (measured < expected) {
                violations.add(
                        "[감소] %s — 기준 %d줄, 실측 %d줄 (%d). 분해했다면 기준값을 실측값으로 낮추십시오. 800줄 이하가 되면 항목을 지우십시오."
                                .formatted(path, expected, measured, measured - expected));
            }
        }

        for (Map.Entry<String, Integer> entry : new TreeMap<>(actual).entrySet()) {
            String path = entry.getKey();
            if (!baseline.containsKey(path) && entry.getValue() > limit) {
                violations.add(
                        "[신규 초과] %s — %d줄. %d줄을 넘는 신규 파일은 허용하지 않습니다. 관심사를 분리하십시오."
                                .formatted(path, entry.getValue(), limit));
            }
        }

        return violations;
    }

    // =====================================================================
    // 게이트 자체 검증
    // =====================================================================

    @Test
    @DisplayName("기준값보다 늘어나면 증가 위반을 낸다")
    void 증가를_검출한다() {
        List<String> violations =
                findViolations(Map.of("a/B.java", 100), Map.of("a/B.java", 101), LIMIT);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("[증가] a/B.java").contains("+1");
    }

    @Test
    @DisplayName("기준값보다 줄어들면 감소 위반을 낸다 — 분해와 기준선 갱신을 같은 커밋으로 묶기 위함")
    void 감소를_검출한다() {
        List<String> violations =
                findViolations(Map.of("a/B.java", 100), Map.of("a/B.java", 40), LIMIT);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("[감소] a/B.java");
    }

    @Test
    @DisplayName("기준선 경로가 실재하지 않으면 위반을 낸다")
    void 경로_부재를_검출한다() {
        List<String> violations = findViolations(Map.of("a/B.java", 100), Map.of(), LIMIT);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("[경로 부재] a/B.java");
    }

    @Test
    @DisplayName("기준선 밖 파일이 한계를 넘으면 위반을 낸다")
    void 신규_초과를_검출한다() {
        List<String> violations = findViolations(Map.of(), Map.of("a/New.java", 801), LIMIT);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("[신규 초과] a/New.java");
    }

    @Test
    @DisplayName("기준값과 실측이 같고 신규 초과가 없으면 위반이 없다")
    void 위반이_없으면_빈_목록이다() {
        List<String> violations =
                findViolations(
                        Map.of("a/B.java", 100),
                        Map.of("a/B.java", 100, "a/Small.java", 10),
                        LIMIT);

        assertThat(violations).isEmpty();
    }

    // =====================================================================
    // 실제 소스 트리 검증
    // =====================================================================

    @Test
    @DisplayName("운영 소스가 기준선과 일치하고 기준선 밖 파일이 800줄을 넘지 않는다")
    void 운영_소스가_동결선을_지킨다() throws IOException {
        Map<String, Integer> baseline = loadBaseline();
        Map<String, Integer> actual = measureSourceTree(sourceRoot());

        List<String> violations = findViolations(baseline, actual, LIMIT);

        assertThat(violations)
                .withFailMessage(
                        """
                        파일 크기 동결선 위반 %d건:

                        %s

                        기준선 파일: src/test/resources%s
                        배경: TASK.md CQ-01 / docs/superpowers/plans/2026-08-05-cq01-council-controller-decomposition.md
                        """
                                .formatted(
                                        violations.size(),
                                        String.join(System.lineSeparator(), violations),
                                        BASELINE_RESOURCE))
                .isEmpty();
    }

    // =====================================================================
    // 입출력 헬퍼
    // =====================================================================

    /** 기준선 리소스를 읽어 경로 → 줄 수 맵으로 만든다. 리소스가 없으면 IllegalStateException. */
    private static Map<String, Integer> loadBaseline() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MaxLinesRatchetTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("기준선 리소스를 찾을 수 없습니다: " + BASELINE_RESOURCE);
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Map<String, Integer> baseline = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            baseline.put(name, Integer.parseInt(properties.getProperty(name).trim()));
        }
        return baseline;
    }

    /** src/main/java 하위 모든 .java 파일의 줄 수를 잰다. 키는 루트 기준 상대 경로('/' 구분자). */
    private static Map<String, Integer> measureSourceTree(Path root) throws IOException {
        Map<String, Integer> measured = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String key = root.relativize(path).toString().replace('\\', '/');
                measured.put(key, Files.readAllLines(path, StandardCharsets.UTF_8).size());
            }
        }
        return measured;
    }

    /**
     * src/main/java 위치를 찾는다.
     *
     * <p>Gradle {@code test} 태스크의 작업 디렉터리는 프로젝트 디렉터리(it_backend)이므로 상대 경로가 바로 맞습니다. IDE가 워크스페이스
     * 루트에서 실행하는 경우를 대비해 {@code it_backend/} 접두 경로도 확인합니다.
     */
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
