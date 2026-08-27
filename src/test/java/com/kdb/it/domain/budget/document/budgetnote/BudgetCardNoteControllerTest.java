package com.kdb.it.domain.budget.document.budgetnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 예산작성 카드 참고사항 컨트롤러 계약을 검증합니다. */
class BudgetCardNoteControllerTest {

    private final BudgetCardNoteService service =
            org.mockito.Mockito.mock(BudgetCardNoteService.class);
    private final BudgetCardNoteController controller = new BudgetCardNoteController(service);

    @Test
    @DisplayName("조회 API는 서비스의 카드 참고사항을 응답한다")
    void getNotes_서비스응답반환() {
        List<BudgetCardNoteDto.Response> notes =
                List.of(new BudgetCardNoteDto.Response("COST", "업무비 안내"));
        given(service.getNotes()).willReturn(notes);

        assertThat(controller.getNotes().getBody()).isEqualTo(notes);
    }

    @Test
    @DisplayName("저장 API는 카드 유형과 본문을 서비스에 전달하고 관리자 권한을 요구한다")
    void save_관리자권한_서비스응답반환() throws Exception {
        BudgetCardNoteDto.SaveRequest request = new BudgetCardNoteDto.SaveRequest("변경 안내");
        BudgetCardNoteDto.Response response = new BudgetCardNoteDto.Response("ORDINARY", "변경 안내");
        given(service.save("ORDINARY", request)).willReturn(response);

        assertThat(controller.save("ORDINARY", request).getBody()).isEqualTo(response);
        verify(service).save("ORDINARY", request);

        Method save =
                BudgetCardNoteController.class.getMethod(
                        "save", String.class, BudgetCardNoteDto.SaveRequest.class);
        assertThat(save.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
