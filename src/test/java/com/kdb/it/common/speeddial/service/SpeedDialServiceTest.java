package com.kdb.it.common.speeddial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.same;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.service.BoardTypeResolver;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.speeddial.dto.SpeedDialDto;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpeedDialServiceTest {

    @Mock private BoardTypeResolver boardTypeResolver;
    @Mock private BoardPostService boardPostService;
    @Mock private BoardPostRepository boardPostRepository;
    @Mock private CustomUserDetails user;

    @Test
    void qnaUsesResolvedTypeBoardAndNeverClientBoardId() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE))
                .willReturn(board("BLBM-CHANGED", "005"));
        given(boardPostService.createPost(eq("BLBM-CHANGED"), any(), same(user)))
                .willReturn("NAC-2026-0001");

        String postId = service.createQna(validRequest(), user);

        assertThat(postId).isEqualTo("NAC-2026-0001");
        verify(boardPostService).createPost(eq("BLBM-CHANGED"), any(), same(user));
    }

    @Test
    void rejectsExternalScreenUrl() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);

        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "정보화사업", "https://example.com", "OTHER", "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("화면 URL");
    }

    private static SpeedDialDto.QnaCreateRequest validRequest() {
        return new SpeedDialDto.QnaCreateRequest(
                "정보화사업", "/info/projects?tab=active", "IMPROVEMENT", "<p>검색 조건을 저장해주세요.</p>");
    }

    private static Cblbmm board(String id, String type) {
        return Cblbmm.builder()
                .blbMngNo(id)
                .blbNm(type.equals("005") ? "Q&A" : "FAQ")
                .itPtlBlbTc(type)
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
