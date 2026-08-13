package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import java.util.List;

/** 메뉴 하위 트리 조회와 메뉴 ID 채번을 제공하는 사용자 정의 저장소 계약입니다. */
public interface CmenumRepositoryCustom {
    /** WHL_MNU_PTH 접두사로 본인 + 모든 후손 조회 (move 재계산용). */
    List<Cmenum> findSubtreeByPathPrefix(String pathPrefix);

    /**
     * 다음 MNU_ID 채번: {@code SQ_TPRMPP_CMENUM_1.NEXTVAL}을 조회한 뒤 Java {@code String.format("%07d",
     * …)}로 좌측을 0으로 채워 {@code "MNU" + 7자리}를 만든다 (예: {@code MNU0000012}).
     *
     * <p>SQL {@code LPAD}가 아니라 {@code String.format}이므로 시퀀스 값이 7자리를 넘어도 잘리지 않고 자릿수가 늘어난다. 따라서 번호가
     * 조용히 충돌하지는 않으나, {@code MNU_ID} 컬럼 길이를 초과하면 저장 시 실패한다.
     */
    String nextMnuId();

    /**
     * 활성 메뉴 전량을 트리 조립용 경량 프로젝션으로 읽는다.
     *
     * <p>{@code DEL_YN='N'} 조건은 {@code findAllActive()}와 동일하다. {@code IMK_NM} 컬럼이 없는 환경에서는 그 컬럼을
     * select 목록에서 제외하므로 {@code imkNm}이 전부 {@code null}로 온다.
     *
     * @return 활성 메뉴 행 목록. 정렬은 하지 않으며 트리 조립 시 서비스가 정렬한다
     */
    List<MenuTreeRow> findActiveMenuTreeRows();

    /**
     * {@code TPRMPP_CMENUM.IMK_NM} 컬럼이 실제 스키마에 있는지 판정한다.
     *
     * <p>스키마는 런타임에 바뀌지 않으므로 최초 호출 때 한 번만 데이터 사전을 읽고 결과를 캐시한다. 판정 실패는 '없음'으로 접는다.
     *
     * @return 컬럼이 있으면 true. 없거나 판정에 실패하면 false
     */
    boolean isIconColumnPresent();
}
