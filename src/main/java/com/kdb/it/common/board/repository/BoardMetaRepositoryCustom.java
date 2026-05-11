package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import java.util.List;

/** 게시판 메타 동적 쿼리 인터페이스 */
public interface BoardMetaRepositoryCustom {
    /** 사이드바용: USE_YN='Y', DEL_YN='N' 전체 목록, SRE_SQN_NO 오름차순 */
    List<Cblbmm> findAllActiveOrdered();
}
