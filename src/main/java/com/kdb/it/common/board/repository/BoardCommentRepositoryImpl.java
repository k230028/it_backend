package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.entity.QCcmmtm;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 공통 게시판 댓글 트리 조회용 QueryDSL 구현체입니다.
 *
 * <p>게시물 관리번호를 기준으로 댓글을 조회하고, 그룹번호·그룹순서 기준으로 트리 표시 순서를 보존합니다.
 */
@RequiredArgsConstructor
public class BoardCommentRepositoryImpl implements BoardCommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Ccmmtm> findCommentsByPost(String nacMngNo) {
        QCcmmtm c = QCcmmtm.ccmmtm;
        return queryFactory
                .selectFrom(c)
                .where(c.nacMngNo.eq(nacMngNo))
                .orderBy(c.cmmtGrpNo.asc(), c.cmmtGrpSqn.asc())
                .fetch();
    }

    /**
     * 게시물의 댓글 목록 조회 — REST 응답 전용 경량 프로젝션. {@link #findCommentsByPost(String)}와 동일한 조건·정렬을 재사용합니다.
     */
    @Override
    public List<BoardCommentListRow> findCommentRowsByPost(String nacMngNo) {
        QCcmmtm c = QCcmmtm.ccmmtm;
        return queryFactory
                .select(listRowProjection(c))
                .from(c)
                .where(c.nacMngNo.eq(nacMngNo))
                .orderBy(c.cmmtGrpNo.asc(), c.cmmtGrpSqn.asc())
                .fetch();
    }

    /**
     * {@link BoardCommentListRow} 11개 필드에 대한 QueryDSL 생성자 프로젝션. 컴포넌트 순서와 select 인자 순서가 정확히 일치해야
     * 합니다.
     */
    private ConstructorExpression<BoardCommentListRow> listRowProjection(QCcmmtm c) {
        return Projections.constructor(
                BoardCommentListRow.class,
                c.cmmtMngNo,
                c.nacMngNo,
                c.cmmtCone,
                c.cmmtGrpNo,
                c.cmmtGrpSqn,
                c.cmmtGrpLev,
                c.hrkCmmtMngNo,
                c.delYn,
                c.fstEnrUsid,
                c.fstEnrDtm,
                c.lstChgDtm);
    }
}
