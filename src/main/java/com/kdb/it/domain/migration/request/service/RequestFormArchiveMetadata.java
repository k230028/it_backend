package com.kdb.it.domain.migration.request.service;

/** 편성요청서 원본의 DB 메타데이터를 물리 컬럼 길이에 맞춥니다. */
final class RequestFormArchiveMetadata {

    static final int FILE_NAME_LIMIT = 100;
    static final int RELATIVE_PATH_LIMIT = 255;

    private RequestFormArchiveMetadata() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    static String fitFileName(String fileName) {
        if (fileName == null || Utf8ByteLimit.length(fileName) <= FILE_NAME_LIMIT) return fileName;
        int dot = fileName.lastIndexOf('.');
        String extension = dot > 0 ? fileName.substring(dot) : "";
        String basename = dot > 0 ? fileName.substring(0, dot) : fileName;
        int extensionBytes = Utf8ByteLimit.length(extension);
        if (extensionBytes >= FILE_NAME_LIMIT)
            return Utf8ByteLimit.truncate(fileName, FILE_NAME_LIMIT);
        return Utf8ByteLimit.truncate(basename, FILE_NAME_LIMIT - extensionBytes) + extension;
    }

    static String fitRelativePath(String relativePath) {
        if (relativePath == null || Utf8ByteLimit.length(relativePath) <= RELATIVE_PATH_LIMIT)
            return relativePath;
        String normalized = relativePath.replace('\\', '/');
        String[] segments = normalized.split("/");
        String prefix = "…/";
        String suffix =
                Utf8ByteLimit.truncate(
                        segments[segments.length - 1],
                        RELATIVE_PATH_LIMIT - Utf8ByteLimit.length(prefix));
        for (int index = segments.length - 2; index >= 0; index--) {
            String candidate = segments[index] + "/" + suffix;
            if (Utf8ByteLimit.length(prefix + candidate) > RELATIVE_PATH_LIMIT) break;
            suffix = candidate;
        }
        return prefix + suffix;
    }
}
