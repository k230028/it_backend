package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.entity.QCcmmtm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;

/**
 * 공통 게시판 댓글 트리 조회용 QueryDSL 구현체입니다.
 *
 * <p>게시물 관리번호를 기준으로 댓글을 조회하고, 그룹번호·그룹순서 기준으로 트리 표시 순서를 보존합니다.</p>
 */
@RequiredArgsConstructor
public class BoardCommentRepositoryImpl implements BoardCommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Ccmmtm> findCommentsByPost(String nacMngNo) {
        QCcmmtm c = QCcmmtm.ccmmtm;
        return queryFactory.selectFrom(c)
            .where(c.nacMngNo.eq(nacMngNo))
            .orderBy(c.cmmtGrpNo.asc(), c.cmmtGrpSqn.asc())
            .fetch();
    }
}
