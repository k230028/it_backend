package com.kdb.it.common.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("댓글 저장과 트리 조회")
class BoardCommentRepositoryIt extends AbstractOracleRepositoryTest {

    @Autowired BoardCommentRepository commentRepository;

    @Test
    @DisplayName("별도 노출여부 없이 댓글을 저장하고 삭제된 부모도 트리 조회에 포함한다")
    void savesAndFindsCommentsWithoutExposureColumn() {
        String postId = "TEST-COMMENT";
        commentRepository.saveAllAndFlush(
                List.of(
                        comment(900000001L, postId, 900000001L, 0, "Y"),
                        comment(900000002L, postId, 900000001L, 1, "N")));

        assertThat(commentRepository.findCommentsByPost(postId))
                .extracting(Ccmmtm::getCmmtMngNo)
                .containsExactly(900000001L, 900000002L);
    }

    @Test
    @DisplayName("findCommentRowsByPost는 삭제된 부모를 포함해 findCommentsByPost와 동일한 정렬·11개 필드를 반환한다")
    void findCommentRowsByPost_matchesEntityQueryIncludingDeletedParent() {
        String postId = "TEST-COMMENT-ROW";
        commentRepository.saveAllAndFlush(
                List.of(
                        // 삭제된 부모(delYn='Y')도 자식 보존을 위해 트리 조회에 포함되어야 한다
                        comment(900000101L, postId, 900000101L, 0, "Y"),
                        comment(900000102L, postId, 900000101L, 1, "N")));

        List<Ccmmtm> entities = commentRepository.findCommentsByPost(postId);
        List<BoardCommentListRow> rows = commentRepository.findCommentRowsByPost(postId);

        assertThat(entities).hasSize(2);
        assertThat(rows).hasSize(2);

        // 그룹번호·그룹순서 기준 정렬이 엔티티·프로젝션 양쪽에서 동일하다
        assertThat(entities)
                .extracting(Ccmmtm::getCmmtMngNo)
                .containsExactly(900000101L, 900000102L);
        assertThat(rows)
                .extracting(BoardCommentListRow::cmmtMngNo)
                .containsExactly(900000101L, 900000102L);

        // 프로젝션 11개 필드가 엔티티 값과 위치별로 정확히 일치한다 (삭제된 부모의 delYn='Y'도 그대로 노출)
        for (int i = 0; i < entities.size(); i++) {
            Ccmmtm e = entities.get(i);
            BoardCommentListRow r = rows.get(i);
            assertThat(r.cmmtMngNo()).isEqualTo(e.getCmmtMngNo());
            assertThat(r.nacMngNo()).isEqualTo(e.getNacMngNo());
            assertThat(r.cmmtCone()).isEqualTo(e.getCmmtCone());
            assertThat(r.cmmtGrpNo()).isEqualTo(e.getCmmtGrpNo());
            assertThat(r.cmmtGrpSqn()).isEqualTo(e.getCmmtGrpSqn());
            assertThat(r.cmmtGrpLev()).isEqualTo(e.getCmmtGrpLev());
            assertThat(r.hrkCmmtMngNo()).isEqualTo(e.getHrkCmmtMngNo());
            assertThat(r.delYn()).isEqualTo(e.getDelYn());
            assertThat(r.fstEnrUsid()).isEqualTo(e.getFstEnrUsid());
            assertThat(r.fstEnrDtm()).isEqualTo(e.getFstEnrDtm());
            assertThat(r.lstChgDtm()).isEqualTo(e.getLstChgDtm());
        }
    }

    @Test
    @DisplayName("댓글 단건 association 조회는 실제 대상 게시물과 삭제 상태를 함께 검증한다")
    void findCommentAssociation_requiresTargetPost() {
        commentRepository.saveAndFlush(comment(900000201L, "POST-R4-A", 900000201L, 0, "N"));

        assertThat(
                        commentRepository.findByCmmtMngNoAndNacMngNoAndDelYn(
                                900000201L, "POST-R4-A", "N"))
                .isPresent();
        assertThat(
                        commentRepository.findByCmmtMngNoAndNacMngNoAndDelYn(
                                900000201L, "POST-R4-B", "N"))
                .isEmpty();
    }

    private Ccmmtm comment(Long id, String postId, Long groupId, int sequence, String deleted) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        return Ccmmtm.builder()
                .cmmtMngNo(id)
                .nacMngNo(postId)
                .cmmtCone("댓글")
                .cmmtGrpNo(groupId)
                .cmmtGrpSqn(sequence)
                .cmmtGrpLev(sequence + 10) // sqn과 다른 값으로 분리하여 필드 순서 실수를 검출
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
