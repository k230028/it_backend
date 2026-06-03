package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.entity.QCmenum;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CmenumRepositoryImpl implements CmenumRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public List<Cmenum> findSubtreeByPathPrefix(String pathPrefix) {
        QCmenum m = QCmenum.cmenum;
        return queryFactory.selectFrom(m)
                .where(m.delYn.eq("N"), m.whlMnuPth.startsWith(pathPrefix))
                .fetch();
    }

    @Override
    public String nextMnuId() {
        Object val = entityManager
                .createNativeQuery("SELECT SEQ_CMENUM.NEXTVAL FROM DUAL")
                .getSingleResult();
        long n = ((Number) val).longValue();
        return "MNU" + String.format("%07d", n);
    }
}
