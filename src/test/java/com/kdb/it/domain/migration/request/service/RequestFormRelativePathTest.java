package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFormRelativePathTest {

    @Test
    @DisplayName("manifest 경로의 역슬래시를 슬래시 상대경로로 정규화한다")
    void normalize_convertsBackslashesToRelativePath() {
        assertThat(RequestFormRelativePath.normalize("2026\\IT부(D01)\\01. 사업\\근거.pdf", "근거.pdf"))
                .isEqualTo("2026/IT부(D01)/01. 사업/근거.pdf");
    }

    @Test
    @DisplayName("빈 세그먼트와 현재 폴더 세그먼트를 제거한다")
    void normalize_removesEmptyAndCurrentDirectorySegments() {
        assertThat(RequestFormRelativePath.normalize("2026//./IT부(D01)/./근거.pdf", "근거.pdf"))
                .isEqualTo("2026/IT부(D01)/근거.pdf");
    }

    @Test
    @DisplayName("manifest 마지막 파일명 대신 업로드 실제 파일명을 사용한다")
    void normalize_replacesManifestFileNameWithOriginalFilename() {
        assertThat(RequestFormRelativePath.normalize("2026/IT부(D01)/위조된이름.pdf", "근거.pdf"))
                .isEqualTo("2026/IT부(D01)/근거.pdf");
    }

    @Test
    @DisplayName("상대경로 탈출과 절대경로 및 드라이브 경로를 거부한다")
    void normalize_rejectsTraversalAndAbsolutePaths() {
        assertThatThrownBy(() -> RequestFormRelativePath.normalize("../secret.pdf", "secret.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(() -> RequestFormRelativePath.normalize("/secret.pdf", "secret.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(() -> RequestFormRelativePath.normalize("C:\\secret.pdf", "secret.pdf"))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("중첩 manifest 세그먼트와 실제 파일명의 드라이브 접두어를 거부한다")
    void normalize_rejectsNestedDrivePrefixes() {
        assertThatThrownBy(
                        () ->
                                RequestFormRelativePath.normalize(
                                        "safe/C:/manifest.pdf", "actual.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                RequestFormRelativePath.normalize(
                                        "safe/manifest.pdf", "C:actual.pdf"))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("제어문자와 잘못된 실제 파일명을 거부한다")
    void normalize_rejectsControlCharactersAndUnsafeOriginalFilenames() {
        assertThatThrownBy(
                        () ->
                                RequestFormRelativePath.normalize(
                                        "2026/\u0000secret.pdf", "secret.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(() -> RequestFormRelativePath.normalize("2026/secret.pdf", ""))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () -> RequestFormRelativePath.normalize("2026/secret.pdf", "../secret.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                RequestFormRelativePath.normalize(
                                        "2026/secret.pdf", "secret/other.pdf"))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                RequestFormRelativePath.normalize(
                                        "2026/secret.pdf", "secret\u0000.pdf"))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("정규화된 경로의 최대 길이 255자를 강제한다")
    void normalize_enforcesMaximumLength() {
        String acceptedKey = "a".repeat(250) + "/x";
        String rejectedKey = "a".repeat(251) + "/x";

        assertThat(RequestFormRelativePath.normalize(acceptedKey, "name")).hasSize(255);
        assertThatThrownBy(() -> RequestFormRelativePath.normalize(rejectedKey, "name"))
                .isInstanceOf(CustomGeneralException.class);
    }
}
