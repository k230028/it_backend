package com.kdb.it.domain.budget.document.budgetnote;

import java.util.Locale;

/** 예산작성 화면의 세 카드와 BGDOCM 문서를 연결하는 고정 카탈로그입니다. */
enum BudgetCardNoteType {
    IT_PROJECT("BNOTE-IT-PROJECT", "BUDGET_CARD_NOTE_IT_PROJECT"),
    COST("BNOTE-COST", "BUDGET_CARD_NOTE_COST"),
    ORDINARY("BNOTE-ORDINARY", "BUDGET_CARD_NOTE_ORDINARY");

    private final String docMngNo;
    private final String documentTitle;

    BudgetCardNoteType(String docMngNo, String documentTitle) {
        this.docMngNo = docMngNo;
        this.documentTitle = documentTitle;
    }

    String docMngNo() {
        return docMngNo;
    }

    String documentTitle() {
        return documentTitle;
    }

    static BudgetCardNoteType require(String cardType) {
        if (cardType == null || cardType.isBlank()) {
            throw new IllegalArgumentException("예산 카드 유형을 입력해야 합니다");
        }
        try {
            return valueOf(cardType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 예산 카드 유형입니다");
        }
    }
}
