package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.entity.QCmenum;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
/** QueryDSL 하위 트리 조회와 Oracle 시퀀스 기반 메뉴 ID 채번을 구현합니다. */
public class CmenumRepositoryImpl implements CmenumRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public List<Cmenum> findSubtreeByPathPrefix(String pathPrefix) {
        QCmenum m = QCmenum.cmenum;
        return queryFactory
                .selectFrom(m)
                .where(m.delYn.eq("N"), m.whlMnuPth.startsWith(pathPrefix))
                .fetch();
    }

    @Override
    public String nextMnuId() {
        Object val =
                entityManager
                        .createNativeQuery("SELECT SQ_TPRMPP_CMENUM_1.NEXTVAL FROM DUAL")
                        .getSingleResult();
        long n = ((Number) val).longValue();
        return "MNU" + String.format("%07d", n);
    }

    /** IMK_NM 컬럼 존재 여부 캐시. 스키마는 런타임에 바뀌지 않으므로 최초 1회만 판정한다. */
    private volatile Boolean iconColumnPresent;

    @Override
    public boolean isIconColumnPresent() {
        Boolean cached = iconColumnPresent;
        if (cached != null) return cached;
        boolean present = probeIconColumn();
        iconColumnPresent = present;
        return present;
    }

    /**
     * 데이터 사전에서 TPRMPP_CMENUM.IMK_NM을 찾는다.
     *
     * <p>접속 계정(ITPAPP)과 객체 소유 스키마(ITPOWN)가 달라 USER_TAB_COLUMNS로는 보이지 않는다. 세션 CURRENT_SCHEMA를 소유자로
     * 놓고 ALL_TAB_COLUMNS를 본다.
     *
     * <p>조회 실패를 '없음'으로 접는 것은 "Repository는 DB 예외를 전파한다"(it_backend/CLAUDE.md §4)에 대한 의도적 예외다. 업무 조회가
     * 아니라 카탈로그 탐지이며, 반대로 판정하면 메뉴 조회 전체가 ORA-00904로 죽는다. 이 방향의 오판은 아이콘이 기본값으로 표시될 뿐이다.
     */
    private boolean probeIconColumn() {
        try {
            Number count =
                    (Number)
                            entityManager
                                    .createNativeQuery(
                                            """
                                            SELECT COUNT(*) FROM ALL_TAB_COLUMNS
                                             WHERE OWNER = SYS_CONTEXT('USERENV','CURRENT_SCHEMA')
                                               AND TABLE_NAME = 'TPRMPP_CMENUM'
                                               AND COLUMN_NAME = 'IMK_NM'
                                            """)
                                    .getSingleResult();
            return count.intValue() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<MenuTreeRow> findActiveMenuTreeRows() {
        QCmenum m = QCmenum.cmenum;
        return queryFactory.select(menuTreeRowProjection(m)).from(m).where(m.delYn.eq("N")).fetch();
    }

    /**
     * IMK_NM 유무에 따라 select 목록이 갈리는 생성자 프로젝션.
     *
     * <p>컬럼이 없을 때 null 리터럴을 select에 넣지 않고 목록에서 아예 뺀다. 생성되는 SQL에 IMK_NM이 등장할 여지가 없어야 ORA-00904가 원천
     * 차단되며, Hibernate의 typed-null 렌더링 동작에 의존하지 않는다. 9인자 보조 생성자가 imkNm을 null로 채운다.
     */
    private ConstructorExpression<MenuTreeRow> menuTreeRowProjection(QCmenum m) {
        if (isIconColumnPresent()) {
            return Projections.constructor(
                    MenuTreeRow.class,
                    m.mnuId,
                    m.hrkMnuId,
                    m.mnuNm,
                    m.mnuTpC,
                    m.srePth,
                    m.mnuSotSqnSno,
                    m.hidYn,
                    m.mnuDep,
                    m.whlMnuPth,
                    m.imkNm);
        }
        return Projections.constructor(
                MenuTreeRow.class,
                m.mnuId,
                m.hrkMnuId,
                m.mnuNm,
                m.mnuTpC,
                m.srePth,
                m.mnuSotSqnSno,
                m.hidYn,
                m.mnuDep,
                m.whlMnuPth);
    }
}
