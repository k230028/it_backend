package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import org.springframework.data.domain.Page;

/** 게시물 동적 쿼리 인터페이스 */
public interface BoardPostRepositoryCustom {
    /**
     * 게시물 목록 조회 — 권한 필터 + 검색 조건 적용
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건
     * @param isAdmin 관리자 여부 (삭제·숨김 게시물도 포함)
     */
    Page<Cblbcm> searchPosts(String blbMngNo, BoardPostDto.SearchCondition cond, boolean isAdmin);

    /**
     * 목록 응답에 필요한 필드만 조회합니다.
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건과 페이지 조건
     * @param isAdmin 관리자 여부
     * @return 권한과 검색 조건을 만족하는 경량 게시물 목록
     */
    Page<BoardPostDto.ListRow> searchPostRows(
            String blbMngNo,
            BoardPostDto.SearchCondition cond,
            boolean isAdmin,
            boolean includePrivatePosts);

    /** Q&amp;A 작성자에게만 비공개 게시물을 포함해 목록을 조회합니다. */
    Page<BoardPostDto.ListRow> searchPostRows(
            String blbMngNo,
            BoardPostDto.SearchCondition cond,
            boolean isAdmin,
            boolean includePrivatePosts,
            String privatePostAuthorEno);

    /** 기존 게시판 목록은 비공개 게시물을 제외하는 기본 정책을 유지합니다. */
    default Page<BoardPostDto.ListRow> searchPostRows(
            String blbMngNo, BoardPostDto.SearchCondition cond, boolean isAdmin) {
        return searchPostRows(blbMngNo, cond, isAdmin, false);
    }
}
