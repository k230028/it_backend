package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 게시판 공통 조회 헬퍼의 성공·실패·문자열 안전 변환을 검증합니다. */
class BoardLookupSupportTest {

    @Test
    @DisplayName("사용자용 활성 게시판을 조회한다")
    void findsActiveBoard() {
        BoardMetaRepository repository = mock(BoardMetaRepository.class);
        Cblbmm board = mock(Cblbmm.class);
        given(repository.findByBlbMngNoAndUseYnAndDelYn("B-1", "Y", "N"))
                .willReturn(Optional.of(board));

        assertThat(BoardLookupSupport.findUserActiveBoard(repository, "B-1")).isSameAs(board);
    }

    @Test
    @DisplayName("게시물 일반·수정 조회를 수행한다")
    void findsPostForReadAndUpdate() {
        BoardPostRepository repository = mock(BoardPostRepository.class);
        Cblbcm post = mock(Cblbcm.class);
        given(repository.findByBlbMngNoAndNacMngNoAndDelYn("B-1", "P-1", "N"))
                .willReturn(Optional.of(post));
        given(repository.findByBlbMngNoAndNacMngNoAndDelYnForUpdate("B-1", "P-1", "N"))
                .willReturn(Optional.of(post));

        assertThat(BoardLookupSupport.findPost(repository, "B-1", "P-1")).isSameAs(post);
        assertThat(BoardLookupSupport.findPostForUpdate(repository, "B-1", "P-1")).isSameAs(post);
    }

    @Test
    @DisplayName("조회 대상이 없으면 NotFoundException을 던지고 null 문자열은 빈 문자열로 바꾼다")
    void handlesMissingAndNull() {
        BoardMetaRepository metaRepository = mock(BoardMetaRepository.class);
        BoardPostRepository postRepository = mock(BoardPostRepository.class);
        given(metaRepository.findByBlbMngNoAndUseYnAndDelYn("B-1", "Y", "N"))
                .willReturn(Optional.empty());
        given(postRepository.findByBlbMngNoAndNacMngNoAndDelYn("B-1", "P-1", "N"))
                .willReturn(Optional.empty());
        given(postRepository.findByBlbMngNoAndNacMngNoAndDelYnForUpdate("B-1", "P-1", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> BoardLookupSupport.findUserActiveBoard(metaRepository, "B-1"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> BoardLookupSupport.findPost(postRepository, "B-1", "P-1"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> BoardLookupSupport.findPostForUpdate(postRepository, "B-1", "P-1"))
                .isInstanceOf(NotFoundException.class);
        assertThat(BoardLookupSupport.safe(null)).isEmpty();
        assertThat(BoardLookupSupport.safe("ok")).isEqualTo("ok");
    }
}
