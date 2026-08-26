package com.kdb.it.domain.migration.request.service;

/** 편성요청서 원본의 DB 메타데이터를 물리 컬럼 길이에 맞춥니다. */
final class RequestFormArchiveMetadata {

    static final int FILE_NAME_LIMIT = 100;
    static final int RELATIVE_PATH_LIMIT = 255;

    private RequestFormArchiveMetadata() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    static String fitFileName(String fileName) {
        if (fileName == null || fileName.length() <= FILE_NAME_LIMIT) return fileName;
        int dot = fileName.lastIndexOf('.');
        String extension = dot > 0 ? fileName.substring(dot) : "";
        if (extension.length() >= FILE_NAME_LIMIT) return fileName.substring(0, FILE_NAME_LIMIT);
        return fileName.substring(0, FILE_NAME_LIMIT - extension.length()) + extension;
    }

    static String fitRelativePath(String relativePath) {
        if (relativePath == null || relativePath.length() <= RELATIVE_PATH_LIMIT) return relativePath;
        String normalized = relativePath.replace('\\', '/');
        String[] segments = normalized.split("/");
        String suffix = segments[segments.length - 1];
        for (int index = segments.length - 2; index >= 0; index--) {
            String candidate = segments[index] + "/" + suffix;
            if (candidate.length() + 2 > RELATIVE_PATH_LIMIT) break;
            suffix = candidate;
        }
        return "…/" + suffix;
    }
}
