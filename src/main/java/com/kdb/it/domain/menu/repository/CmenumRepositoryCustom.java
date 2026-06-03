package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import java.util.List;

public interface CmenumRepositoryCustom {
    /** WHL_MNU_PTH 접두사로 본인 + 모든 후손 조회 (move 재계산용). */
    List<Cmenum> findSubtreeByPathPrefix(String pathPrefix);

    /** 다음 MNU_ID 채번: 'MNU' + LPAD(SEQ_CMENUM.NEXTVAL, 7, '0'). */
    String nextMnuId();
}
