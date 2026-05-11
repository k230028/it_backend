package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import java.util.List;

public interface BoardCommentRepositoryCustom {
    /** 게시물의 댓글 목록 — 삭제 포함 트리 정렬 (자식 보존용) */
    List<Ccmmtm> findCommentsByPost(String nacMngNo);
}
