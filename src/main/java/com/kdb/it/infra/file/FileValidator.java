package com.kdb.it.infra.file;

import com.kdb.it.exception.CustomGeneralException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 파일 확장자 화이트리스트 검증 컴포넌트 — SEC-04
 *
 * <p>업로드 허용 확장자만 통과시키고, 실행 가능하거나 위험한 확장자는 {@link CustomGeneralException}으로 차단합니다. 대소문자를 무시합니다.
 *
 * <p>허용 확장자: pdf, hwp, hwpx, doc, docx, xls, xlsx, ppt, pptx, jpg, jpeg, png, gif, lzstr
 */
@Component
public class FileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    "pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg",
                    "jpeg", "png", "gif", "lzstr");

    /**
     * 파일명의 확장자가 허용 목록에 포함되는지 검증합니다.
     *
     * @param filename 검증할 원본 파일명 (null 불허)
     * @throws IllegalArgumentException filename이 null인 경우
     * @throws CustomGeneralException 확장자가 없거나 허용되지 않은 경우
     */
    public void validateExtension(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("파일명은 null일 수 없습니다.");
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new CustomGeneralException("허용되지 않은 파일 형식입니다. 확장자가 없는 파일은 업로드할 수 없습니다.");
        }

        String ext = filename.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomGeneralException("허용되지 않은 파일 형식입니다: " + ext);
        }
    }
}
