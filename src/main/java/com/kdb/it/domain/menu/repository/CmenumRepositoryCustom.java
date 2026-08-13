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
     * <p>{@code DEL_YN='N'} 조건은 {@code findAllActive()}와 동일하다. {@code iconColumnPresent}가 {@code
     * false}이면 {@code IMK_NM} 컬럼을 select 목록에서 제외하므로 {@code imkNm}이 전부 {@code null}로 온다.
     *
     * <p>호출부가 {@link #isIconColumnPresent()} 판정값을 인자로 넘기는 이유는, 그 값이 프로세스 수명 내내 고정 캐시가 아니라 매 호출 재판정될
     * 수 있기 때문이다({@link #isIconColumnPresent()} 참조). 이 메서드 내부에서 다시 판정하면 같은 요청 안에서도 select 목록과 아이콘 채움
     * 여부가 서로 다른 판정 결과를 쓸 수 있어, 호출부가 한 번만 판정한 값을 그대로 전달한다.
     *
     * @param iconColumnPresent {@code TPRMPP_CMENUM.IMK_NM} 컬럼 존재 여부
     * @return 활성 메뉴 행 목록. 정렬은 하지 않으며 트리 조립 시 서비스가 정렬한다
     */
    List<MenuTreeRow> findActiveMenuTreeRows(boolean iconColumnPresent);

    /**
     * {@code TPRMPP_CMENUM.IMK_NM} 컬럼이 실제 스키마에 있는지 판정한다.
     *
     * <p>스키마는 런타임에 바뀌지 않으므로 실제 판정에 성공한 결과만 캐시하고, 이후 호출은 캐시를 그대로 돌려준다. 판정 자체가 실패하면(예: 일시적 DB 장애) 이번
     * 호출만 '없음'으로 접고 캐시하지 않으므로 다음 호출에서 다시 판정한다.
     *
     * @return 컬럼이 있으면 true. 없거나 이번 호출의 판정에 실패하면 false
     */
    boolean isIconColumnPresent();
}
