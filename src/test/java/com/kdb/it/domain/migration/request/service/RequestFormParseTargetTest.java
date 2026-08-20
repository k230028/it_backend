package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RequestFormParseTargetTest {

    @ParameterizedTest(name = "{0}은 요청서 파싱 대상이 {1}이다")
    @MethodSource("filenames")
    void classifiesRequestFormWorkbookBasenames(String originalFilename, boolean expected) {
        assertThat(RequestFormParseTarget.isTarget(originalFilename)).isEqualTo(expected);
    }

    private static Stream<Arguments> filenames() {
        return Stream.of(
                Arguments.of("2026 요청서.xls", true),
                Arguments.of("[자료1] 요청서.XLSX", true),
                Arguments.of("요청서폴더/견적서.xls", false),
                Arguments.of("요청서\\견적서.xlsx", false),
                Arguments.of("견적서.xlsx", false),
                Arguments.of("요청서.pdf", false),
                Arguments.of("요청서", false),
                Arguments.of("요청서.xls.pdf", false),
                Arguments.of(null, false));
    }
}
