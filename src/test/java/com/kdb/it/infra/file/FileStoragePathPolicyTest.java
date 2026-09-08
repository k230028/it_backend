package com.kdb.it.infra.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.exception.CustomGeneralException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 업무 파일 종류가 한글 물리 폴더를 만들지 않도록 저장 경로 매핑을 검증합니다. */
class FileStoragePathPolicyTest {

    static Stream<Arguments> directoryMappings() {
        return Stream.of(
                Arguments.of("가이드문서", "guide-documents"),
                Arguments.of("공통게시판", "common-board"),
                Arguments.of("배너", "banners"),
                Arguments.of("사용자가이드", "user-guides"),
                Arguments.of("요구사항정의서", "requirement-documents"),
                Arguments.of("정보화사업", "it-projects"),
                Arguments.of("전산업무비", "it-costs"),
                Arguments.of("편성요청서반입", "request-form-imports"),
                Arguments.of("사업계획서", "business-plans"),
                Arguments.of("타당성검토표", "feasibility-reviews"),
                Arguments.of("협의회관련자료", "council-materials"),
                Arguments.of("검토의견", "review-comments"),
                Arguments.of("다이어그램", "diagrams"));
    }

    @ParameterizedTest
    @MethodSource("directoryMappings")
    @DisplayName("등록된 파일 종류를 영문 폴더명으로 변환한다")
    void directoryName_등록종류_영문폴더반환(String fileKind, String expectedDirectory) {
        assertThat(FileStoragePathPolicy.directoryName(fileKind)).isEqualTo(expectedDirectory);
        assertThat(expectedDirectory).matches("[a-z0-9-]+");
    }

    @Test
    @DisplayName("등록되지 않은 종류는 새 물리 폴더를 만들지 못한다")
    void directoryName_미등록종류_거부() {
        assertThatThrownBy(() -> FileStoragePathPolicy.directoryName("새한글종류"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("저장 디렉터리");
    }

    @Test
    @DisplayName("기존 한글 DB 경로를 이동 후 영문 경로로 변환한다")
    void resolveStoredDirectory_기존한글경로_영문경로반환() {
        Path resolved =
                FileStoragePathPolicy.resolveStoredDirectory(
                        Path.of("data", "springitp", "정보화사업", "2026", "09").toString());

        assertThat(resolved).isEqualTo(Path.of("data", "springitp", "it-projects", "2026", "09"));
    }
}
