package com.kdb.it.infra.file;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FileValidator 단위 테스트 — SEC-04
 *
 * <p>파일 확장자 화이트리스트 검증 로직을 검증합니다.
 */
class FileValidatorTest {

    private final FileValidator fileValidator = new FileValidator();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "문서.pdf",
                "보고서.hwp",
                "양식.hwpx",
                "문서.doc",
                "문서.docx",
                "표.xls",
                "표.xlsx",
                "발표.ppt",
                "발표.pptx",
                "사진.jpg",
                "사진.jpeg",
                "이미지.png",
                "gif.gif",
                "excalidraw-scene.lzstr"
            })
    @DisplayName("허용 확장자 파일 업로드 시 예외 없음")
    void validateExtension_allowedExtension_noException(String filename) {
        assertThatCode(() -> fileValidator.validateExtension(filename)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"악성.jsp", "스크립트.sh", "배치.bat", "실행.exe", "웹페이지.html", "자바.class", "압축.zip"})
    @DisplayName("비허용 확장자 파일 업로드 시 CustomGeneralException 발생")
    void validateExtension_deniedExtension_throwsException(String filename) {
        assertThatThrownBy(() -> fileValidator.validateExtension(filename))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("허용되지 않은 파일 형식");
    }

    @Test
    @DisplayName("확장자 없는 파일명 시 CustomGeneralException 발생")
    void validateExtension_noExtension_throwsException() {
        assertThatThrownBy(() -> fileValidator.validateExtension("파일명"))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("null 파일명 시 IllegalArgumentException 발생")
    void validateExtension_nullFilename_throwsIllegalArgument() {
        assertThatThrownBy(() -> fileValidator.validateExtension(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대문자 확장자도 허용 (대소문자 무관)")
    void validateExtension_upperCaseExtension_noException() {
        assertThatCode(() -> fileValidator.validateExtension("문서.PDF")).doesNotThrowAnyException();
    }
}
