package com.kdb.it.domain.migration.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/** 부점 편성요청서의 시트 종류입니다. 시트명은 영문 양식에서도 국문이라 국문 키워드로 판별합니다. */
@Schema(name = "RequestFormSheetKind", description = "편성요청서 시트 종류")
public enum FormSheetKind {
    /** `① (정보화사업) 1-1. 정보화사업 개요` → BPROJM */
    CAPITAL_OVERVIEW("1-1"),
    /** `① (정보화사업) 1-2. 소요자원 상세내용` → BITEMM */
    CAPITAL_RESOURCE("1-2"),
    /** `② (경상사업) 2. 경상적인 사업` → BPROJM(경상) + BITEMM */
    RECURRING("경상"),
    /** `③ (일반관리비) 전산 일반관리비 편성요청서` → BCOSTM */
    GENERAL_EXPENSE("일반관리비");

    private final String keyword;

    FormSheetKind(String keyword) {
        this.keyword = keyword;
    }

    /**
     * 시트명으로 종류를 판별합니다.
     *
     * <p>부점이 시트명 앞뒤에 부점명이나 공백을 덧붙이는 경우가 있어 정확 일치가 아니라 키워드 포함으로 봅니다. `1-1`과 `1-2`는 접두 번호가 겹치지 않으므로
     * 선언 순서대로 검사해도 어긋나지 않습니다.
     *
     * @param sheetName 워크북이 준 시트명. null·공백이면 빈 Optional
     * @return 판별된 종류. 어느 키워드에도 걸리지 않으면 빈 Optional
     */
    public static Optional<FormSheetKind> ofSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) return Optional.empty();
        String normalized = sheetName.replace(" ", "");
        for (FormSheetKind kind : values()) {
            if (normalized.contains(kind.keyword.replace(" ", ""))) return Optional.of(kind);
        }
        return Optional.empty();
    }
}
