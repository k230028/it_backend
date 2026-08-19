package com.kdb.it.domain.migration.request.service.adapter;

/** 편성요청서에서 읽은 업무 문자열을 필드 용도에 맞게 정규화합니다. */
final class FormText {

    private FormText() {}

    /** 이름 필드의 연속 개행을 공백 하나로 바꾸고 앞뒤 공백을 제거합니다. */
    static String singleLineName(String value) {
        return value == null ? null : value.replaceAll("\\R+", " ").trim();
    }
}
