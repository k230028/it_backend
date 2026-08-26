package com.kdb.it.domain.budget.document.budgetnote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 예산작성 카드 참고사항 API 계약입니다. */
public final class BudgetCardNoteDto {

    private BudgetCardNoteDto() {}

    /** 카드별 참고사항 응답입니다. */
    public record Response(String cardType, String content) {}

    /** 관리자 참고사항 저장 요청입니다. */
    public record SaveRequest(
            @NotBlank(message = "참고사항을 입력해야 합니다")
                    @Size(max = 2000, message = "참고사항은 2000자 이하여야 합니다")
                    String content) {}
}
