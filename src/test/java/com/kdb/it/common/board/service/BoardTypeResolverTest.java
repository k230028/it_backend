package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.exception.CustomGeneralException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardTypeResolverTest {

    @Mock private BoardMetaRepository boardMetaRepository;

    @InjectMocks private BoardTypeResolver resolver;

    @Test
    void activeSpecialBoardMustBeUnique() {
        given(boardMetaRepository.findAllByItPtlBlbTcAndUseYnAndDelYn("004", "Y", "N"))
                .willReturn(List.of(board("BLBM-0426"), board("BLBM-9001")));

        assertThatThrownBy(() -> resolver.requireUniqueActiveBoard("004"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("고유 게시판");
    }

    @Test
    void returnsOnlyActiveBoardForTypeCode() {
        Cblbmm faq = board("BLBM-0426");
        given(boardMetaRepository.findAllByItPtlBlbTcAndUseYnAndDelYn("004", "Y", "N"))
                .willReturn(List.of(faq));

        assertThat(resolver.requireUniqueActiveBoard("004")).isSameAs(faq);
    }

    private static Cblbmm board(String blbMngNo) {
        return Cblbmm.builder()
                .blbMngNo(blbMngNo)
                .blbNm("FAQ")
                .itPtlBlbTc("004")
                .repUseYn("N")
                .cmmtUseYn("N")
                .flEsnYn("N")
                .hedTagUseYn("N")
                .sreSqnNo(1)
                .useYn("Y")
                .delYn("N")
                .build();
    }
}
