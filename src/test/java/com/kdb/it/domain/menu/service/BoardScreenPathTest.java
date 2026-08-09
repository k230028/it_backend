package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 게시판 화면경로 규약 테스트.
 *
 * <p>게시판 참조는 별도 컬럼이 아니라 화면경로 문자열 하나다. 이 파싱이 느슨하면 엉뚱한 경로가 게시판 메뉴로 저장되거나(예: {@code /boardroom/x}),
 * 사용자 트리에서 살아 있는 게시판이 감춰진다.
 */
class BoardScreenPathTest {

    @Test
    @DisplayName("게시판관리번호로 화면경로를 만들고 다시 꺼낸다")
    void pathRoundTrip() {
        String path = BoardScreenPath.pathOf("BLBM-0001");

        assertThat(path).isEqualTo("/board/BLBM-0001");
        assertThat(BoardScreenPath.boardNoOf(path)).isEqualTo("BLBM-0001");
    }

    @Test
    @DisplayName("/board/ 접두사로 시작하면 형식이 깨져도 게시판 후보로 판정한다")
    void boardPrefixIsBoardPath() {
        assertThat(BoardScreenPath.isBoardPath("/board/BLBM-0001")).isTrue();
        assertThat(BoardScreenPath.isBoardPath("/board/")).isTrue();
        assertThat(BoardScreenPath.isBoardPath("/board/BLBM-0001/posts")).isTrue();
        assertThat(BoardScreenPath.isBoardPath("/boardroom/BLBM-0001")).isFalse();
        assertThat(BoardScreenPath.isBoardPath(null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "/budget/list", // 접두사 없음
                "/boardroom/BLBM-0001", // 접두사를 포함하는 다른 경로
                "/board/", // 번호 없음
                "/board/ ", // 공백 번호
                "/board/BLBM-0001/posts" // 하위 경로가 더 붙음
            })
    @DisplayName("유효한 게시판 번호 경로가 아니면 게시판관리번호를 돌려주지 않는다")
    void rejectsNonBoardPaths(String srePth) {
        assertThat(BoardScreenPath.boardNoOf(srePth)).isNull();
    }
}
