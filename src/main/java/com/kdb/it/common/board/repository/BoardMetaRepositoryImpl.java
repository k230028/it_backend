package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.QCblbmm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RequiredArgsConstructor
public class BoardMetaRepositoryImpl implements BoardMetaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Cblbmm> findAllActiveOrdered() {
        QCblbmm m = QCblbmm.cblbmm;
        return queryFactory.selectFrom(m)
            .where(m.useYn.eq("Y").and(m.delYn.eq("N")))
            .orderBy(m.sreSqnNo.asc())
            .fetch();
    }
}
