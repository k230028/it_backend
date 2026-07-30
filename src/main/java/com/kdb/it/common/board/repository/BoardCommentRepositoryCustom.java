package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import java.util.List;

/** 게시물 댓글 트리를 삭제된 부모까지 포함해 조회하는 사용자 정의 저장소 계약입니다. */
public interface BoardCommentRepositoryCustom {
    /** 게시물의 댓글 목록 — 삭제 포함 트리 정렬 (자식 보존용) */
    List<Ccmmtm> findCommentsByPost(String nacMngNo);

    /**
     * 게시물의 댓글 목록 조회 — REST 응답 전용 경량 프로젝션.
     *
     * <p>{@link #findCommentsByPost(String)}와 동일한 조건·정렬(삭제 포함 트리)을 사용한다. {@code
     * BoardCommentService.getComments} 전용이며 단건 조회·쓰기 경로는 이 메서드를 사용하지 않는다.
     */
    List<BoardCommentListRow> findCommentRowsByPost(String nacMngNo);
}
