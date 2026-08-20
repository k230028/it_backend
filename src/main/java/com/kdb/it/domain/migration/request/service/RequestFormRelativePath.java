package com.kdb.it.domain.migration.request.service;

import com.kdb.it.exception.CustomGeneralException;
import java.util.ArrayList;
import java.util.List;

/** 편성요청서 manifest 파일 키를 공통첨부파일 원본 상대경로로 정규화합니다. */
public final class RequestFormRelativePath {

    private static final int MAX_LENGTH = 255;

    private RequestFormRelativePath() {}

    /**
     * manifest의 파일 키에서 안전한 원본 상대경로를 만듭니다.
     *
     * <p>마지막 파일명은 manifest 값을 신뢰하지 않고 실제 업로드 파일명으로 교체합니다.
     *
     * @param fileKey manifest에 담긴 전체 파일 키
     * @param fileName 실제 업로드 파일의 원본 파일명
     * @return `/` 구분자의 최대 255자 상대경로
     * @throws CustomGeneralException 경로 또는 실제 파일명이 안전하지 않은 경우
     */
    public static String normalize(String fileKey, String fileName) {
        validateFileName(fileName);
        if (fileKey == null || containsControlCharacter(fileKey)) {
            throw invalidPath();
        }

        String slashPath = fileKey.replace('\\', '/');
        if (slashPath.startsWith("/") || hasWindowsDrivePrefix(slashPath)) {
            throw invalidPath();
        }

        List<String> segments = new ArrayList<>();
        for (String segment : slashPath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw invalidPath();
            }
            if (hasWindowsDrivePrefix(segment)) {
                throw invalidPath();
            }
            segments.add(segment);
        }
        if (!segments.isEmpty()) {
            segments.removeLast();
        }
        segments.add(fileName);
        String relativePath = String.join("/", segments);
        if (relativePath.length() > MAX_LENGTH) {
            throw invalidPath();
        }
        return relativePath;
    }

    private static void validateFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0
                || fileName.contains("..")
                || hasWindowsDrivePrefix(fileName)
                || containsControlCharacter(fileName)) {
            throw invalidPath();
        }
    }

    private static boolean hasWindowsDrivePrefix(String path) {
        return path.length() >= 2
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':';
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static CustomGeneralException invalidPath() {
        return new CustomGeneralException("안전하지 않은 편성요청서 원본 상대경로입니다.");
    }
}
