package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestForm2026SampleSmokeTest {

    private static final String SAMPLE_DIR_ENV = "REQUEST_FORM_SAMPLE_2026_DIR";

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    @Test
    @DisplayName("2026 샘플 Excel을 모두 열고 요청서와 증빙을 기존 개수로 분류한다")
    void opensAndClassifiesSample2026() throws IOException {
        Path sampleRoot = sampleRoot();
        Assumptions.assumeTrue(Files.isDirectory(sampleRoot), "로컬 2026 샘플이 없어 건너뜁니다");

        List<Path> excelFiles;
        try (var paths = Files.walk(sampleRoot)) {
            excelFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(RequestForm2026SampleSmokeTest::isExcel)
                            .sorted()
                            .toList();
        }

        int requestForms = 0;
        for (Path path : excelFiles) {
            String fileKey = sampleRoot.relativize(path).toString().replace('\\', '/');
            try (Workbook workbook = reader.open(Files.readAllBytes(path), fileKey)) {
                if (!reader.classify(workbook).isEmpty()) requestForms++;
            }
        }

        assertThat(excelFiles).hasSize(44);
        assertThat(requestForms).isEqualTo(32);
        assertThat(excelFiles.size() - requestForms).isEqualTo(12);
    }

    /** worktree에서는 공유 샘플의 절대경로를 환경변수로 받고, 일반 실행은 기존 형제 경로를 씁니다. */
    private static Path sampleRoot() {
        String configured = System.getenv(SAMPLE_DIR_ENV);
        Path path =
                configured == null || configured.isBlank()
                        ? Path.of("..", "sample", "2026")
                        : Path.of(configured);
        return path.toAbsolutePath().normalize();
    }

    private static boolean isExcel(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xls") || name.endsWith(".xlsx");
    }
}
