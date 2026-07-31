package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import java.util.List;

/** 메뉴 하위 트리 조회와 메뉴 ID 채번을 제공하는 사용자 정의 저장소 계약입니다. */
public interface CmenumRepositoryCustom {
    /** WHL_MNU_PTH 접두사로 본인 + 모든 후손 조회 (move 재계산용). */
    List<Cmenum> findSubtreeByPathPrefix(String pathPrefix);

    /**
     * 다음 MNU_ID 채번: {@code SQ_TPRMPP_CMENUM_1.NEXTVAL}을 조회한 뒤 Java {@code String.format("%07d", …)}로 좌측을 0으로 채워
     * {@code "MNU" + 7자리}를 만든다 (예: {@code MNU0000012}).
     *
     * <p>SQL {@code LPAD}가 아니라 {@code String.format}이므로 시퀀스 값이 7자리를 넘어도 잘리지 않고 자릿수가 늘어난다. 따라서 번호가
     * 조용히 충돌하지는 않으나, {@code MNU_ID} 컬럼 길이를 초과하면 저장 시 실패한다.
     */
    String nextMnuId();
}
