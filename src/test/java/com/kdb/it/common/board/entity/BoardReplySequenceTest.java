package com.kdb.it.common.board.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardReplySequenceTest {

    @Test
    @DisplayName("게시물 그룹 순서 이동은 깊이와 무관하게 현재 순서를 한 칸 증가시킨다")
    void postShift_incrementsSequenceOnly() {
        Cblbcm post =
                Cblbcm.builder()
                        .nacMngNo("NAC-TEST-1")
                        .blbMngNo("BOARD-1")
                        .nacNm("형제 게시물")
                        .nacGrpSqn(3)
                        .nacGrpLev(1)
                        .build();

        post.shiftGroupSequence();

        assertThat(post.getNacGrpSqn()).isEqualTo(4);
        assertThat(post.getNacGrpLev()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 그룹 순서 이동은 깊이와 무관하게 현재 순서를 한 칸 증가시킨다")
    void commentShift_incrementsSequenceOnly() {
        Ccmmtm comment =
                Ccmmtm.builder()
                        .cmmtMngNo(1L)
                        .nacMngNo("NAC-TEST-1")
                        .cmmtCone("형제 댓글")
                        .cmmtGrpNo(1L)
                        .cmmtGrpSqn(3)
                        .cmmtGrpLev(1)
                        .build();

        comment.shiftGroupSequence();

        assertThat(comment.getCmmtGrpSqn()).isEqualTo(4);
        assertThat(comment.getCmmtGrpLev()).isEqualTo(1);
    }
}
