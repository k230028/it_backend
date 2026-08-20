package com.kdb.it.domain.migration.request.service;

import java.util.Locale;

/** 업로드 원본 파일명이 편성요청서 파싱 대상인지 판별합니다. */
public final class RequestFormParseTarget {

    private RequestFormParseTarget() {}

    /**
     * 최종 파일명에 요청서가 포함된 Excel 파일만 반입 대상으로 판별합니다.
     *
     * @param originalFilename 업로드된 원본 파일명. null이면 대상이 아닙니다
     * @return `.xls` 또는 `.xlsx` 확장자를 가지며 확장자를 제외한 최종 파일명에 요청서가 있으면 true
     */
    public static boolean isTarget(String originalFilename) {
        if (originalFilename == null) return false;

        int separatorIndex =
                Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        String basename = originalFilename.substring(separatorIndex + 1);
        String lowercaseBasename = basename.toLowerCase(Locale.ROOT);
        String extension =
                lowercaseBasename.endsWith(".xlsx")
                        ? ".xlsx"
                        : lowercaseBasename.endsWith(".xls") ? ".xls" : null;
        return extension != null
                && basename.substring(0, basename.length() - extension.length()).contains("요청서");
    }
}
