package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFormArchiveMetadataTest {

    @Test
    @DisplayName("긴 한글 파일명은 확장자를 보존해 100바이트 이하로 줄인다")
    void fitsFileNamePreservingExtension() {
        String fitted = RequestFormArchiveMetadata.fitFileName("가".repeat(40) + ".xlsx");

        assertThat(fitted).isEqualTo("가".repeat(31) + ".xlsx");
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(98);
    }

    @Test
    @DisplayName("긴 한글 상대경로는 파일명과 가까운 폴더를 보존해 255바이트 이하로 줄인다")
    void fitsRelativePathPreservingLeaf() {
        String path = "상위/" + "긴폴더/".repeat(30) + "요청서.xlsx";

        String fitted = RequestFormArchiveMetadata.fitRelativePath(path);

        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(255);
        assertThat(fitted).startsWith("…/").endsWith("요청서.xlsx");
    }

    @Test
    @DisplayName("4바이트 문자를 절단할 때 대체문자를 만들지 않는다")
    void doesNotSplitFourByteCharacter() {
        String fitted = RequestFormArchiveMetadata.fitFileName("😀".repeat(30) + ".xlsx");

        assertThat(fitted).isEqualTo("😀".repeat(23) + ".xlsx");
        assertThat(fitted).doesNotContain("�");
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(97);
    }

    @Test
    @DisplayName("확장자가 없는 긴 파일명도 100바이트 이하의 이름을 보존한다")
    void fitsFileNameWithoutExtension() {
        String fitted = RequestFormArchiveMetadata.fitFileName("가".repeat(40));

        assertThat(fitted).isEqualTo("가".repeat(33));
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(99);
    }

    @Test
    @DisplayName("null 파일명과 null 상대경로는 그대로 반환한다")
    void returnsNullInputsAsIs() {
        assertThat(RequestFormArchiveMetadata.fitFileName(null)).isNull();
        assertThat(RequestFormArchiveMetadata.fitRelativePath(null)).isNull();
    }

    @Test
    @DisplayName("제한 이하의 파일명과 상대경로는 절단 없이 그대로 반환한다")
    void keepsValuesWithinLimits() {
        String fileName = "요청서.xlsx";
        String relativePath = "2026/IT기획부(180)/요청서.xlsx";

        assertThat(RequestFormArchiveMetadata.fitFileName(fileName)).isSameAs(fileName);
        assertThat(RequestFormArchiveMetadata.fitRelativePath(relativePath)).isSameAs(relativePath);
    }

    @Test
    @DisplayName("확장자만으로 100바이트를 넘으면 파일명 전체를 절단한다")
    void truncatesWholeNameWhenExtensionExceedsLimit() {
        // given: 확장자가 121바이트라 확장자 보존이 불가능한 파일명
        String fitted = RequestFormArchiveMetadata.fitFileName("이름." + "확".repeat(40));

        assertThat(fitted).isEqualTo("이름." + "확".repeat(31));
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(100);
    }

    @Test
    @DisplayName("폴더 없는 긴 단일 파일명 경로는 접두어와 절단된 이름만 남긴다")
    void fitsSingleSegmentPathWithoutFolders() {
        // given: 구분자가 없어 상위 폴더 복원 루프가 실행되지 않는 270바이트 경로
        String fitted = RequestFormArchiveMetadata.fitRelativePath("가".repeat(90));

        assertThat(fitted).isEqualTo("…/" + "가".repeat(83));
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(253);
    }

    @Test
    @DisplayName("상위 폴더 전체가 정확히 들어맞으면 255바이트를 꽉 채워 보존한다")
    void fitsRelativePathAtExactLimit() {
        // given: 절단된 파일명 앞에 1바이트 폴더를 붙이면 정확히 255바이트가 되는 경로
        String fitted = RequestFormArchiveMetadata.fitRelativePath("a/" + "가".repeat(85));

        assertThat(fitted).isEqualTo("…/a/" + "가".repeat(83));
        assertThat(fitted.getBytes(StandardCharsets.UTF_8)).hasSize(255);
    }

    @Test
    @DisplayName("생성자는 메타데이터 유틸리티 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = RequestFormArchiveMetadata.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
