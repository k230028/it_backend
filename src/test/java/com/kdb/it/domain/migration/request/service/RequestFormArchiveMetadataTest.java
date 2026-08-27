package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
