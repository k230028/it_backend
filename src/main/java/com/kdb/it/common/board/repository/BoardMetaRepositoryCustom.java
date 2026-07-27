package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import java.util.List;

/** 게시판 메타 동적 쿼리 인터페이스 */
public interface BoardMetaRepositoryCustom {
    /** 사이드바용: USE_YN='Y', DEL_YN='N' 전체 목록, SRE_SQN_NO 오름차순 */
    List<Cblbmm> findAllActiveOrdered();

    /**
     * 사이드바용 목록 조회 — REST 응답 전용 경량 프로젝션.
     *
     * <p>{@link #findAllActiveOrdered()}와 동일한 조건·정렬을 사용한다. {@code BoardMetaService.getAllActive}
     * 전용이며 단건 조회·쓰기 경로는 이 메서드를 사용하지 않는다.
     */
    List<BoardMetaListRow> findAllActiveOrderedRows();
}
