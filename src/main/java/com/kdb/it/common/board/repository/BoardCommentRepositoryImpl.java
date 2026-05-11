package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.entity.QCcmmtm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RequiredArgsConstructor
public class BoardCommentRepositoryImpl implements BoardCommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Ccmmtm> findCommentsByPost(String nacMngNo) {
        QCcmmtm c = QCcmmtm.ccmmtm;
        return queryFactory.selectFrom(c)
            .where(c.nacMngNo.eq(nacMngNo).and(c.sreYn.eq("Y")))
            .orderBy(c.cmmtGrpNo.asc(), c.cmmtGrpSqn.asc())
            .fetch();
    }
}
