package com.kdb.it.common.popup;

import java.util.Arrays;

/** 사용자 화면에 노출할 수 있는 안내 팝업의 허용 목록입니다. */
public enum CommonPopupType {
    POPUP("popup", "common.popup", "PDOC-", "공통 안내 팝업"),
    INFO("info", "common.info", "IPOP-", "정보화사업 작성 안내"),
    ORDN("ordn", "common.ordn", "OPOP-", "경상사업 작성 안내"),
    COST("cost", "common.cost", "CPOP-", "전산업무비 작성 안내");

    private final String key;
    private final String documentIdentifier;
    private final String documentNumberPrefix;
    private final String documentName;

    CommonPopupType(
            String key,
            String documentIdentifier,
            String documentNumberPrefix,
            String documentName) {
        this.key = key;
        this.documentIdentifier = documentIdentifier;
        this.documentNumberPrefix = documentNumberPrefix;
        this.documentName = documentName;
    }

    public String key() {
        return key;
    }

    public String documentIdentifier() {
        return documentIdentifier;
    }

    public String documentNumberPrefix() {
        return documentNumberPrefix;
    }

    public String documentName() {
        return documentName;
    }

    /** URL 키를 고정된 팝업 유형으로 변환합니다. */
    public static CommonPopupType fromKey(String key) {
        return Arrays.stream(values())
                .filter(type -> type.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 안내 팝업 유형입니다."));
    }
}
