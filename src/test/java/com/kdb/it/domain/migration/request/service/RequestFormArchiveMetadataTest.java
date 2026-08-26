package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFormArchiveMetadataTest {

    @Test
    @DisplayName("긴 파일명은 확장자를 보존해 100자로 줄인다")
    void fitsFileNamePreservingExtension() {
        String fitted = RequestFormArchiveMetadata.fitFileName("가".repeat(120) + ".xlsx");

        assertThat(fitted).hasSize(100).endsWith(".xlsx");
    }

    @Test
    @DisplayName("긴 상대경로는 파일명과 가까운 폴더를 보존해 255자로 줄인다")
    void fitsRelativePathPreservingLeaf() {
        String path = "상위/" + "긴폴더/".repeat(100) + "요청서.xlsx";

        String fitted = RequestFormArchiveMetadata.fitRelativePath(path);

        assertThat(fitted).hasSizeLessThanOrEqualTo(255).startsWith("…/").endsWith("요청서.xlsx");
    }
}
