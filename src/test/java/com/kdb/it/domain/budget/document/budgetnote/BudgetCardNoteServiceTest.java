package com.kdb.it.domain.budget.document.budgetnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예산작성 카드 참고사항의 조회·저장 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class BudgetCardNoteServiceTest {

    @Mock private GuideDocRepository guideDocRepository;

    @InjectMocks private BudgetCardNoteService service;

    @Test
    @DisplayName("공개 조회는 카드 순서대로 등록된 참고사항을 반환한다")
    void getNotes_등록문서_카드순서반환() {
        given(guideDocRepository.findAllByDocMngNoStartingWithAndDelYn("BNOTE-", "N"))
                .willReturn(
                        List.of(
                                note("BNOTE-ORDINARY", "BUDGET_CARD_NOTE_ORDINARY", "경상 안내"),
                                note("BNOTE-IT", "BUDGET_CARD_NOTE_IT_PROJECT", "사업 안내")));

        assertThat(service.getNotes())
                .containsExactly(
                        new BudgetCardNoteDto.Response("IT_PROJECT", "사업 안내"),
                        new BudgetCardNoteDto.Response("ORDINARY", "경상 안내"));
    }

    @Test
    @DisplayName("기존 카드 참고사항 저장은 같은 문서의 본문을 수정한다")
    void save_기존문서_본문수정() {
        Bgdocm document = note("BNOTE-COST", "BUDGET_CARD_NOTE_COST", "이전 안내");
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                "BUDGET_CARD_NOTE_COST", "BNOTE-", "N"))
                .willReturn(Optional.of(document));

        BudgetCardNoteDto.Response result =
                service.save("COST", new BudgetCardNoteDto.SaveRequest("  변경 안내  "));

        assertThat(result).isEqualTo(new BudgetCardNoteDto.Response("COST", "변경 안내"));
        assertThat(document.getNacTxtInf()).isEqualTo("변경 안내");
    }

    @Test
    @DisplayName("미등록 카드 참고사항 저장은 고정 문서번호로 새 행을 만든다")
    void save_미등록문서_고정문서번호생성() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                "BUDGET_CARD_NOTE_IT_PROJECT", "BNOTE-", "N"))
                .willReturn(Optional.empty());
        given(guideDocRepository.save(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BudgetCardNoteDto.Response result =
                service.save("IT_PROJECT", new BudgetCardNoteDto.SaveRequest("사업 안내"));

        ArgumentCaptor<Bgdocm> captor = ArgumentCaptor.forClass(Bgdocm.class);
        verify(guideDocRepository).save(captor.capture());
        assertThat(captor.getValue().getDocMngNo()).isEqualTo("BNOTE-IT-PROJECT");
        assertThat(captor.getValue().getDocTtlCone()).isEqualTo("BUDGET_CARD_NOTE_IT_PROJECT");
        assertThat(captor.getValue().getNacTxtInf()).isEqualTo("사업 안내");
        assertThat(result).isEqualTo(new BudgetCardNoteDto.Response("IT_PROJECT", "사업 안내"));
    }

    @Test
    @DisplayName("지원하지 않는 카드 또는 빈 참고사항은 저장하지 않는다")
    void save_잘못된요청_거부() {
        assertThatThrownBy(() -> service.save("UNKNOWN", new BudgetCardNoteDto.SaveRequest("안내")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("COST", new BudgetCardNoteDto.SaveRequest("  ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("COST", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("COST", new BudgetCardNoteDto.SaveRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Bgdocm note(String docMngNo, String title, String content) {
        return Bgdocm.builder()
                .docMngNo(docMngNo)
                .docTtlCone(title)
                .nacTxtInf(content)
                .build();
    }
}
