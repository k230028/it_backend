package com.kdb.it.domain.budget.document.entity;

/** BGDOC 문서상세항목코드의 시스템 고정값입니다. 표시명은 공통코드 {@code DOC_DTL_ITM_C}에서 관리합니다. */
public enum BgdocDocumentType {
    BUSINESS_GUIDE("01"),
    FORM_GUIDE("02"),
    USER_GUIDE("03"),
    CONTACT_INFO("04"),
    NOTICE_POPUP("05");

    public static final String CODE_GROUP = "DOC_DTL_ITM_C";

    private final String code;

    BgdocDocumentType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
