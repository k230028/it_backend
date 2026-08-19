package com.kdb.it.domain.migration.request.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 편성요청서 상대경로에서 원본 보관 그룹 키를 계산합니다. */
final class RequestFormArchiveGroup {

    private static final Pattern DEPARTMENT_FOLDER =
            Pattern.compile(".*[(（]\\s*[0-9A-Za-z]{1,100}\\s*[)）]$");
    private static final Pattern NUMBERED_FOLDER = Pattern.compile("^\\s*\\d{1,3}\\..*");

    private RequestFormArchiveGroup() {
        throw new UnsupportedOperationException("경로 유틸리티 — 인스턴스화 금지");
    }

    /**
     * 파일 상대경로를 사업 보관 그룹으로 접습니다.
     *
     * @param fileKey 브라우저가 보낸 상대경로
     * @return `/` 구분 그룹 키. 폴더가 없으면 빈 문자열
     */
    static String keyOf(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return "";

        List<String> segments = new ArrayList<>();
        for (String segment : fileKey.replace('\\', '/').split("/")) {
            if (!segment.isBlank()) segments.add(segment);
        }
        if (segments.size() < 2) return "";

        List<String> folders = segments.subList(0, segments.size() - 1);
        int departmentIndex = 0;
        for (int i = folders.size() - 1; i >= 0; i--) {
            if (DEPARTMENT_FOLDER.matcher(folders.get(i)).matches()) {
                departmentIndex = i;
                break;
            }
        }

        int groupEnd = departmentIndex;
        boolean numbered = false;
        for (int i = departmentIndex + 1; i < folders.size(); i++) {
            if (!NUMBERED_FOLDER.matcher(folders.get(i)).matches()) continue;
            groupEnd = i;
            numbered = true;
            break;
        }
        if (!numbered && departmentIndex + 1 < folders.size()) groupEnd = departmentIndex + 1;
        return String.join("/", folders.subList(0, groupEnd + 1));
    }
}
