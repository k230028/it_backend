package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import java.util.List;

/** 게시물 댓글 트리를 삭제된 부모까지 포함해 조회하는 사용자 정의 저장소 계약입니다. */
public interface BoardCommentRepositoryCustom {
    /** 게시물의 댓글 목록 — 삭제 포함 트리 정렬 (자식 보존용) */
    List<Ccmmtm> findCommentsByPost(String nacMngNo);
}
