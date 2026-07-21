package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("댓글 저장과 트리 조회")
class BoardCommentRepositoryIt extends AbstractOracleRepositoryTest {

    @Autowired
    BoardCommentRepository commentRepository;

    @Test
    @DisplayName("별도 노출여부 없이 댓글을 저장하고 삭제된 부모도 트리 조회에 포함한다")
    void savesAndFindsCommentsWithoutExposureColumn() {
        String postId = "TEST-COMMENT";
        commentRepository.saveAllAndFlush(List.of(
                comment(900000001L, postId, 900000001L, 0, "Y"),
                comment(900000002L, postId, 900000001L, 1, "N")));

        assertThat(commentRepository.findCommentsByPost(postId))
                .extracting(Ccmmtm::getCmmtMngNo)
                .containsExactly(900000001L, 900000002L);
    }

    private Ccmmtm comment(Long id, String postId, Long groupId, int sequence, String deleted) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        return Ccmmtm.builder()
                .cmmtMngNo(id)
                .nacMngNo(postId)
                .cmmtCone("댓글")
                .cmmtGrpNo(groupId)
                .cmmtGrpSqn(sequence)
                .cmmtGrpLev(sequence)
                .fstEnrUsid("TEST")
                .fstEnrDtm(now)
                .lstChgUsid("TEST")
                .lstChgDtm(now)
                .delYn(deleted)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .build();
    }
}
