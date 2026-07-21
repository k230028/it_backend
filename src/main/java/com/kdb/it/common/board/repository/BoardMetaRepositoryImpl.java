package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.QCblbmm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 공통 게시판 메타 QueryDSL 구현체입니다.
 *
 * <p>사용 중이고 논리 삭제되지 않은 게시판만 노출하며, 화면 노출 순서인 SRE_SQN_NO 오름차순으로 정렬합니다.
 */
@RequiredArgsConstructor
public class BoardMetaRepositoryImpl implements BoardMetaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Cblbmm> findAllActiveOrdered() {
        QCblbmm m = QCblbmm.cblbmm;
        // USE_YN='Y'와 DEL_YN='N'을 함께 만족하는 게시판만 사용자 메뉴와 게시판 홈에 노출합니다.
        return queryFactory
                .selectFrom(m)
                .where(m.useYn.eq("Y").and(m.delYn.eq("N")))
                .orderBy(m.sreSqnNo.asc())
                .fetch();
    }
}
