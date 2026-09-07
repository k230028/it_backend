package com.kdb.it.infra.file;

import com.kdb.it.exception.CustomGeneralException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/** 한글 업무 파일 종류와 영문 물리 저장 폴더의 대응 규칙입니다. */
public final class FileStoragePathPolicy {

    private static final Map<String, String> DIRECTORY_BY_FILE_KIND =
            Map.ofEntries(
                    Map.entry("가이드문서", "guide-documents"),
                    Map.entry("공통게시판", "common-board"),
                    Map.entry("배너", "banners"),
                    Map.entry("사용자가이드", "user-guides"),
                    Map.entry("요구사항정의서", "requirement-documents"),
                    Map.entry("정보화사업", "it-projects"),
                    Map.entry("편성요청서반입", "request-form-imports"),
                    Map.entry("사업계획서", "business-plans"),
                    Map.entry("타당성검토표", "feasibility-reviews"),
                    Map.entry("협의회관련자료", "council-materials"),
                    Map.entry("검토의견", "review-comments"),
                    Map.entry("다이어그램", "diagrams"));

    private FileStoragePathPolicy() {}

    /** 파일 종류에 대응하는 영문 물리 폴더명을 반환합니다. */
    public static String directoryName(String fileKind) {
        String directoryName = DIRECTORY_BY_FILE_KIND.get(fileKind);
        if (directoryName == null) {
            throw new CustomGeneralException("파일 종류에 대응하는 저장 디렉터리가 없습니다.");
        }
        return directoryName;
    }

    /** 기존 DB 경로의 한글 폴더 세그먼트를 이동 후의 영문 폴더명으로 변환합니다. */
    public static Path resolveStoredDirectory(String storedDirectory) {
        Path storedPath = Paths.get(storedDirectory);
        Path resolved = storedPath.getRoot();
        for (Path segment : storedPath) {
            String name = segment.toString();
            String resolvedName = DIRECTORY_BY_FILE_KIND.getOrDefault(name, name);
            resolved = resolved == null ? Paths.get(resolvedName) : resolved.resolve(resolvedName);
        }
        return resolved == null ? storedPath : resolved;
    }
}
