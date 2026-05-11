package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import java.util.List;

/** 게시물 동적 쿼리 인터페이스 */
public interface BoardPostRepositoryCustom {
    /**
     * 게시물 목록 조회 — 권한 필터 + 검색 조건 적용
     *
     * @param blbMngNo      게시판관리번호
     * @param cond          검색 조건
     * @param isAdmin       관리자 여부 (삭제·숨김 게시물도 포함)
     * @param userBbrC      사용자 부서코드 (부서 한정 필터용)
     * @param bbrLmtnUseYn  게시판 담당부서한정 사용 여부
     */
    List<Cblbcm> searchPosts(
        String blbMngNo,
        BoardPostDto.SearchCondition cond,
        boolean isAdmin,
        String userBbrC,
        String bbrLmtnUseYn
    );
}
