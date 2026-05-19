package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.entity.QCinfmm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 마스터 QueryDSL 구현체.
 *
 * <p>Spring Data JPA 네이밍 규약({@code *Impl})에 따라 {@link CinfmmRepository} 인터페이스와
 * 자동 합성되며, 본 클래스는 별도 {@code @Repository} 등록 없이 발견된다.</p>
 */
@RequiredArgsConstructor
public class CinfmmRepositoryImpl implements CinfmmRepositoryCustom {

    private final JPAQueryFactory query;

    private static final QCinfmm c = QCinfmm.cinfmm;

    @Override
    public Page<Cinfmm> findInbox(String rcvUsid, Boolean unreadOnly, Pageable pageable) {
        var where = c.rcvUsid.eq(rcvUsid)
            .and(c.delYn.eq("N"));
        if (Boolean.TRUE.equals(unreadOnly)) {
            where = where.and(c.rddYn.eq("N"));
        }

        List<Cinfmm> rows = query
            .selectFrom(c)
            .where(where)
            .orderBy(c.fstEnrDtm.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = query
            .select(c.count())
            .from(c)
            .where(where)
            .fetchOne();

        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public long countUnread(String rcvUsid) {
        Long count = query
            .select(c.count())
            .from(c)
            .where(
                c.rcvUsid.eq(rcvUsid),
                c.delYn.eq("N"),
                c.rddYn.eq("N")
            )
            .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public long markAllReadByRcvUsid(String rcvUsid) {
        return query
            .update(c)
            .set(c.rddYn, "Y")
            .set(c.rddDtm, LocalDateTime.now())
            .where(
                c.rcvUsid.eq(rcvUsid),
                c.delYn.eq("N"),
                c.rddYn.eq("N")
            )
            .execute();
    }
}
